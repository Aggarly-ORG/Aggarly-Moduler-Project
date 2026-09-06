# Handoff Report: User Service & Architecture Survey (Gen 3)

## 1. Observation
1. **Account Deactivation Session Retention**:
   - `src/main/java/com/luna/aggarly/user/service/impl/UserProfileServiceImpl.java:172-179`:
     ```java
     @Override
     @Transactional
     public void deactivateAccount(UUID userId) {
         User user = findUserById(userId);
         user.setDeleted(true);
         userRepository.save(user);
         log.info("Account deactivated (soft-deleted) for user: {}", user.getEmail());
     }
     ```
     `refreshTokenRepository.revokeAllUserTokens(user)` is missing, whereas `AuthServiceImpl.java:493-499` includes it.
   - `src/main/java/com/luna/aggarly/user/security/UserPrincipal.java:72-74`:
     ```java
     @Override
     public boolean isEnabled() {
         return true;
     }
     ```
     Unconditionally returns `true` without checking `user.isDeleted()`.

2. **Controller Layer Direct Repository Access**:
   - `src/main/java/com/luna/aggarly/user/controller/UserController.java:31-32, 53-61`:
     ```java
     private final UserRepository userRepository;
     private final UserMapper userMapper;
     ...
     users = userRepository.searchUsers(query.trim(), PageRequest.of(0, max)).stream()
             .filter(u -> currentUserId == null || !u.getId().equals(currentUserId))
             .toList();
     ```
     Zero service layer abstraction; database query and business logic executed inside HTTP controller methods without transaction boundaries.

3. **Duplicated & Dead Service Methods**:
   - `src/main/java/com/luna/aggarly/user/service/AuthService.java:16-26` and `AuthServiceImpl.java:293-320, 365-383, 426-500`:
     Declares and implements `getCurrentUserProfile`, `updateCurrentUserProfile`, `changePassword`, `sendPhoneOtp`, `verifyPhone`, `becomeHost`, `deactivateAccount`.
   - `src/main/java/com/luna/aggarly/user/controller/AuthController.java:1-112`:
     Never calls any of these 7 methods. All are routed via `UserProfileController` to `UserProfileService`.

4. **OTP Timing Side-Channel & Premature Deletion**:
   - `src/main/java/com/luna/aggarly/user/service/impl/OtpServiceImpl.java:83-100`:
     ```java
     storedOtp = redisTemplate.opsForValue().get(key);
     if (storedOtp != null) {
         redisTemplate.delete(key);
     }
     ...
     return code.trim().equals(storedOtp);
     ```
     Deletes Redis key prior to validation; uses non-constant-time equality.

5. **Jakarta Validation Absences**:
   - `src/main/java/com/luna/aggarly/user/dto/request/ConfirmMfaRequest.java:3`:
     `public record ConfirmMfaRequest(String token,String totpCode) {}` has 0 annotations.
   - `src/main/java/com/luna/aggarly/user/dto/request/UserProfileUpdate.java:6-13`:
     Contains 0 validation annotations.
   - `src/main/java/com/luna/aggarly/user/controller/AuthController.java:58`:
     `public ResponseEntity<ApiResponse<Void>> logout(@RequestParam("refreshToken") String refreshToken)` passes refresh token in URL query params.

6. **Inverted Common-to-User Coupling & Foreign Repository Injections**:
   - `src/main/java/com/luna/aggarly/common/security/SecurityConfig.java:7-8` imports `com.luna.aggarly.user.security.oauth2.*`.
   - `src/main/java/com/luna/aggarly/common/security/jwt/JwtService.java:3-4, 54` imports `User` and defines `generateToken(User user)`.
   - `src/main/java/com/luna/aggarly/chat/service/impl/ConversationServiceImpl.java:20-21, 42` directly injects `com.luna.aggarly.user.repository.UserRepository`.
   - `src/main/java/com/luna/aggarly/notification/service/impl/NotificationDispatcherImpl.java:14-15, 34` directly injects `com.luna.aggarly.user.repository.UserRepository`.

## 2. Logic Chain
1. *From Obs 1*: `UserProfileController.deactivateAccount` delegates to `UserProfileServiceImpl.deactivateAccount`, which soft-deletes `User` but does not revoke refresh tokens. When the client calls `POST /api/v1/auth/refresh` with their existing refresh token, `AuthServiceImpl.refresh` fetches the `RefreshToken` entity. Because `UserPrincipal.isEnabled()` always returns `true`, the session remains valid, and the deactivated user retains API access.
2. *From Obs 2*: `UserController` directly handles persistence and filtering logic. This breaks the Single Responsibility Principle, violates layered architecture, leaves queries unmanaged by `@Transactional(readOnly = true)`, and couples presentation directly to database schema.
3. *From Obs 3*: Having duplicate method signatures across `AuthService` and `UserProfileService` creates confusion. Because `AuthController` does not call them, those in `AuthServiceImpl` are dead code, diverging in implementation details (e.g. session revocation).
4. *From Obs 4*: In `OtpServiceImpl`, calling `redisTemplate.delete(key)` before `code.trim().equals(storedOtp)` destroys the user's OTP on their very first typo. Furthermore, `code.trim().equals(storedOtp)` executes variable-time character comparisons, leaking timing information.
5. *From Obs 5*: Controllers using `@Valid @RequestBody ConfirmMfaRequest` or `UserProfileUpdate` believe they are protected by bean validation. Because the underlying DTO fields have no constraint annotations, validation is bypassed completely.
6. *From Obs 6*: Foundational security in `common` importing domain code from `user` creates cyclic modular dependencies. Foreign modules (`chat`, `notification`) injecting `UserRepository` directly couple feature databases to the user internal data layer rather than domain services.

## 3. Caveats
- No changes to source code were made (Audit & Report Only constraint).
- Unit tests could not be run synchronously due to tool execution permissions timing out, but assertions are backed by direct line-by-line inspection of source files and existing test files (`AuthServiceImplTest.java`).
- No other caveats.

## 4. Conclusion
The service layer in `com.luna.aggarly.user` exhibits solid baseline functionality (such as Refresh Token Rotation grace windows and standardized `ApiResponse` formatting), but suffers from critical architectural bugs:
- An account deactivation bypass (refresh tokens unrevoked + `UserPrincipal.isEnabled() == true`).
- A controller bypassing service abstraction (`UserController`).
- Dead code / dual service duplication between `AuthService` and `UserProfileService`.
- OTP timing vulnerability and single-typo destruction.
- Credential query-parameter leakage and missing DTO validations.
- Cross-module boundary violations (circular dependency from `common` to `user`, and direct `UserRepository` injection by `chat` and `notification`).

Detailed findings with code remediation snippets are fully documented in:
`e:\java project\aggarly\.agents\teamwork_preview_explorer_survey_1_gen3\survey_services_architecture.md`.

## 5. Verification Method
1. Inspect `src/main/java/com/luna/aggarly/user/service/impl/UserProfileServiceImpl.java:172-179` and verify that `refreshTokenRepository.revokeAllUserTokens(user)` is not called.
2. Inspect `src/main/java/com/luna/aggarly/user/security/UserPrincipal.java:72-74` and verify `isEnabled()` returns constant `true`.
3. Inspect `src/main/java/com/luna/aggarly/user/controller/UserController.java:31-32` and verify direct injection of `UserRepository`.
4. Inspect `src/main/java/com/luna/aggarly/user/service/impl/OtpServiceImpl.java:90-99` and verify premature `redisTemplate.delete` and `equals` call.
5. Inspect `src/main/java/com/luna/aggarly/user/dto/request/ConfirmMfaRequest.java` and confirm complete absence of Jakarta validation annotations.
6. Invalidation Condition: If `UserProfileServiceImpl.deactivateAccount` is updated to revoke tokens, `UserPrincipal.isEnabled()` checks `user.isDeleted()`, and `UserController` delegates to a service, the findings are remediated.
