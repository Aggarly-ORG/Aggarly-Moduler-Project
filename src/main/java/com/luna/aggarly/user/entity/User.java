package com.luna.aggarly.user.entity;

import com.luna.aggarly.common.entity.BaseEntity;
import com.luna.aggarly.user.entity.enums.AuthProvider;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.util.HashSet;
import java.util.Set;

/**
 * Entity representing a platform User (Guest/Host/Admin).
 * Extends BaseEntity to inherit auditing and soft delete attributes.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLRestriction("is_deleted = false")
public class User extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    @Column(length = 30)
    private String phone;

    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;

    @Column(length = 500)
    private String bio;

    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private boolean emailVerified = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false, length = 20)
    @Builder.Default
    private AuthProvider authProvider = AuthProvider.LOCAL;

    @Column(name = "totp_secret", length = 32)
    private String totpSecret;

    @Column(name = "mfa_enabled", nullable = false)
    @Builder.Default
    private boolean mfaEnabled = false;

    @Column(name = "phone_verified", nullable = false)
    @Builder.Default
    private boolean phoneVerified = false;

    @Column(name = "identity_verified", nullable = false)
    @Builder.Default
    private boolean identityVerified = false;

    @Column(name = "status", length = 32)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "kyc_status", length = 32)
    @Builder.Default
    private String kycStatus = "UNVERIFIED";

    @Column(name = "kyc_tier", length = 16)
    @Builder.Default
    private String kycTier = "TIER_I";

    @Column(name = "kyc_verified_at")
    private java.time.Instant kycVerifiedAt;

    @Column(name = "trust_score")
    @Builder.Default
    private Integer trustScore = 85;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "user_roles",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Set<Role> getRoles() { return roles; }
    public String getUsername() { return username; }
}