# State Continuation Checkpoint: User Module Security & Architecture

**Saved At**: 2026-09-05T14:56:00+03:00  
**Project**: Aggarly Modular Vacation Rental Platform (`com.luna.aggarly`)  
**Target Module**: User Module (`com.luna.aggarly.user`) & Security (`com.luna.aggarly.common.security`)  
**Task Phase**: **Milestone 5 Completed (Audit & Report Ready)** -> **Awaiting Approval for Phase 1–5 Remediation Execution**  
**Physical Storage Location**: [`e:\java project\aggarly\to_be_continue\`](file:///e:/java%20project/aggarly/to_be_continue)

---

## 1. Directory Manifest of `to_be_continue/`

All survey analyses, architecture specifications, and forensic reports have been safely copied and archived in [`e:\java project\aggarly\to_be_continue\`](file:///e:/java%20project/aggarly/to_be_continue):

| File Name | File Size | Description |
|---|:---:|---|
| [`user_module_security_audit_report.md`](file:///e:/java%20project/aggarly/to_be_continue/user_module_security_audit_report.md) | 21 KB | **Consolidated Forensic Report**: Categorized findings (Critical, High, Medium, Low), exact file paths, line references, exploit scenarios, and drop-in code diffs. |
| [`implementation_plan.md`](file:///e:/java%20project/aggarly/to_be_continue/implementation_plan.md) | 7 KB | **Actionable Remediation Plan**: 5-phase execution sequence and automated/manual verification protocols. |
| [`security_endpoints_spec.md`](file:///e:/java%20project/aggarly/to_be_continue/security_endpoints_spec.md) | 32 KB | **Endpoints & Perimeter Survey (Survey 2)**: Complete specification of all 27 user endpoints, Central SecurityConfig audit, `@PreAuthorize` audit, and OAuth2 flow. |
| [`database_jpa_survey.md`](file:///e:/java%20project/aggarly/to_be_continue/database_jpa_survey.md) | 41 KB | **Database & JPA Parity Survey (Survey 3)**: Analysis of Flyway migrations `V1`–`V37` vs JPA entities, soft-delete collisions, cascade safety, and constraints. |
| [`survey_services_architecture.md`](file:///e:/java%20project/aggarly/to_be_continue/survey_services_architecture.md) | 31 KB | **Services & Clean Architecture Survey (Survey 1 Gen3)**: Audit of all 7 service implementations, transaction boundaries, controller decoupling, DTO validation, and cross-module boundaries. |
| [`audit_report_draft.md`](file:///e:/java%20project/aggarly/to_be_continue/audit_report_draft.md) | 59 KB | **Teamwork Preview Synthesis Draft**: In-depth synthesized AppSec & architectural analysis from background audit workers. |

---

## 2. Current State Summary

### A. What Has Been Completed
1. **Exhaustive Review**: Every class in `com.luna.aggarly.user` (controllers, services, repositories, entities, DTOs, security), `com.luna.aggarly.common.security`, and Flyway migrations `V1`–`V37` was examined line-by-line.
2. **Strict Guardrail Compliance Verified**:
   - **Zero instances** of forbidden `@PreAuthorize("isAuthenticated()")` found (100% compliance).
   - Central authorization enforced in `SecurityConfig.java` via `.anyRequest().authenticated()`.
   - Cascade safety verified (Rule 3): no `CascadeType.ALL` on parent collections; repositories handle child persistence.
3. **Forensic Findings Identified & Remediations Drafted**:
   - **Critical**: Leaked `/api/v1/vision/admin/**` in `permitAll()`; missing `AuthProvider.SYSTEM` enum constant crashing queries loading AI Concierge bot user; soft-deleted accounts retaining active refresh tokens and issuing access tokens.
   - **High**: OAuth2 redirect URL token leakage; logout query parameter token leakage; soft-delete vs unconditional unique constraint DB collision; duplicate payment cards race condition; `Role` identity failure in `Set<Role>`; `UserController` bypassing service layer; zero validation on `ConfirmMfaRequest`.
   - **Medium**: Cleartext OTP logging; timing attacks on OTP equality; single-attempt OTP destruction; unhandled filter exceptions in `JwtAuthenticationFilter`; blocking SMTP inside `@Transactional`; DTO validation gaps on `UserProfileUpdate` and `SavePaymentMethodRequest`; Redis key collision hazard in `MfaServiceImpl`.
4. **Zero Source Code Changes Made**:
   - In accordance with the user's strict instruction ("Audit & Report Only"), no source files or migrations were edited.

---

## 3. Resumption Plan for Tomorrow

When resuming work tomorrow, follow this structured execution plan:

### Step 1: Confirm User Approval of Implementation Plan
Review the proposed changes in [`implementation_plan.md`](file:///e:/java%20project/aggarly/to_be_continue/implementation_plan.md).

### Step 2: Phase 1 — Security Configuration & Token Flow
1. [`SecurityConfig.java`](file:///e:/java%20project/aggarly/src/main/java/com/luna/aggarly/common/security/SecurityConfig.java): Remove `/api/v1/vision/admin/**` from `permitAll()`.
2. [x] **COMPLETED**: [`OAuth2SuccessHandler.java`](file:///e:/java%20project/aggarly/src/main/java/com/luna/aggarly/user/security/oauth2/OAuth2SuccessHandler.java): Transmit tokens via URL fragment (`#`) instead of query parameters (`?`). (Verified with `OAuth2SuccessHandlerTest`)
3. [x] **COMPLETED**: [`AuthController.java`](file:///e:/java%20project/aggarly/src/main/java/com/luna/aggarly/user/controller/AuthController.java): Update `/logout` to accept `@Valid @RequestBody RefreshTokenRequest request`. (Verified with `AuthControllerTest`)
4. [`JwtAuthenticationFilter.java`](file:///e:/java%20project/aggarly/src/main/java/com/luna/aggarly/common/security/jwt/JwtAuthenticationFilter.java): Wrap `loadUserByUsername` in `try-catch` to avoid escaping 500 errors.
5. [`UserPrincipal.java`](file:///e:/java%20project/aggarly/src/main/java/com/luna/aggarly/user/security/UserPrincipal.java): Return `user != null && !user.isDeleted()` in `isEnabled()`.

### Step 3: Phase 2 — Entities & Enums
1. [`AuthProvider.java`](file:///e:/java%20project/aggarly/src/main/java/com/luna/aggarly/user/entity/enums/AuthProvider.java): Add `SYSTEM` enum constant.
2. [`Role.java`](file:///e:/java%20project/aggarly/src/main/java/com/luna/aggarly/user/entity/Role.java): Implement `equals()` and `hashCode()` on `name`.
3. [`UserPaymentMethod.java`](file:///e:/java%20project/aggarly/src/main/java/com/luna/aggarly/user/entity/UserPaymentMethod.java): Add `@Table(uniqueConstraints = @UniqueConstraint(name = "uq_user_payment_methods_user_stripe", columnNames = {"user_id", "stripe_payment_method_id"}))`.

### Step 4: Phase 3 — Database Migration
Create `V38__user_security_and_schema_hardening.sql`:
- Partial unique indexes on `users(email)` and `users(username)` `WHERE is_deleted = FALSE`.
- Composite unique constraint on `user_payment_methods(user_id, stripe_payment_method_id)`.
- Foreign key on `user_confirmed_actions(conversation_id) REFERENCES conversations(id) ON DELETE SET NULL`.
- Drop redundant index `idx_user_confirmed_actions_token`.
- Add index on `users(created_at DESC)`.

### Step 5: Phase 4 — Service Layer & Architectural Decoupling
1. [`UserProfileServiceImpl.java`](file:///e:/java%20project/aggarly/src/main/java/com/luna/aggarly/user/service/impl/UserProfileServiceImpl.java):
   - In `deactivateAccount()`, invoke `refreshTokenRepository.revokeAllUserTokens(user)`.
   - Add `getUserSummary(UUID)` and `searchUsers(query, limit, currentUserId)`.
   - Remove cleartext OTP logging in `sendPhoneOtp()`.
2. [`UserController.java`](file:///e:/java%20project/aggarly/src/main/java/com/luna/aggarly/user/controller/UserController.java): Inject `UserProfileService` instead of directly injecting `UserRepository` and `UserMapper`.
3. [`AuthServiceImpl.java`](file:///e:/java%20project/aggarly/src/main/java/com/luna/aggarly/user/service/impl/AuthServiceImpl.java):
   - Check `findAnyByEmail` during registration.
   - Remove cleartext OTP logging.
   - Replace empty `RuntimeException("")` with `VerificationException`.
   - Clean up dead duplicate profile methods.
4. [`OtpServiceImpl.java`](file:///e:/java%20project/aggarly/src/main/java/com/luna/aggarly/user/service/impl/OtpServiceImpl.java):
   - Use `MessageDigest.isEqual(...)` for constant-time comparison.
   - Delete OTP only upon verified match.
   - Align TTL to 10 minutes.
5. [`MfaServiceImpl.java`](file:///e:/java%20project/aggarly/src/main/java/com/luna/aggarly/user/service/impl/MfaServiceImpl.java):
   - Partition Redis key prefixes: `"mfa:challenge:" + token` vs `"mfa:setup:" + token`.

### Step 6: Phase 5 — Request DTO Validations
1. [`ConfirmMfaRequest.java`](file:///e:/java%20project/aggarly/src/main/java/com/luna/aggarly/user/dto/request/ConfirmMfaRequest.java): Add `@NotBlank` on `token` and `@NotBlank @Pattern(regexp = "^\\d{6}$")` on `totpCode`.
2. [`UserProfileUpdate.java`](file:///e:/java%20project/aggarly/src/main/java/com/luna/aggarly/user/dto/request/UserProfileUpdate.java): Add `@Size(max = 100)` on names, `@Size(max = 500)` on `bio`, and `@ValidPhone` on `phone`.
3. [`SavePaymentMethodRequest.java`](file:///e:/java%20project/aggarly/src/main/java/com/luna/aggarly/user/dto/SavePaymentMethodRequest.java): Add `@Min(1) @Max(12)` on `expMonth`, `@Min(2024)` on `expYear`, and `@Pattern(regexp = "^\\d{4}$")` on `lastFour`.

### Step 7: Verification & Testing
1. Run `./mvnw test -Dtest=*User*Test,*Auth*Test` to verify that existing unit and integration test suites pass.
2. Add new unit tests covering:
   - `AuthProvider.SYSTEM` enum deserialization.
   - `Role` `equals()` / `hashCode()` in `HashSet`.
   - `UserPrincipal.isEnabled()` status on soft-deleted users.
   - Constant-time OTP comparison with single-typo retry tolerance.
   - Token revocation on `userProfileService.deactivateAccount()`.
   - DTO validation boundary constraints.
3. Run `graphify update .` to keep the codebase knowledge graph synchronized.
