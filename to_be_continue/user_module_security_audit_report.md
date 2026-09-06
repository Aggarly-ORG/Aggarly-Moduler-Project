# Aggarly User Module — Comprehensive Security & Architectural Audit Report

**Audit Date**: September 5, 2026  
**Auditor**: Antigravity AppSec & Architecture Team (Surveys 1, 2, 3 Consolidated)  
**Scope**: `com.luna.aggarly.user`, `com.luna.aggarly.common.security`, Flyway Migrations `V1`–`V37`, and cross-module contracts  
**Status**: Complete (Audit & Report Phase — Awaiting User Approval Before Code Modifications)

---

## Executive Summary

An exhaustive security audit and architectural code review was conducted across all 66 Java classes and 37 Flyway migration scripts governing identity, authentication, session management, and authorization in Aggarly.

### Key Architectural Strengths Verified
1. **100% Compliance with `@PreAuthorize("isAuthenticated()")` Ban**: Verified zero occurrences across the entire codebase. Authorization boundaries are enforced centrally in `SecurityConfig.java`.
2. **Robust Refresh Token Rotation (RTR)**: The implementation of token families (`family_id`), SHA-256 access token binding (`associatedAccessTokenHash`), and the 15-second concurrency grace window operates as designed to mitigate token theft while preventing race condition lockouts on concurrent browser requests.
3. **BOLA / IDOR Protection on Saved Payment Methods**: Mutating endpoints on saved payment methods (`UserPaymentMethodController`) strictly scope all queries by compound `(id, userId)`.
4. **JPA Cascade Safety**: Complete compliance with Aggarly Architectural Guardrails Rule 3. `User.java` maintains no `@OneToMany` cascade collections for `RefreshToken`, `UserPaymentMethod`, or `UserConfirmedAction`. Child records are saved explicitly via dedicated repositories.

### Consolidated Findings Breakdown
| Severity | Count | Primary Areas |
|---|:---:|---|
| **CRITICAL** | **3** | Public exposure of internal AI admin APIs; runtime crash on AI system user load; soft-deleted accounts retaining active refresh tokens |
| **HIGH** | **7** | Token leakage in OAuth2 redirect; tokens leaked in logout query param; soft-delete unique key collisions; duplicate payment cards race condition; `Role` identity failure; `UserPrincipal.isEnabled()` auth bypass; direct DB access in controller |
| **MEDIUM** | **10** | Cleartext OTP logging; OTP timing attacks; single-attempt OTP destruction & TTL mismatch; unhandled filter exceptions in `JwtAuthenticationFilter`; blocking SMTP inside DB transactions; DTO validation gaps (`ConfirmMfaRequest`, `UserProfileUpdate`, `SavePaymentMethodRequest`); Redis key namespace collisions; redundant DB index on action tokens; missing conversation FK |
| **LOW** | **4** | Dead duplicate methods in `AuthServiceImpl`; generic runtime exceptions; missing query indexes; in-memory fallback cluster risk |

---

## 1. CRITICAL Severity Findings

### [CRIT-01] Public Exposure of Vision AI Administration Endpoints
- **File**: [`src/main/java/com/luna/aggarly/common/security/SecurityConfig.java#L95-L97`](file:///e:/java%20project/aggarly/src/main/java/com/luna/aggarly/common/security/SecurityConfig.java#L95-L97)
- **Vulnerability Type**: Broken Access Control / Inadvertent Public PermitAll (OWASP A01:2021)
- **Threat & Root Cause Analysis**:
  In `SecurityConfig.java`, the following route patterns are explicitly listed inside the public `.permitAll()` matcher block:
  ```java
  "/api/v1/vision/admin/ollama/**",
  "/api/v1/vision/admin/clip/**",
  "/api/v1/vision/admin/models"
  ```
  These endpoints control administrative AI model loading, memory management, and Ollama/CLIP system status. Because they are permitted to all traffic, any unauthenticated attacker on the internet can call these endpoints to trigger heavy vision model downloads or memory allocations, creating a severe Denial of Service (DoS) vulnerability.
- **Exploit Scenario**:
  An external attacker sends an unauthenticated `POST /api/v1/vision/admin/ollama/pull` or triggers model reload APIs repeatedly, exhausting host GPU/RAM and taking down the Aggarly backend.
- **Drop-In Remediation**:
  Remove administrative vision routes from the `.permitAll()` list in `SecurityConfig.java`. They will automatically fall under the central `.anyRequest().authenticated()` rule and method-level `@PreAuthorize("hasRole('ADMIN')")`.

```diff
--- a/src/main/java/com/luna/aggarly/common/security/SecurityConfig.java
+++ b/src/main/java/com/luna/aggarly/common/security/SecurityConfig.java
@@ -92,9 +92,6 @@
                             "/api/v1/storage/files/view/**",
                             "/api/v1/vision/search",
                             "/api/v1/vision/search/**",
-                            "/api/v1/vision/admin/ollama/**",
-                            "/api/v1/vision/admin/clip/**",
-                            "/api/v1/vision/admin/models"
                     ).permitAll()
```

---

### [CRIT-02] Missing Enum Constant `AuthProvider.SYSTEM` Causing JPA Runtime Crash
- **Files**:
  - [`src/main/java/com/luna/aggarly/user/entity/enums/AuthProvider.java#L6-L10`](file:///e:/java%20project/aggarly/src/main/java/com/luna/aggarly/user/entity/enums/AuthProvider.java#L6-L10)
  - [`src/main/resources/db/migration/V26__seed_system_ai_user.sql#L12-L14`](file:///e:/java%20project/aggarly/src/main/resources/db/migration/V26__seed_system_ai_user.sql#L12-L14)
- **Vulnerability Type**: Application Integrity & Availability / Enum Deserialization Failure
- **Threat & Root Cause Analysis**:
  Flyway migration `V26__seed_system_ai_user.sql` seeds the system AI Concierge user with `auth_provider = 'SYSTEM'`.
  However, `AuthProvider.java` only defines:
  ```java
  public enum AuthProvider {
      LOCAL,
      GOOGLE,
      GITHUB
  }
  ```
  Whenever any query (such as `userRepository.findById(ChatAiBridgeService.AI_BOT_SYSTEM_ID)`, user admin listings, or conversation participant resolution) loads the AI Concierge user from PostgreSQL, Hibernate's `EnumType.STRING` mapping attempts to deserialize `'SYSTEM'` and immediately crashes with:
  `java.lang.IllegalArgumentException: No enum constant com.luna.aggarly.user.entity.enums.AuthProvider.SYSTEM`
- **Exploit Scenario**:
  Any user starting or resuming an AI Concierge chat triggers user resolution for the system bot identity. The request crashes with an unhandled HTTP 500 error, disabling AI concierge services.
- **Drop-In Remediation**:
  Add `SYSTEM` to `AuthProvider.java`:

```diff
--- a/src/main/java/com/luna/aggarly/user/entity/enums/AuthProvider.java
+++ b/src/main/java/com/luna/aggarly/user/entity/enums/AuthProvider.java
@@ -6,5 +6,6 @@
 public enum AuthProvider {
     LOCAL,
     GOOGLE,
-    GITHUB
+    GITHUB,
+    SYSTEM
 }
```

---

### [CRIT-03] Session Persistence Flaw: Refresh Tokens Active on Deactivated Accounts
- **Files**:
  - [`src/main/java/com/luna/aggarly/user/service/impl/UserProfileServiceImpl.java#L174-L179`](file:///e:/java%20project/aggarly/src/main/java/com/luna/aggarly/user/service/impl/UserProfileServiceImpl.java#L174-L179)
  - [`src/main/java/com/luna/aggarly/user/security/UserPrincipal.java#L72-L74`](file:///e:/java%20project/aggarly/src/main/java/com/luna/aggarly/user/security/UserPrincipal.java#L72-L74)
- **Vulnerability Type**: Authentication Bypass / Broken Session Invalidation (OWASP A07:2021)
- **Threat & Root Cause Analysis**:
  When a user deactivates their account via `DELETE /api/v1/users/me`, `UserProfileController` invokes `userProfileService.deactivateAccount(userId)`.
  In `UserProfileServiceImpl`:
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
  1. `refreshTokenRepository.revokeAllUserTokens(user)` is **omitted**.
  2. In `UserPrincipal.java`, `isEnabled()` hardcodes `return true;`, completely ignoring `user.isDeleted()`.
  Consequently, a deactivated or banned account continues to hold valid refresh tokens, and subsequent calls to `/api/v1/auth/refresh` successfully issue fresh access tokens, allowing continuous API access.
- **Drop-In Remediation**:
  1. Revoke tokens in `UserProfileServiceImpl.deactivateAccount()`:
     ```java
     refreshTokenRepository.revokeAllUserTokens(user);
     ```
  2. Enforce deletion status in `UserPrincipal.java`:
     ```java
     @Override
     public boolean isEnabled() {
         return user != null && !user.isDeleted();
     }
     ```

---

## 2. HIGH Severity Findings

### [HIGH-01] Sensitive Token Exposure in OAuth2 Callback URL
- **File**: [`src/main/java/com/luna/aggarly/user/security/oauth2/OAuth2SuccessHandler.java#L67-L70`](file:///e:/java%20project/aggarly/src/main/java/com/luna/aggarly/user/security/oauth2/OAuth2SuccessHandler.java#L67-L70)
- **Vulnerability Type**: Credential Exposure in URL Parameters (CWE-598)
- **Threat Analysis**:
  `OAuth2SuccessHandler` redirects the browser with Bearer JWT and 7-day Refresh Tokens in query parameters:
  `redirectUrl = String.format("%s?token=%s&refreshToken=%s", frontendRedirectUrl, accessToken, refreshTokenValue);`
  Exposes tokens in browser history, proxy access logs, and HTTP `Referer` headers.
- **Drop-In Remediation**:
  Transmit tokens via URL fragment (`#token=...&refreshToken=...`), which standard web browsers never send to servers or transmit via `Referer` headers:

```diff
--- a/src/main/java/com/luna/aggarly/user/security/oauth2/OAuth2SuccessHandler.java
+++ b/src/main/java/com/luna/aggarly/user/security/oauth2/OAuth2SuccessHandler.java
@@ -67,3 +67,3 @@
-        String redirectUrl = String.format("%s?token=%s&refreshToken=%s",
+        String redirectUrl = String.format("%s#token=%s&refreshToken=%s",
                 frontendRedirectUrl, accessToken, refreshTokenValue);
```

---

### [HIGH-02] Sensitive Refresh Token Accepted in Logout Query Parameter
- **File**: [`src/main/java/com/luna/aggarly/user/controller/AuthController.java#L58`](file:///e:/java%20project/aggarly/src/main/java/com/luna/aggarly/user/controller/AuthController.java#L58)
- **Vulnerability Type**: Credential Exposure in URL Query String (CWE-598)
- **Threat Analysis**:
  ```java
  @PostMapping("/logout")
  public ResponseEntity<ApiResponse<Void>> logout(@RequestParam("refreshToken") String refreshToken)
  ```
  Accepting the refresh token via `@RequestParam` puts the full token into web server access logs, reverse proxy logs (NGINX/Cloudflare), and browser URL address history.
- **Drop-In Remediation**:
  Change `logout` to accept a JSON request body (`@Valid @RequestBody RefreshTokenRequest request`).

---

### [HIGH-03] Soft-Delete vs. Unique Constraint Collision on `users` Table
- **Files**:
  - [`src/main/resources/db/migration/V2__create_auth_schema.sql#L10,L15`](file:///e:/java%20project/aggarly/src/main/resources/db/migration/V2__create_auth_schema.sql#L10,L15)
  - [`src/main/java/com/luna/aggarly/user/entity/User.java#L23,L26,L41`](file:///e:/java%20project/aggarly/src/main/java/com/luna/aggarly/user/entity/User.java#L23,L26,L41)
  - [`src/main/java/com/luna/aggarly/user/service/impl/AuthServiceImpl.java#L69-L75`](file:///e:/java%20project/aggarly/src/main/java/com/luna/aggarly/user/service/impl/AuthServiceImpl.java#L69-L75)
- **Vulnerability Type**: Unhandled 500 Failure on Soft-Deleted Re-registration
- **Threat Analysis**:
  `users.email` and `users.username` have unconditional `UNIQUE` constraints in PostgreSQL. Because `User` has `@SQLRestriction("is_deleted = false")`, `userRepository.existsByEmail()` returns `false` for soft-deleted accounts. Standard registration proceeds to `INSERT` and crashes with an unhandled `DataIntegrityViolationException` (500).
- **Drop-In Remediation**:
  1. Add partial unique indexes in Flyway `V38`:
     ```sql
     ALTER TABLE users DROP CONSTRAINT IF EXISTS users_email_key;
     ALTER TABLE users DROP CONSTRAINT IF EXISTS users_username_key;
     CREATE UNIQUE INDEX idx_users_active_email ON users (email) WHERE is_deleted = FALSE;
     CREATE UNIQUE INDEX idx_users_active_username ON users (username) WHERE is_deleted = FALSE;
     ```
  2. Check `findAnyByEmail` in `AuthServiceImpl.register()` to return a clean 409 or support account reactivation.

---

### [HIGH-04] Missing Composite Unique Constraint on Saved Payment Methods
- **Files**:
  - [`src/main/resources/db/migration/V30__create_user_payment_methods_and_action_states.sql#L6-L18`](file:///e:/java%20project/aggarly/src/main/resources/db/migration/V30__create_user_payment_methods_and_action_states.sql#L6-L18)
  - [`src/main/java/com/luna/aggarly/user/entity/UserPaymentMethod.java#L11-L18`](file:///e:/java%20project/aggarly/src/main/java/com/luna/aggarly/user/entity/UserPaymentMethod.java#L11-L18)
  - [`src/main/java/com/luna/aggarly/user/service/impl/UserPaymentMethodServiceImpl.java#L51-L56`](file:///e:/java%20project/aggarly/src/main/java/com/luna/aggarly/user/service/impl/UserPaymentMethodServiceImpl.java#L51-L56)
- **Vulnerability Type**: Race Condition / Data Corruption on Concurrent Saves
- **Threat Analysis**:
  Table `user_payment_methods` has no unique constraint on `(user_id, stripe_payment_method_id)`. Concurrent requests insert duplicate rows. Subsequent lookups throw `NonUniqueResultException`, breaking the user's payment method listing.
- **Drop-In Remediation**:
  Add `ALTER TABLE user_payment_methods ADD CONSTRAINT uq_user_payment_methods_user_stripe UNIQUE (user_id, stripe_payment_method_id);` in `V38` and annotate `UserPaymentMethod.java`.

---

### [HIGH-05] Missing `equals()` and `hashCode()` on `Role` Entity
- **Files**:
  - [`src/main/java/com/luna/aggarly/user/entity/Role.java#L11-L16`](file:///e:/java%20project/aggarly/src/main/java/com/luna/aggarly/user/entity/Role.java#L11-L16)
  - [`src/main/java/com/luna/aggarly/user/service/impl/UserProfileServiceImpl.java#L165-L168`](file:///e:/java%20project/aggarly/src/main/java/com/luna/aggarly/user/service/impl/UserProfileServiceImpl.java#L165-L168)
- **Vulnerability Type**: Entity Identity Failure / Duplicate PK Violations
- **Threat Analysis**:
  `Role` relies on default `Object` identity. When stored in `Set<Role>` across detached sessions, `.contains(hostRole)` evaluates to `false`. Hibernate attempts duplicate insertion into `user_roles(user_id, role_id)`, throwing primary key violations.
- **Drop-In Remediation**:
  Implement `equals()` and `hashCode()` on `Role.java` based on `name`.

---

### [HIGH-06] Direct Repository Access & Business Logic in `UserController`
- **File**: [`src/main/java/com/luna/aggarly/user/controller/UserController.java#L31-L68`](file:///e:/java%20project/aggarly/src/main/java/com/luna/aggarly/user/controller/UserController.java#L31-L68)
- **Vulnerability Type**: Architectural Layering Violation (Controller Bypasses Service)
- **Threat Analysis**:
  `UserController` injects `UserRepository` and `UserMapper` directly, performing query logic, pagination, and user filtering without service boundaries or transactions.
- **Drop-In Remediation**:
  Route directory lookups through `UserProfileService` (`getUserSummary(UUID)` and `searchUsers(query, limit, currentUserId)`).

---

### [HIGH-07] Zero Validation Annotations on `ConfirmMfaRequest`
- **File**: [`src/main/java/com/luna/aggarly/user/dto/request/ConfirmMfaRequest.java#L3`](file:///e:/java%20project/aggarly/src/main/java/com/luna/aggarly/user/dto/request/ConfirmMfaRequest.java#L3)
- **Vulnerability Type**: Missing Input Validation / No-Op `@Valid`
- **Threat Analysis**:
  `ConfirmMfaRequest(String token, String totpCode)` contains no annotations. Calling `/api/v1/auth/confirm-mfa` with null or blank tokens bypasses validation and triggers unhandled exceptions in `MfaService`.
- **Drop-In Remediation**:
  Add `@NotBlank` on `token` and `@NotBlank @Pattern(regexp = "^\\d{6}$")` on `totpCode`.

---

## 3. MEDIUM Severity Findings

### [MED-01] Cleartext Logging of 6-Digit OTP Codes
- **Files**:
  - [`src/main/java/com/luna/aggarly/user/service/impl/AuthServiceImpl.java#L98, L358, L400, L439`](file:///e:/java%20project/aggarly/src/main/java/com/luna/aggarly/user/service/impl/AuthServiceImpl.java#L98)
  - [`src/main/java/com/luna/aggarly/user/service/impl/UserProfileServiceImpl.java#L131`](file:///e:/java%20project/aggarly/src/main/java/com/luna/aggarly/user/service/impl/UserProfileServiceImpl.java#L131)
- **Remediation**: Remove `otpCode` parameters from all log messages.

### [MED-02] Timing Attack on OTP Verification
- **File**: [`src/main/java/com/luna/aggarly/user/service/impl/OtpServiceImpl.java#L99`](file:///e:/java%20project/aggarly/src/main/java/com/luna/aggarly/user/service/impl/OtpServiceImpl.java#L99)
- **Remediation**: Use `MessageDigest.isEqual(...)` for constant-time comparison.

### [MED-03] Single-Attempt OTP Destruction & Expiry Mismatch
- **Files**:
  - [`src/main/java/com/luna/aggarly/user/service/impl/OtpServiceImpl.java#L30, L90-L94`](file:///e:/java%20project/aggarly/src/main/java/com/luna/aggarly/user/service/impl/OtpServiceImpl.java#L30)
  - [`src/main/java/com/luna/aggarly/user/service/impl/EmailServiceImpl.java#L42`](file:///e:/java/project/aggarly/src/main/java/com/luna/aggarly/user/service/impl/EmailServiceImpl.java#L42)
- **Remediation**: Align TTL to 10 minutes; delete the OTP only upon verified match (`MessageDigest.isEqual == true`).

### [MED-04] Unhandled Filter Exception in `JwtAuthenticationFilter`
- **File**: [`src/main/java/com/luna/aggarly/common/security/jwt/JwtAuthenticationFilter.java#L48`](file:///e:/java%20project/aggarly/src/main/java/com/luna/aggarly/common/security/jwt/JwtAuthenticationFilter.java#L48)
- **Remediation**: Wrap `loadUserByUsername` in a `try-catch` block to prevent unhandled 500 errors.

### [MED-05] Blocking SMTP Network I/O Inside `@Transactional`
- **File**: [`src/main/java/com/luna/aggarly/user/service/impl/AuthServiceImpl.java#L357, L399`](file:///e:/java%20project/aggarly/src/main/java/com/luna/aggarly/user/service/impl/AuthServiceImpl.java#L357)
- **Threat Analysis**: Synchronous `emailService.send(...)` holds database connections open during network calls.
- **Remediation**: Dispatch email sending asynchronously (`@Async`) or via `@TransactionalEventListener(phase = AFTER_COMMIT)`.

### [MED-06] Missing DTO Validation on `UserProfileUpdate`
- **File**: [`src/main/java/com/luna/aggarly/user/dto/request/UserProfileUpdate.java#L6-L13`](file:///e:/java%20project/aggarly/src/main/java/com/luna/aggarly/user/dto/request/UserProfileUpdate.java#L6-L13)
- **Remediation**: Add `@Size(max = 100)` on names, `@Size(max = 500)` on `bio`, and `@ValidPhone` on `phone`.

### [MED-07] Incomplete Validation on `SavePaymentMethodRequest`
- **File**: [`src/main/java/com/luna/aggarly/user/dto/SavePaymentMethodRequest.java#L23-L28`](file:///e:/java%20project/aggarly/src/main/java/com/luna/aggarly/user/dto/SavePaymentMethodRequest.java#L23-L28)
- **Remediation**: Add `@Min(1) @Max(12)` on `expMonth`, `@Min(2024)` on `expYear`, and `@Pattern(regexp = "^\\d{4}$")` on `lastFour`.

### [MED-08] Redis Key Namespace Collision in `MfaServiceImpl`
- **File**: [`src/main/java/com/luna/aggarly/user/service/impl/MfaServiceImpl.java#L32, L54`](file:///e:/java%20project/aggarly/src/main/java/com/luna/aggarly/user/service/impl/MfaServiceImpl.java#L32)
- **Remediation**: Partition Redis keys into `"mfa:challenge:" + token` vs `"mfa:setup:" + token`.

### [MED-09] Redundant DB Index on `user_confirmed_actions(token)`
- **File**: [`src/main/resources/db/migration/V30__create_user_payment_methods_and_action_states.sql#L28`](file:///e:/java%20project/aggarly/src/main/resources/db/migration/V30__create_user_payment_methods_and_action_states.sql#L28)
- **Remediation**: Drop redundant index `idx_user_confirmed_actions_token` in `V38`.

### [MED-10] Missing Foreign Key on `user_confirmed_actions.conversation_id`
- **File**: [`src/main/resources/db/migration/V30__create_user_payment_methods_and_action_states.sql#L27`](file:///e:/java%20project/aggarly/src/main/resources/db/migration/V30__create_user_payment_methods_and_action_states.sql#L27)
- **Remediation**: Add `REFERENCES conversations(id) ON DELETE SET NULL` in `V38`.

---

## 4. LOW Severity & Best Practice Findings

- **[LOW-01] Dead Duplicate Profile Code in `AuthServiceImpl`**: Remove 7 unused profile methods mirroring `UserProfileServiceImpl`.
- **[LOW-02] Generic `RuntimeException("")` in `requestMfa`**: Replace with `VerificationException("MFA is already enabled")`.
- **[LOW-03] Missing Index on `users(created_at)`**: Add index `idx_users_created_at` in `V38`.
- **[LOW-04] In-Memory Fallback Map in Clustered Environments**: Log warning or disable `localMemoryStore` in multi-replica production deployments.

---
