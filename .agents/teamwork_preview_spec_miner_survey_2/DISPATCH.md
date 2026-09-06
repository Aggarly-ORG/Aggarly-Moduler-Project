# Task Assignment: Survey Phase - Security & Auth Endpoints Spec Mining

**Agent Working Directory**: `e:\java project\aggarly\.agents\teamwork_preview_spec_miner_survey_2`
**Original Request**: `e:\java project\aggarly\.agents\ORIGINAL_REQUEST.md`

## Mission
Investigate and catalog all authentication, authorization, and user management endpoints, security filter configurations, and architectural security rules compliance in Aggarly.

## Scope & Instructions
1. Read `e:\java project\aggarly\.agents\ORIGINAL_REQUEST.md`.
2. Inspect `SecurityConfig.java` and all user-facing controllers in `com.luna.aggarly.user.controller`.
3. Catalog all endpoints:
   - Endpoint HTTP method & route (e.g., `/api/v1/auth/register`, `/login`, `/refresh`, `/logout`, `/verify-email`, `/reset-password`, `/mfa/**`, `/oauth2/**`, `/users/**`)
   - Central security authorization rules in `SecurityConfig.java` (is it `.anyRequest().authenticated()`? What is permitted via `permitAll()`?)
   - Rule check: scan all controller methods for `@PreAuthorize("isAuthenticated()")` (which is strictly forbidden by Aggarly architecture rules) vs proper method-level or central security.
   - Authentication schemes (JWT header, refresh cookies, OAuth2 redirect, MFA TOTP flow, OTP).
   - Token lifecycle details: refresh token rotation logic, family ID, grace window, token storage/revocation.
4. Record all requirements, contracts, and threat surfaces discovered.
5. Write your comprehensive spec report to `e:\java project\aggarly\.agents\teamwork_preview_spec_miner_survey_2\security_endpoints_spec.md` and complete your `handoff.md`.
6. Remember: Strict Audit & Report Only constraint — do not edit any source code.
