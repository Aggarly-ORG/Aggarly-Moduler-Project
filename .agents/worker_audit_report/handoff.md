# Handoff Report: Aggarly User Module Audit Findings Synthesis

**Agent Identity**: `teamwork_preview_worker` (Audit Findings Synthesizer)  
**Working Directory**: `e:\java project\aggarly\.agents\worker_audit_report`  
**Report Type**: Hard Handoff (Task Complete)  
**Parent Conversation ID**: `2e9c52e6-3e07-44f6-81cf-3b71e16c12ab`  
**Primary Deliverable**: `e:\java project\aggarly\.agents\worker_audit_report\audit_report_draft.md`  

---

## 1. Observation

Direct code observations verified across `com.luna.aggarly.user`, `com.luna.aggarly.common.security`, and database migration files:

1. **SecurityConfig Central Authorization & PermitAll Leakage**:
   - `src/main/java/com/luna/aggarly/common/security/SecurityConfig.java:95-97`:
     ```java
     "/api/v1/vision/admin/ollama/**",
     "/api/v1/vision/admin/clip/**",
     "/api/v1/vision/admin/models"
     ```
     These administrative endpoints are explicitly listed within `.requestMatchers(...).permitAll()`.
   - `SecurityConfig.java:113`: Central authorization properly enforces `.anyRequest().authenticated()`.
   - Grep search for `@PreAuthorize("isAuthenticated()")` across all Java files returned exactly `0` occurrences.

2. **OAuth2 Redirect Token Exposure**:
   - `src/main/java/com/luna/aggarly/user/security/oauth2/OAuth2SuccessHandler.java:67-70`:
     ```java
     String redirectUrl = String.format("%s?token=%s&refreshToken=%s",
             frontendRedirectUrl, accessToken, refreshTokenValue);
     response.sendRedirect(redirectUrl);
     ```
     Access and refresh tokens are appended as GET query parameters in browser redirect URLs.

3. **Plaintext OTP Logging**:
   - `src/main/java/com/luna/aggarly/user/service/impl/AuthServiceImpl.java`:
     - Line 98: `log.info("📧 6-digit Email verification OTP for {}: {}", user.getEmail(), otpCode);`
     - Line 358: `log.info("sent 6-digit Email verification OTP for {}: {}", email, otpCode);`
     - Line 360: `log.error("failed to send 6-digit Email verification OTP for {}: {}", email, otpCode);`
     - Line 400: `log.info("sent 6-digit verification OTP for {}: {}", request.email(), otpCode);`
     - Line 402: `log.error("failed to send 6-digit verification OTP for {}: {}", request.email(), otpCode);`
     - Line 439: `log.info("📱 6-digit OTP sent to phone {} for user {}: {}", user.getPhone(), user.getEmail(), otpCode);`

4. **OTP Timing Attack & Premature Invalidation**:
   - `src/main/java/com/luna/aggarly/user/service/impl/OtpServiceImpl.java:90-99`:
     ```java
     storedOtp = redisTemplate.opsForValue().get(key);
     if (storedOtp != null) {
         redisTemplate.delete(key);
     }
     ...
     return code.trim().equals(storedOtp);
     ```
     `redisTemplate.delete(key)` destroys the stored OTP prior to validation. `code.trim().equals(storedOtp)` uses non-constant-time string comparison.

5. **Account Deactivation Token Retention & Principal Enabled Flaw**:
   - `src/main/java/com/luna/aggarly/user/service/impl/UserProfileServiceImpl.java:174-179`:
     `deactivateAccount` sets `user.setDeleted(true)` and saves `User`, but fails to call `refreshTokenRepository.revokeAllUserTokens(user)`.
   - `src/main/java/com/luna/aggarly/user/security/UserPrincipal.java:72-74`:
     `isEnabled()` unconditionally returns `true` regardless of `user.isDeleted()`.

6. **Enum Desynchronization (`AuthProvider.SYSTEM`)**:
   - `src/main/resources/db/migration/V26__seed_system_ai_user.sql:13` inserts `'SYSTEM'`.
   - `src/main/java/com/luna/aggarly/user/entity/enums/AuthProvider.java:6-10` defines only `LOCAL, GOOGLE, GITHUB`.
   - `User.java:57-60` maps `authProvider` via `@Enumerated(EnumType.STRING)`.

7. **Soft-Delete Unique Constraint Collision**:
   - `src/main/resources/db/migration/V2__create_auth_schema.sql:10, 15`: Unconditional `UNIQUE` on `email` and `username`.
   - `src/main/java/com/luna/aggarly/user/entity/User.java:23`: `@SQLRestriction("is_deleted = false")`.
   - `src/main/java/com/luna/aggarly/user/service/impl/AuthServiceImpl.java:69-75`: `userRepository.existsByEmail()` returns `false` for soft-deleted accounts; subsequent `INSERT` fails with PostgreSQL `duplicate key value violates unique constraint "users_email_key"`.

8. **Missing Composite Unique Constraint on Payment Methods**:
   - `src/main/resources/db/migration/V30__create_user_payment_methods_and_action_states.sql:6-18` and `UserPaymentMethod.java`: No unique constraint on `(user_id, stripe_payment_method_id)`.
   - `UserPaymentMethodServiceImpl.java:51-56`: Race conditions on concurrent requests insert duplicate records, breaking `findByUserIdAndStripePaymentMethodId` with `NonUniqueResultException`.

9. **Missing Identity Contract on `Role` Entity**:
   - `src/main/java/com/luna/aggarly/user/entity/Role.java:10-24`: Lacks `equals()` and `hashCode()`.
   - `User.java:84`: `Set<Role> roles = new HashSet<>()`.
   - `UserProfileServiceImpl.java:165-168`: `!user.getRoles().contains(hostRole)` evaluates to `false` for detached role entities, causing duplicate key insertions into `user_roles(user_id, role_id)`.

10. **Controller Layer Architectural Breach & Dead Code**:
    - `src/main/java/com/luna/aggarly/user/controller/UserController.java:31-68`: Directly injects `UserRepository` and `UserMapper`, bypassing the service layer.
    - `src/main/java/com/luna/aggarly/user/service/impl/AuthServiceImpl.java:293-320, 365-383, 426-500`: 7 duplicate user profile methods never routed by `AuthController`.

11. **DTO Validation Deficits & Query Parameter Logout**:
    - `src/main/java/com/luna/aggarly/user/dto/request/ConfirmMfaRequest.java`: 0 validation annotations.
    - `src/main/java/com/luna/aggarly/user/dto/request/UserProfileUpdate.java`: 0 validation annotations.
    - `src/main/java/com/luna/aggarly/user/controller/AuthController.java:58`: `logout(@RequestParam("refreshToken") String refreshToken)` accepts refresh token in query parameters.

12. **Cascade Safety Verification**:
    - `User.java` declares 0 `@OneToMany` relationships. Zero instances of `CascadeType.ALL` or `CascadeType.PERSIST` on parent collections.

---

## 2. Logic Chain

1. **Premise**: Administrative vision endpoints must be protected by authentication and role checks.
   - *Observation*: `SecurityConfig.java:95-97` includes `/api/v1/vision/admin/ollama/**`, `/api/v1/vision/admin/clip/**`, and `/api/v1/vision/admin/models` inside `.permitAll()`.
   - *Inference*: Anyone on the public internet can trigger resource-heavy AI model management without credentials.
   - *Conclusion*: CRITICAL vulnerability SEC-01; remove lines 95–97 from `permitAll()`.

2. **Premise**: Loading persistent entities from the database requires valid enum mappings.
   - *Observation*: `V26` inserts `auth_provider = 'SYSTEM'`, but `AuthProvider` enum only defines `LOCAL, GOOGLE, GITHUB`.
   - *Inference*: Any query materializing the system AI concierge entity (`aaac7011-3626-460c-a47e-c94535d34c65`) throws `IllegalArgumentException` from Hibernate's `EnumType.STRING` converter.
   - *Conclusion*: CRITICAL flaw ARC-01; add `SYSTEM` to `AuthProvider.java`.

3. **Premise**: Deactivating an account must terminate all ongoing sessions and revoke active credentials.
   - *Observation*: `UserProfileServiceImpl.deactivateAccount` sets `user.setDeleted(true)` but does not revoke refresh tokens. `UserPrincipal.isEnabled()` always returns `true`.
   - *Inference*: Deactivated users can call `/api/v1/auth/refresh`, receive new access tokens, and access protected APIs indefinitely.
   - *Conclusion*: CRITICAL flaw SEC-05; call `refreshTokenRepository.revokeAllUserTokens(user)` and update `UserPrincipal.isEnabled()` to return `!user.isDeleted()`.

4. **Premise**: Soft-deleted entities must not crash future user registrations with unhandled 500 errors.
   - *Observation*: PostgreSQL has unconditional `UNIQUE(email)` and `UNIQUE(username)`. `User` has `@SQLRestriction("is_deleted = false")`. `userRepository.existsByEmail()` returns `false` for soft-deleted emails.
   - *Inference*: `AuthServiceImpl.register` proceeds to insert the same email, triggering PostgreSQL `users_email_key` violation.
   - *Conclusion*: HIGH vulnerability ARC-02; convert constraints to PostgreSQL partial unique indexes (`WHERE is_deleted = false`) and check `findAnyByEmail` in `AuthServiceImpl`.

5. **Premise**: OTP validation must be timing-safe and fault-tolerant to user typos.
   - *Observation*: `OtpServiceImpl` calls `redisTemplate.delete(key)` before validation and uses `code.trim().equals(storedOtp)`.
   - *Inference*: Single typos destroy the OTP, and non-constant-time comparison leaks timing information.
   - *Conclusion*: HIGH vulnerability SEC-08/SEC-09; use `MessageDigest.isEqual` and delete key only on successful match.

6. **Premise**: Controllers must not bypass the service layer.
   - *Observation*: `UserController` directly calls `userRepository` and `userMapper` without transaction boundaries.
   - *Inference*: Violates layered architecture, SRP, and leaves queries unmanaged.
   - *Conclusion*: HIGH architectural flaw ARC-06; introduce `UserService` abstraction.

---

## 3. Caveats

1. **Audit & Report Only**: Strictly observed; no source code or database migration files were modified during this phase.
2. **External Third-Party APIs**: Interactions with live Stripe gateways, Google OAuth2, and GitHub OAuth2 servers were reviewed via static code analysis; live sandbox integration tests were not executed.
3. **Database Performance**: Index recommendations (`users.created_at`, `user_confirmed_actions`) are based on DDL analysis and query access patterns; runtime PostgreSQL query plans (`EXPLAIN ANALYZE`) were not gathered.

---

## 4. Conclusion

The Aggarly User Module demonstrates excellent adherence to architectural rules banning `@PreAuthorize("isAuthenticated()")` and prohibiting `CascadeType.ALL` on parent collections, alongside a robust Refresh Token Rotation (RTR) engine with family lineages and concurrency grace windows.

However, four **CRITICAL** issues and six **HIGH** issues require remediation before release:
1. **[CRITICAL] SEC-01**: Remove `/api/v1/vision/admin/**` from `SecurityConfig.permitAll()`.
2. **[CRITICAL] ARC-01**: Add `SYSTEM` to `AuthProvider` enum.
3. **[CRITICAL] SEC-05**: Revoke refresh tokens on account deactivation and check `!user.isDeleted()` in `UserPrincipal.isEnabled()`.
4. **[HIGH] ARC-02**: Add partial unique indexes on `users(email, username) WHERE is_deleted = false`.
5. **[HIGH] ARC-03**: Add composite unique constraint `uq_user_payment_methods_user_stripe`.
6. **[HIGH] ARC-04**: Implement `equals()` and `hashCode()` on `Role.java`.
7. **[HIGH] SEC-03**: Transmit OAuth2 tokens via HttpOnly cookies instead of URL query parameters.
8. **[HIGH] SEC-08/09**: Use `MessageDigest.isEqual` and preserve OTP on failed validation.
9. **[HIGH] ARC-06**: Refactor `UserController` to delegate to a new `UserService`.
10. **[HIGH] ARC-10**: Add Jakarta validation constraints to `ConfirmMfaRequest` and `UserProfileUpdate`.

The complete exhaustive report with exact drop-in code remediation snippets is published at:
`e:\java project\aggarly\.agents\worker_audit_report\audit_report_draft.md`.

---

## 5. Verification Method

To independently verify the synthesized findings:

1. **Verify Vision Admin PermitAll Exposure**:
   ```powershell
   Select-String -Path "src\main\java\com\luna\aggarly\common\security\SecurityConfig.java" -Pattern "vision/admin"
   ```
   *Expected result*: Lines 95–97 matching within `.permitAll()`.

2. **Verify Missing Enum Constant `AuthProvider.SYSTEM`**:
   ```powershell
   Select-String -Path "src\main\java\com\luna\aggarly\user\entity\enums\AuthProvider.java" -Pattern "SYSTEM"
   ```
   *Expected result*: No matches found.

3. **Verify Deactivation Missing Token Revocation**:
   Inspect `src/main/java/com/luna/aggarly/user/service/impl/UserProfileServiceImpl.java` lines 174–179.
   *Expected result*: `refreshTokenRepository.revokeAllUserTokens(user)` is not called.

4. **Verify `UserPrincipal.isEnabled()` Indefinite Auth**:
   Inspect `src/main/java/com/luna/aggarly/user/security/UserPrincipal.java` lines 72–74.
   *Expected result*: Method returns unconditional `true`.

5. **Verify OTP Timing Attack & Premature Deletion**:
   Inspect `src/main/java/com/luna/aggarly/user/service/impl/OtpServiceImpl.java` lines 90–99.
   *Expected result*: `redisTemplate.delete(key)` precedes `equals()`.

6. **Verify Complete Synthesized Draft Report**:
   Read `e:\java project\aggarly\.agents\worker_audit_report\audit_report_draft.md`.
