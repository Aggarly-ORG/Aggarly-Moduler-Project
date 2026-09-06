# Comprehensive Security Endpoints & Architecture Specification

**Project**: Aggarly Modular Vacation Rental Platform (`com.luna.aggarly`)  
**Component**: User Module, Authentication & Spring Security Configuration  
**Author**: teamwork_preview_spec_miner  
**Date**: 2026-09-05  
**Audit Mode**: Survey Phase — Audit & Report Only  

---

## 1. Executive Summary

This document presents the complete specification and security audit of all authentication, authorization, and user management endpoints, filters, token lifecycle management, and architectural guardrails across `com.luna.aggarly.user` and `com.luna.aggarly.common.security`.

### Key Verification Highlights:
1. **Architectural Guardrail Compliance**:
   - **Zero instances** of `@PreAuthorize("isAuthenticated()")` exist across the entire codebase (100% compliance with Aggarly Architectural Guardrails).
   - Method security `@PreAuthorize` is strictly restricted to role-based checks (e.g., `hasRole('ADMIN')`, `hasRole('HOST')`).
   - Central authentication is enforced by default via `.anyRequest().authenticated()` in `SecurityConfig.java`.
2. **Endpoint Coverage**:
   - 11 Authentication endpoints in `AuthController` (`/api/v1/auth/**`).
   - 2 Public/Directory User endpoints in `UserController` (`/api/v1/users/**`).
   - 10 Profile & Account Management endpoints in `UserProfileController` (`/api/v1/users/me/**`).
   - 4 Saved Card/Payment Method endpoints in `UserPaymentMethodController` (`/api/v1/user/payment-methods/**`).
   - OAuth2 redirection endpoints (`/oauth2/authorization/**`, `/login/oauth2/code/**`).
3. **Severe Vulnerabilities & Threat Surfaces Uncovered**:
   - **Critical Authorization Leak**: `SecurityConfig.java` lines 95-97 permit all traffic (`permitAll()`) to `/api/v1/vision/admin/**` (including `ollama/**`, `clip/**`, and `models`), completely bypassing admin role checks!
   - **Token Leakage in OAuth2 Callback**: `OAuth2SuccessHandler.java` leaks `token` and `refreshToken` in HTTP GET redirect URL query parameters.
   - **Plaintext OTP Logging**: `AuthServiceImpl.java` logs generated 6-digit OTP codes directly to SLF4J application logs in cleartext.
   - **Timing Attack on OTP Verification**: `OtpServiceImpl.java` performs standard `String.equals()` instead of constant-time `MessageDigest.isEqual()`.

---

## 2. Central Authorization Architecture & SecurityConfig Analysis

### 2.1 Central Enforcement (`SecurityConfig.java`)
- **Stateless Session Management**: `SessionCreationPolicy.STATELESS` configured; no `HttpSession` or JSESSIONID cookies are maintained.
- **CSRF**: Disabled via `AbstractHttpConfigurer::disable` (appropriate for stateless REST APIs utilizing Bearer JWTs, but relevant to cookie exposure considerations).
- **Default Authentication Boundary**:
  - `authorizeHttpRequests(auth -> auth ... .anyRequest().authenticated())` is strictly configured at line 113.
- **Exception Delegation**:
  - `DelegatingAuthenticationEntryPoint` intercepts unauthenticated HTTP requests (401 Unauthorized) and forwards them to Spring MVC's `HandlerExceptionResolver`, ensuring standard `ApiResponse` JSON envelopes.
  - `DelegatingAccessDeniedHandler` intercepts forbidden HTTP requests (403 Forbidden) and forwards them similarly.
- **Filter Pipeline Order**:
  1. `CorrelationIdFilter` (before `DisableEncodeUrlFilter.class`): Extracts or generates MDC correlation ID (`X-Correlation-Id`).
  2. `JwtAuthenticationFilter` (before `UsernamePasswordAuthenticationFilter.class`): Extracts `Authorization: Bearer <jwt>`, validates signature, extracts subject email, and sets `UsernamePasswordAuthenticationToken` into `SecurityContextHolder`.
  3. `OAuth2AuthorizationRequestRedirectFilter`: Handles social login redirects.

### 2.2 PermitAll Boundaries
The following routes are explicitly marked `.permitAll()` in `SecurityConfig.java`:

| Route Pattern | Method | Purpose / Assessment | Security Evaluation |
|---|---|---|---|
| `/api/v1/auth/register` | POST | User registration | Legitimate public endpoint |
| `/api/v1/auth/login` | POST | Local username/password login | Legitimate public endpoint |
| `/api/v1/auth/refresh` | POST | Access token rotation using refresh token | Legitimate public endpoint |
| `/api/v1/auth/logout` | POST | Session revocation | Needs token query param; public |
| `/api/v1/auth/verify-email` | POST | OTP verification for email activation | Legitimate public endpoint |
| `/api/v1/auth/send-verification` | POST | Resend verification OTP code | Legitimate public endpoint |
| `/api/v1/auth/forgot-password` | POST | Trigger password reset OTP email | Legitimate public endpoint |
| `/api/v1/auth/reset-password` | POST | Execute password reset using OTP | Legitimate public endpoint |
| `/api/v1/auth/validate-mfa` | POST | Second-factor login validation | Legitimate public endpoint |
| `/api/v1/payments/webhook` | POST | Stripe webhook callback | External webhook (Stripe signature checked) |
| `/api/mock/stripe/**` | ALL | Stripe development mock | Test mock (Risk if enabled in prod) |
| `/login/oauth2/code/**` | GET | Spring Security OAuth2 authorization callback | Legitimate OAuth2 callback |
| `/api/v1/properties/search` | GET | Public property catalog browsing | Legitimate public endpoint |
| `/api/v1/properties/{id}` | GET | Public property detail view | Legitimate public endpoint |
| `/api/v1/storage/files/view/**` | GET | Serving uploaded images/assets | Legitimate public file read |
| `/api/v1/vision/search/**` | GET/POST| Public image/multimodal search | Legitimate public endpoint |
| `/api/v1/vision/admin/ollama/**` | ALL | Administrative Ollama vision control | 🚨 **CRITICAL VULNERABILITY**: Public bypass of admin APIs! |
| `/api/v1/vision/admin/clip/**` | ALL | Administrative CLIP model control | 🚨 **CRITICAL VULNERABILITY**: Public bypass of admin APIs! |
| `/api/v1/vision/admin/models` | ALL | Administrative vision model inspection | 🚨 **CRITICAL VULNERABILITY**: Public bypass of admin APIs! |
| `/swagger-ui/**`, `/api-docs/**` | GET | OpenAPI & Swagger UI documentation | Development docs (Restrict in prod) |
| `/ws/**`, `/ws/chat/**` | GET | WebSocket / STOMP handshake endpoints | Permitted per Aggarly architecture rules |

### 2.3 Forbidden `@PreAuthorize("isAuthenticated()")` Rule Audit
- **Rule**: Banned in all controller methods. Controllers must rely on central `.anyRequest().authenticated()` or explicit role authorizations.
- **Codebase Scan**:
  - Executed regex query across `src/main/java/**/*.java`.
  - **Result**: `0` occurrences of `@PreAuthorize("isAuthenticated()")`.
  - Legitimate role-based checks present:
    - `@PreAuthorize("hasRole('ADMIN')")` in `AiAuditController`, `CouponController`, `PaymentAdminController`, `AmenityController`, `CleaningIssueController`.
    - `@PreAuthorize("hasRole('HOST')")` in `PricingRuleController`, `PropertyImageController`, `AvailabilityController`.
    - `@PreAuthorize("hasAnyRole('HOST','ADMIN')")` in `PropertyController`, `VisionHostController`, `CleaningTaskController`.

---

## 3. Comprehensive Endpoints Catalog

### 3.1 Authentication Controller (`com.luna.aggarly.user.controller.AuthController`)
Base Path: `/api/v1/auth`

| HTTP Method | Route | Description | Auth Required | Request DTO | Response DTO | Error Status Codes |
|---|---|---|---|---|---|---|
| `POST` | `/api/v1/auth/register` | Register new user, hash password, seed GUEST role, send 6-digit OTP | None (`permitAll`) | `RegisterRequest` | `ApiResponse<AuthResponse>` | 400 (Validation), 409 (Email/Username exists) |
| `POST` | `/api/v1/auth/login` | Local credential authentication; returns tokens or MFA challenge | None (`permitAll`) | `LoginRequest` | `ApiResponse<AuthResponse>` | 400 (Validation), 401 (Bad credentials) |
| `POST` | `/api/v1/auth/refresh` | Rotate access token; enforces 15s grace window and family lineage | None (`permitAll`) | `RefreshTokenRequest` | `ApiResponse<AuthResponse>` | 400 (Validation), 401 (Invalid/expired/revoked token) |
| `POST` | `/api/v1/auth/logout` | Revoke entire refresh token family | None (`permitAll`) | `@RequestParam("refreshToken")` | `ApiResponse<Void>` | 400 (Missing param), 401 (Invalid token) |
| `POST` | `/api/v1/auth/verify-email` | Verify email address via 6-digit OTP | None (`permitAll`) | `VerifyEmailRequest` | `ApiResponse<Void>` | 400 (Validation / invalid OTP), 404 (User not found) |
| `POST` | `/api/v1/auth/send-verification`| Resend 6-digit OTP code to email | None (`permitAll`) | `@RequestParam("email")` | `ApiResponse<Void>` | 400 (Already verified), 404 (User not found) |
| `POST` | `/api/v1/auth/forgot-password`| Request password reset OTP | None (`permitAll`) | `ForgotPasswordRequest` | `ApiResponse<Void>` | 400 (Validation); silent 200 on unknown email |
| `POST` | `/api/v1/auth/reset-password` | Reset password using 6-digit OTP | None (`permitAll`) | `ResetPasswordRequest` | `ApiResponse<Void>` | 400 (Invalid OTP / validation), 404 (User not found) |
| `POST` | `/api/v1/auth/enable-mfa` | Generate TOTP secret and QR code URI | Bearer JWT (`authenticated`) | None | `ApiResponse<RequestMfaResponse>`| 401 (Unauth), 500 (MFA already enabled) |
| `POST` | `/api/v1/auth/confirm-mfa` | Validate initial TOTP code to activate MFA | Bearer JWT (`authenticated`) | `ConfirmMfaRequest` | `ApiResponse<Void>` | 401 (Invalid TOTP / token), 404 (User not found) |
| `POST` | `/api/v1/auth/validate-mfa`| Verify TOTP code during login challenge | None (`permitAll`) | `TotpRequest` | `ApiResponse<AuthResponse>` | 400 (Validation), 401 (Invalid MFA token / wrong TOTP) |

### 3.2 User Directory Controller (`com.luna.aggarly.user.controller.UserController`)
Base Path: `/api/v1/users`

| HTTP Method | Route | Description | Auth Required | Query / Path Params | Response DTO | Error Status Codes |
|---|---|---|---|---|---|---|
| `GET` | `/api/v1/users/{id}` | Fetch public user summary profile by ID | Bearer JWT (`authenticated`) | `id` (UUID) | `ApiResponse<UserProfileSummaryResponse>` | 401 (Unauth), 404 (User not found) |
| `GET` | `/api/v1/users/search` | Search user directory for messaging by query | Bearer JWT (`authenticated`) | `query` (String), `limit` (int, default 10, max 50) | `ApiResponse<List<UserProfileSummaryResponse>>` | 401 (Unauth) |

### 3.3 User Profile Controller (`com.luna.aggarly.user.controller.UserProfileController`)
Base Path: `/api/v1/users/me`

| HTTP Method | Route | Description | Auth Required | Request / Payload | Response DTO | Error Status Codes |
|---|---|---|---|---|---|---|
| `GET` | `/api/v1/users/me` | Retrieve profile of authenticated user | Bearer JWT (`authenticated`) | None (`@AuthenticationPrincipal`) | `ApiResponse<UserProfileResponse>` | 401 (Unauth), 404 (User not found) |
| `PUT` | `/api/v1/users/me` | Update authenticated user's profile | Bearer JWT (`authenticated`) | `UserProfileUpdate` | `ApiResponse<UserProfileResponse>` | 400 (Validation), 401 (Unauth) |
| `POST` | `/api/v1/users/me/avatar` | Upload and attach avatar image | Bearer JWT (`authenticated`) | `MultipartFile` (`multipart/form-data`) | `ApiResponse<Map<String, String>>` | 400 (Empty file), 401 (Unauth) |
| `PUT` | `/api/v1/users/me/avatar-url`| Set avatar URL directly | Bearer JWT (`authenticated`) | `@RequestParam("avatarUrl")` | `ApiResponse<Void>` | 401 (Unauth) |
| `DELETE` | `/api/v1/users/me/avatar` | Remove user's avatar image | Bearer JWT (`authenticated`) | None | `ApiResponse<Void>` | 401 (Unauth) |
| `PUT` | `/api/v1/users/me/change-password`| Change password verifying current password | Bearer JWT (`authenticated`) | `ChangePasswordRequest` | `ApiResponse<Void>` | 400 (Same as old / validation), 401 (Bad current password) |
| `POST` | `/api/v1/users/me/phone/send-otp`| Send 6-digit OTP code to registered phone | Bearer JWT (`authenticated`) | None | `ApiResponse<Void>` | 400 (No phone registered / already verified), 401 (Unauth) |
| `POST` | `/api/v1/users/me/phone/verify` | Verify phone number with 6-digit OTP | Bearer JWT (`authenticated`) | `VerifyPhoneRequest` | `ApiResponse<Void>` | 400 (Invalid OTP / already verified), 401 (Unauth) |
| `POST` | `/api/v1/users/me/become-host` | Upgrade authenticated user to `HOST` role | Bearer JWT (`authenticated`) | None | `ApiResponse<Void>` | 400 (Email not verified), 401 (Unauth) |
| `DELETE` | `/api/v1/users/me` | Soft-delete account and revoke all sessions | Bearer JWT (`authenticated`) | None | `ApiResponse<Void>` | 401 (Unauth) |

### 3.4 User Payment Method Controller (`com.luna.aggarly.user.controller.UserPaymentMethodController`)
Base Path: `/api/v1/user/payment-methods`

| HTTP Method | Route | Description | Auth Required | Request / Payload | Response DTO | Error Status Codes |
|---|---|---|---|---|---|---|
| `GET` | `/api/v1/user/payment-methods` | List all saved payment methods of user | Bearer JWT (`authenticated`) | None (`@AuthenticationPrincipal`) | `ApiResponse<List<UserPaymentMethodResponse>>` | 401 (Unauth) |
| `POST` | `/api/v1/user/payment-methods` | Save new Stripe payment method | Bearer JWT (`authenticated`) | `SavePaymentMethodRequest` | `ApiResponse<UserPaymentMethodResponse>` | 400 (Validation), 401 (Unauth) |
| `DELETE` | `/api/v1/user/payment-methods/{id}`| Delete saved payment method (BOLA-safe) | Bearer JWT (`authenticated`) | `id` (UUID) | `ApiResponse<Void>` | 401 (Unauth), 404 (Not found or belongs to other user) |
| `PATCH` | `/api/v1/user/payment-methods/{id}/default`| Set default payment method (BOLA-safe) | Bearer JWT (`authenticated`) | `id` (UUID) | `ApiResponse<Void>` | 401 (Unauth), 404 (Not found or belongs to other user) |

### 3.5 OAuth2 Authentication & Social Login
- **Initiation Endpoint**: `GET /oauth2/authorization/{provider}` (e.g. `/oauth2/authorization/google`, `/oauth2/authorization/github`).
- **Callback Endpoint**: `GET /login/oauth2/code/{provider}`.
- **Frontend Success Redirect**: Configurable via `app.oauth2.frontend-redirect-url` (defaults to `http://localhost:3000/oauth2/callback?token={jwt}&refreshToken={uuid}`).

---

## 4. Features Discovered Table

| # | Category | Feature | Description | Inputs | Outputs | Error Behavior | Discovered Via |
|---|---|---|---|---|---|---|---|
| 1 | Auth | User Registration | Registers a new user account with BCrypt password, GUEST role, and sends 6-digit email OTP | `RegisterRequest` (email, password, username, firstName, lastName, phone, etc.) | `AuthResponse` (token, refreshToken, status) | Throws `EmailAlreadyExistsException` (409 Conflict) if email or username taken | `AuthController.java:37`, `AuthServiceImpl.java:68` |
| 2 | Auth | Local Login | Authenticates credentials; returns token pair or MFA challenge | `LoginRequest` (email, password) | `AuthResponse` (status AUTH_SUCCESS or MFA_REQUIRED) | Throws `InvalidCredentialsException` (401 Unauthorized) | `AuthController.java:44`, `AuthServiceImpl.java:105` |
| 3 | Auth | Refresh Token Rotation | Rotates access token and refresh token, tracking family ID and 15s concurrency grace window | `RefreshTokenRequest` (refreshToken, expiredAccessToken) | `AuthResponse` (new JWT token, new refreshToken) | Throws `InvalidRefreshTokenException` (401); revokes entire family on replay breach | `AuthController.java:51`, `AuthServiceImpl.java:191` |
| 4 | Auth | Session Revocation (Logout) | Revokes active refresh token and its entire family | Query param `refreshToken` | `ApiResponse<Void>` | Throws `InvalidRefreshTokenException` (401) | `AuthController.java:58`, `AuthServiceImpl.java:284` |
| 5 | Auth | Email OTP Verification | Verifies user email address using 6-digit numeric OTP with 5 min TTL | `VerifyEmailRequest` (email, otpCode) | `ApiResponse<Void>` | Throws `VerificationException` (400) if code mismatch or expired | `AuthController.java:65`, `AuthServiceImpl.java:323` |
| 6 | Auth | Resend Email OTP | Generates and emails new 6-digit OTP code | Query param `email` | `ApiResponse<Void>` | Throws `VerificationException` (400) if already verified; `UserNotFoundException` (404) | `AuthController.java:72`, `AuthServiceImpl.java:344` |
| 7 | Auth | Forgot Password OTP | Sends 6-digit password reset OTP to email | `ForgotPasswordRequest` (email) | `ApiResponse<Void>` | Silently ignores unknown emails to prevent email enumeration | `AuthController.java:79`, `AuthServiceImpl.java:387` |
| 8 | Auth | Reset Password OTP | Sets new BCrypt password upon validating 6-digit reset OTP; revokes all sessions | `ResetPasswordRequest` (email, otpCode, newPassword) | `ApiResponse<Void>` | Throws `VerificationException` (400); `UserNotFoundException` (404) | `AuthController.java:86`, `AuthServiceImpl.java:408` |
| 9 | MFA | Request TOTP Setup | Generates TOTP secret and Base64 QR code image | Authenticated user principal | `RequestMfaResponse` (qr, uri, token) | Throws `RuntimeException` (500) if already enabled | `AuthController.java:93`, `AuthServiceImpl.java:150` |
| 10| MFA | Confirm TOTP Setup | Confirms TOTP code and activates `mfa_enabled = true` on user | `ConfirmMfaRequest` (token, totpCode) | `ApiResponse<Void>` | Throws `InvalidTotpException` (401) | `AuthController.java:100`, `AuthServiceImpl.java:174` |
| 11| MFA | Login TOTP Validation | Validates 6-digit TOTP against Redis challenge token to complete login | `TotpRequest` (token, totpCode) | `AuthResponse` (JWT and refresh token pair) | Throws `InvalidMfaTokenException` (401), `InvalidTotpException` (401) | `AuthController.java:107`, `AuthServiceImpl.java:126` |
| 12| Profile | Retrieve Own Profile | Returns complete user profile including roles, verification flags, and phone | Authenticated user principal | `UserProfileResponse` | Throws `UserNotFoundException` (404) | `UserProfileController.java:41`, `UserProfileServiceImpl.java:43` |
| 13| Profile | Update Own Profile | Updates first/last name, bio, display name, phone, avatar | `UserProfileUpdate` DTO | `UserProfileResponse` | Throws 401 if unauthenticated | `UserProfileController.java:50`, `UserProfileServiceImpl.java:49` |
| 14| Profile | Upload Avatar | Uploads image via `FileStorageService` and sets `avatar_url` | `MultipartFile` | `Map<String, String>` (avatarUrl) | Throws `IllegalArgumentException` (400) if empty | `UserProfileController.java:60`, `UserProfileServiceImpl.java:66` |
| 15| Profile | Change Password | Updates password with BCrypt, requiring verification of current password; revokes all sessions | `ChangePasswordRequest` (currentPassword, newPassword) | `ApiResponse<Void>` | Throws `InvalidCredentialsException` (401); `VerificationException` (400) if new == old | `UserProfileController.java:89`, `UserProfileServiceImpl.java:104` |
| 16| Profile | Send Phone OTP | Generates 6-digit OTP for user's registered phone number | Authenticated user principal | `ApiResponse<Void>` | Throws `VerificationException` (400) if phone missing or already verified | `UserProfileController.java:99`, `UserProfileServiceImpl.java:123` |
| 17| Profile | Verify Phone OTP | Confirms phone number with 6-digit OTP code | `VerifyPhoneRequest` (otpCode) | `ApiResponse<Void>` | Throws `VerificationException` (400) | `UserProfileController.java:108`, `UserProfileServiceImpl.java:136` |
| 18| Profile | Become Host Role | Upgrades user to `HOST` role (requires `email_verified = true`) | Authenticated user principal | `ApiResponse<Void>` | Throws `VerificationException` (400) if email not verified | `UserProfileController.java:118`, `UserProfileServiceImpl.java:155` |
| 19| Profile | Deactivate Account | Soft-deletes user (`is_deleted = true`) and revokes all active refresh tokens | Authenticated user principal | `ApiResponse<Void>` | Throws 401 if unauthenticated | `UserProfileController.java:127`, `UserProfileServiceImpl.java:174` |
| 20| Payments| List Payment Methods | Lists saved cards for current user ordered by default then created date | Authenticated user principal | `List<UserPaymentMethodResponse>` | Throws 401 if unauthenticated | `UserPaymentMethodController.java:37`, `UserPaymentMethodServiceImpl.java:27` |
| 21| Payments| Save Payment Method | Persists Stripe payment method ID, brand, last 4, exp date | `SavePaymentMethodRequest` | `UserPaymentMethodResponse` | Throws 400 on validation failure | `UserPaymentMethodController.java:46`, `UserPaymentMethodServiceImpl.java:36` |
| 22| Payments| Delete Payment Method | Removes payment method strictly scoped to authenticated user ID (BOLA-safe) | Path variable `id` | `ApiResponse<Void>` | Throws `EntityNotFoundException` (404) if ID not found or unowned | `UserPaymentMethodController.java:56`, `UserPaymentMethodServiceImpl.java:74` |
| 23| Payments| Set Default Method | Sets target payment method as default, unsetting prior defaults for user | Path variable `id` | `ApiResponse<Void>` | Throws `EntityNotFoundException` (404) | `UserPaymentMethodController.java:66`, `UserPaymentMethodServiceImpl.java:84` |
| 24| Directory| Get User By ID | Public profile summary lookup by UUID | Path variable `id` | `UserProfileSummaryResponse` | Returns 404 with notFound response | `UserController.java:36` |
| 25| Directory| Search Directory | Search users by name, username, or email for chat messaging | Query params `query`, `limit` | `List<UserProfileSummaryResponse>` | Sanitizes limit between 1 and 50; excludes self | `UserController.java:45` |
| 26| Maintenance| Token DB Cleanup | Purges expired tokens and revoked tokens older than retention window (7 days) | Scheduled cron `0 0 3 * * ?` | DB row deletion | Catches and logs errors | `RefreshTokenCleanupService.java:34` |
| 27| OAuth2 | Social Provisioning | Handles Google/GitHub login; provisions new GUEST user or reactivates soft-deleted account | OAuth2 attributes from provider | `UserPrincipal` | Throws `OAuth2AuthenticationException` if email missing | `OAuth2UserService.java:46` |
| 28| OAuth2 | Callback & Redirection | Issues JWT and refresh token, redirects to frontend URL with query parameters | OAuth2 authentication principal | HTTP 302 Redirect | IO error on response failure | `OAuth2SuccessHandler.java:47` |

---

## 5. Edge Cases & Boundary Probing Table

| # | Feature | Input / Scenario | Observed Behavior |
|---|---|---|---|
| 1 | Refresh Token Rotation | Replaying an already-revoked refresh token within 15 seconds | Grace window applies: detects `secondsSinceRevocation <= 15s`, finds active successor token, generates fresh access token, updates SHA-256 binding, and returns HTTP 200 without revoking the family (`AuthServiceImpl.java:202-225`). |
| 2 | Refresh Token Rotation | Replaying an already-revoked refresh token after 16+ seconds | Theft detected: executes `refreshTokenRepository.revokeFamily()`, setting `revoked = true` for all tokens in the lineage, and throws `InvalidRefreshTokenException` (`AuthServiceImpl.java:229-232`). |
| 3 | Refresh Token Rotation | Supplying mismatched `expiredAccessToken` in request | Session hijacking detected: compares `hashToken(request.expiredAccessToken())` against `associatedAccessTokenHash`; on mismatch, family is revoked and throws `InvalidRefreshTokenException` (`AuthServiceImpl.java:241-248`). |
| 4 | Refresh Token Rotation | Refresh token past its expiry date (`Instant.now() > expiryDate`) | Expiry detected: executes `revokeFamily()` and throws `InvalidRefreshTokenException` (`AuthServiceImpl.java:235-238`). |
| 5 | Forgot Password | Requesting password reset for non-existent email | Returns HTTP 200 with generic message `"If the email exists, a 6-digit OTP has been sent."` and exits without error, preventing email enumeration (`AuthServiceImpl.java:388-392`). |
| 6 | Change Password | Attempting to change password when new password equals current password | Rejected: `passwordEncoder.matches(request.newPassword(), user.getPasswordHash())` triggers `VerificationException("New password must be different from current password")` (400 Bad Request). |
| 7 | Account Deactivation | Deactivating account (`DELETE /api/v1/users/me`) | Sets `user.setDeleted(true)` (Hibernate `@SQLRestriction("is_deleted = false")` hides user from normal queries) and executes `revokeAllUserTokens(user)`. |
| 8 | OAuth2 Account Linking | Social login with an email matching an existing soft-deleted user | Reactivation: `OAuth2UserService.java:76-79` detects `findAnyByEmail(email)` with `is_deleted = true`, un-deletes account (`deletedUser.setDeleted(false)`), and logs them in. |
| 9 | OAuth2 Username Collisions | Social login with a username that already exists in DB | Conflict resolution: appends `"-" + UUID.randomUUID().toString().substring(0, 5)` to ensure uniqueness (`OAuth2UserService.java:81-83`). |
| 10| Saved Payment Methods | Deleting another user's saved card (`DELETE /api/v1/user/payment-methods/{foreignId}`) | BOLA/IDOR prevention: Repository query requires both `id` AND `userId` (`findByIdAndUserId(paymentMethodId, userId)`). Returns 404 `EntityNotFoundException`, completely preventing cross-tenant card manipulation. |
| 11| OTP Storage Fallback | Redis connection failure during OTP generation/validation | Resilience fallback: `OtpServiceImpl.java:76-79, 94-97` falls back to in-memory `ConcurrentHashMap`. (Note: causes consistency failure across multiple server replicas). |
| 12| MFA Setup Duplicate | Calling `/api/v1/auth/enable-mfa` when MFA is already enabled | Throws unhandled raw `RuntimeException("")` causing a generic HTTP 500 instead of a structured 400 Conflict response (`AuthServiceImpl.java:152-154`). |

---

## 6. Authentication Schemes & Token Lifecycle

### 6.1 Authentication Schemes
1. **JWT Bearer Authentication**:
   - Header: `Authorization: Bearer <jwt>`
   - Signing: HMAC-SHA256 with key derived from `app.jwt.secret` (ensuring at least 32 bytes/256 bits).
   - Claims:
     - `sub`: User email address
     - `userId`: UUID string
     - `roles`: List of strings (`GUEST`, `HOST`, `ADMIN`) without `ROLE_` prefix.
   - Expiration: `app.jwt.access-token-expiration-ms` (Default: 900,000 ms / 15 minutes).
2. **Refresh Token Scheme**:
   - Format: UUID v4 string (`UUID.randomUUID().toString()`).
   - Transport: JSON body in `RefreshTokenRequest` (`refreshToken`, optional `expiredAccessToken`).
   - Lineage: Every session is assigned a `family_id` (UUID).
   - Grace Window: 15-second grace window allowing concurrent or duplicate refresh calls from browser multi-tabbing without triggering breach locks.
   - Access Token Binding: Stores SHA-256 hash of the issued access token in `associated_access_token_hash`.
   - Expiration: `app.jwt.refresh-expiration-days` (Default: 7 days).
3. **OAuth2 Social Login**:
   - Supported Providers: Google, GitHub.
   - Registration flow dynamically resolves provider, extracts email/name/avatar, auto-provisions `GUEST` role.
   - Callback issues both JWT and Refresh Token and redirects to frontend client.
4. **MFA TOTP Flow**:
   - Engine: `com.warrenstrange.googleauth.GoogleAuthenticator` & `org.jboss.aerogear.security.otp.Totp`.
   - Challenge Tokens: UUID tokens stored in Redis with 5-minute TTL (`mfa:{token}`).
   - Setup: Generates OTP Auth URL (`otpauth://totp/Aggarly:...`) and Base64-encoded QR code.
5. **6-Digit OTP Flow**:
   - Generation: `SecureRandom.nextInt(1_000_000)` formatted as `%06d`.
   - Storage: Redis keys `otp:email_verify:{email}`, `otp:password_reset:{email}`, `otp:phone_verify:{userId}` with 5-minute TTL.
   - Single-Use: Consumed and deleted immediately upon successful validation.

---

## 7. Threat Surfaces & Security Vulnerabilities Discovered

### Finding 1: [CRITICAL] Public Exposure of Vision Admin Endpoints in SecurityConfig
- **File**: `src/main/java/com/luna/aggarly/common/security/SecurityConfig.java:95-97`
- **Observed Configuration**:
  ```java
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
  ```
- **Risk & Threat Analysis**:
  The route patterns `/api/v1/vision/admin/ollama/**`, `/api/v1/vision/admin/clip/**`, and `/api/v1/vision/admin/models` are administrative routes that trigger resource-heavy AI model loading, evaluation, and system configurations. By placing them under `permitAll()`, any unauthenticated user on the internet can call these administrative endpoints!
- **Architectural Violation**: Violates minimal `permitAll()` boundary rule.

### Finding 2: [HIGH] Sensitive Token Exposure in OAuth2 Callback URL
- **File**: `src/main/java/com/luna/aggarly/user/security/oauth2/OAuth2SuccessHandler.java:67-70`
- **Observed Code**:
  ```java
  String redirectUrl = String.format("%s?token=%s&refreshToken=%s",
          frontendRedirectUrl, accessToken, refreshTokenValue);
  response.sendRedirect(redirectUrl);
  ```
- **Risk & Threat Analysis**:
  Passing sensitive Bearer tokens and long-lived Refresh tokens in HTTP GET query parameters exposes them to:
  1. Browser history logging.
  2. HTTP `Referer` headers when navigating to third-party resources.
  3. Intermediate proxy and web server access logs.
- **Recommended Remediation**: Deliver tokens via secure, `HttpOnly`, `SameSite=Strict` cookies, or redirect with tokens in the URL fragment `#token=...` (which browsers do not send in HTTP requests or `Referer` headers).

### Finding 3: [MEDIUM] Cleartext Logging of 6-Digit OTP Codes
- **File**: `src/main/java/com/luna/aggarly/user/service/impl/AuthServiceImpl.java:98, 358, 400, 439`
- **Observed Code**:
  ```java
  log.info("📧 6-digit Email verification OTP for {}: {}", user.getEmail(), otpCode);
  log.info("sent 6-digit Email verification OTP for {}: {}", email, otpCode);
  log.info("sent 6-digit verification OTP for {}: {}", request.email(), otpCode);
  log.info("📱 6-digit OTP sent to phone {} for user {}: {}", user.getPhone(), user.getEmail(), otpCode);
  ```
- **Risk & Threat Analysis**:
  Writing active one-time passwords directly to standard application log files compromises second-factor and password-reset security if logs are centralized into tools like Datadog, ELK, or CloudWatch where unauthorized operators or log consumers have access.
- **Remediation**: Remove `otpCode` from log parameters; log only that the event was dispatched.

### Finding 4: [MEDIUM] Timing Attack Vulnerability in OTP Validation
- **File**: `src/main/java/com/luna/aggarly/user/service/impl/OtpServiceImpl.java:99`
- **Observed Code**:
  ```java
  return code.trim().equals(storedOtp);
  ```
- **Risk & Threat Analysis**:
  Standard `String.equals()` terminates immediately on the first differing character, leaking execution timing. For short numeric codes (6 digits), timing variations can facilitate code guessing.
- **Remediation**: Use `java.security.MessageDigest.isEqual(code.trim().getBytes(StandardCharsets.UTF_8), storedOtp.getBytes(StandardCharsets.UTF_8))`.

### Finding 5: [LOW] Unhandled Exception and Blank Error Message on Duplicate MFA Setup
- **File**: `src/main/java/com/luna/aggarly/user/service/impl/AuthServiceImpl.java:152-154`
- **Observed Code**:
  ```java
  if(user.isMfaEnabled()) {
      throw new RuntimeException("");
  }
  ```
- **Risk & Threat Analysis**:
  Throws a generic `RuntimeException` with an empty string message, resulting in HTTP 500 Internal Server Error instead of a proper 400 or 409 client error.
- **Remediation**: Create a dedicated exception (e.g. `IllegalStateException("MFA is already enabled")` or `VerificationException`).

---

## 8. Summary of Architectural Verification

| Guardrail / Rule | Status | Evidence / Notes |
|---|---|---|
| Rule 1: No `@PreAuthorize("isAuthenticated()")` | **PASS (100%)** | 0 occurrences in entire codebase. |
| Rule 2: Central enforcement via `.anyRequest().authenticated()` | **PASS** | Present at `SecurityConfig.java:113`. |
| Rule 3: Minimal `permitAll()` Boundaries | **FAIL** | Vision admin routes (`/api/v1/vision/admin/**`) are inadvertently permitted to the public! |
| Rule 4: Soft-delete consistency (`@SQLRestriction`) | **PASS** | `User`, `RefreshToken` properly annotated with `is_deleted = false`. |
| Rule 5: BOLA / IDOR Protection on Payment Methods | **PASS** | All mutating operations query by compound `(id, userId)`. |
