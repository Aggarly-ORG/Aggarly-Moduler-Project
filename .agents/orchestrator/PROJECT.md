# Project: Aggarly User Module Security Audit & Architectural Code Review

## Architecture
- **Target Subsystem**: `com.luna.aggarly.user` and associated security configuration (`com.luna.aggarly.common.security`).
- **Core Modules & Boundaries**:
  - `com.luna.aggarly.user.controller`: `AuthController`, `UserController`, `UserProfileController`, `UserPaymentMethodController`.
  - `com.luna.aggarly.user.service`: `AuthServiceImpl`, `EmailServiceImpl`, `MfaServiceImpl`, `OtpServiceImpl`, `UserPaymentMethodServiceImpl`, `UserProfileServiceImpl`, `RefreshTokenCleanupService`.
  - `com.luna.aggarly.user.repository`: `UserRepository`, `RoleRepository`, `RefreshTokenRepository`, `UserPaymentMethodRepository`, `UserConfirmedActionRepository`.
  - `com.luna.aggarly.user.entity`: `User`, `Role`, `RefreshToken`, `UserPaymentMethod`, `UserConfirmedAction`, `enums.AuthProvider`, `enums.AuthStatus`.
  - `com.luna.aggarly.user.security`: `JwtAuthenticationFilter`, `JwtService`, `UserPrincipal`, `CustomUserDetailsService`, `OAuth2SuccessHandler`, `OAuth2FailureHandler`, `HttpCookieOAuth2AuthorizationRequestRepository`.
  - `com.luna.aggarly.common.security`: `SecurityConfig`, `AuthConfig`, `SecurityUtils`.
  - Database schema: PostgreSQL Flyway migrations `V1` through `V37` in `src/main/resources/db/migration/`.

## Feature Inventory
| # | Feature / Area | Description | Milestone | Source |
|---|----------------|-------------|-----------|--------|
| 1 | Controller Security & `@PreAuthorize` Compliance | Scan all controllers for `@PreAuthorize("isAuthenticated()")` and verify central security boundaries | M1, M2 | ORIGINAL_REQUEST §R1 |
| 2 | Central Auth & PermitAll Boundaries | Audit `SecurityConfig.java` for `.anyRequest().authenticated()` and minimal `permitAll()` exposure | M1, M2 | ORIGINAL_REQUEST §R1 |
| 3 | Authentication Paths & Token Lifecycle | Deep dive on `/register`, `/login`, `/refresh`, `/logout`, `/verify-email`, `/reset-password`, MFA/TOTP, and OTP verification | M2 | ORIGINAL_REQUEST §R1 |
| 4 | Refresh Token Rotation & Session Hijacking | Audit RTR family IDs, 15-second grace window, SHA-256 token binding, revocation, and cleanup | M2 | ORIGINAL_REQUEST §R1 |
| 5 | OWASP Top 10 & AppSec (BOLA/IDOR, Crypto) | Evaluate object-level authorization, token exposure in OAuth2 URL query, OTP timing attacks, cleartext logs | M2 | ORIGINAL_REQUEST §R1 |
| 6 | JPA & Database Schema Parity | Audit JPA entity mappings against Flyway migrations V2-V37, composite constraints, enum synchronization | M3 | ORIGINAL_REQUEST §R2 |
| 7 | JPA Cascades & Repository Saves | Verify compliance with zero `CascadeType.ALL` on parent collections, soft-delete restrictions | M3 | ORIGINAL_REQUEST §R2 |
| 8 | Validation & Clean Code Hygiene | Audit Jakarta validation annotations, service-controller boundaries, DTO hygiene, and dead code | M3 | ORIGINAL_REQUEST §R2 |
| 9 | Exception Safety & Information Leakage | Audit global and local exception handling, absence of stack traces/raw SQL leakage | M3 | ORIGINAL_REQUEST §R2 |
| 10 | Independent Review & Adversarial Stress Testing | Independent verification and challenge of all audit findings and exploit vectors | M4 | Workflow |
| 11 | Forensic Integrity Audit & Remediation Synthesis | Forensic audit against fabrication/hallucination, synthesis of R3 prioritized findings report | M5 | ORIGINAL_REQUEST §R3 |

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| 1 | Survey & Codebase Inventory | Full codebase discovery, endpoint catalog, Flyway & JPA parity matrix | none | DONE |
| 2 | Security & AppSec Deep Dive | R1 AppSec audit: auth flows, RTR, OAuth2 leakage, OTP timing attacks, BOLA, SecurityConfig admin leak | M1 | IN_PROGRESS |
| 3 | Architecture, JPA & Clean Code Review | R2 Clean code & JPA audit: `AuthProvider.SYSTEM` enum mismatch, soft-delete collisions, Role equals/hashCode, DTO validation | M1 | IN_PROGRESS |
| 4 | Independent Review & Adversarial Challenge | Independent review and adversarial verification of exploit scenarios and remediation snippets | M2, M3 | PLANNED |
| 5 | Forensic Audit & Prioritized Findings Report | R3 Final synthesis: executive summary, severity rankings (Critical, High, Medium, Low), line references, drop-in remediations | M4 | PLANNED |

## Code Layout
- Controllers: `src/main/java/com/luna/aggarly/user/controller/`
- Services: `src/main/java/com/luna/aggarly/user/service/` and `src/main/java/com/luna/aggarly/user/service/impl/`
- Entities: `src/main/java/com/luna/aggarly/user/entity/` and `src/main/java/com/luna/aggarly/user/entity/enums/`
- Repositories: `src/main/java/com/luna/aggarly/user/repository/`
- DTOs: `src/main/java/com/luna/aggarly/user/dto/`
- Security & OAuth2: `src/main/java/com/luna/aggarly/user/security/` and `src/main/java/com/luna/aggarly/common/security/`
- Migrations: `src/main/resources/db/migration/`
- Agent metadata: `.agents/` (orchestrator, explorers, reviewers, challengers, auditors)

## Interface Contracts & Findings Synthesis Matrix
- All findings strictly documented with:
  - Exact file path and line numbers
  - Vulnerability / architectural issue description
  - Threat vector / exploit scenario
  - Concrete code remediation snippet

