# Task Assignment: Survey Phase - Database Schema, Flyway Migrations & JPA Parity

**Agent Working Directory**: `e:\java project\aggarly\.agents\teamwork_preview_explorer_survey_3`
**Original Request**: `e:\java project\aggarly\.agents\ORIGINAL_REQUEST.md`

## Mission
Survey and map the database persistence layer for the User Module: JPA entities vs Flyway migration scripts (specifically V2, V3, V4, V30, V37 and any relevant migrations), relationship cascades, soft-delete patterns, and constraints.

## Scope & Instructions
1. Read `e:\java project\aggarly\.agents\ORIGINAL_REQUEST.md`.
2. Locate all Flyway migration SQL scripts under `src/main/resources/db/migration/` relevant to users, roles, permissions, user_profiles, user_tokens, refresh_tokens, oauth2, mfa, etc.
3. Map all JPA entities under `com.luna.aggarly.user.entity` (e.g. `User`, `Role`, `Permission`, `RefreshToken`, `UserSession`, `UserProfile`, `UserAddress`, `PaymentMethod`, etc.):
   - Table name and column definitions vs Flyway DDL definitions
   - Cascade types on `@OneToMany` and `@ManyToMany` (check specifically for `CascadeType.ALL` on parent collections vs repository saves)
   - Soft-delete annotations (`@SQLRestriction("is_deleted = false")` vs `@Where` or missing soft-delete)
   - Unique constraints (e.g. composite unique constraints, email uniqueness, soft-delete unique indexing)
   - Foreign key constraints, index utilization, and schema parity
4. Write your comprehensive survey report to `e:\java project\aggarly\.agents\teamwork_preview_explorer_survey_3\database_jpa_survey.md` and complete your `handoff.md`.
5. Remember: Strict Audit & Report Only constraint — do not edit any source code.

## 2026-09-05T11:04:35Z
You are the Database JPA Parity Explorer for the Survey Phase of Aggarly.
Your identity: teamwork_preview_explorer
Your working directory: e:\java project\aggarly\.agents\teamwork_preview_explorer_survey_3
Original request file: e:\java project\aggarly\.agents\ORIGINAL_REQUEST.md
Dispatch file: e:\java project\aggarly\.agents\teamwork_preview_explorer_survey_3\DISPATCH.md

STRICT CONSTRAINT: Audit & Report Only — do not edit or create source code files.

Instructions:
1. Read e:\java project\aggarly\.agents\ORIGINAL_REQUEST.md and your DISPATCH.md.
2. Locate all Flyway migration SQL scripts under src/main/resources/db/migration/ relevant to users, roles, permissions, user_profiles, user_tokens, refresh_tokens, oauth2, mfa, etc. (specifically V2, V3, V4, V30, V37 and all intermediate/subsequent migrations).
3. Map all JPA entities under com.luna.aggarly.user.entity:
   - Table name and column definitions vs Flyway DDL definitions
   - Cascade types on @OneToMany and @ManyToMany (check specifically for CascadeType.ALL on parent collections vs repository saves)
   - Soft-delete annotations (@SQLRestriction("is_deleted = false") vs @Where or missing soft-delete)
   - Unique constraints (composite unique constraints, email uniqueness, soft-delete unique indexing)
   - Foreign key constraints, index utilization, and schema parity
4. Maintain progress.md with regular "Last visited" timestamps.
5. Write your comprehensive survey report to e:\java project\aggarly\.agents\teamwork_preview_explorer_survey_3\database_jpa_survey.md.
6. Complete your handoff.md and notify the parent orchestrator via send_message when done.
