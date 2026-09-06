# Handoff Report: Security Endpoints Spec Mining (Survey Phase)

**Agent Identity**: `teamwork_preview_spec_miner`  
**Working Directory**: `e:\java project\aggarly\.agents\teamwork_preview_spec_miner_survey_2`  
**Report Type**: Hard Handoff (Task Complete)  
**Parent Conversation ID**: `2e9c52e6-3e07-44f6-81cf-3b71e16c12ab`  

---

## 1. Observation

Direct code observations from inspecting `com.luna.aggarly.user` and security infrastructure:

1. **Rule Check: `@PreAuthorize("isAuthenticated()")`**:
   - Grep query: `grep_search(Query="isAuthenticated", SearchPath="e:\java project\aggarly\src")` returned `No results found`.
   - Grep query: `grep_search(Query="PreAuthorize", SearchPath="e:\java project\aggarly\src")` returned 47 matches. Every `@PreAuthorize` annotation in the project uses explicit role checks (e.g. `hasRole('ADMIN')`, `hasRole('HOST')`, or `hasAnyRole('HOST','ADMIN')`).
   - Line reference: Zero controller methods use `@PreAuthorize("isAuthenticated()")`, strictly adhering to Aggarly Architecture Rule 1.

2. **Central Authorization Boundaries (`SecurityConfig.java`)**:
   - `SecurityConfig.java:113`: Enforces default authentication with `.anyRequest().authenticated()`.
   - `SecurityConfig.java:74-87`: Declares legitimate public authentication and webhook endpoints (`/api/v1/auth/register`, `/login`, `/refresh`, `/logout`, `/verify-email`, `/send-verification`, `/forgot-password`, `/reset-password`, `/validate-mfa`, `/payments/webhook`, `/login/oauth2/code/**`).
   - `SecurityConfig.java:95-97`: **CRITICAL LEAK OBSERVATION**:
     ```java
     "/api/v1/vision/admin/ollama/**",
     "/api/v1/vision/admin/clip/**",
     "/api/v1/vision/admin/models"
     ```
     These administrative endpoints are explicitly listed under `permitAll()`, allowing unauthenticated public execution of model loading and administration.

3. **Controller & Endpoint Catalog**:
   - `AuthController.java` (lines 35-110): 11 endpoints covering `/register`, `/login`, `/refresh`, `/logout`, `/verify-email`, `/send-verification`, `/forgot-password`, `/reset-password`, `/enable-mfa`, `/confirm-mfa`, `/validate-mfa`.
   - `UserController.java` (lines 34-68): 2 endpoints (`GET /{id}`, `GET /search`) using `UserPrincipal` and pagination.
   - `UserProfileController.java` (lines 39-132): 10 endpoints for authenticated user profile operations, avatar upload/removal, phone OTP, password change, host upgrade, account deactivation.
   - `UserPaymentMethodController.java` (lines 35-72): 4 endpoints for saved payment methods, all scoped to `principal.getUserId()`.

4. **Token Lifecycle & Rotation Details**:
   - `AuthServiceImpl.java:191-280`: Enforces 15-second grace window (`secondsSinceRevocation <= 15`) for concurrency multi-tab replay. Outside the 15s window, entire family is revoked via `refreshTokenRepository.revokeFamily(oldToken.getFamilyId(), Instant.now())`.
   - SHA-256 access token binding: `AuthServiceImpl.java:241-248` validates `hashToken(request.expiredAccessToken())` against `oldToken.getAssociatedAccessTokenHash()`.
   - Token cleanup: `RefreshTokenCleanupService.java:30-49` runs daily scheduled cleanup (`0 0 3 * * ?`) using `deleteExpiredAndOldRevokedTokens(now, cutoff)` with 7-day retention.

5. **Sensitive Token Exposure in OAuth2**:
   - `OAuth2SuccessHandler.java:67-70`:
     ```java
     String redirectUrl = String.format("%s?token=%s&refreshToken=%s",
             frontendRedirectUrl, accessToken, refreshTokenValue);
     response.sendRedirect(redirectUrl);
     ```
     Exposes raw JWT and Refresh Token in URL query strings.

6. **Cleartext OTP Logging**:
   - `AuthServiceImpl.java:98, 358, 400, 439`: Logs plain 6-digit OTP codes into application logs (`log.info("📧 6-digit Email verification OTP for {}: {}", user.getEmail(), otpCode)`).

7. **Timing Attack on OTP Validation**:
   - `OtpServiceImpl.java:99`: Uses `code.trim().equals(storedOtp)` instead of constant-time comparison `MessageDigest.isEqual()`.

---

## 2. Logic Chain

1. **Step 1 — Controller Security Compliance**:
   - *Premise*: Architectural rules state: "NEVER use `@PreAuthorize("isAuthenticated()")` on controller methods."
   - *Observation*: Source grep yielded 0 occurrences of `@PreAuthorize("isAuthenticated()")`. Central configuration uses `.anyRequest().authenticated()` at `SecurityConfig.java:113`.
   - *Conclusion*: The codebase is 100% compliant with the ban on `@PreAuthorize("isAuthenticated()")`.

2. **Step 2 — Evaluation of PermitAll Boundaries**:
   - *Premise*: Architectural rules require minimal `permitAll()` boundaries strictly for public auth routes, Swagger, and WebSockets.
   - *Observation*: `SecurityConfig.java:95-97` includes `/api/v1/vision/admin/ollama/**`, `/api/v1/vision/admin/clip/**`, and `/api/v1/vision/admin/models` inside `.permitAll()`.
   - *Conclusion*: A severe access control bypass exists whereby administrative endpoints are exposed to the public internet without authentication.

3. **Step 3 — Verification of BOLA / IDOR Protection**:
   - *Premise*: User-owned resources (saved cards, profile mutations) must prevent cross-tenant access.
   - *Observation*: `UserPaymentMethodServiceImpl.java:75` uses `paymentMethodRepository.findByIdAndUserId(paymentMethodId, userId)`. Profile operations resolve the authenticated user strictly via `SecurityUtils.getCurrentUserId()`.
   - *Conclusion*: Object-level authorization for payment methods and profile management is properly isolated and secure against IDOR.

4. **Step 4 — Token Rotation & Session Hijacking Robustness**:
   - *Premise*: Refresh tokens must defend against token theft and concurrent request races.
   - *Observation*: `RefreshToken` contains `familyId`, `associatedAccessTokenHash`, and `revokedAt`. The implementation permits a 15-second grace window to re-issue the active successor, and revokes the whole lineage upon detecting post-grace reuse.
   - *Conclusion*: Token rotation is robustly architected, matching modern OAuth2 Best Current Practice (RFC 6819). However, transport leakage in `OAuth2SuccessHandler.java` degrades overall security.

---

## 3. Caveats

- **External Services**: Stripe webhook execution and Google/GitHub OAuth2 credential endpoints were reviewed through static code analysis; live network interaction with Google/GitHub/Stripe was not performed.
- **In-Memory OTP Fallback**: `OtpServiceImpl.java` contains a local in-memory fallback if Redis is unavailable. While resilient for single-instance setups, it is not distributed across multiple horizontal replicas.
- **Audit & Report Only**: In strict adherence to dispatch constraints, no source code was altered during this survey phase.

---

## 4. Conclusion

The Aggarly security and user authentication architecture is highly sophisticated, featuring robust state-of-the-art token rotation (family IDs, 15-second concurrency grace window, SHA-256 access token binding), complete avoidance of forbidden `@PreAuthorize("isAuthenticated()")` annotations, and solid BOLA protection on user assets. 

However, four immediate threat surfaces require remediation before release:
1. **[CRITICAL]** Remove `/api/v1/vision/admin/**` from `permitAll()` in `SecurityConfig.java`.
2. **[HIGH]** Stop passing JWT and refresh tokens in GET redirect query parameters in `OAuth2SuccessHandler.java`.
3. **[MEDIUM]** Remove cleartext OTP codes from log statements in `AuthServiceImpl.java`.
4. **[MEDIUM]** Implement constant-time string comparison (`MessageDigest.isEqual`) in `OtpServiceImpl.java`.

---

## 5. Verification Method

To independently verify these findings:

1. **Verify absence of `@PreAuthorize("isAuthenticated()")`**:
   ```powershell
   git grep "isAuthenticated" -- src/main/java
   ```
   *Expected result*: No matches found.

2. **Verify Vision Admin PermitAll exposure**:
   Inspect `src/main/java/com/luna/aggarly/common/security/SecurityConfig.java` at lines 95-97:
   ```powershell
   git grep -n "vision/admin" src/main/java/com/luna/aggarly/common/security/SecurityConfig.java
   ```
   *Expected result*: Matches inside `.permitAll()`.

3. **Verify OAuth2 redirect token leakage**:
   Inspect `src/main/java/com/luna/aggarly/user/security/oauth2/OAuth2SuccessHandler.java` at lines 67-70:
   ```powershell
   git grep -n "token=" src/main/java/com/luna/aggarly/user/security/oauth2/OAuth2SuccessHandler.java
   ```

4. **Verify Detailed Spec**:
   Read `e:\java project\aggarly\.agents\teamwork_preview_spec_miner_survey_2\security_endpoints_spec.md`.
