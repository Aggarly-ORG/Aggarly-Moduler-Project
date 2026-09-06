# User Service & Architecture Survey Report (Gen 3)
**Module**: `com.luna.aggarly.user`  
**Explorer**: `teamwork_preview_explorer` (Gen 3)  
**Date**: 2026-09-05  
**Scope**: All Services, Clean Code Hygiene, Transaction Boundaries, Exception Safety, Jakarta Validation, and Cross-Module Boundaries  

---

## 1. Executive Summary

An exhaustive architectural audit and clean code inspection was conducted across the entire service and domain boundary of the User Module (`com.luna.aggarly.user`). This survey complements the Endpoint Specifications (Survey 2) and Database/JPA Parity (Survey 3) by examining service implementation details, transactional boundaries, exception resilience, input validation hygiene, and cross-module cohesion.

### Key Architectural Findings
1. **Critical Authentication & Session Desynchronization in Account Deactivation**:
   - `AuthServiceImpl.deactivateAccount()` properly revokes all user refresh tokens (`refreshTokenRepository.revokeAllUserTokens(user)`).
   - However, `UserProfileServiceImpl.deactivateAccount()` (which is the actual implementation bound to the public REST endpoint `DELETE /api/v1/users/me`) **only** sets `user.setDeleted(true)` and **fails to revoke refresh tokens**.
   - Compounding this flaw, `UserPrincipal.isEnabled()` unconditionally returns `true` (ignoring `user.isDeleted()`). Consequently, a deactivated user can continue to rotate their refresh token and issue new valid access tokens indefinitely.
2. **Dual Service Schizophrenia & Dead Code**:
   - Seven identical user profile methods (`getCurrentUserProfile`, `updateCurrentUserProfile`, `changePassword`, `sendPhoneOtp`, `verifyPhone`, `becomeHost`, `deactivateAccount`) are declared and implemented in both `AuthService`/`AuthServiceImpl` and `UserProfileService`/`UserProfileServiceImpl`.
   - `AuthController` never routes to these methods in `AuthService`, making them completely dead code in `AuthServiceImpl`.
3. **Controller Layer Architectural Breach (`UserController`)**:
   - `UserController` completely bypasses the service layer, directly injecting `UserRepository` and `UserMapper`. It performs query assembly, pagination, and business filtering (`!u.getId().equals(currentUserId)`) directly within HTTP handler methods, without transactional boundaries.
4. **Timing Attack Vulnerability & Premature Invalidation in OTP Service**:
   - `OtpServiceImpl.validateAndConsumeOtp` uses standard string equality (`code.trim().equals(storedOtp)`), creating a side-channel timing oracle.
   - It deletes the OTP from Redis *before* validating equality. A single accidental typo by a legitimate user instantly destroys their OTP, denying retries within the 5-minute validity window.
5. **Validation Gaps & Credential Exposure**:
   - `ConfirmMfaRequest` and `UserProfileUpdate` DTOs contain **zero Jakarta validation annotations**, rendering `@Valid` in controllers a complete no-op.
   - `AuthController.logout` accepts `refreshToken` via `@RequestParam`, leaking high-privilege credentials into web server access logs and browser history.
6. **Inverted Common-to-User Circular Module Dependencies**:
   - Foundational shared packages (`com.luna.aggarly.common.security`) directly import domain entities and handlers from `com.luna.aggarly.user` (`JwtService -> User`, `SecurityConfig -> OAuth2SuccessHandler`, `SecurityUtils -> UserPrincipal`).
   - Feature modules (`chat` and `notification`) directly inject `user.repository.UserRepository` rather than consuming domain service contracts or publishing asynchronous domain events.

---

## 2. Deep-Dive Service Implementation Analysis

The User Module contains seven service implementations in `com.luna.aggarly.user.service` and `com.luna.aggarly.user.service.impl`:

### 2.1 `AuthServiceImpl` (`com.luna.aggarly.user.service.impl.AuthServiceImpl`)
- **Core Responsibilities**: Registration, local credential authentication, JWT token issuance, Refresh Token Rotation (RTR) with 15-second grace window, TOTP MFA challenge-response, email verification, password reset, and phone OTP verification.
- **Architectural Observations**:
  1. **Transactional Boundaries on Private Methods**:
     - Lines 501, 511, 516: Methods `getAuthenticatedUser()` and `issueTokens(User, UUID)` are annotated with `@Transactional(readOnly = true)` and `@Transactional(propagation = Propagation.MANDATORY)`.
     - *Clean Code Smell*: In Spring AOP proxying, private methods are invoked internally via self-invocation (`this.issueTokens(...)`), completely bypassing the Spring proxy. The `@Transactional` annotations on private methods are ignored at runtime and give developers a false sense of security regarding propagation enforcement.
  2. **Blocking Network I/O Inside Open Database Transactions**:
     - Line 357 (`sendVerificationEmail`) & Line 399 (`forgotPassword`): Calls `emailService.send(...)` (synchronous SMTP network communication) directly inside a method annotated with `@Transactional`.
     - *Architectural Flaw*: Holding an active database transaction and connection pool lease open while waiting on external SMTP network round-trips creates connection pool exhaustion under load.
  3. **Silent Failure & Exception Swallowing**:
     - Lines 359-361 (`sendVerificationEmail`) & Lines 401-403 (`forgotPassword`): `MessagingException` is caught and logged at `ERROR` level, but the method returns normally.
     - *Client Impact*: The API returns HTTP 200 OK (`ApiResponse.empty("6-digit verification OTP resent...")`), but the email was never delivered. The client is misled into believing the OTP is in their inbox.
  4. **Crude Exception Throwing**:
     - Line 153 (`requestMfa`): `if(user.isMfaEnabled()) { throw new RuntimeException(""); }` — Throws raw `RuntimeException` with an empty string message instead of a domain exception (e.g. `IllegalStateException` or `MfaAlreadyEnabledException`).
     - Line 78 & Line 481: `throw new RuntimeException("Default GUEST/HOST role not seeded in database")` — Raw `RuntimeException` results in generic 500 error responses.
     - Line 135 (`totpValidate`): `userRepository.findById(otpEntry.userId()).orElseThrow();` — Throws `NoSuchElementException` without context if user is missing, resulting in an unhandled 500 error.

### 2.2 `EmailServiceImpl` (`com.luna.aggarly.user.service.impl.EmailServiceImpl`)
- **Core Responsibilities**: JavaMailSender HTML email dispatch and Thymeleaf template rendering.
- **Architectural Observations**:
  1. **Missing Configuration Fallback**:
     - Line 20: `@Value("${app.mail.from}") private String from;` lacks a default fallback (e.g. `@Value("${app.mail.from:noreply@aggarly.com}")`). If this key is missing from `application.yml` or test environments, context initialization fails.
  2. **Tight Coupling to Single Template**:
     - Lines 37-48 (`otpTemplate`): Directly hardcodes `"verify-email"` template and `"10 minutes"` expiration string, even though password reset and email verification OTPs have different semantics and TTLs (5 minutes in `OtpServiceImpl`).

### 2.3 `MfaServiceImpl` (`com.luna.aggarly.user.service.impl.MfaServiceImpl`)
- **Core Responsibilities**: Redis-backed temporary challenge storage for two-factor authentication (5-minute TTL).
- **Architectural Observations**:
  1. **Naming Convention Violation**:
     - Line 47: `public String CreateMfa(UUID userId, String secret)` uses PascalCase method naming instead of standard camelCase (`createMfa`).
  2. **Redis Key Prefix Collision & ClassCastException Hazard**:
     - Line 32: `create(UUID userId)` stores an `Mfa` record into key `"mfa:" + token`.
     - Line 54: `CreateMfa(UUID userId, String secret)` stores an `MfaConfirmation` record into key `"mfa:" + token`.
     - Both methods use the identical Redis key namespace (`"mfa:" + token`). If a token generated during MFA enrollment is passed to `get(token)` (line 39) or login challenge token to `getMfaConfirm(token)` (line 60), an unhandled `ClassCastException` is thrown at runtime during deserialization.
     - *Remediation*: Partition Redis keys into distinct namespaces: `"mfa:challenge:" + token` vs `"mfa:setup:" + token`.

### 2.4 `OtpServiceImpl` (`com.luna.aggarly.user.service.impl.OtpServiceImpl`)
- **Core Responsibilities**: 6-digit numeric OTP generation, storage in Redis (with in-memory fallback), and validation.
- **Architectural & Security Observations**:
  1. **Non-Constant-Time String Comparison (Timing Attack)**:
     - Line 99: `return code.trim().equals(storedOtp);`
     - Standard `String.equals()` terminates on the first mismatched character. Attackers measuring sub-microsecond response latencies over repeated attempts can theoretically infer character positions.
     - *Remediation*: Use `MessageDigest.isEqual(code.trim().getBytes(StandardCharsets.UTF_8), storedOtp.getBytes(StandardCharsets.UTF_8))`.
  2. **Premature Destruction of OTP (Denial of Retry)**:
     - Lines 90-93:
       ```java
       storedOtp = redisTemplate.opsForValue().get(key);
       if (storedOtp != null) {
           redisTemplate.delete(key);
       }
       return code.trim().equals(storedOtp);
       ```
     - The OTP is deleted from Redis *before* verifying the code match. If a user enters 5 digits or makes a single typo, their valid 5-minute OTP is destroyed instantly.
     - Furthermore, lack of retry limits and absence of rate-limiting enables brute-forcing of the 6-digit space (1,000,000 combinations) if automated rapid requests are sent before expiration.
  3. **Unbounded Memory Leak in Local Fallback**:
     - Lines 32, 78, 96: `localMemoryStore` is a `ConcurrentHashMap<String, String>()`. When Redis is unavailable, OTPs are stored in this map without any TTL eviction, expiration thread, or size capping. In long-running degraded environments, this causes memory accumulation.
  4. **Documentation Discrepancy**:
     - Line 15 javadoc states: `"short TTL (15 minutes)"`.
     - Line 30 constant defines: `OTP_EXPIRATION = Duration.ofMinutes(5);`.

### 2.5 `UserPaymentMethodServiceImpl` (`com.luna.aggarly.user.service.impl.UserPaymentMethodServiceImpl`)
- **Core Responsibilities**: Saved payment cards CRUD, default card switching, Stripe customer payment method binding.
- **Architectural Observations**:
  1. **Strict IDOR / BOLA Prevention**:
     - Lines 28, 52, 75, 85: All repository queries (`findByUserIdOrderBy...`, `findByUserIdAndStripePaymentMethodId`, `findByIdAndUserId`) strictly enforce the `userId` predicate extracted from the authenticated JWT principal.
  2. **Default Card Invariant Violation on Delete**:
     - Lines 73-80: When a user deletes a payment method that is currently set as `isDefault = true`, the service deletes the record without reassigning the default status to another existing card. The user is left in a state with saved cards but no default card.
  3. **NPE Risk on Unsanitized Card Brand**:
     - Line 58: `entity.setCardBrand(request.getCardBrand().toLowerCase());` throws `NullPointerException` if `cardBrand` is null (even though DTO has `@NotBlank`, programmatic invocation without validation will crash).
  4. **JPA Exception Leaking into Service Layer**:
     - Line 76 & Line 98: Throws raw `jakarta.persistence.EntityNotFoundException` instead of domain-specific `PaymentMethodNotFoundException` or `AggarlyException`.

### 2.6 `UserProfileServiceImpl` (`com.luna.aggarly.user.service.impl.UserProfileServiceImpl`)
- **Core Responsibilities**: Profile retrieval, profile update, avatar image upload via `FileStorageService`, avatar removal, password change, phone verification, host role elevation, and account deactivation.
- **Architectural Observations**:
  1. **CRITICAL FLAW: Failure to Revoke Refresh Tokens on Account Deactivation**:
     - Lines 172-179:
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
     - Unlike `AuthServiceImpl.deactivateAccount()` (which calls `refreshTokenRepository.revokeAllUserTokens(user)`), `UserProfileServiceImpl.deactivateAccount()` leaves all issued refresh tokens active in the database.
     - Combined with `UserPrincipal.isEnabled() == true`, the deactivated user retains the ability to authenticate, refresh tokens, and call APIs.
  2. **Architectural Duplication with `AuthServiceImpl`**:
     - Duplicate implementations of `changePassword`, `sendPhoneOtp`, `verifyPhone`, `becomeHost`, and `deactivateAccount`.
     - In `UserProfileServiceImpl`, methods take `UUID userId` (passed from `@AuthenticationPrincipal` in `UserProfileController`), whereas `AuthServiceImpl` calls `SecurityUtils.getCurrentUserId()`.
  3. **Clean File Storage Integration**:
     - Lines 65-82: Correctly leverages `FileStorageService.uploadFile(file)`, `markAsActive(id)`, and `resolveUrl(key)` to manage avatar image assets.

### 2.7 `RefreshTokenCleanupService` (`com.luna.aggarly.user.service.RefreshTokenCleanupService`)
- **Core Responsibilities**: Daily scheduled background job to delete expired refresh tokens and revoked tokens past retention window (7 days).
- **Architectural Observations**:
  - Clean implementation: Configured with `@Scheduled(cron = "${app.jwt.cleanup-cron:0 0 3 * * ?}")` and `@Transactional`.
  - Catches general exceptions to prevent executor thread crash while logging appropriate context.

---

## 3. Architecture & Clean Code Hygiene (R2)

### 3.1 Transaction Boundaries (`@Transactional`)
| Service / Component | Method | Current Transaction Setting | Assessment / Issues |
|---|---|---|---|
| `AuthServiceImpl` | `register`, `login`, `totpValidate`, `refresh`, `logout`, etc. | `@Transactional` (class/method) | Appropriate write transactions. |
| `AuthServiceImpl` | `getCurrentUserProfile` | `@Transactional(readOnly = true)` | Correct read-only boundary. |
| `AuthServiceImpl` | `getAuthenticatedUser` | `@Transactional(readOnly = true)` | **Anti-Pattern**: Private method; ignored by Spring proxy. |
| `AuthServiceImpl` | `issueTokens` (both overloads) | `@Transactional(propagation = Propagation.MANDATORY)` | **Anti-Pattern**: Private method; ignored by Spring proxy. |
| `AuthServiceImpl` | `sendVerificationEmail`, `forgotPassword` | `@Transactional` | **Architectural Hazard**: Holds DB transaction open across external SMTP call. |
| `UserProfileServiceImpl` | `getProfile` | `@Transactional(readOnly = true)` | Correct. |
| `UserProfileServiceImpl` | `sendPhoneOtp` | `@Transactional(readOnly = true)` | Correct. |
| `UserProfileServiceImpl` | `updateProfile`, `uploadAvatar`, `changePassword`, `deactivateAccount` | `@Transactional` | Correct. |
| `UserPaymentMethodServiceImpl` | `getUserPaymentMethods` | `@Transactional(readOnly = true)` | Correct. |
| `UserPaymentMethodServiceImpl` | `savePaymentMethod`, `deletePaymentMethod`, `setDefaultPaymentMethod` | `@Transactional` | Correct. |
| `RefreshTokenCleanupService` | `cleanupRevokedAndExpiredTokens` | `@Transactional` | Correct. |
| `UserController` | `getUserById`, `searchUsers` | **None** (no service layer) | **Violation**: Direct DB calls executed without managed transaction context. |

### 3.2 Separation of Concerns & Controller-Service Decoupling
1. **Controller Layer Direct Data Access (`UserController`)**:
   - `UserController` (`com.luna.aggarly.user.controller.UserController`) injects `UserRepository` and `UserMapper` directly:
     ```java
     private final UserRepository userRepository;
     private final UserMapper userMapper;
     ```
   - It performs query branching, repository pagination, and in-memory list filtering (`.filter(u -> currentUserId == null || !u.getId().equals(currentUserId))`) directly inside the controller.
   - *Architectural Rule Violation*: Controllers must only handle HTTP routing, deserialization, validation, and HTTP response packaging. Business logic and repository orchestration must reside in service classes (`UserService` or `UserProfileService`).
2. **Ambiguous Service Responsibility (`AuthService` vs `UserProfileService`)**:
   - Two parallel services manage user account state:
     - `AuthService`: Owns auth lifecycle, but defines 7 profile methods (`updateCurrentUserProfile`, `changePassword`, etc.) that are never invoked by `AuthController`.
     - `UserProfileService`: Actually wired to `UserProfileController` for `/api/v1/users/me/**`.
   - *Remediation*: Remove profile methods from `AuthService` and `AuthServiceImpl`. Unify all user profile and account modification operations under `UserProfileService`.

### 3.3 Exception Handling & Sensitive Information Leakage
1. **Response Safety & Abstraction**:
   - All responses in `UserExceptionHandler` and `GlobalExceptionHandler` produce standardized `ApiResponse<T>` envelopes.
   - `GlobalExceptionHandler.handleGeneralException` catches `Exception.class` and logs the full stack trace internally (`log.error(...)`) while returning a sanitized message: `"An unexpected internal server error occurred"` (HTTP 500).
   - No stack traces, internal file paths, or raw database SQL errors are leaked to HTTP clients.
2. **Exception Hierarchy Inconsistencies**:
   - Domain exceptions (`UserNotFoundException`, `VerificationException`, etc.) properly extend `AggarlyException`.
   - However, `UserPaymentMethodServiceImpl` throws `jakarta.persistence.EntityNotFoundException` instead of domain-specific exceptions.
   - `AuthServiceImpl` throws empty `RuntimeException("")` and `NoSuchElementException`.

### 3.4 Jakarta Validation Hygiene & Parameter Security
A thorough inspection of all request DTOs and controller signatures revealed several critical validation deficits:

| DTO / Endpoint Parameter | Field / Parameter | Present Annotations | Missing Validation / Vulnerability |
|---|---|---|---|
| `ConfirmMfaRequest` | `token`, `totpCode` | **None** | **Severe**: Entire record lacks validation; `@Valid` in `AuthController.confirmMFA` is a no-op. Allows null/blank tokens. |
| `UserProfileUpdate` | `firstName`, `lastName`, `displayName`, `phone`, `avatarUrl`, `bio` | **None** | **Severe**: Entire record lacks validation. Allows unbounded bio strings, invalid phone strings, and malformed avatar URLs. |
| `SavePaymentMethodRequest` | `expMonth` | `@NotNull` | Missing `@Min(1) @Max(12)`. Allows month values like -1 or 99. |
| `SavePaymentMethodRequest` | `expYear` | `@NotNull` | Missing `@Min(2024)`. Allows arbitrary past years. |
| `SavePaymentMethodRequest` | `lastFour` | `@NotBlank` | Missing `@Pattern(regexp = "^\\d{4}$")` or `@Size(min = 4, max = 4)`. Allows arbitrary non-numeric text. |
| `SavePaymentMethodRequest` | `stripePaymentMethodId` | `@NotBlank` | Missing pattern validation (e.g. `^pm_[a-zA-Z0-9]+$`). |
| `RegisterRequest` | `firstName`, `lastName` | `@NotBlank` | Missing `@Size(max = 50)`. Allows arbitrarily long names. |
| `RegisterRequest` | `bio` | None | Missing `@Size(max = 500)`. |
| `AuthController.logout` | `@RequestParam("refreshToken")` | None | **Critical**: Sensitive credential accepted in query string (URL logging, referer leakage). Missing `@NotBlank`. |
| `AuthController.sendVerification` | `@RequestParam("email")` | None | Missing `@NotBlank`, `@Email`. |
| `UserProfileController.updateAvatarUrl` | `@RequestParam("avatarUrl")` | None | Missing `@NotBlank`, `@URL`. Accepts arbitrary javascript or malformed URLs. |
| `UserProfileController.uploadAvatar` | `@RequestParam("file") MultipartFile` | None | Missing file size limits and MIME-type restrictions. |

---

## 4. Cross-Module Interactions & Boundaries

```
                 ┌──────────────────────────────────────┐
                 │       common.security (Shared)       │
                 └───────▲──────────────────────▲───────┘
                         │ (Circular Inversion) │
                         │                      │
                 ┌───────┴──────┐        ┌──────┴──────┐
                 │ user.security│        │ user.entity │
                 └───────▲──────┘        └──────▲──────┘
                         │                      │
                 ┌───────┴──────────────────────┴──────┐
                 │             user module             │
                 │      (Services, Entities, Repos)    │
                 └───────▲──────────────▲──────▲───────┘
                         │              │      │
          (Direct Repo)  │ (Direct Repo)│      │ (Clean Service)
                         │              │      ▼
               ┌─────────┴────┐  ┌──────┴─────┐┌──────────────┐
               │     chat     │  │notification││ filestorage  │
               └──────────────┘  └────────────┘└──────────────┘
```

### 4.1 Inverted Dependency: `common.security` -> `user`
In a well-structured modular monolith or layered architecture, common foundational packages must have zero dependencies on domain feature modules.
- **Observed Violations**:
  1. `com.luna.aggarly.common.security.SecurityConfig` imports:
     - `com.luna.aggarly.user.security.oauth2.OAuth2SuccessHandler`
     - `com.luna.aggarly.user.security.oauth2.OAuth2UserService`
  2. `com.luna.aggarly.common.security.jwt.JwtService` imports:
     - `com.luna.aggarly.user.entity.User`
     - `com.luna.aggarly.user.security.UserPrincipal`
     - Defines method: `public String generateToken(User user)`
  3. `com.luna.aggarly.common.security.SecurityUtils` imports:
     - `com.luna.aggarly.user.security.UserPrincipal`
- **Impact**: Creates a circular dependency loop between `common` and `user`. Common security cannot be reused or decoupled without dragging the user module with it.
- **Remediation**:
  - Relocate `OAuth2UserService` and `OAuth2SuccessHandler` to `common.security.oauth2` or configure them via generic interfaces/beans.
  - Change `JwtService` to accept generic `UserDetails` or custom lightweight interface `AuthenticatedUserPrincipal(UUID id, String username, Set<String> roles)`.

### 4.2 Leaky Abstractions: Direct `UserRepository` Access from Outside Modules
1. **`chat` Module (`ConversationServiceImpl`)**:
   - `ConversationServiceImpl` directly injects `com.luna.aggarly.user.repository.UserRepository`:
     ```java
     private final UserRepository userRepository;
     ...
     Map<UUID, User> userMap = userRepository.findAllById(userIds)...
     ```
   - It performs direct batch queries to resolve display names, avatars, and usernames for conversation participants.
2. **`notification` Module (`NotificationDispatcherImpl`)**:
   - `NotificationDispatcherImpl` directly injects `com.luna.aggarly.user.repository.UserRepository`:
     ```java
     Optional<User> userOpt = userRepository.findById(userId);
     String email = userOpt.get().getEmail();
     ```
3. **`aiagent` Module (`AiConversationController`)**:
   - Directly injects `com.luna.aggarly.user.repository.UserConfirmedActionRepository`.
- **Architectural Violation**: Cross-module communication must traverse defined service interfaces (e.g. `UserService.findPublicSummariesByIds(Set<UUID>)`) or rely on asynchronous domain events, never direct JPA repositories of foreign modules.

### 4.3 Payment Module Interaction & `UserPaymentMethod` Decoupling
- `UserPaymentMethod` entity lives in `com.luna.aggarly.user.entity.UserPaymentMethod`, backed by table `user_payment_methods`.
- The `payment` module (`com.luna.aggarly.payment`) contains **zero references** to `UserPaymentMethod` or `UserPaymentMethodService`.
- When making payments, `PaymentRequest` receives an unverified `paymentMethodId` string from the frontend. The payment gateway assumes the client already coordinated with Stripe.
- *Evaluation*: While this keeps the payment module decoupled, there is no server-side validation ensuring that the `stripePaymentMethodId` provided during checkout belongs to the authenticated `userId`.

### 4.4 Booking, Property, Review, and Cleaning Modules
- Cleanly decoupled: None of these modules import user entities or repositories. They store raw UUID foreign keys (`guestId`, `hostId`, `userId`, `authorId`) and reference `UserPrincipal` exclusively in controllers for `@AuthenticationPrincipal` extraction.

---

## 5. Prioritized Architectural & Code Hygiene Findings Matrix

| Finding ID | Severity | Location | Summary / Threat Vector | Concrete Remediation |
|---|---|---|---|---|
| **ARC-01** | **CRITICAL** | `UserProfileServiceImpl.java:174-179` | **Deactivation Fails to Revoke Refresh Tokens**: Account deactivation sets `is_deleted = true` but fails to call `refreshTokenRepository.revokeAllUserTokens(user)`. Paired with `UserPrincipal.isEnabled() == true`, user remains authenticated. | Inject `RefreshTokenRepository` in `UserProfileServiceImpl` and invoke `refreshTokenRepository.revokeAllUserTokens(user)` inside `deactivateAccount`. |
| **ARC-02** | **CRITICAL** | `UserPrincipal.java:72-74` | **Disabled/Deleted Accounts Authenticate**: `isEnabled()` returns `true` unconditionally. Soft-deleted accounts continue to pass Spring Security authentication. | Change `isEnabled()` to `return !user.isDeleted();`. |
| **ARC-03** | **HIGH** | `OtpServiceImpl.java:83-100` | **Timing Attack & Premature Deletion in OTP Validation**: `equals()` creates timing side-channel. `redisTemplate.delete(key)` destroys OTP on first attempt before verification, denying legitimate retries. | Use `MessageDigest.isEqual(...)`. Do not delete OTP unless code verification succeeds. Track attempt count with rate limiting. |
| **ARC-04** | **HIGH** | `UserController.java:31-68` | **Direct Repository & Logic in Controller**: Controller injects `UserRepository` and `UserMapper` directly; executes queries, pagination, and filtering without service layer. | Introduce `UserService` or extend `UserProfileService` with `getUserSummary(UUID)` and `searchUsers(query, limit, currentUserId)`. Remove `UserRepository` from controller. |
| **ARC-05** | **HIGH** | `AuthController.java:58` | **Refresh Token in Request Param**: `logout(@RequestParam("refreshToken") String refreshToken)` exposes sensitive refresh token in URL logs, browser history, and referer headers. | Change logout to accept `@Valid @RequestBody RefreshTokenRequest request`. |
| **ARC-06** | **HIGH** | `OAuth2SuccessHandler.java:67-70` | **Tokens Exposed in Redirect URL**: `sendRedirect("%s?token=%s&refreshToken=%s")` leaks JWT and Refresh Token via GET query parameters. | Use secure HTTP-only SameSite cookies or postMessage authorization code flow. |
| **ARC-07** | **HIGH** | `ConfirmMfaRequest.java:3` | **Zero Validation Annotations**: Record fields have no `@NotBlank` or length checks; `@Valid` in `AuthController.confirmMFA` is ineffective. | Add `@NotBlank` and `@Size(min = 6, max = 6)` to fields in `ConfirmMfaRequest`. |
| **ARC-08** | **MEDIUM** | `AuthServiceImpl.java:357, 399` | **Blocking SMTP I/O Inside `@Transactional`**: Calls `emailService.send(...)` while holding open database transaction. | Move email sending outside transaction boundary (e.g. after transaction commit via Spring `@TransactionalEventListener` or asynchronous `@Async`). |
| **ARC-09** | **MEDIUM** | `AuthServiceImpl.java:293-320, 365-383, 426-500` | **Dead Duplicate Code in `AuthServiceImpl`**: Profile methods (`getCurrentUserProfile`, `changePassword`, `deactivateAccount`, etc.) duplicate `UserProfileServiceImpl` and are never invoked by `AuthController`. | Remove duplicate profile methods from `AuthService` and `AuthServiceImpl`. |
| **ARC-10** | **MEDIUM** | `MfaServiceImpl.java:32, 54` | **Redis Key Namespace Collision**: `create` and `CreateMfa` share `"mfa:" + token` prefix for different object types (`Mfa` vs `MfaConfirmation`), risking `ClassCastException`. | Prefix challenge tokens with `"mfa:challenge:"` and enrollment tokens with `"mfa:setup:"`. |
| **ARC-11** | **MEDIUM** | `SecurityConfig.java:7-8`, `JwtService.java:3-4` | **Circular Module Coupling (Common -> User)**: Common security components directly import user domain classes. | Refactor `JwtService` and `SecurityConfig` to rely on generic security interfaces rather than domain entities. |
| **ARC-12** | **MEDIUM** | `UserProfileUpdate.java:6-13` | **Missing DTO Validation Annotations**: No length constraints on `bio`, names, or formatting for `phone`/`avatarUrl`. | Add `@Size(max = 50)` on names, `@Size(max = 500)` on bio, `@ValidPhone`, and `@URL`. |
| **ARC-13** | **MEDIUM** | `SavePaymentMethodRequest.java:23-28` | **Missing Card Boundary Validations**: `expMonth` lacks `@Min(1) @Max(12)`, `expYear` lacks `@Min(2024)`, `lastFour` lacks `@Pattern("^\\d{4}$")`. | Add boundary annotations to ensure card data validity before persistence. |
| **ARC-14** | **LOW** | `AuthServiceImpl.java:153, 78, 481` | **Crude `RuntimeException` Usage**: Throws raw `RuntimeException("")` and `NoSuchElementException`. | Replace with specialized domain exceptions inheriting from `AggarlyException`. |
| **ARC-15** | **LOW** | `OtpServiceImpl.java:32` | **Unbounded Fallback Map**: `localMemoryStore` lacks TTL cleanup mechanism. | Replace with Guava/Caffeine cache with automatic expiration and maximum size bounds. |

---

## 6. Verification and Parity Checklist

- [x] All 7 service implementations in `com.luna.aggarly.user.service` audited line-by-line.
- [x] Transactional boundaries, proxy limitations, and network I/O overlap evaluated.
- [x] Separation of concerns across all controllers (`AuthController`, `UserController`, `UserProfileController`, `UserPaymentMethodController`) inspected.
- [x] Exception handling and information leakage vectors examined across `UserExceptionHandler` and `GlobalExceptionHandler`.
- [x] All request DTOs and controller parameters audited for Jakarta validation completeness.
- [x] Cross-module boundaries mapped across payment, booking, notification, chat, aiagent, and common security.
- [x] Findings prioritized into an actionable remediation matrix.
