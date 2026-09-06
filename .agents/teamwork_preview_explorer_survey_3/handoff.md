# Handoff Report: Database Schema, Flyway Migrations & JPA Parity Survey
**Agent**: `teamwork_preview_explorer`  
**Working Directory**: `e:\java project\aggarly\.agents\teamwork_preview_explorer_survey_3`  
**Parent Orchestrator ID**: `2e9c52e6-3e07-44f6-81cf-3b71e16c12ab`  
**Deliverable**: `e:\java project\aggarly\.agents\teamwork_preview_explorer_survey_3\database_jpa_survey.md`  
**Status**: Task Complete (Hard Handoff)

---

## 1. Observation

Direct observations and evidence gathered during the audit:

1. **Enum Parity Flaw (`AuthProvider.SYSTEM`)**:
   - `src/main/resources/db/migration/V26__seed_system_ai_user.sql`, lines 2-17:
     ```sql
     INSERT INTO users (
         id, email, first_name, last_name, display_name, username,
         email_verified, auth_provider, is_deleted, created_at, updated_at
     ) VALUES (
         'aaac7011-3626-460c-a47e-c94535d34c65',
         'ai-concierge@aggarly.internal',
         'Aggarly',
         'AI Concierge',
         'Aggarly AI',
         'ai_concierge',
         TRUE,
         'SYSTEM',
         FALSE,
         NOW(),
         NOW()
     ) ON CONFLICT (id) DO NOTHING;
     ```
   - `src/main/java/com/luna/aggarly/user/entity/enums/AuthProvider.java`, lines 6-10:
     ```java
     public enum AuthProvider {
         LOCAL,
         GOOGLE,
         GITHUB
     }
     ```
   - `src/main/java/com/luna/aggarly/user/entity/User.java`, lines 57-60:
     ```java
     @Enumerated(EnumType.STRING)
     @Column(name = "auth_provider", nullable = false, length = 20)
     @Builder.Default
     private AuthProvider authProvider = AuthProvider.LOCAL;
     ```

2. **Soft-Delete vs. Unique Constraint Collision**:
   - `src/main/resources/db/migration/V2__create_auth_schema.sql`, lines 10, 15:
     ```sql
     email VARCHAR(255) UNIQUE NOT NULL,
     username VARCHAR(100) UNIQUE NOT NULL,
     ```
   - `src/main/java/com/luna/aggarly/user/entity/User.java`, line 23:
     ```java
     @SQLRestriction("is_deleted = false")
     public class User extends BaseEntity { ... }
     ```
   - `src/main/java/com/luna/aggarly/user/service/impl/AuthServiceImpl.java`, lines 69-75:
     ```java
     if (userRepository.existsByEmail(request.email())) {
         throw new EmailAlreadyExistsException("Email already in use: " + request.email());
     }
     ```
   - `src/main/java/com/luna/aggarly/user/security/oauth2/OAuth2UserService.java`, lines 74-79:
     ```java
     User deletedUser = userRepository.findAnyByEmail(email).orElse(null);
     if (deletedUser != null) {
         deletedUser.setDeleted(false);
         return deletedUser;
     }
     ```

3. **Missing Composite Unique Constraint on Payment Methods**:
   - `src/main/resources/db/migration/V30__create_user_payment_methods_and_action_states.sql`, lines 6-18:
     `user_payment_methods` has no unique constraint on `(user_id, stripe_payment_method_id)`.
   - `src/main/java/com/luna/aggarly/user/service/impl/UserPaymentMethodServiceImpl.java`, lines 51-56:
     ```java
     UserPaymentMethod entity = paymentMethodRepository
             .findByUserIdAndStripePaymentMethodId(userId, request.getStripePaymentMethodId())
             .orElseGet(() -> UserPaymentMethod.builder() ... build());
     ```
   - `src/main/java/com/luna/aggarly/user/repository/UserPaymentMethodRepository.java`, line 15:
     ```java
     Optional<UserPaymentMethod> findByUserIdAndStripePaymentMethodId(UUID userId, String stripePaymentMethodId);
     ```

4. **Missing Identity Contract on `Role` Entity**:
   - `src/main/java/com/luna/aggarly/user/entity/Role.java`, lines 10-24:
     `Role` has `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder`, but lacks `equals()` and `hashCode()`.
   - `src/main/java/com/luna/aggarly/user/entity/User.java`, lines 77-84:
     `private Set<Role> roles = new HashSet<>();`
   - `src/main/java/com/luna/aggarly/user/service/impl/UserProfileServiceImpl.java`, lines 165-168:
     ```java
     if (!user.getRoles().contains(hostRole)) {
         user.getRoles().add(hostRole);
         userRepository.save(user);
     }
     ```

5. **Token Revocation Gap on Deactivation**:
   - `src/main/java/com/luna/aggarly/user/service/impl/UserProfileServiceImpl.java`, lines 174-179:
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
     `refreshTokenRepository.revokeAllUserTokens(user)` is omitted.

6. **Redundant Index on `user_confirmed_actions`**:
   - `src/main/resources/db/migration/V30__create_user_payment_methods_and_action_states.sql`, lines 28, 38-39:
     `confirmation_token VARCHAR(255) NOT NULL UNIQUE` + `CREATE INDEX idx_user_confirmed_actions_token ON user_confirmed_actions (confirmation_token);`.

7. **Cascade Safety Verification**:
   - `User.java` contains no `@OneToMany` relationships.
   - `RefreshToken.java`, `UserPaymentMethod.java`, and `UserConfirmedAction.java` maintain independent lifecycles without cascading conflicts.

---

## 2. Logic Chain

1. **Inference for Enum Crash**:
   - From Observation 1, `V26` writes `'SYSTEM'` into `users.auth_provider`.
   - From Observation 1, `User.java` uses `@Enumerated(EnumType.STRING)` mapped to `AuthProvider`.
   - From Observation 1, `AuthProvider` does not define `SYSTEM`.
   - **Conclusion**: Any query reading the row for `aaac7011-3626-460c-a47e-c94535d34c65` fails with `IllegalArgumentException`.

2. **Inference for Unique Constraint Soft-Delete Collision**:
   - From Observation 2, `users.email` has an unconditional database unique constraint.
   - From Observation 2, `User.java` applies `@SQLRestriction("is_deleted = false")`.
   - When a soft-deleted user attempts to re-register via `AuthServiceImpl.register()`, `existsByEmail` queries only active rows and returns `false`.
   - `AuthServiceImpl` attempts to `INSERT` a row with the same email.
   - PostgreSQL rejects the insert with `users_email_key` violation, producing an unhandled 500 error instead of a graceful business response.

3. **Inference for Concurrent Payment Method Corruption**:
   - From Observation 3, neither DDL nor JPA enforces uniqueness on `(user_id, stripe_payment_method_id)`.
   - Concurrent requests can both observe absence and execute `save()`.
   - Subsequent execution of `findByUserIdAndStripePaymentMethodId` encounters multiple rows and throws `IncorrectResultSizeDataAccessException`.

4. **Inference for Role Set Duplication**:
   - From Observation 4, `Role` relies on reference equality (`==`).
   - Adding a detached or separately queried instance of `Role` with the same name/id to `Set<Role>` treats it as a distinct entry.
   - Flushing the `@ManyToMany` join table attempts to insert duplicate keys into `user_roles(user_id, role_id)`, causing a primary key constraint failure.

5. **Inference for Orphaned Refresh Tokens**:
   - From Observation 5, deactivating an account sets `user.deleted = true` but does not revoke refresh tokens in `refresh_tokens`.
   - The user or an attacker possessing that refresh token can still call `/refresh` and acquire fresh access tokens.

---

## 3. Caveats

1. **No Source Modifications Made**: This investigation strictly obeyed the "Audit & Report Only" constraint. No code or migration scripts were modified.
2. **Other Modules' Entities**: Non-user modules (such as `aiagent`, `cleaning`, `booking`, `property`) were examined for foreign key relationships and soft-delete consistency (`@SQLRestriction`), but their internal domain logic was outside the scope of this audit.
3. **Database Performance Benchmarks**: Index analysis was based on query analysis and DDL inspection rather than live query execution plans (`EXPLAIN ANALYZE`).

---

## 4. Conclusion

The persistence layer for the User module is structurally solid and adheres strictly to the rule prohibiting `CascadeType.ALL` on parent collections. However, critical runtime defects exist:
1. `AuthProvider.SYSTEM` must be added immediately to prevent runtime crashes when system user records are read.
2. Partial unique indexes in PostgreSQL (`WHERE is_deleted = false`) or explicit email/username soft-delete checks must be introduced in `AuthServiceImpl`.
3. A composite unique constraint on `user_payment_methods(user_id, stripe_payment_method_id)` is required to prevent duplicate payment method corruption.
4. `Role.java` must implement `equals()` and `hashCode()`.
5. `UserProfileServiceImpl.deactivateAccount()` must invoke `refreshTokenRepository.revokeAllUserTokens(user)`.

---

## 5. Verification Method

To independently verify all findings:
1. **Enum Verification**:
   Inspect `src/main/resources/db/migration/V26__seed_system_ai_user.sql` (line 13) vs `src/main/java/com/luna/aggarly/user/entity/enums/AuthProvider.java`. Confirm `SYSTEM` is missing.
2. **Soft-Delete Collision Verification**:
   Inspect `V2__create_auth_schema.sql` (line 10), `User.java` (line 23), and `AuthServiceImpl.java` (lines 69-75). Trace registration flow with a soft-deleted email.
3. **Payment Method Duplicate Verification**:
   Inspect `V30__create_user_payment_methods_and_action_states.sql` (line 6-18) and `UserPaymentMethodServiceImpl.java` (lines 51-56).
4. **Role Identity Verification**:
   Inspect `Role.java` (lines 9-24) to confirm absence of `equals`/`hashCode`.
5. **Full Survey Report**:
   Review detailed line-by-line analyses in `e:\java project\aggarly\.agents\teamwork_preview_explorer_survey_3\database_jpa_survey.md`.
