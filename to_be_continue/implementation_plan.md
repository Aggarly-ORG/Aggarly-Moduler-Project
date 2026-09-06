# Remediation Plan: User Module Security & Architecture Fixes

Comprehensive remediation plan to resolve all vulnerabilities and architectural defects identified during the User Module security audit.

## User Review Required

> [!IMPORTANT]
> **No source code files or database migrations have been modified yet.**
> Per project guidelines, please review and approve this remediation plan before changes are executed.

> [!WARNING]
> Database migration `V38__user_security_and_schema_hardening.sql` will:
> 1. Convert unconditional `UNIQUE` constraints on `users.email` and `users.username` into partial unique indexes (`WHERE is_deleted = FALSE`).
> 2. Add composite `UNIQUE(user_id, stripe_payment_method_id)` on `user_payment_methods`.
> 3. Add foreign key `fk_user_confirmed_actions_conversation` on `user_confirmed_actions(conversation_id)`.
> 4. Drop redundant index `idx_user_confirmed_actions_token`.
> 5. Add index `idx_users_created_at` on `users(created_at DESC)`.

## Open Questions

None. Root causes and exact code remediations are verified.

---

## Proposed Changes

### Phase 1: Security Configuration & Token Flow

#### [MODIFY] [SecurityConfig.java](file:///e:/java%20project/aggarly/src/main/java/com/luna/aggarly/common/security/SecurityConfig.java)
- Remove administrative vision endpoints (`/api/v1/vision/admin/ollama/**`, `/api/v1/vision/admin/clip/**`, `/api/v1/vision/admin/models`) from `permitAll()`.

#### [MODIFY] [OAuth2SuccessHandler.java](file:///e:/java%20project/aggarly/src/main/java/com/luna/aggarly/user/security/oauth2/OAuth2SuccessHandler.java)
- Transmit tokens via URL fragment (`#token=...&refreshToken=...`) instead of query parameters (`?token=...`).

#### [MODIFY] [AuthController.java](file:///e:/java%20project/aggarly/src/main/java/com/luna/aggarly/user/controller/AuthController.java)
- Change `/logout` endpoint to accept `@Valid @RequestBody RefreshTokenRequest request` instead of `@RequestParam("refreshToken")`.

#### [MODIFY] [JwtAuthenticationFilter.java](file:///e:/java%20project/aggarly/src/main/java/com/luna/aggarly/common/security/jwt/JwtAuthenticationFilter.java)
- Catch `UsernameNotFoundException` and clear context to allow clean 401 handling instead of unhandled 500 error.

#### [MODIFY] [UserPrincipal.java](file:///e:/java%20project/aggarly/src/main/java/com/luna/aggarly/user/security/UserPrincipal.java)
- Update `isEnabled()` to return `user != null && !user.isDeleted()`.

---

### Phase 2: Entities & Enums

#### [MODIFY] [AuthProvider.java](file:///e:/java%20project/aggarly/src/main/java/com/luna/aggarly/user/entity/enums/AuthProvider.java)
- Add `SYSTEM` enum constant to match `V26__seed_system_ai_user.sql`.

#### [MODIFY] [Role.java](file:///e:/java%20project/aggarly/src/main/java/com/luna/aggarly/user/entity/Role.java)
- Implement `equals()` and `hashCode()` based on natural business key `name`.

#### [MODIFY] [UserPaymentMethod.java](file:///e:/java%20project/aggarly/src/main/java/com/luna/aggarly/user/entity/UserPaymentMethod.java)
- Add `@Table(uniqueConstraints = @UniqueConstraint(name = "uq_user_payment_methods_user_stripe", columnNames = {"user_id", "stripe_payment_method_id"}))`.

---

### Phase 3: Database Migration

#### [NEW] [V38__user_security_and_schema_hardening.sql](file:///e:/java%20project/aggarly/src/main/resources/db/migration/V38__user_security_and_schema_hardening.sql)
- Replace table unique constraints with partial unique indexes on `users(email)` and `users(username)`.
- Add composite unique constraint `uq_user_payment_methods_user_stripe`.
- Add foreign key `fk_user_confirmed_actions_conversation`.
- Drop redundant index `idx_user_confirmed_actions_token`.
- Add index `idx_users_created_at`.

---

### Phase 4: Service Layer & Architectural Decoupling

#### [MODIFY] [UserProfileServiceImpl.java](file:///e:/java%20project/aggarly/src/main/java/com/luna/aggarly/user/service/impl/UserProfileServiceImpl.java)
- In `deactivateAccount()`, call `refreshTokenRepository.revokeAllUserTokens(user)`.
- Add `getUserSummary(UUID)` and `searchUsers(query, limit, currentUserId)` to support `UserController`.
- Remove cleartext OTP logging in `sendPhoneOtp()`.

#### [MODIFY] [UserController.java](file:///e:/java%20project/aggarly/src/main/java/com/luna/aggarly/user/controller/UserController.java)
- Inject `UserProfileService` instead of directly injecting `UserRepository` and `UserMapper`.

#### [MODIFY] [AuthServiceImpl.java](file:///e:/java%20project/aggarly/src/main/java/com/luna/aggarly/user/service/impl/AuthServiceImpl.java)
- Check `findAnyByEmail` during registration to detect soft-deleted collisions.
- Remove cleartext OTP logging across all auth flows.
- Throw `VerificationException("MFA is already enabled")` instead of `new RuntimeException("")`.
- Remove dead duplicate profile methods that belong in `UserProfileService`.

#### [MODIFY] [OtpServiceImpl.java](file:///e:/java%20project/aggarly/src/main/java/com/luna/aggarly/user/service/impl/OtpServiceImpl.java)
- Replace `code.trim().equals(storedOtp)` with `MessageDigest.isEqual(...)`.
- Delete OTP key only upon verified match (`MessageDigest.isEqual == true`).
- Align TTL with email template (10 minutes).

#### [MODIFY] [MfaServiceImpl.java](file:///e:/java%20project/aggarly/src/main/java/com/luna/aggarly/user/service/impl/MfaServiceImpl.java)
- Partition Redis key prefixes: `"mfa:challenge:" + token` vs `"mfa:setup:" + token`.

---

### Phase 5: Request DTO Validation

#### [MODIFY] [ConfirmMfaRequest.java](file:///e:/java%20project/aggarly/src/main/java/com/luna/aggarly/user/dto/request/ConfirmMfaRequest.java)
- Add `@NotBlank` on `token` and `@NotBlank @Pattern(regexp = "^\\d{6}$")` on `totpCode`.

#### [MODIFY] [UserProfileUpdate.java](file:///e:/java%20project/aggarly/src/main/java/com/luna/aggarly/user/dto/request/UserProfileUpdate.java)
- Add `@Size(max = 100)` on `firstName`, `lastName`, `displayName`, `@Size(max = 500)` on `bio`, and `@ValidPhone` on `phone`.

#### [MODIFY] [SavePaymentMethodRequest.java](file:///e:/java%20project/aggarly/src/main/java/com/luna/aggarly/user/dto/SavePaymentMethodRequest.java)
- Add `@Min(1) @Max(12)` on `expMonth`, `@Min(2024)` on `expYear`, and `@Pattern(regexp = "^\\d{4}$")` on `lastFour`.

---

## Verification Plan

### Automated Tests
1. Run `./mvnw test -Dtest=*User*Test,*Auth*Test` to verify that existing unit/integration test suites pass.
2. Run newly added test cases covering:
   - `AuthProvider.SYSTEM` enum deserialization.
   - `Role` `equals()`/`hashCode()` behavior in `HashSet`.
   - `UserPrincipal.isEnabled()` status on soft-deleted users.
   - Constant-time OTP comparison with single-typo retry tolerance.
   - Refresh token revocation upon `userProfileService.deactivateAccount()`.
   - DTO validation boundary tests.

### Manual Verification
1. Verify public access to `/api/v1/vision/admin/**` is blocked with HTTP 401.
2. Confirm OAuth2 redirect URL uses fragment `#token=...` instead of query parameters.
