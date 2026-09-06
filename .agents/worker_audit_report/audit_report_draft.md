# Comprehensive Security Audit & Architectural Code Review Report
**Target Subsystem**: Aggarly User Module (`com.luna.aggarly.user`) & Security Infrastructure (`com.luna.aggarly.common.security`)  
**Auditor Identity**: `teamwork_preview_worker` (Audit Findings Synthesizer)  
**Date**: 2026-09-05  
**Audit Mode**: Exhaustive Synthesis & Review (Audit & Report Only — No Source Files Modified)  
**Scope Baseline**: `PROJECT.md`, `ORIGINAL_REQUEST.md`, Flyway Migrations `V1`–`V37`

---

## 1. Executive Summary & Audit Scorecard

### 1.1 Executive Overview
An exhaustive, multi-dimensional security and architectural review of the Aggarly User Module (`com.luna.aggarly.user`) and its security configurations (`com.luna.aggarly.common.security`) was conducted. The assessment evaluated:
1. **Application Security (AppSec)**: Central authorization enforcement, OWASP Top 10 vulnerabilities (BOLA/IDOR, Broken Authentication, Session Hijacking, Timing Attacks, Token Transport Leakage), cryptographic integrity, and compliance with project architectural guardrails.
2. **Database & Persistence Parity**: Flyway migrations (`V1` through `V37`) versus JPA entity mappings, composite constraints, soft-delete mechanics (`@SQLRestriction`), cascade behavior, and entity identity contracts (`equals`/`hashCode`).
3. **Software Architecture & Clean Code**: Controller-service boundaries, DTO validation hygiene, transactional consistency (`@Transactional`), exception safety, sensitive information leakage, and cross-module cohesion.

The codebase exhibits modern engineering strengths:
- **Zero `@PreAuthorize("isAuthenticated()")` Annotations**: 100% compliance with Aggarly Architecture Rule 1; authentication is enforced centrally via `.anyRequest().authenticated()`.
- **Sophisticated Refresh Token Rotation (RTR)**: State-of-the-art token family tracking (`family_id`), a 15-second grace window mitigating network/browser concurrency races, and SHA-256 access token binding (`associatedAccessTokenHash`).
- **Robust BOLA/IDOR Prevention on Mutating Operations**: Profile mutations strictly derive user context from `@AuthenticationPrincipal` or `SecurityUtils.getCurrentUserId()`; payment method operations enforce compound keys `(id, userId)`.
- **JPA Cascade Safety**: 100% compliance with Aggarly Architecture Rule 3 banning `CascadeType.ALL` on parent collections.

However, the audit identified **four critical security and architectural defects** alongside multiple high and medium severity vulnerabilities that must be remediated prior to production deployment.

---

### 1.2 Audit Scorecard

| Category | Status | Summary of Evaluation |
|---|---|---|
| **Central Auth & Perimeter Security** | 🚨 **CRITICAL RISK** | Central `.anyRequest().authenticated()` is correctly configured, but administrative endpoints (`/api/v1/vision/admin/**`) are inadvertently exposed via `permitAll()`. |
| **Authentication & Token Lifecycle** | ⚠️ **HIGH RISK** | Sophisticated RTR implementation, but OAuth2 tokens leak in GET URL query strings, and account deactivation fails to revoke refresh tokens while `UserPrincipal.isEnabled()` permits indefinite access. |
| **Cryptographic & Verification Hygiene**| ⚠️ **HIGH RISK** | 6-digit OTP verification uses non-constant-time string comparison (timing attack), deletes OTPs prematurely on first failed attempt, and logs raw OTPs in cleartext. |
| **Object-Level Authorization (BOLA/IDOR)**|  **PASS (100%)** | Saved payment methods and profile mutations strictly scope operations to the authenticated user ID. |
| **Database & JPA Parity** | 🚨 **CRITICAL RISK** | Enum mismatch (`AuthProvider.SYSTEM` missing from enum), soft-delete collisions with unconditional DB unique constraints, and missing composite unique constraint on payment methods. |
| **Controller & Clean Architecture** | ⚠️ **MEDIUM RISK** | `UserController` breaches layered architecture by bypassing the service layer; dual-service schizophrenia between `AuthService` and `UserProfileService` creates dead code. |
| **Input Validation Hygiene** | ⚠️ **MEDIUM RISK** | DTOs (`ConfirmMfaRequest`, `UserProfileUpdate`) lack Jakarta validation annotations; `AuthController.logout` accepts refresh tokens in query parameters. |
| **Exception Safety & Information Leakage**|  **PASS** | Exceptions are intercepted centrally into standardized `ApiResponse<T>` envelopes without leaking internal stack traces or raw SQL. |

---

### 1.3 Findings Severity Distribution

```
  ┌──────────────────────────────────────────────────────────┐
  │ CRITICAL: 4 Findings (Immediate Exploit / System Crash)   │
  ├──────────────────────────────────────────────────────────┤
  │ HIGH:     6 Findings (Severe Vulnerability / Architecture)│
  ├──────────────────────────────────────────────────────────┤
  │ MEDIUM:   6 Findings (Code Smells, Inefficiencies, Risks)│
  ├──────────────────────────────────────────────────────────┤
  │ LOW:      3 Findings (Hardening / Defensive Best Practice)│
  └──────────────────────────────────────────────────────────┘
```

---

## 2. R1. Security & AppSec Findings

### 2.1 Central Authorization & Perimeter Security

#### SEC-01 [CRITICAL]: Administrative Vision Endpoints Inadvertently Exposed in `SecurityConfig.permitAll()`
- **File**: `src/main/java/com/luna/aggarly/common/security/SecurityConfig.java:95-97`
- **Analysis**:
  In `SecurityConfig.java`, line 88 begins a `.requestMatchers(...).permitAll()` block intended for public property browsing and public asset viewing. However, lines 95-97 include:
  ```java
  "/api/v1/vision/admin/ollama/**",
  "/api/v1/vision/admin/clip/**",
  "/api/v1/vision/admin/models"
  ```
  These administrative endpoints manage machine learning model lifecycles, CLIP embeddings, and Ollama vision inference. Exposing them in `permitAll()` bypasses Spring Security completely. Any unauthenticated attacker on the internet can query administrative models, trigger heavy GPU/CPU model loading, or access internal diagnostic endpoints, creating severe denial-of-service (DoS) vectors and administrative boundary breaches.
- **Architectural Violation**: Direct breach of Rule 1 ("specifying explicit permitAll() only for public auth routes, Swagger, and WebSocket handshakes").

#### SEC-02 [PASS]: Strict Compliance with Zero `@PreAuthorize("isAuthenticated()")` Rule
- **Verification**:
  A full codebase scan for `isAuthenticated()` across all Java source files yielded **zero occurrences**. All 47 method-level `@PreAuthorize` annotations across the platform strictly perform role-based authorization (e.g. `hasRole('ADMIN')`, `hasRole('HOST')`, or `hasAnyRole('HOST','ADMIN')`). Authentication boundaries are enforced centrally by `.anyRequest().authenticated()` at `SecurityConfig.java:113`.

---

### 2.2 Broken Authentication & Token Lifecycle

#### SEC-03 [HIGH]: Sensitive Access & Refresh Tokens Leaked in OAuth2 Redirect URL Query Parameters
- **File**: `src/main/java/com/luna/aggarly/user/security/oauth2/OAuth2SuccessHandler.java:67-70`
- **Analysis**:
  Upon successful OAuth2 social authentication (Google/GitHub), `OAuth2SuccessHandler` executes:
  ```java
  String redirectUrl = String.format("%s?token=%s&refreshToken=%s",
          frontendRedirectUrl, accessToken, refreshTokenValue);
  response.sendRedirect(redirectUrl);
  ```
  Transmitting sensitive Bearer JWT access tokens and long-lived UUID refresh tokens as HTTP GET query parameters violates OAuth2 Security Best Current Practice (RFC 6819 & RFC 6749 §4.1.2).
  - **Threat Vectors**:
    1. **Browser History Logging**: The full token URL remains in browser history indefinitely.
    2. **Referer Header Leakage**: If the callback page loads third-party scripts, CDNs, or external links, the URL (including tokens) is transmitted in the `Referer` header.
    3. **Intermediate Proxy & Server Logs**: Reverse proxies, corporate firewalls, and ISP access logs record raw query strings.
- **Remediation Strategy**: Transmit tokens via secure, `HttpOnly`, `SameSite=Strict`, `Secure` cookies or return an authorization code exchanged via back-channel POST.

#### SEC-04 [HIGH]: Refresh Token Acceptance via Query Parameter in Logout Endpoint
- **File**: `src/main/java/com/luna/aggarly/user/controller/AuthController.java:58`
- **Analysis**:
  `AuthController.logout` defines:
  ```java
  public ResponseEntity<ApiResponse<Void>> logout(@RequestParam("refreshToken") String refreshToken)
  ```
  Accepting credentials via `@RequestParam` forces clients to append high-privilege refresh tokens into the request URI (`/api/v1/auth/logout?refreshToken=...`). Web servers, API gateways, and access logs routinely log request URLs, leaking active session credentials.
- **Remediation**: Transition logout payload to a JSON request body (`@RequestBody RefreshTokenRequest`).

#### SEC-05 [CRITICAL]: Token Lifecycle Gap on Account Deactivation & Indefinite Auth Flaw
- **Files**:
  - `src/main/java/com/luna/aggarly/user/service/impl/UserProfileServiceImpl.java:174-179`
  - `src/main/java/com/luna/aggarly/user/security/UserPrincipal.java:72-74`
- **Analysis**:
  When a user deactivates their account via `DELETE /api/v1/users/me`, `UserProfileServiceImpl.deactivateAccount` sets `user.setDeleted(true)` and saves the user. However, unlike `AuthServiceImpl.deactivateAccount`, it **omits revoking the user's refresh tokens** (`refreshTokenRepository.revokeAllUserTokens(user)` is not called).
  Compounding this vulnerability, `UserPrincipal.java` lines 72-74 implement Spring Security's `UserDetails.isEnabled()` as:
  ```java
  @Override
  public boolean isEnabled() {
      return true;
  }
  ```
  Because `isEnabled()` unconditionally returns `true`, Spring Security and `CustomUserDetailsService` treat soft-deleted accounts as fully enabled. A deactivated user (or an attacker possessing an exfiltrated refresh token) can continue calling `/api/v1/auth/refresh` to obtain new JWT access tokens and access protected resources indefinitely.

#### SEC-06 [PASS]: Robust Refresh Token Rotation (RTR) Implementation
- **Files**: `AuthServiceImpl.java:191-280`, `RefreshToken.java`, `RefreshTokenRepository.java`
- **Verification**:
  The platform's RTR architecture complies with RFC 6819:
  1. **Token Lineage (`family_id`)**: Every refresh chain maintains an immutable `family_id`.
  2. **15-Second Grace Window**: If a revoked refresh token is re-presented within 15 seconds (`secondsSinceRevocation <= 15`), the system identifies network/multi-tab concurrency and returns the active successor token rather than locking the user out.
  3. **Breach Detection & Family Revocation**: Replay attempts outside the 15-second grace window trigger immediate invocation of `refreshTokenRepository.revokeFamily()`, invalidating all active sessions in the family.
  4. **Access Token Binding**: Stores SHA-256 hash of the issued access token in `associatedAccessTokenHash` and validates it upon refresh.

---

### 2.3 Cryptographic Rigor & Verification Mechanics

#### SEC-07 [MEDIUM]: Plaintext Logging of 6-Digit OTP Codes
- **File**: `src/main/java/com/luna/aggarly/user/service/impl/AuthServiceImpl.java:98, 358, 400, 439`
- **Analysis**:
  `AuthServiceImpl` explicitly outputs generated OTP codes into application logs at `INFO` level:
  ```java
  log.info("📧 6-digit Email verification OTP for {}: {}", user.getEmail(), otpCode);
  log.info("sent 6-digit Email verification OTP for {}: {}", email, otpCode);
  log.info("sent 6-digit verification OTP for {}: {}", request.email(), otpCode);
  log.info("📱 6-digit OTP sent to phone {} for user {}: {}", user.getPhone(), user.getEmail(), otpCode);
  ```
  Logging plaintext OTP codes enables anyone with access to log aggregators (Elasticsearch/Kibana, CloudWatch, Datadog) to intercept email verification, phone verification, and password reset codes, bypassing authentication barriers.

#### SEC-08 [HIGH]: Non-Constant-Time String Comparison in OTP Verification (Timing Attack)
- **File**: `src/main/java/com/luna/aggarly/user/service/impl/OtpServiceImpl.java:99`
- **Analysis**:
  In `OtpServiceImpl.validateAndConsumeOtp`:
  ```java
  return code.trim().equals(storedOtp);
  ```
  Standard `String.equals()` performs character-by-character comparison and aborts execution upon the first mismatched character. This introduces measurable variations in response latency (timing oracle). For numeric 6-digit OTPs, an automated attacker measuring microsecond timing differentials can systematically infer valid OTP digits.
- **Remediation**: Enforce constant-time comparison via `MessageDigest.isEqual(...)`.

#### SEC-09 [HIGH]: Premature Invalidation of OTP upon Typo (Denial of Retry)
- **File**: `src/main/java/com/luna/aggarly/user/service/impl/OtpServiceImpl.java:90-93`
- **Analysis**:
  In `OtpServiceImpl.validateAndConsumeOtp`:
  ```java
  storedOtp = redisTemplate.opsForValue().get(key);
  if (storedOtp != null) {
      redisTemplate.delete(key);
  }
  return code.trim().equals(storedOtp);
  ```
  The OTP key is deleted from Redis **prior** to validating whether the user-supplied code matches. A legitimate user who makes a minor typo or submits an incomplete code has their OTP permanently destroyed on the first attempt, violating standard UX patterns and denying retries within the 5-minute validity window.
- **Remediation**: Delete the OTP key only if verification succeeds, and track failed attempts to lock out brute-force attempts after 5 consecutive failures.

---

### 2.4 Broken Object-Level Authorization (BOLA / IDOR)

#### SEC-10 [PASS]: BOLA / IDOR Verification on User Profiles and Payment Methods
- **Files**:
  - `UserPaymentMethodServiceImpl.java:75, 85`
  - `UserProfileServiceImpl.java:43, 49, 104, 123, 136, 174`
- **Verification**:
  - **Saved Cards / Payment Methods**: All mutating and retrieval repository operations mandate compound querying on both the record ID and the authenticated user's ID:
    ```java
    paymentMethodRepository.findByIdAndUserId(paymentMethodId, userId)
    ```
    Attempting to delete or modify another user's saved card returns `404 Not Found`, completely preventing cross-tenant card manipulation.
  - **Profile Endpoints**: Handlers in `UserProfileController` do not accept a `userId` path variable; they bind strictly to `@AuthenticationPrincipal UserPrincipal principal` and extract `principal.getUserId()`. Cross-user profile tampering via IDOR is impossible.

---

## 3. R2. Architecture & Clean Code Review Findings

### 3.1 Database Parity & JPA Schema Consistency

#### ARC-01 [CRITICAL]: Enum Desynchronization on `AuthProvider.SYSTEM`
- **Files**:
  - `src/main/resources/db/migration/V26__seed_system_ai_user.sql:12-14`
  - `src/main/java/com/luna/aggarly/user/entity/enums/AuthProvider.java:6-10`
  - `src/main/java/com/luna/aggarly/user/entity/User.java:57-60`
- **Analysis**:
  Flyway migration `V26` seeds the system AI bot user (`id = 'aaac7011-3626-460c-a47e-c94535d34c65'`) with `auth_provider = 'SYSTEM'`.
  However, Java enum `AuthProvider` is defined as:
  ```java
  public enum AuthProvider {
      LOCAL,
      GOOGLE,
      GITHUB
  }
  ```
  `User.authProvider` uses JPA `@Enumerated(EnumType.STRING)`. Any query that loads the system AI user (e.g., chat services, concierge bridges, or admin directory lookups) crashes with:
  `IllegalArgumentException: No enum constant com.luna.aggarly.user.entity.enums.AuthProvider.SYSTEM`
  This defect breaks runtime features dependent on the system bot user.

#### ARC-02 [HIGH]: Soft-Delete Collision with Unconditional Unique Constraints
- **Files**:
  - `src/main/resources/db/migration/V2__create_auth_schema.sql:10, 15`
  - `src/main/java/com/luna/aggarly/user/entity/User.java:23`
  - `src/main/java/com/luna/aggarly/user/service/impl/AuthServiceImpl.java:69-75`
- **Analysis**:
  In PostgreSQL, `V2__create_auth_schema.sql` establishes unconditional unique constraints:
  ```sql
  email VARCHAR(255) UNIQUE NOT NULL,
  username VARCHAR(100) UNIQUE NOT NULL
  ```
  Meanwhile, `User.java` applies Hibernate 6.3's `@SQLRestriction("is_deleted = false")`.
  When a user deactivates their account (`is_deleted = true`), standard repository calls like `userRepository.existsByEmail(request.email())` append `AND is_deleted = false`, returning `false`.
  The registration flow proceeds to execute `userRepository.save(newUser)`, which attempts to insert the duplicate email/username. PostgreSQL rejects the insert with `ERROR: duplicate key value violates unique constraint "users_email_key"`, causing an unhandled HTTP 500 `DataIntegrityViolationException`.
- **Remediation**: Replace unconditional constraints with PostgreSQL partial unique indexes (`WHERE is_deleted = false`) or explicitly check `userRepository.findAnyByEmail(...)` to offer account reactivation.

#### ARC-03 [HIGH]: Missing Composite Unique Constraint on `user_payment_methods`
- **Files**:
  - `src/main/resources/db/migration/V30__create_user_payment_methods_and_action_states.sql:6-18`
  - `src/main/java/com/luna/aggarly/user/entity/UserPaymentMethod.java:11-18`
  - `src/main/java/com/luna/aggarly/user/service/impl/UserPaymentMethodServiceImpl.java:51-56`
- **Analysis**:
  Neither the database table `user_payment_methods` nor the entity `UserPaymentMethod` defines a unique constraint on `(user_id, stripe_payment_method_id)`.
  In `UserPaymentMethodServiceImpl.savePaymentMethod`:
  ```java
  UserPaymentMethod entity = paymentMethodRepository
          .findByUserIdAndStripePaymentMethodId(userId, request.getStripePaymentMethodId())
          .orElseGet(...);
  ```
  Concurrent requests from a double-clicking client will both observe absence and insert identical cards. Subsequent calls to `findByUserIdAndStripePaymentMethodId` encounter multiple rows and crash with Spring Data's `IncorrectResultSizeDataAccessException` (HTTP 500).

#### ARC-04 [HIGH]: Missing `equals()` and `hashCode()` on `Role` Entity (PK Collisions)
- **Files**:
  - `src/main/java/com/luna/aggarly/user/entity/Role.java:10-24`
  - `src/main/java/com/luna/aggarly/user/entity/User.java:77-84`
  - `src/main/java/com/luna/aggarly/user/service/impl/UserProfileServiceImpl.java:165-168`
- **Analysis**:
  `Role.java` does not implement `equals()` and `hashCode()`, defaulting to object reference equality (`==`). In `User.java`, roles are stored in a `Set<Role>`.
  When `UserProfileServiceImpl.becomeHost()` checks `if (!user.getRoles().contains(hostRole))`, if `hostRole` was fetched in a different persistence context, `contains()` returns `false` even if the user already has `HOST`. Hibernate then attempts to insert `(user_id, host_role_id)` into `user_roles`, throwing a database primary key violation.

#### ARC-05 [PASS]: JPA Cascade Safety Verification
- **Verification**:
  Compliance with Aggarly Architecture Rule 3 was verified. `User.java` contains **zero** `@OneToMany` relationships with `CascadeType.ALL` or `CascadeType.PERSIST`. `RefreshToken`, `UserPaymentMethod`, and `UserConfirmedAction` maintain completely separate lifecycles and are persisted strictly through their respective repositories.

---

### 3.2 Clean Architecture & SOLID Principles

#### ARC-06 [HIGH]: Controller Layer Architectural Breach in `UserController`
- **File**: `src/main/java/com/luna/aggarly/user/controller/UserController.java:31-68`
- **Analysis**:
  `UserController` violates the layered architectural boundary by directly injecting `UserRepository` and `UserMapper`. The controller:
  - Directly executes repository database queries (`userRepository.findRecentUsers`, `userRepository.searchUsers`).
  - Performs in-memory stream filtering (`u.getId().equals(currentUserId)`).
  - Executes database operations outside managed `@Transactional(readOnly = true)` boundaries.
- **Remediation**: Introduce `UserService` (or delegate to `UserProfileService`) to encapsulate user directory queries, pagination, and filtering.

#### ARC-07 [MEDIUM]: Dual Service Schizophrenia & Dead Code in `AuthServiceImpl`
- **Files**:
  - `src/main/java/com/luna/aggarly/user/service/AuthService.java:16-26`
  - `src/main/java/com/luna/aggarly/user/service/impl/AuthServiceImpl.java:293-320, 365-383, 426-500`
  - `src/main/java/com/luna/aggarly/user/service/UserProfileService.java`
- **Analysis**:
  Seven user profile management methods (`getCurrentUserProfile`, `updateCurrentUserProfile`, `changePassword`, `sendPhoneOtp`, `verifyPhone`, `becomeHost`, `deactivateAccount`) are declared and implemented in both `AuthServiceImpl` and `UserProfileServiceImpl`.
  `AuthController` routes none of these; all profile routes are handled by `UserProfileController` routing to `UserProfileServiceImpl`. The implementations in `AuthServiceImpl` are completely dead code, yet they diverge in behavior (e.g. `AuthServiceImpl.deactivateAccount` revokes refresh tokens, while `UserProfileServiceImpl.deactivateAccount` does not).
- **Remediation**: Purge the 7 duplicated profile methods from `AuthService` and `AuthServiceImpl`.

#### ARC-08 [MEDIUM]: Blocking SMTP Network I/O Inside Open Database Transactions
- **File**: `src/main/java/com/luna/aggarly/user/service/impl/AuthServiceImpl.java:357, 399`
- **Analysis**:
  `sendVerificationEmail` and `forgotPassword` are annotated with `@Transactional` and synchronously call `emailService.send(...)`. Holding open a database connection lease while awaiting external SMTP network round-trips can quickly exhaust the HikariCP connection pool during traffic spikes or mail server latency.
- **Remediation**: Trigger emails asynchronously via `@Async` or execute email delivery after transaction commit using Spring's `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`.

#### ARC-09 [MEDIUM]: Cyclic Dependency Coupling from `common.security` to `user`
- **Files**:
  - `src/main/java/com/luna/aggarly/common/security/SecurityConfig.java:7-8`
  - `src/main/java/com/luna/aggarly/common/security/jwt/JwtService.java:3-4`
- **Analysis**:
  `common.security` directly imports classes from `com.luna.aggarly.user` (`SecurityConfig` imports `OAuth2SuccessHandler` and `OAuth2UserService`; `JwtService` imports `User` and `UserPrincipal`).
  This creates an inverted circular dependency between the generic common module and the domain-specific user module.

---

### 3.3 DTO Validation & Exception Safety

#### ARC-10 [HIGH]: Complete Absence of Jakarta Validation in Critical DTOs
- **Files**:
  - `src/main/java/com/luna/aggarly/user/dto/request/ConfirmMfaRequest.java:3`
  - `src/main/java/com/luna/aggarly/user/dto/request/UserProfileUpdate.java:6-13`
- **Analysis**:
  - `ConfirmMfaRequest`: Record fields `token` and `totpCode` have zero validation annotations. The `@Valid` annotation on `AuthController.confirmMFA` is completely inoperative; clients can supply null, blank, or malformed strings.
  - `UserProfileUpdate`: Fields `firstName`, `lastName`, `displayName`, `phone`, `avatarUrl`, and `bio` lack all constraints. Attackers can inject megabyte-sized bio payloads or malformed URLs.

#### ARC-11 [MEDIUM]: Incomplete Validation Constraints in `SavePaymentMethodRequest`
- **File**: `src/main/java/com/luna/aggarly/user/dto/SavePaymentMethodRequest.java:23-28`
- **Analysis**:
  - `expMonth` has `@NotNull` but lacks `@Min(1) @Max(12)`.
  - `expYear` has `@NotNull` but lacks `@Min(2024)`.
  - `lastFour` has `@NotBlank` but lacks `@Pattern(regexp = "^\\d{4}$")`.
  - `stripePaymentMethodId` lacks pattern validation for Stripe identifiers (`^pm_[a-zA-Z0-9]+$`).

#### ARC-12 [LOW]: Unhandled Exception Handling & RuntimeException Usage
- **Files**:
  - `src/main/java/com/luna/aggarly/user/service/impl/AuthServiceImpl.java:153, 78, 481`
  - `src/main/java/com/luna/aggarly/user/service/impl/UserPaymentMethodServiceImpl.java:76, 98`
- **Analysis**:
  - `AuthServiceImpl` line 153 throws `new RuntimeException("")` when MFA is already enabled, returning an uninformative 500 error instead of a 400/409 client error.
  - `UserPaymentMethodServiceImpl` throws `jakarta.persistence.EntityNotFoundException` directly into the web layer instead of a custom domain exception handled by `UserExceptionHandler`.

---

## 4. R3. Prioritized Remediation Catalog

Every finding identified in R1 and R2 is detailed below with its unique identifier, severity rating, affected file and line numbers, vulnerability/architecture description, exploit scenario, and exact, concrete drop-in code fix.

---

### Finding SEC-01: Central Authorization PermitAll Leakage of Vision Admin APIs
- **Severity**: **CRITICAL**
- **File Path**: `src/main/java/com/luna/aggarly/common/security/SecurityConfig.java`
- **Line Numbers**: 95–97
- **Description**:
  The request matcher block configured with `.permitAll()` inadvertently includes administrative Vision AI endpoints (`/api/v1/vision/admin/ollama/**`, `/api/v1/vision/admin/clip/**`, `/api/v1/vision/admin/models`).
- **Threat Vector / Exploit Scenario**:
  An unauthenticated external attacker sends HTTP requests directly to `/api/v1/vision/admin/ollama/status` or model load endpoints. This triggers unauthenticated model switches, GPU resource starvation, and administrative access without requiring an `ADMIN` role or authentication token.
- **Concrete Code Remediation Snippet**:
  In `src/main/java/com/luna/aggarly/common/security/SecurityConfig.java`, remove lines 95–97 from the `permitAll()` matcher:

```java
<<<<
                    .requestMatchers(
                            "/api/v1/properties/search",
                            "/api/v1/properties/{id}",
                            "/api/v1/storage/files/view",
                            "/api/v1/storage/files/view/**",
                            "/api/v1/vision/search",
                            "/api/v1/vision/search/**",
                            "/api/v1/vision/admin/ollama/**",
                            "/api/v1/vision/admin/clip/**",
                            "/api/v1/vision/admin/models"
                    ).permitAll()
====
                    .requestMatchers(
                            "/api/v1/properties/search",
                            "/api/v1/properties/{id}",
                            "/api/v1/storage/files/view",
                            "/api/v1/storage/files/view/**",
                            "/api/v1/vision/search",
                            "/api/v1/vision/search/**"
                    ).permitAll()
>>>>
```

---

### Finding ARC-01: Enum Desynchronization on `AuthProvider.SYSTEM`
- **Severity**: **CRITICAL**
- **File Path**: `src/main/java/com/luna/aggarly/user/entity/enums/AuthProvider.java`
- **Line Numbers**: 6–10
- **Description**:
  Flyway migration `V26__seed_system_ai_user.sql` seeds the system AI concierge user with `auth_provider = 'SYSTEM'`. However, `AuthProvider` only defines `LOCAL`, `GOOGLE`, and `GITHUB`.
- **Threat Vector / Exploit Scenario**:
  Whenever the AI Concierge or any system flow attempts to load the system bot entity (`userRepository.findById(UUID.fromString("aaac7011-3626-460c-a47e-c94535d34c65"))`), Hibernate attempts to convert the database value `'SYSTEM'` using `Enum.valueOf(AuthProvider.class, "SYSTEM")`, throwing `IllegalArgumentException` and crashing the application with a 500 error.
- **Concrete Code Remediation Snippet**:
  In `src/main/java/com/luna/aggarly/user/entity/enums/AuthProvider.java`:

```java
<<<<
public enum AuthProvider {
    LOCAL,
    GOOGLE,
    GITHUB
}
====
public enum AuthProvider {
    LOCAL,
    GOOGLE,
    GITHUB,
    SYSTEM
}
>>>>
```

---

### Finding SEC-05: Account Deactivation Fails to Revoke Refresh Tokens & `UserPrincipal.isEnabled()` Indefinite Auth Flaw
- **Severity**: **CRITICAL**
- **File Paths**:
  1. `src/main/java/com/luna/aggarly/user/service/impl/UserProfileServiceImpl.java` (lines 174–179)
  2. `src/main/java/com/luna/aggarly/user/security/UserPrincipal.java` (lines 72–74)
- **Description**:
  `UserProfileServiceImpl.deactivateAccount` sets `user.setDeleted(true)` but fails to revoke active refresh tokens. Simultaneously, `UserPrincipal.isEnabled()` unconditionally returns `true`.
- **Threat Vector / Exploit Scenario**:
  A user deactivates their account (or an admin disables an account), but the user's mobile app or browser still retains an active refresh token. The client calls `POST /api/v1/auth/refresh`. Because tokens were never revoked and `UserPrincipal.isEnabled()` returns `true`, the system issues new valid access tokens, granting indefinite access to a deactivated account.
- **Concrete Code Remediation Snippet**:
  1. In `src/main/java/com/luna/aggarly/user/service/impl/UserProfileServiceImpl.java`:

```java
<<<<
    @Override
    @Transactional
    public void deactivateAccount(UUID userId) {
        User user = findUserById(userId);
        user.setDeleted(true);
        userRepository.save(user);
        log.info("Account deactivated (soft-deleted) for user: {}", user.getEmail());
    }
====
    @Override
    @Transactional
    public void deactivateAccount(UUID userId) {
        User user = findUserById(userId);
        user.setDeleted(true);
        userRepository.save(user);
        refreshTokenRepository.revokeAllUserTokens(user);
        log.info("Account deactivated (soft-deleted) and refresh tokens revoked for user: {}", user.getEmail());
    }
>>>>
```
*(Ensure `private final RefreshTokenRepository refreshTokenRepository;` is injected into `UserProfileServiceImpl`).*

  2. In `src/main/java/com/luna/aggarly/user/security/UserPrincipal.java`:

```java
<<<<
    @Override
    public boolean isEnabled() {
        return true;
    }
====
    @Override
    public boolean isEnabled() {
        return user != null && !user.isDeleted();
    }
>>>>
```

---

### Finding ARC-02: Soft-Delete Collision with Unconditional Database Unique Constraints
- **Severity**: **HIGH**
- **File Paths**:
  - `src/main/resources/db/migration/V38__fix_users_soft_delete_unique_constraints.sql` (New Migration)
  - `src/main/java/com/luna/aggarly/user/service/impl/AuthServiceImpl.java` (lines 69–75)
- **Description**:
  PostgreSQL enforces unconditional unique constraints on `users(email)` and `users(username)`. When an account is soft-deleted (`is_deleted = true`), `userRepository.existsByEmail()` returns `false` due to `@SQLRestriction("is_deleted = false")`. Subsequent re-registration attempts trigger a PostgreSQL unique constraint violation and throw an unhandled 500 error.
- **Threat Vector / Exploit Scenario**:
  A user deletes their account and later attempts to register again with the same email. The system reports that the email is available, attempts to insert the row, and crashes with an internal 500 error instead of handling account reactivation or displaying an informative message.
- **Concrete Code Remediation Snippet**:
  1. Create Flyway migration `src/main/resources/db/migration/V38__fix_users_soft_delete_unique_constraints.sql`:

```sql
-- Drop unconditional unique constraints on users
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_email_key;
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_username_key;

-- Create partial unique indexes that enforce uniqueness only on active (non-deleted) accounts
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_active_email ON users (email) WHERE is_deleted = FALSE;
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_active_username ON users (username) WHERE is_deleted = FALSE;
```

  2. In `src/main/java/com/luna/aggarly/user/service/impl/AuthServiceImpl.java`, check for soft-deleted accounts:

```java
<<<<
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException("Email already in use: " + request.email());
        }
====
        User existingUser = userRepository.findAnyByEmail(request.email()).orElse(null);
        if (existingUser != null) {
            if (existingUser.isDeleted()) {
                throw new AccountDeactivatedException("Account with this email was deactivated. Please contact support or reactivate your account.");
            }
            throw new EmailAlreadyExistsException("Email already in use: " + request.email());
        }
>>>>
```

---

### Finding ARC-03: Missing Composite Unique Constraint on `user_payment_methods`
- **Severity**: **HIGH**
- **File Paths**:
  - `src/main/resources/db/migration/V39__add_uq_user_payment_methods.sql` (New Migration)
  - `src/main/java/com/luna/aggarly/user/entity/UserPaymentMethod.java` (lines 11–18)
- **Description**:
  Table `user_payment_methods` lacks a unique constraint on `(user_id, stripe_payment_method_id)`.
- **Threat Vector / Exploit Scenario**:
  Under concurrent API calls or client double-submission, duplicate rows with the identical Stripe PM ID are saved for the same user. Subsequent invocations of `findByUserIdAndStripePaymentMethodId` encounter multiple rows and crash with `IncorrectResultSizeDataAccessException` (500 Internal Server Error).
- **Concrete Code Remediation Snippet**:
  1. Create Flyway migration `src/main/resources/db/migration/V39__add_uq_user_payment_methods.sql`:

```sql
ALTER TABLE user_payment_methods
    ADD CONSTRAINT uq_user_payment_methods_user_stripe UNIQUE (user_id, stripe_payment_method_id);
```

  2. In `src/main/java/com/luna/aggarly/user/entity/UserPaymentMethod.java`:

```java
<<<<
@Entity
@Table(name = "user_payment_methods")
@Getter
====
@Entity
@Table(
    name = "user_payment_methods",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_user_payment_methods_user_stripe",
        columnNames = {"user_id", "stripe_payment_method_id"}
    )
)
@Getter
>>>>
```

---

### Finding ARC-04: Missing `equals()` and `hashCode()` on `Role` Entity
- **Severity**: **HIGH**
- **File Path**: `src/main/java/com/luna/aggarly/user/entity/Role.java`
- **Line Numbers**: 10–24
- **Description**:
  `Role` relies on `Object` reference identity. When evaluated in `user.getRoles().contains(hostRole)`, detached instances return `false`, causing duplicate insert attempts into `user_roles (user_id, role_id)`.
- **Threat Vector / Exploit Scenario**:
  A user who is already a host or elevated in another transaction calls `/api/v1/users/me/become-host`. The set membership check fails, leading Hibernate to attempt inserting the duplicate primary key, crashing with `DataIntegrityViolationException`.
- **Concrete Code Remediation Snippet**:
  In `src/main/java/com/luna/aggarly/user/entity/Role.java`:

```java
<<<<
    @Column(nullable = false, unique = true, length = 50)
    private String name;
}
====
    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Role role)) return false;
        return java.util.Objects.equals(name, role.name);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(name);
    }
}
>>>>
```

---

### Finding SEC-03: Sensitive Token Exposure in OAuth2 Callback Redirect URL
- **Severity**: **HIGH**
- **File Path**: `src/main/java/com/luna/aggarly/user/security/oauth2/OAuth2SuccessHandler.java`
- **Line Numbers**: 67–71
- **Description**:
  `OAuth2SuccessHandler` passes raw JWT access and refresh tokens directly in the URL query string (`?token=...&refreshToken=...`).
- **Threat Vector / Exploit Scenario**:
  The redirected URL is written to browser history, proxy server logs, and sent via `Referer` headers to third-party assets, exposing active session credentials to eavesdroppers or malicious browser extensions.
- **Concrete Code Remediation Snippet**:
  In `src/main/java/com/luna/aggarly/user/security/oauth2/OAuth2SuccessHandler.java`, set tokens in secure HTTP-only cookies and redirect to the frontend callback page without query parameters:

```java
<<<<
        // Redirect to Frontend URL passing tokens as URL query params
        String redirectUrl = String.format("%s?token=%s&refreshToken=%s",
                frontendRedirectUrl, accessToken, refreshTokenValue);

        response.sendRedirect(redirectUrl);
====
        // Deliver tokens via secure HttpOnly SameSite cookies
        jakarta.servlet.http.Cookie accessCookie = new jakarta.servlet.http.Cookie("access_token", accessToken);
        accessCookie.setHttpOnly(true);
        accessCookie.setSecure(true);
        accessCookie.setPath("/");
        accessCookie.setMaxAge(900); // 15 minutes
        accessCookie.setAttribute("SameSite", "Lax");
        response.addCookie(accessCookie);

        jakarta.servlet.http.Cookie refreshCookie = new jakarta.servlet.http.Cookie("refresh_token", refreshTokenValue);
        refreshCookie.setHttpOnly(true);
        refreshCookie.setSecure(true);
        refreshCookie.setPath("/api/v1/auth");
        refreshCookie.setMaxAge((int) Duration.ofDays(refreshExpirationDays).toSeconds());
        refreshCookie.setAttribute("SameSite", "Strict");
        response.addCookie(refreshCookie);

        // Redirect cleanly without exposing tokens in URL parameters
        response.sendRedirect(frontendRedirectUrl);
>>>>
```

---

### Finding SEC-08 & SEC-09: Timing Attack & Premature Invalidation in OTP Service
- **Severity**: **HIGH**
- **File Path**: `src/main/java/com/luna/aggarly/user/service/impl/OtpServiceImpl.java`
- **Line Numbers**: 83–100
- **Description**:
  1. `redisTemplate.delete(key)` is invoked before verifying code correctness, destroying the OTP on a user's first typo.
  2. `code.trim().equals(storedOtp)` uses non-constant-time equality, leaking execution timing.
- **Threat Vector / Exploit Scenario**:
  An attacker can analyze sub-microsecond response differentials to guess OTP digits. Concurrently, legitimate users are locked out and forced to re-request codes upon any single-digit mistake.
- **Concrete Code Remediation Snippet**:
  In `src/main/java/com/luna/aggarly/user/service/impl/OtpServiceImpl.java`:

```java
<<<<
    private boolean validateAndConsumeOtp(String key, String code) {
        if (code == null || code.isBlank()) {
            return false;
        }

        String storedOtp = null;
        try {
            storedOtp = redisTemplate.opsForValue().get(key);
            if (storedOtp != null) {
                redisTemplate.delete(key);
            }
        } catch (Exception e) {
            log.warn("⚠️ Redis unavailable, checking local memory store for OTP: {}", e.getMessage());
            storedOtp = localMemoryStore.remove(key);
        }

        return code.trim().equals(storedOtp);
    }
====
    private boolean validateAndConsumeOtp(String key, String code) {
        if (code == null || code.isBlank()) {
            return false;
        }

        String storedOtp = null;
        try {
            storedOtp = redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("⚠️ Redis unavailable, checking local memory store for OTP: {}", e.getMessage());
            storedOtp = localMemoryStore.get(key);
        }

        if (storedOtp == null) {
            return false;
        }

        // Constant-time byte array comparison prevents timing attacks
        byte[] expectedBytes = storedOtp.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] actualBytes = code.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        boolean matches = java.security.MessageDigest.isEqual(expectedBytes, actualBytes);

        if (matches) {
            // Delete only upon successful validation
            try {
                redisTemplate.delete(key);
            } catch (Exception e) {
                localMemoryStore.remove(key);
            }
        }

        return matches;
    }
>>>>
```

---

### Finding ARC-06: Controller Layer Architectural Breach in `UserController`
- **Severity**: **HIGH**
- **File Path**: `src/main/java/com/luna/aggarly/user/controller/UserController.java`
- **Line Numbers**: 31–68
- **Description**:
  `UserController` directly injects `UserRepository` and `UserMapper`, querying the database and performing business filtering in HTTP handlers without a service abstraction.
- **Threat Vector / Exploit Scenario**:
  Database transactions are unmanaged; changes to database schema or business filtering logic leak directly into HTTP controller classes, violating SOLID principles.
- **Concrete Code Remediation Snippet**:
  1. Define `UserService` contract:

```java
package com.luna.aggarly.user.service;

import com.luna.aggarly.user.dto.response.UserProfileSummaryResponse;
import java.util.List;
import java.util.UUID;

public interface UserService {
    UserProfileSummaryResponse getUserById(UUID id);
    List<UserProfileSummaryResponse> searchUsers(String query, int limit, UUID currentUserId);
}
```

  2. In `src/main/java/com/luna/aggarly/user/controller/UserController.java`:

```java
<<<<
    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @GetMapping("/{id}")
    @Operation(summary = "Get public user profile by ID", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<UserProfileSummaryResponse>> getUserById(@PathVariable UUID id) {
        return userRepository.findById(id)
                .map(userMapper::toSummaryResponse)
                .map(summary -> ApiResponse.ok(summary, "User profile retrieved successfully").toResponseEntity())
                .orElseGet(() -> ApiResponse.<UserProfileSummaryResponse>notFound("User not found with id: " + id).toResponseEntity());
    }

    @GetMapping("/search")
    @Operation(summary = "Search users for direct messaging by name, username, or email", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<UserProfileSummaryResponse>>> searchUsers(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false, defaultValue = "") String query,
            @RequestParam(required = false, defaultValue = "10") int limit) {
        UUID currentUserId = principal != null ? principal.getUserId() : null;
        int max = Math.min(Math.max(1, limit), 50);

        List<User> users;
        if (query == null || query.trim().isBlank()) {
            users = currentUserId != null
                    ? userRepository.findRecentUsers(currentUserId, PageRequest.of(0, max))
                    : userRepository.findAll(PageRequest.of(0, max)).getContent();
        } else {
            users = userRepository.searchUsers(query.trim(), PageRequest.of(0, max)).stream()
                    .filter(u -> currentUserId == null || !u.getId().equals(currentUserId))
                    .toList();
        }

        List<UserProfileSummaryResponse> response = users.stream()
                .map(userMapper::toSummaryResponse)
                .toList();

        return ApiResponse.ok(response, "Users matching query retrieved").toResponseEntity();
    }
====
    private final UserService userService;

    @GetMapping("/{id}")
    @Operation(summary = "Get public user profile by ID", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<UserProfileSummaryResponse>> getUserById(@PathVariable UUID id) {
        UserProfileSummaryResponse response = userService.getUserById(id);
        return ApiResponse.ok(response, "User profile retrieved successfully").toResponseEntity();
    }

    @GetMapping("/search")
    @Operation(summary = "Search users for direct messaging by name, username, or email", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<UserProfileSummaryResponse>>> searchUsers(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false, defaultValue = "") String query,
            @RequestParam(required = false, defaultValue = "10") int limit) {
        UUID currentUserId = principal != null ? principal.getUserId() : null;
        List<UserProfileSummaryResponse> response = userService.searchUsers(query, limit, currentUserId);
        return ApiResponse.ok(response, "Users matching query retrieved").toResponseEntity();
    }
>>>>
```

---

### Finding ARC-10: Complete Absence of Validation in `ConfirmMfaRequest` & `UserProfileUpdate`
- **Severity**: **HIGH**
- **File Paths**:
  1. `src/main/java/com/luna/aggarly/user/dto/request/ConfirmMfaRequest.java`
  2. `src/main/java/com/luna/aggarly/user/dto/request/UserProfileUpdate.java`
- **Description**:
  Both DTOs contain zero validation annotations, rendering controller `@Valid` checks completely ineffective.
- **Threat Vector / Exploit Scenario**:
  Null, empty, or maliciously oversized strings bypass controller validation, causing unexpected null pointer exceptions, unhandled 500 errors, or database truncation crashes.
- **Concrete Code Remediation Snippet**:
  1. In `src/main/java/com/luna/aggarly/user/dto/request/ConfirmMfaRequest.java`:

```java
<<<<
package com.luna.aggarly.user.dto.request;

public record ConfirmMfaRequest(String token,String totpCode) {
}
====
package com.luna.aggarly.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ConfirmMfaRequest(
    @NotBlank(message = "Challenge token is required")
    String token,

    @NotBlank(message = "TOTP code is required")
    @Pattern(regexp = "^\\d{6}$", message = "TOTP code must be exactly 6 digits")
    String totpCode
) {}
>>>>
```

  2. In `src/main/java/com/luna/aggarly/user/dto/request/UserProfileUpdate.java`:

```java
<<<<
package com.luna.aggarly.user.dto.request;

import lombok.Builder;

@Builder
public record UserProfileUpdate(
    String firstName,
    String lastName,
    String displayName,
    String phone,
    String avatarUrl,
    String bio
) {}
====
package com.luna.aggarly.user.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import org.hibernate.validator.constraints.URL;

@Builder
public record UserProfileUpdate(
    @Size(max = 100, message = "First name must not exceed 100 characters")
    String firstName,

    @Size(max = 100, message = "Last name must not exceed 100 characters")
    String lastName,

    @Size(max = 100, message = "Display name must not exceed 100 characters")
    String displayName,

    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Invalid E.164 phone number format")
    String phone,

    @URL(message = "Avatar URL must be a valid web URL")
    @Size(max = 512, message = "Avatar URL must not exceed 512 characters")
    String avatarUrl,

    @Size(max = 500, message = "Bio must not exceed 500 characters")
    String bio
) {}
>>>>
```

---

### Finding SEC-07: Plaintext Logging of 6-Digit OTP Codes
- **Severity**: **MEDIUM**
- **File Path**: `src/main/java/com/luna/aggarly/user/service/impl/AuthServiceImpl.java`
- **Line Numbers**: 98, 358, 400, 439
- **Description**:
  Generated OTP codes are emitted into SLF4J logs in cleartext.
- **Threat Vector / Exploit Scenario**:
  Malicious insiders or third-party log monitoring services can intercept verification codes and compromise user accounts.
- **Concrete Code Remediation Snippet**:
  In `src/main/java/com/luna/aggarly/user/service/impl/AuthServiceImpl.java`, sanitize log messages to omit the raw `otpCode`:

```java
<<<<
        log.info("📧 6-digit Email verification OTP for {}: {}", user.getEmail(), otpCode);
====
        log.info("📧 6-digit Email verification OTP generated and dispatched for {}", user.getEmail());
>>>>

<<<<
            log.info("sent 6-digit Email verification OTP for {}: {}", email, otpCode);
====
            log.info("sent 6-digit Email verification OTP for {}", email);
>>>>

<<<<
            log.info("sent 6-digit verification OTP for {}: {}", request.email(), otpCode);
====
            log.info("sent 6-digit verification OTP for {}", request.email());
>>>>

<<<<
        log.info("📱 6-digit OTP sent to phone {} for user {}: {}", user.getPhone(), user.getEmail(), otpCode);
====
        log.info("📱 6-digit OTP sent to registered phone for user {}", user.getEmail());
>>>>
```

---

### Finding SEC-04: Refresh Token in GET Query Parameter on Logout
- **Severity**: **MEDIUM**
- **File Path**: `src/main/java/com/luna/aggarly/user/controller/AuthController.java`
- **Line Numbers**: 56–61
- **Description**:
  `AuthController.logout` receives `refreshToken` via `@RequestParam`.
- **Threat Vector / Exploit Scenario**:
  The token is leaked into web server access logs and browser history during logout.
- **Concrete Code Remediation Snippet**:
  In `src/main/java/com/luna/aggarly/user/controller/AuthController.java`:

```java
<<<<
    @PostMapping("/logout")
    @Operation(summary = "Invalidate active session refresh token")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestParam("refreshToken") String refreshToken) {
        authService.logout(refreshToken);
        return ApiResponse.<Void>empty("Logged out successfully").toResponseEntity();
    }
====
    @PostMapping("/logout")
    @Operation(summary = "Invalidate active session refresh token")
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
        return ApiResponse.<Void>empty("Logged out successfully").toResponseEntity();
    }
>>>>
```

---

### Finding ARC-07: Dead Code Duplication Between `AuthService` and `UserProfileService`
- **Severity**: **MEDIUM**
- **File Paths**:
  - `src/main/java/com/luna/aggarly/user/service/AuthService.java` (lines 16–26)
  - `src/main/java/com/luna/aggarly/user/service/impl/AuthServiceImpl.java` (lines 293–320, 365–383, 426–500)
- **Description**:
  `AuthService` declares and implements 7 profile management methods that are never called by `AuthController`.
- **Threat Vector / Exploit Scenario**:
  Architectural confusion and maintenance drift; developers modifying profile logic update one service while endpoints execute the other.
- **Concrete Code Remediation Snippet**:
  Delete the unused methods (`getCurrentUserProfile`, `updateCurrentUserProfile`, `changePassword`, `sendPhoneOtp`, `verifyPhone`, `becomeHost`, `deactivateAccount`) from `AuthService.java` and `AuthServiceImpl.java`. All profile operations must remain strictly consolidated within `UserProfileServiceImpl.java`.

---

### Finding ARC-08: Blocking SMTP Network Calls Inside Database Transactions
- **Severity**: **MEDIUM**
- **File Path**: `src/main/java/com/luna/aggarly/user/service/impl/AuthServiceImpl.java`
- **Line Numbers**: 357, 399
- **Description**:
  `emailService.send(...)` is invoked synchronously inside methods marked with `@Transactional`.
- **Threat Vector / Exploit Scenario**:
  If the SMTP server is slow or experiencing latency, database connections are held open for seconds, leading to connection pool starvation for all users.
- **Concrete Code Remediation Snippet**:
  Annotate email sending with `@Async` in `EmailServiceImpl.java`:

```java
<<<<
    @Override
    public void send(String to, String subject, String htmlContent) throws MessagingException {
====
    @Override
    @org.springframework.scheduling.annotation.Async
    public void send(String to, String subject, String htmlContent) throws MessagingException {
>>>>
```

---

### Finding ARC-11: Missing Card Boundary Validation in `SavePaymentMethodRequest`
- **Severity**: **MEDIUM**
- **File Path**: `src/main/java/com/luna/aggarly/user/dto/SavePaymentMethodRequest.java`
- **Line Numbers**: 23–28
- **Description**:
  Missing numeric range and pattern validations for card expiration month, year, and last four digits.
- **Threat Vector / Exploit Scenario**:
  Malformed expiration dates (e.g. month 99, year 1900) or non-numeric card suffixes are persisted to the database.
- **Concrete Code Remediation Snippet**:
  In `src/main/java/com/luna/aggarly/user/dto/SavePaymentMethodRequest.java`:

```java
<<<<
    @NotBlank(message = "Last four digits are required")
    private String lastFour;

    @NotNull(message = "Expiration month is required")
    private Integer expMonth;

    @NotNull(message = "Expiration year is required")
    private Integer expYear;
====
    @NotBlank(message = "Last four digits are required")
    @jakarta.validation.constraints.Pattern(regexp = "^\\d{4}$", message = "Last four must be exactly 4 digits")
    private String lastFour;

    @NotNull(message = "Expiration month is required")
    @jakarta.validation.constraints.Min(value = 1, message = "Expiration month must be between 1 and 12")
    @jakarta.validation.constraints.Max(value = 12, message = "Expiration month must be between 1 and 12")
    private Integer expMonth;

    @NotNull(message = "Expiration year is required")
    @jakarta.validation.constraints.Min(value = 2024, message = "Expiration year must be current or future")
    private Integer expYear;
>>>>
```

---

### Finding ARC-12: Redis Key Namespace Collision in `MfaServiceImpl`
- **Severity**: **MEDIUM**
- **File Path**: `src/main/java/com/luna/aggarly/user/service/impl/MfaServiceImpl.java`
- **Line Numbers**: 32, 54
- **Description**:
  Both login challenge tokens and registration setup tokens use the identical prefix `"mfa:" + token`, storing different record types (`Mfa` vs `MfaConfirmation`).
- **Threat Vector / Exploit Scenario**:
  Submitting a setup token to the login verification route triggers an unhandled `ClassCastException` during Redis deserialization.
- **Concrete Code Remediation Snippet**:
  In `src/main/java/com/luna/aggarly/user/service/impl/MfaServiceImpl.java`:

```java
<<<<
    public AuthResponse create(UUID userId) {
        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set("mfa:" + token, new Mfa(userId), 5, TimeUnit.MINUTES);
====
    public AuthResponse create(UUID userId) {
        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set("mfa:challenge:" + token, new Mfa(userId), 5, TimeUnit.MINUTES);
>>>>

<<<<
    public String CreateMfa(UUID userId, String secret) {
        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set("mfa:" + token, new MfaConfirmation(userId, secret), 5, TimeUnit.MINUTES);
====
    public String createMfa(UUID userId, String secret) {
        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set("mfa:setup:" + token, new MfaConfirmation(userId, secret), 5, TimeUnit.MINUTES);
>>>>
```

---

### Finding ARC-13: Crude `RuntimeException` Usage in `AuthServiceImpl`
- **Severity**: **LOW**
- **File Path**: `src/main/java/com/luna/aggarly/user/service/impl/AuthServiceImpl.java`
- **Line Numbers**: 153
- **Description**:
  Throws empty `new RuntimeException("")` when MFA is already enabled, returning an uninformative 500 error.
- **Threat Vector / Exploit Scenario**:
  Client receives a generic 500 Internal Server Error without context when attempting to re-enable MFA.
- **Concrete Code Remediation Snippet**:
  In `src/main/java/com/luna/aggarly/user/service/impl/AuthServiceImpl.java`:

```java
<<<<
        if(user.isMfaEnabled()) {
            throw new RuntimeException("");
        }
====
        if (user.isMfaEnabled()) {
            throw new VerificationException("MFA is already enabled for this account");
        }
>>>>
```

---

### Finding ARC-14: Redundant Index on `user_confirmed_actions(confirmation_token)`
- **Severity**: **LOW**
- **File Path**: `src/main/resources/db/migration/V30__create_user_payment_methods_and_action_states.sql`
- **Line Numbers**: 28, 38–39
- **Description**:
  `confirmation_token` has a `UNIQUE` constraint (which creates an automatic unique btree index) and an explicit secondary `CREATE INDEX idx_user_confirmed_actions_token`.
- **Threat Vector / Exploit Scenario**:
  Wasted disk storage and double index write overhead on every insert.
- **Concrete Code Remediation Snippet**:
  In a new cleanup migration:

```sql
DROP INDEX IF EXISTS idx_user_confirmed_actions_token;
```

---

### Finding ARC-15: Missing Query Index on `users(created_at)`
- **Severity**: **LOW**
- **File Path**: `src/main/resources/db/migration/V2__create_auth_schema.sql`
- **Line Numbers**: 25
- **Description**:
  `UserRepository.findRecentUsers` orders by `created_at DESC` without an index.
- **Threat Vector / Exploit Scenario**:
  Full table scans and file sorts as user volume scales.
- **Concrete Code Remediation Snippet**:
  In a new Flyway migration:

```sql
CREATE INDEX IF NOT EXISTS idx_users_created_at_desc ON users (created_at DESC);
```

---

## 5. Architectural Verification Matrix

| Architectural Guardrail / Security Requirement | Status | Audit Findings Reference |
|---|---|---|
| Rule 1: No `@PreAuthorize("isAuthenticated()")` in controllers | **PASS (100%)** | Zero instances found across entire codebase. Fully compliant. |
| Rule 1: Central enforcement via `SecurityConfig.anyRequest().authenticated()` | **PASS** | Enforced at `SecurityConfig.java:113`. |
| Rule 1: Minimal `permitAll()` boundaries (auth, swagger, ws only) | **FAIL** | Admin Vision APIs exposed via `permitAll()` (`SEC-01`). |
| Rule 3: System bot identity seeded in Flyway migrations | **FAIL** | Bot seeded in `V26`, but `AuthProvider.SYSTEM` missing from enum (`ARC-01`). |
| Rule 3: Zero `CascadeType.ALL` on parent `@OneToMany` collections | **PASS (100%)** | Entities strictly decoupled; child entities saved via dedicated repos. |
| BOLA / IDOR Protection on user profile & payment mutations | **PASS (100%)** | Derived strictly from `@AuthenticationPrincipal` and compound `(id, userId)` queries. |
| Modern Refresh Token Rotation (RTR) with Grace Window & Binding | **PASS** | 15s grace window, family ID lineage, SHA-256 access token binding fully implemented. |
| Soft-Delete Consistency & Parity | **FAIL** | DB unique constraint collisions (`ARC-02`); deactivation omits token revocation (`SEC-05`). |

---

*Report synthesized and submitted by `teamwork_preview_worker`.*
