# Comprehensive Database Schema, Flyway Migrations & JPA Parity Survey Report
**Module**: Aggarly User & Authentication Subsystem (`com.luna.aggarly.user`)  
**Auditor**: Teamwork Database JPA Parity Explorer (`teamwork_preview_explorer`)  
**Date**: 2026-09-05  
**Working Directory**: `e:\java project\aggarly\.agents\teamwork_preview_explorer_survey_3`  
**Status**: Complete (Audit & Report Only — No Source Files Modified)

---

## 1. Executive Summary

This survey provides an exhaustive audit of database schema migrations and JPA entity parity for the User Module in Aggarly. The audit evaluated all 37 Flyway migration scripts located in `src/main/resources/db/migration/`—with specific focus on migrations `V2`, `V3`, `V4`, `V26`, `V30`, and `V37`—against the domain entities under `com.luna.aggarly.user.entity`, base entity definitions in `com.luna.aggarly.common.entity`, repository queries, and service-layer transactional semantics.

### Key Audit Findings
1. **Critical Enum Mismatch (`AuthProvider.SYSTEM`)**:
   Flyway migration `V26__seed_system_ai_user.sql` seeds the system AI Concierge user with `auth_provider = 'SYSTEM'`. However, `com.luna.aggarly.user.entity.enums.AuthProvider` only defines `LOCAL`, `GOOGLE`, and `GITHUB`. Any JPA query or repository operation that materializes this system user will crash at runtime with `IllegalArgumentException: No enum constant com.luna.aggarly.user.entity.enums.AuthProvider.SYSTEM`.
2. **Soft-Delete vs. Unique Constraint Collision**:
   Table `users` enforces unconditional PostgreSQL `UNIQUE` constraints on `email` and `username` (`V2__create_auth_schema.sql`), while entity `User` applies Hibernate 6.3's `@SQLRestriction("is_deleted = false")`. When an account is deactivated (`is_deleted = true`), `UserRepository.existsByEmail()` returns `false`. Any subsequent registration attempt with that email bypasses service-level guards and crashes with a database-level `DataIntegrityViolationException` (500 Internal Server Error) instead of returning a clean error or supporting reactivation.
3. **Missing Composite Unique Constraint on Payment Methods**:
   Table `user_payment_methods` (`V30`) lacks a composite unique constraint on `(user_id, stripe_payment_method_id)`. Concurrent requests to `UserPaymentMethodServiceImpl.savePaymentMethod()` can insert duplicate rows, which subsequently breaks `paymentMethodRepository.findByUserIdAndStripePaymentMethodId()` with `IncorrectResultSizeDataAccessException` / `NonUniqueResultException`.
4. **Missing Identity Contract on `Role` Entity (`equals` / `hashCode`)**:
   `Role.java` does not implement `equals()` or `hashCode()`, relying on default `Object` identity. When stored in `Set<Role>` on `User.java`, duplicate instances of the same role (e.g., during `UserProfileServiceImpl.becomeHost()`) can trigger duplicate primary key insertion attempts on `user_roles (user_id, role_id)`.
5. **JPA Cascade Safety**:
   The user domain exhibits excellent adherence to the architectural guardrail against `CascadeType.ALL` on parent collections. `User.java` maintains no `@OneToMany` collections for `RefreshToken`, `UserPaymentMethod`, or `UserConfirmedAction`. Child entities are saved explicitly via dedicated repositories, preventing composite unique key collision bugs.
6. **Token Lifecycle Gap on Account Deactivation**:
   In `UserProfileServiceImpl.deactivateAccount(UUID userId)`, `user.setDeleted(true)` is executed, but `refreshTokenRepository.revokeAllUserTokens(user)` is NOT invoked. Existing JWT refresh tokens remain active and can continue issuing access tokens for a soft-deleted account until expiration.

---

## 2. Flyway Migrations Inventory & Evolution

The Aggarly database schema contains 37 Flyway migration scripts. Below is the historical progression of all migrations directly governing or impacting user persistence:

| Migration Script | Description & Schema Modifications | Impact on User Domain |
|---|---|---|
| `V1__create_extensions.sql` | Enables PostgreSQL extensions: `uuid-ossp` and `btree_gist`. | Provides UUID generation functions and GiST indexing. |
| `V2__create_auth_schema.sql` | Creates tables: `roles`, `users`, `user_roles`, and `refresh_tokens`. Seeds initial roles (`GUEST`, `HOST`, `ADMIN`). | Core schema baseline for authentication and authorization. Defines initial user attributes, email verification tokens, and refresh token structure. |
| `V3__add_user_verification_fields.sql` | Adds columns to `users`: `password_reset_token VARCHAR(255)`, `password_reset_expiry TIMESTAMP WITH TIME ZONE`, `phone_verified BOOLEAN DEFAULT FALSE`, `identity_verified BOOLEAN DEFAULT FALSE`. | Extended user verification and credential recovery attributes directly on the `users` table. |
| `V4__create_token_tables.sql` | Drops columns from `users`: `email_verification_token`, `password_reset_token`, and `password_reset_expiry`. | Architectural shift: verification and password reset tokens transitioned to Redis OTP / short-lived ephemeral storage rather than persisting token state on the `users` record. |
| `V6__fix_auditing_column_types.sql` | Alters `created_by` and `updated_by` types on `properties` to `UUID`. | Reinforces UUID auditing alignment with `BaseEntity`. |
| `V26__seed_system_ai_user.sql` | Inserts system AI Concierge user: `id = 'aaac7011-3626-460c-a47e-c94535d34c65'`, `email = 'ai-concierge@aggarly.internal'`, `username = 'ai_concierge'`, `auth_provider = 'SYSTEM'`. | Fulfills architectural guardrail requiring persistent bot identities. Introduces `auth_provider = 'SYSTEM'`. |
| `V30__create_user_payment_methods_and_action_states.sql` | Creates tables: `user_payment_methods` and `user_confirmed_actions`. Adds indexes on `user_id` and `confirmation_token`. | Introduces database-backed saved payment methods (Stripe PM IDs) and persistent multi-step action confirmations. |
| `V37__update_refresh_token_family_and_revocation.sql` | Alters `refresh_tokens`: adds `family_id UUID NOT NULL`, `revoked_at TIMESTAMP WITH TIME ZONE`, `replaced_by_token VARCHAR(255)`. Creates performance indexes: `idx_refresh_tokens_family_id`, `idx_refresh_tokens_user_id`, `idx_refresh_tokens_cleanup`. | Implements Refresh Token Rotation (RTR) with Token Families and 15-second grace window support. |

---

## 3. JPA Entity Mapping & Schema Parity Analysis

### 3.1. Entity 1: `User` (`com.luna.aggarly.user.entity.User`)
- **Table**: `users`
- **Superclass**: `BaseEntity` (`com.luna.aggarly.common.entity.BaseEntity`)
- **Annotations**: `@Entity`, `@Table(name = "users")`, `@SQLRestriction("is_deleted = false")`, `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder`

#### Column Parity Matrix
| JPA Field | JPA Annotations | Flyway DDL Definition | Parity Status | Notes |
|---|---|---|---|---|
| `id` | `@Id @GeneratedValue(strategy = GenerationType.UUID)` (in `BaseEntity`) | `id UUID PRIMARY KEY` (V2) | **MATCH** | UUID v4 assigned by JPA runtime or DB. |
| `email` | `@Column(nullable = false, unique = true)` | `email VARCHAR(255) UNIQUE NOT NULL` (V2) | **PARITY FLAW** | JPA matches DDL, but DB `UNIQUE` constraint conflicts with soft-delete (`@SQLRestriction`). |
| `passwordHash` | `@Column(name = "password_hash")` | `password_hash VARCHAR(255)` (V2) | **MATCH** | Nullable for OAuth2 users. BCrypt hashed. |
| `firstName` | `@Column(name = "first_name", length = 100)` | `first_name VARCHAR(100)` (V2) | **MATCH** | Nullable, max length 100. |
| `lastName` | `@Column(name = "last_name", length = 100)` | `last_name VARCHAR(100)` (V2) | **MATCH** | Nullable, max length 100. |
| `displayName` | `@Column(name = "display_name", length = 100)` | `display_name VARCHAR(100)` (V2) | **MATCH** | Nullable, max length 100. |
| `username` | `@Column(nullable = false, unique = true, length = 100)` | `username VARCHAR(100) UNIQUE NOT NULL` (V2) | **PARITY FLAW** | Unconditional DB unique constraint collides with soft-delete. |
| `phone` | `@Column(length = 30)` | `phone VARCHAR(30)` (V2) | **MATCH** | Nullable, max length 30. |
| `avatarUrl` | `@Column(name = "avatar_url", length = 512)` | `avatar_url VARCHAR(512)` (V2) | **MATCH** | Nullable S3/MinIO URL. |
| `bio` | `@Column(length = 500)` | `bio VARCHAR(500)` (V2) | **MATCH** | Nullable, max length 500. |
| `emailVerified` | `@Column(name = "email_verified", nullable = false) @Builder.Default private boolean emailVerified = false;` | `email_verified BOOLEAN NOT NULL DEFAULT FALSE` (V2) | **MATCH** | Primitive boolean defaults to false. |
| `authProvider` | `@Enumerated(EnumType.STRING) @Column(name = "auth_provider", nullable = false, length = 20) @Builder.Default private AuthProvider authProvider = AuthProvider.LOCAL;` | `auth_provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL'` (V2) | **CRITICAL FLAW** | DDL accommodates VARCHAR(20). V26 inserts `'SYSTEM'`. Enum `AuthProvider` only contains `{LOCAL, GOOGLE, GITHUB}`. |
| `totpSecret` | `@Column(name = "totp_secret", length = 32)` | `totp_secret VARCHAR(32)` (V2) | **MATCH** | Base32 encoded TOTP shared secret. |
| `mfaEnabled` | `@Column(name = "mfa_enabled", nullable = false) @Builder.Default private boolean mfaEnabled = false;` | `mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE` (V2) | **MATCH** | Primitive boolean defaults to false. |
| `phoneVerified` | `@Column(name = "phone_verified", nullable = false) @Builder.Default private boolean phoneVerified = false;` | `phone_verified BOOLEAN NOT NULL DEFAULT FALSE` (V3) | **MATCH** | Added in V3. Primitive boolean. |
| `identityVerified` | `@Column(name = "identity_verified", nullable = false) @Builder.Default private boolean identityVerified = false;` | `identity_verified BOOLEAN NOT NULL DEFAULT FALSE` (V3) | **MATCH** | Added in V3. Primitive boolean. |
| `deleted` | `@Column(name = "is_deleted", nullable = false)` (in `BaseEntity`) | `is_deleted BOOLEAN NOT NULL DEFAULT FALSE` (V2) | **MATCH** | Soft-delete flag. |
| `createdAt` | `@CreatedDate @Column(name = "created_at", nullable = false, updatable = false)` (in `BaseEntity`) | `created_at TIMESTAMP WITH TIME ZONE NOT NULL` (V2) | **MATCH** | Audited via `AuditingEntityListener`. |
| `updatedAt` | `@LastModifiedDate @Column(name = "updated_at")` (in `BaseEntity`) | `updated_at TIMESTAMP WITH TIME ZONE` (V2) | **MATCH** | Audited via `AuditingEntityListener`. |
| `createdBy` | `@CreatedBy @Column(name = "created_by", updatable = false)` (in `BaseEntity`) | `created_by UUID` (V2) | **MATCH** | Audited via Spring Security principal. |
| `updatedBy` | `@LastModifiedBy @Column(name = "updated_by")` (in `BaseEntity`) | `updated_by UUID` (V2) | **MATCH** | Audited via Spring Security principal. |
| *(dropped)* | *(not mapped)* | `email_verification_token` (V2 -> dropped V4) | **MATCH** | Dropped in V4. Not present in entity. |
| *(dropped)* | *(not mapped)* | `password_reset_token` (V3 -> dropped V4) | **MATCH** | Dropped in V4. Not present in entity. |
| *(dropped)* | *(not mapped)* | `password_reset_expiry` (V3 -> dropped V4) | **MATCH** | Dropped in V4. Not present in entity. |

---

### 3.2. Entity 2: `Role` (`com.luna.aggarly.user.entity.Role`)
- **Table**: `roles`
- **Superclass**: None (Does not extend `BaseEntity`; no auditing or soft-delete columns in `roles` table)
- **Annotations**: `@Entity`, `@Table(name = "roles")`, `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder`

#### Column Parity Matrix
| JPA Field | JPA Annotations | Flyway DDL Definition | Parity Status | Notes |
|---|---|---|---|---|
| `id` | `@Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;` | `id BIGSERIAL PRIMARY KEY` (V2) | **MATCH** | `BIGSERIAL` maps to `GenerationType.IDENTITY` and `Long`. |
| `name` | `@Column(nullable = false, unique = true, length = 50) private String name;` | `name VARCHAR(50) UNIQUE NOT NULL` (V2) | **MATCH** | Seeded with `'GUEST'`, `'HOST'`, `'ADMIN'`. |

#### Structural Issues in `Role`:
- **Missing `equals()` and `hashCode()`**: `Role` does not define `equals()` or `hashCode()`. When used in `Set<Role>` on `User.java`, set operations (such as `.contains(hostRole)`) use reference identity.

---

### 3.3. Entity 3: `RefreshToken` (`com.luna.aggarly.user.entity.RefreshToken`)
- **Table**: `refresh_tokens`
- **Superclass**: `BaseEntity`
- **Annotations**: `@Entity`, `@Table(name = "refresh_tokens")`, `@SQLRestriction("is_deleted = false")`, `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder`

#### Column Parity Matrix
| JPA Field | JPA Annotations | Flyway DDL Definition | Parity Status | Notes |
|---|---|---|---|---|
| `id` | `@Id @GeneratedValue(strategy = GenerationType.UUID)` (in `BaseEntity`) | `id UUID PRIMARY KEY` (V2) | **MATCH** | Primary key. |
| `token` | `@Column(nullable = false, unique = true) private String token;` | `token VARCHAR(255) UNIQUE NOT NULL` (V2) | **MATCH** | Cryptographic random or JWT refresh token string. |
| `familyId` | `@Column(name = "family_id", nullable = false) private UUID familyId;` | `family_id UUID NOT NULL` (V37) | **MATCH** | Added in V37 for RTR token family tracking. Indexed. |
| `user` | `@ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false) private User user;` | `user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE` (V2) | **MATCH** | Foreign key with `ON DELETE CASCADE`. Lazy fetched. |
| `expiryDate` | `@Column(name = "expiry_date", nullable = false) private Instant expiryDate;` | `expiry_date TIMESTAMP WITH TIME ZONE NOT NULL` (V2) | **MATCH** | Token expiration timestamp. |
| `revoked` | `@Column(nullable = false) @Builder.Default private boolean revoked = false;` | `revoked BOOLEAN NOT NULL DEFAULT FALSE` (V2) | **MATCH** | Revocation flag. |
| `revokedAt` | `@Column(name = "revoked_at") private Instant revokedAt;` | `revoked_at TIMESTAMP WITH TIME ZONE` (V37) | **MATCH** | Added in V37 for grace period calculations. |
| `replacedByToken` | `@Column(name = "replaced_by_token", length = 255) private String replacedByToken;` | `replaced_by_token VARCHAR(255)` (V37) | **MATCH** | Added in V37 for rotation tracing. |
| `associatedAccessTokenHash` | `@Column(name = "associated_access_token_hash", length = 256) private String associatedAccessTokenHash;` | `associated_access_token_hash VARCHAR(256)` (V2) | **MATCH** | SHA-256 hash of bound access token. |
| Auditing fields | Inherited from `BaseEntity` | `created_at`, `updated_at`, `created_by`, `updated_by`, `is_deleted` (V2) | **MATCH** | Full parity with V2 auditing columns. |

---

### 3.4. Entity 4: `UserPaymentMethod` (`com.luna.aggarly.user.entity.UserPaymentMethod`)
- **Table**: `user_payment_methods`
- **Superclass**: None (Does not extend `BaseEntity`)
- **Annotations**: `@Entity`, `@Table(name = "user_payment_methods")`, `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder`

#### Column Parity Matrix
| JPA Field | JPA Annotations | Flyway DDL Definition | Parity Status | Notes |
|---|---|---|---|---|
| `id` | `@Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;` | `id UUID PRIMARY KEY DEFAULT gen_random_uuid()` (V30) | **MATCH** | UUID primary key. |
| `userId` | `@Column(name = "user_id", nullable = false) private UUID userId;` | `user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE` (V30) | **MATCH** | Direct UUID mapping without JPA `@ManyToOne` entity navigation. |
| `stripePaymentMethodId` | `@Column(name = "stripe_payment_method_id", nullable = false) private String stripePaymentMethodId;` | `stripe_payment_method_id VARCHAR(255) NOT NULL` (V30) | **MATCH** | Stripe PM identifier (`pm_...`). |
| `cardBrand` | `@Column(name = "card_brand", nullable = false, length = 50) private String cardBrand;` | `card_brand VARCHAR(50) NOT NULL` (V30) | **MATCH** | e.g. "visa", "mastercard". |
| `lastFour` | `@Column(name = "last_four", nullable = false, length = 4) private String lastFour;` | `last_four VARCHAR(4) NOT NULL` (V30) | **MATCH** | 4-digit card suffix. |
| `expMonth` | `@Column(name = "exp_month", nullable = false) private Integer expMonth;` | `exp_month INT NOT NULL` (V30) | **MATCH** | Expiration month (1-12). |
| `expYear` | `@Column(name = "exp_year", nullable = false) private Integer expYear;` | `exp_year INT NOT NULL` (V30) | **MATCH** | Expiration year (YYYY). |
| `cardholderName` | `@Column(name = "cardholder_name") private String cardholderName;` | `cardholder_name VARCHAR(255)` (V30) | **MATCH** | Cardholder name. |
| `isDefault` | `@Column(name = "is_default", nullable = false) private boolean isDefault;` | `is_default BOOLEAN NOT NULL DEFAULT FALSE` (V30) | **MATCH** | Primary payment method indicator. |
| `createdAt` | `@CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;` | `created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()` (V30) | **MATCH** | Timestamped on insert. |
| `updatedAt` | `@UpdateTimestamp @Column(name = "updated_at", nullable = false) private Instant updatedAt;` | `updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()` (V30) | **MATCH** | Timestamped on update. |

#### Structural Issues in `UserPaymentMethod`:
- **Missing Unique Constraint**: Neither DDL nor JPA defines `UNIQUE(user_id, stripe_payment_method_id)`. Concurrent requests can insert duplicates, breaking `findByUserIdAndStripePaymentMethodId`.

---

### 3.5. Entity 5: `UserConfirmedAction` (`com.luna.aggarly.user.entity.UserConfirmedAction`)
- **Table**: `user_confirmed_actions`
- **Superclass**: None (Does not extend `BaseEntity`)
- **Annotations**: `@Entity`, `@Table(name = "user_confirmed_actions")`, `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder`

#### Column Parity Matrix
| JPA Field | JPA Annotations | Flyway DDL Definition | Parity Status | Notes |
|---|---|---|---|---|
| `id` | `@Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;` | `id UUID PRIMARY KEY DEFAULT gen_random_uuid()` (V30) | **MATCH** | UUID primary key. |
| `userId` | `@Column(name = "user_id", nullable = false) private UUID userId;` | `user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE` (V30) | **MATCH** | Foreign key to `users(id)`. |
| `conversationId` | `@Column(name = "conversation_id") private UUID conversationId;` | `conversation_id UUID` (V30) | **MATCH** | References `conversations(id)` logically, but lacks DB FK. |
| `confirmationToken` | `@Column(name = "confirmation_token", nullable = false, unique = true) private String confirmationToken;` | `confirmation_token VARCHAR(255) NOT NULL UNIQUE` (V30) | **MATCH** | Unique confirmation token. |
| `toolName` | `@Column(name = "tool_name", nullable = false, length = 100) private String toolName;` | `tool_name VARCHAR(100) NOT NULL` (V30) | **MATCH** | AI tool requiring user approval. |
| `status` | `@Column(name = "status", nullable = false, length = 50) @Builder.Default private String status = "CONFIRMED";` | `status VARCHAR(50) NOT NULL DEFAULT 'CONFIRMED'` (V30) | **MATCH** | Status string. |
| `detailsJson` | `@Column(name = "details_json", columnDefinition = "TEXT") private String detailsJson;` | `details_json TEXT` (V30) | **MATCH** | Payload details stored as JSON string. |
| `confirmedAt` | `@CreationTimestamp @Column(name = "confirmed_at", nullable = false, updatable = false) private Instant confirmedAt;` | `confirmed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()` (V30) | **MATCH** | Timestamped on insert. |

---

## 4. Cascade Behavior & Parent-Child Relationships

### 4.1. Compliance with Architectural Guardrail (CascadeType vs. Repository Saves)
Aggarly Architectural & Security Guardrails Rule 3 states:
> *"JPA Cascade vs. Repository Saves: Do not use CascadeType.ALL on parent @OneToMany collections when child entities are saved through their respective repositories, to avoid duplicate insert violations on composite unique constraints (e.g. uq_conversation_user)."*

#### Audit Assessment:
- **`User.java`**: Does **NOT** declare `@OneToMany` collections for `RefreshToken`, `UserPaymentMethod`, `UserConfirmedAction`, `Booking`, or `Property`.
  - Child entities maintain their own life-cycle via `RefreshTokenRepository`, `UserPaymentMethodRepository`, and `UserConfirmedActionRepository`.
  - Saving a `User` entity never triggers inadvertent cascaded inserts or updates on active tokens, payment methods, or action states.
- **`RefreshToken.java`**: The `@ManyToOne` relationship to `User` does not declare `cascade = CascadeType.ALL` or `CascadeType.PERSIST`. It is strictly `@ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false)`.
- **`UserPaymentMethod.java` & `UserConfirmedAction.java`**: Both models declare raw `private UUID userId;` rather than JPA `@ManyToOne User user` entity associations. This completely decouples their persistence from the Hibernate session graph of `User`.

### 4.2. `@ManyToMany` Relationship on `User.roles`
In `User.java` (lines 77-84):
```java
@ManyToMany(fetch = FetchType.EAGER)
@JoinTable(
    name = "user_roles",
    joinColumns = @JoinColumn(name = "user_id"),
    inverseJoinColumns = @JoinColumn(name = "role_id")
)
@Builder.Default
private Set<Role> roles = new HashSet<>();
```
1. **No Cascades**: `cascade` is intentionally omitted. Roles are immutable reference data seeded by Flyway (`V2__create_auth_schema.sql`). Hibernate manages insertions and deletions into the join table `user_roles` without attempting to mutate the `roles` table.
2. **Fetch Type EAGER**: `fetch = FetchType.EAGER` ensures that when Spring Security loads a `UserPrincipal` (`UserPrincipalDetailsService.java`), user authorities are pre-loaded.
3. **Identity Risk**: Because `Role.java` lacks `equals()` and `hashCode()`, inserting an unmanaged or detached `Role` instance into `roles` can fail `Set` deduplication, generating duplicate key violations on `user_roles(user_id, role_id)`.

---

## 5. Soft-Delete Implementation & Architecture

### 5.1. Annotation Standards: `@SQLRestriction` vs. `@Where`
- The deprecated `@Where` annotation is **100% absent** from the entire codebase.
- The project has standardized on Hibernate 6.3+'s `@SQLRestriction("is_deleted = false")`.
- In the User module:
  - `User.java`: Annotated with `@SQLRestriction("is_deleted = false")` (line 23).
  - `RefreshToken.java`: Annotated with `@SQLRestriction("is_deleted = false")` (line 21).
  - `Role`, `UserPaymentMethod`, and `UserConfirmedAction`: Do not have soft-delete columns in their respective Flyway tables (`is_deleted` column does not exist) and correctly do not have `@SQLRestriction`.

### 5.2. Unique Constraint Collision with Soft-Delete
In PostgreSQL:
- `V2__create_auth_schema.sql` establishes:
  ```sql
  email VARCHAR(255) UNIQUE NOT NULL,
  username VARCHAR(100) UNIQUE NOT NULL
  ```
  These are unconditional table-level unique constraints that index **all rows**, including those where `is_deleted = TRUE`.
- In `User.java`, `@SQLRestriction("is_deleted = false")` is applied.
- **The Resulting Failure Mode**:
  1. User registers with email `alice@example.com` and username `alice`.
  2. User deactivates their account via `DELETE /api/v1/users/me` (`UserProfileServiceImpl.deactivateAccount`). `user.setDeleted(true)` is persisted.
  3. Alice (or a new registrant) attempts to register again with `alice@example.com`.
  4. In `AuthServiceImpl.register`:
     ```java
     if (userRepository.existsByEmail(request.email())) { ... }
     ```
     Because of `@SQLRestriction("is_deleted = false")`, Spring Data executes:
     `SELECT count(*) FROM users WHERE email = ? AND (is_deleted = false)`
     This returns `0` (`existsByEmail == false`).
  5. `AuthServiceImpl` proceeds to create a new `User` and invokes `userRepository.save(user)`.
  6. PostgreSQL rejects the `INSERT` with:
     `ERROR: duplicate key value violates unique constraint "users_email_key"`
  7. The user receives an unhandled 500 Internal Server Error rather than an informative error or an account reactivation prompt.
- **Discrepancy Between Local Auth and OAuth2**:
  - In `OAuth2UserService.java` (lines 72-79), the developer recognized this issue and explicitly used a native query `findAnyByEmail`:
    ```java
    User deletedUser = userRepository.findAnyByEmail(email).orElse(null);
    if (deletedUser != null) {
        deletedUser.setDeleted(false);
        return deletedUser;
    }
    ```
  - In `AuthServiceImpl.java`, this check was omitted, leaving standard email/password registration vulnerable to unhandled 500 errors.

### 5.3. Incomplete Account Deactivation Lifecycle
In `UserProfileServiceImpl.java` (lines 174-179):
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
- Contrast with `UserProfileServiceImpl.changePassword` (line 117):
  `refreshTokenRepository.revokeAllUserTokens(user);`
- When an account is deactivated, existing refresh tokens in `refresh_tokens` are **never revoked**. Active client sessions can continue refreshing tokens and accessing APIs until their refresh tokens expire.

---

## 6. Constraints, Keys, and Indexing Parity

### 6.1. Foreign Key Actions & Delete Propagation
| Table | Column | References | ON DELETE Action | Evaluation |
|---|---|---|---|---|
| `user_roles` | `user_id` | `users(id)` | `CASCADE` | **CORRECT**: Deleting user removes join records. |
| `user_roles` | `role_id` | `roles(id)` | `CASCADE` | **CORRECT**: Deleting role removes join records. |
| `refresh_tokens` | `user_id` | `users(id)` | `CASCADE` | **CORRECT**: Hard-deleting user purges all tokens. |
| `user_payment_methods` | `user_id` | `users(id)` | `CASCADE` | **CORRECT**: Deleting user purges saved cards. |
| `user_confirmed_actions` | `user_id` | `users(id)` | `CASCADE` | **CORRECT**: Deleting user purges confirmation states. |
| `user_confirmed_actions` | `conversation_id` | `conversations(id)` | *None (No FK)* | **DEFECT**: Missing FK constraint; orphaned action records possible if conversation is deleted. |

### 6.2. Index Parity & Redundancies
1. **Redundant Index on `user_confirmed_actions` (`V30`)**:
   - Column `confirmation_token VARCHAR(255) NOT NULL UNIQUE` automatically causes PostgreSQL to create a unique btree index.
   - Migration `V30` adds: `CREATE INDEX IF NOT EXISTS idx_user_confirmed_actions_token ON user_confirmed_actions (confirmation_token);`.
   - **Result**: PostgreSQL maintains two identical btree indexes on `confirmation_token`, wasting write I/O and storage.
2. **Index Optimization on `refresh_tokens` (`V37`)**:
   - Migration `V37` creates three indexes:
     - `idx_refresh_tokens_family_id ON refresh_tokens(family_id)`
     - `idx_refresh_tokens_user_id ON refresh_tokens(user_id)`
     - `idx_refresh_tokens_cleanup ON refresh_tokens(revoked, expiry_date, revoked_at)`
   - Matches repository queries: `findByFamilyId`, `revokeFamily`, `revokeAllUserTokens`, and `deleteExpiredAndOldRevokedTokens`.
3. **Missing Index for User Search & Recent Users (`UserRepository`)**:
   - `searchUsers(...)` performs `LOWER(...) LIKE '%:query%'` across 5 columns without trigram (`gin_trgm_ops`) support.
   - `findRecentUsers(...)` performs `ORDER BY u.createdAt DESC`. Table `users` lacks an index on `created_at`.

---

## 7. Prioritized Discrepancy & Remediation Catalog

### Discrepancy 1: Missing Enum Constant `AuthProvider.SYSTEM`
- **Severity**: **CRITICAL**
- **Affected Files**:
  - `src/main/java/com/luna/aggarly/user/entity/enums/AuthProvider.java` (lines 6-10)
  - `src/main/resources/db/migration/V26__seed_system_ai_user.sql` (lines 12-14)
  - `src/main/java/com/luna/aggarly/user/entity/User.java` (line 60)
- **Problem Description**:
  Flyway migration `V26__seed_system_ai_user.sql` seeds the system AI Concierge user with `auth_provider = 'SYSTEM'`.
  However, `AuthProvider.java` only defines:
  ```java
  public enum AuthProvider {
      LOCAL,
      GOOGLE,
      GITHUB
  }
  ```
- **Exploit / Failure Scenario**:
  Whenever any query (e.g. `userRepository.findById(ChatAiBridgeService.AI_BOT_SYSTEM_ID)`, user admin listings, or user lookups) loads the seeded AI user entity into the persistence context, Hibernate's `EnumType.STRING` converter throws:
  `java.lang.IllegalArgumentException: No enum constant com.luna.aggarly.user.entity.enums.AuthProvider.SYSTEM`
  This causes unhandled HTTP 500 errors across chat, AI concierge bridge, and user management workflows.
- **Remediation**:
  Add `SYSTEM` to `AuthProvider.java`:
  ```java
  public enum AuthProvider {
      LOCAL,
      GOOGLE,
      GITHUB,
      SYSTEM
  }
  ```

---

### Discrepancy 2: Unique Constraint Collision on Soft-Deleted Users
- **Severity**: **HIGH**
- **Affected Files**:
  - `src/main/resources/db/migration/V2__create_auth_schema.sql` (lines 10, 15)
  - `src/main/java/com/luna/aggarly/user/entity/User.java` (lines 23, 26, 41)
  - `src/main/java/com/luna/aggarly/user/service/impl/AuthServiceImpl.java` (lines 69-75)
- **Problem Description**:
  `users.email` and `users.username` have unconditional `UNIQUE` constraints in PostgreSQL. When a user deactivates their account, `is_deleted` is set to `true`.
  Because `User` has `@SQLRestriction("is_deleted = false")`, `userRepository.existsByEmail()` and `existsByUsername()` return `false` for soft-deleted accounts. Standard registration proceeds to `INSERT` and triggers an unhandled `DataIntegrityViolationException`.
- **Exploit / Failure Scenario**:
  A deactivated user attempting to re-register receives an unexpected 500 Internal Server Error. The system neither allows reactivation nor returns a helpful error.
- **Remediation**:
  1. Add partial unique indexes in a new Flyway migration script:
     ```sql
     -- Drop unconditional unique constraints
     ALTER TABLE users DROP CONSTRAINT IF EXISTS users_email_key;
     ALTER TABLE users DROP CONSTRAINT IF EXISTS users_username_key;

     -- Create partial unique indexes that apply only to active users
     CREATE UNIQUE INDEX idx_users_active_email ON users (email) WHERE is_deleted = FALSE;
     CREATE UNIQUE INDEX idx_users_active_username ON users (username) WHERE is_deleted = FALSE;
     ```
  2. In `AuthServiceImpl.register()`, check for soft-deleted accounts using `userRepository.findAnyByEmail(request.email())` to either prompt account reactivation or prevent duplicate registration with a clean business exception (`EmailAlreadyExistsException`).

---

### Discrepancy 3: Missing Composite Unique Constraint on `user_payment_methods`
- **Severity**: **HIGH**
- **Affected Files**:
  - `src/main/resources/db/migration/V30__create_user_payment_methods_and_action_states.sql` (lines 6-18)
  - `src/main/java/com/luna/aggarly/user/entity/UserPaymentMethod.java` (lines 11-18)
  - `src/main/java/com/luna/aggarly/user/service/impl/UserPaymentMethodServiceImpl.java` (lines 51-56)
- **Problem Description**:
  `user_payment_methods` allows arbitrary duplicate entries of `(user_id, stripe_payment_method_id)`.
  `UserPaymentMethodServiceImpl.savePaymentMethod` queries `findByUserIdAndStripePaymentMethodId` and saves if absent. Under concurrent requests, both transactions can insert the same card. Subsequent reads throw `NonUniqueResultException`.
- **Exploit / Failure Scenario**:
  A user double-clicking "Save Card" or calling the API concurrently creates duplicate records, permanently breaking their payment methods listing endpoint (`/api/v1/users/me/payment-methods`) with a 500 error.
- **Remediation**:
  1. Add composite unique constraint in Flyway:
     ```sql
     ALTER TABLE user_payment_methods
         ADD CONSTRAINT uq_user_payment_methods_user_stripe UNIQUE (user_id, stripe_payment_method_id);
     ```
  2. Annotate `UserPaymentMethod.java`:
     ```java
     @Entity
     @Table(
         name = "user_payment_methods",
         uniqueConstraints = @UniqueConstraint(
             name = "uq_user_payment_methods_user_stripe",
             columnNames = {"user_id", "stripe_payment_method_id"}
         )
     )
     public class UserPaymentMethod { ... }
     ```

---

### Discrepancy 4: Missing `equals()` and `hashCode()` on `Role` Entity
- **Severity**: **HIGH**
- **Affected Files**:
  - `src/main/java/com/luna/aggarly/user/entity/Role.java` (lines 11-16)
  - `src/main/java/com/luna/aggarly/user/entity/User.java` (line 84)
  - `src/main/java/com/luna/aggarly/user/service/impl/UserProfileServiceImpl.java` (lines 165-168)
- **Problem Description**:
  `Role` relies on default `java.lang.Object` identity equality. In `User.java`, `roles` is stored in a `Set<Role>`.
  In `UserProfileServiceImpl.becomeHost`:
  ```java
  Role hostRole = roleRepository.findByName("HOST").orElseThrow(...);
  if (!user.getRoles().contains(hostRole)) {
      user.getRoles().add(hostRole);
      userRepository.save(user);
  }
  ```
  If `user` was loaded in a different persistence session or detached context, `user.getRoles().contains(hostRole)` evaluates to `false` even if the user already has the `HOST` role. Hibernate then attempts to insert `(user_id, host_role_id)` into `user_roles`, throwing a primary key violation (`PRIMARY KEY (user_id, role_id)`).
- **Remediation**:
  Implement `equals()` and `hashCode()` on `Role.java` based on business key (`name`) or identifier (`id`):
  ```java
  @Override
  public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Role role)) return false;
      return Objects.equals(name, role.name);
  }

  @Override
  public int hashCode() {
      return Objects.hash(name);
  }
  ```

---

### Discrepancy 5: Refresh Tokens Not Revoked on Account Deactivation
- **Severity**: **MEDIUM**
- **Affected Files**:
  - `src/main/java/com/luna/aggarly/user/service/impl/UserProfileServiceImpl.java` (lines 174-179)
  - `src/main/java/com/luna/aggarly/user/repository/RefreshTokenRepository.java` (line 30)
- **Problem Description**:
  When a user deactivates their account via `deactivateAccount()`, only `user.setDeleted(true)` is set. Active refresh tokens remain valid in `refresh_tokens`.
- **Exploit / Failure Scenario**:
  A compromised or disgruntled user deactivates their account, but an attacker with an exfiltrated refresh token can continue refreshing access tokens and accessing protected resources.
- **Remediation**:
  Update `deactivateAccount()` in `UserProfileServiceImpl.java`:
  ```java
  @Override
  @Transactional
  public void deactivateAccount(UUID userId) {
      User user = findUserById(userId);
      user.setDeleted(true);
      userRepository.save(user);
      refreshTokenRepository.revokeAllUserTokens(user);
      log.info("Account deactivated (soft-deleted) and tokens revoked for user: {}", user.getEmail());
  }
  ```

---

### Discrepancy 6: Redundant Index on `user_confirmed_actions(confirmation_token)`
- **Severity**: **MEDIUM**
- **Affected Files**:
  - `src/main/resources/db/migration/V30__create_user_payment_methods_and_action_states.sql` (lines 28, 38-39)
- **Problem Description**:
  Migration `V30` defines `confirmation_token VARCHAR(255) NOT NULL UNIQUE` (which automatically creates unique index `user_confirmed_actions_confirmation_token_key`), and then executes `CREATE INDEX IF NOT EXISTS idx_user_confirmed_actions_token ON user_confirmed_actions (confirmation_token);`.
- **Exploit / Failure Scenario**:
  Duplicate index creates redundant disk storage and double maintenance overhead on every insert/update.
- **Remediation**:
  In a cleanup migration:
  ```sql
  DROP INDEX IF EXISTS idx_user_confirmed_actions_token;
  ```

---

### Discrepancy 7: Missing Foreign Key on `user_confirmed_actions.conversation_id`
- **Severity**: **MEDIUM**
- **Affected Files**:
  - `src/main/resources/db/migration/V30__create_user_payment_methods_and_action_states.sql` (line 27)
- **Problem Description**:
  Column `conversation_id UUID` has no foreign key constraint referencing `conversations(id) ON DELETE SET NULL` or `ON DELETE CASCADE`.
- **Exploit / Failure Scenario**:
  Hard-deleting a conversation leaves orphaned records in `user_confirmed_actions`, or invalid conversation UUIDs can be inserted without referential validation.
- **Remediation**:
  Add foreign key constraint:
  ```sql
  ALTER TABLE user_confirmed_actions
      ADD CONSTRAINT fk_user_confirmed_actions_conversation
      FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE SET NULL;
  ```

---

### Discrepancy 8: Missing Query Index on `users(created_at)`
- **Severity**: **LOW**
- **Affected Files**:
  - `src/main/java/com/luna/aggarly/user/repository/UserRepository.java` (lines 36-37)
  - `src/main/resources/db/migration/V2__create_auth_schema.sql` (line 25)
- **Problem Description**:
  `UserRepository.findRecentUsers` queries `ORDER BY u.createdAt DESC`. Table `users` lacks an index on `created_at`.
- **Exploit / Failure Scenario**:
  As the user base scales, administrative dashboard queries or recent user queries will perform full table scans and disk-based file sorts.
- **Remediation**:
  Add index:
  ```sql
  CREATE INDEX IF NOT EXISTS idx_users_created_at ON users (created_at DESC);
  ```

---

## 8. Cross-Entity Soft-Delete Consistency Analysis

An investigation of all classes extending `BaseEntity` across the application revealed inconsistent adoption of Hibernate 6.3 `@SQLRestriction`:

| Entity Class | Extends BaseEntity | Contains `is_deleted` column | `@SQLRestriction("is_deleted = false")` Present? | Risk Assessment |
|---|---|---|---|---|
| `User` | **YES** | **YES** | **YES** | Consistent. |
| `RefreshToken` | **YES** | **YES** | **YES** | Consistent. |
| `AvailabilitySlot` | **YES** | **YES** | **NO** (Missing) | Soft-deleted availability slots remain visible to standard repository queries! |
| `AiConversation` | **YES** | **YES** | **NO** (Missing) | Soft-deleted AI conversations loaded by `findAll` / `findByUserId`. |
| `AiMessage` | **YES** | **YES** | **NO** (Missing) | Soft-deleted messages returned in chat history. |
| `AiToolInvocation` | **YES** | **YES** | **NO** (Missing) | Tool invocations for deleted conversations materialized. |
| `AiSearchContext` | **YES** | **YES** | **NO** (Missing) | Contexts for deleted conversations retained. |
| `AiUserMemory` | **YES** | **YES** | **NO** (Missing) | Memories marked deleted still injected into prompt context. |
| `Booking` | **YES** | **YES** | **YES** | Consistent. |
| `BookingStatusHistory` | **YES** | **YES** | **YES** | Consistent. |
| `Property` | **YES** | **YES** | **YES** | Consistent. |
| `Review` | **YES** | **YES** | **YES** | Consistent. |
| `Wishlist` | **YES** | **YES** | **YES** | Consistent. |
| `WishlistItem` | **YES** | **YES** | **YES** | Consistent. |
| `Notification` | **YES** | **YES** | **YES** | Consistent. |
| `UserNotificationPreference` | **YES** | **YES** | **YES** | Consistent. |
| `UserAlert` | **YES** | **YES** | **YES** | Consistent. |
| `CleaningTask` | **YES** | **YES** | **YES** | Consistent. |
| `CleaningChecklist` | **YES** | **YES** | **YES** | Consistent. |
| `CleaningChecklistItem` | **YES** | **YES** | **YES** | Consistent. |
| `CleaningPhoto` | **YES** | **YES** | **YES** | Consistent. |
| `CleaningIssue` | **YES** | **YES** | **YES** | Consistent. |
| `Message` | **YES** | **YES** | **YES** | Consistent. |
| `MessageReadReceipt` | **YES** | **YES** | **YES** | Consistent. |
| `Conversation` | **YES** | **YES** | **YES** | Consistent. |
| `ConversationParticipant` | **YES** | **YES** | **YES** | Consistent. |

### Architectural Recommendation:
Define `@SQLRestriction("is_deleted = false")` on `BaseEntity` itself so that all subclasses automatically inherit soft-delete filtering by default, eliminating manual annotation omissions across future entities.

---

## 9. Conclusion

The Aggarly User Module persistence layer demonstrates solid foundational alignment with Spring Data JPA and Flyway migration patterns. The architectural rule against `CascadeType.ALL` on parent `@OneToMany` collections is strictly observed, and schema column definitions closely track migration history from `V2` through `V37`.

However, the eight discrepancies identified—most urgently the missing `AuthProvider.SYSTEM` enum constant and the database unique constraint collision on soft-deleted users—require targeted remediation to avoid runtime exceptions and guarantee schema-level data integrity before production deployment.
