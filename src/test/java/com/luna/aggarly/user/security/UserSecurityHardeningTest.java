package com.luna.aggarly.user.security;

import com.luna.aggarly.user.dto.SavePaymentMethodRequest;
import com.luna.aggarly.user.dto.request.ConfirmMfaRequest;
import com.luna.aggarly.user.entity.Role;
import com.luna.aggarly.user.entity.User;
import com.luna.aggarly.user.entity.enums.AuthProvider;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserSecurityHardeningTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("Role equals and hashCode should prevent duplicate roles in HashSet")
    void role_EqualsAndHashCode_ShouldPreventDuplicatesInSet() {
        Role role1 = Role.builder().id(1L).name("HOST").build();
        Role role2 = Role.builder().id(2L).name("HOST").build();

        Set<Role> roles = new HashSet<>();
        roles.add(role1);
        roles.add(role2);

        assertThat(roles).hasSize(1);
        assertThat(roles.contains(Role.builder().name("HOST").build())).isTrue();
    }

    @Test
    @DisplayName("AuthProvider enum should include SYSTEM for AI Concierge bot user")
    void authProvider_ShouldIncludeSystem() {
        AuthProvider systemProvider = AuthProvider.valueOf("SYSTEM");
        assertThat(systemProvider).isEqualTo(AuthProvider.SYSTEM);
    }

    @Test
    @DisplayName("UserPrincipal isEnabled should return false for soft-deleted user")
    void userPrincipal_IsEnabled_ShouldReflectSoftDeletedStatus() {
        User activeUser = User.builder().email("active@example.com").build();
        activeUser.setDeleted(false);
        UserPrincipal activePrincipal = new UserPrincipal(activeUser);
        assertThat(activePrincipal.isEnabled()).isTrue();

        User deletedUser = User.builder().email("deleted@example.com").build();
        deletedUser.setDeleted(true);
        UserPrincipal deletedPrincipal = new UserPrincipal(deletedUser);
        assertThat(deletedPrincipal.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("ConfirmMfaRequest should validate token not blank and totpCode is 6 digits")
    void confirmMfaRequest_Validation() {
        ConfirmMfaRequest invalidRequest = new ConfirmMfaRequest("", "123");
        Set<ConstraintViolation<ConfirmMfaRequest>> violations = validator.validate(invalidRequest);
        assertThat(violations).hasSize(2);

        ConfirmMfaRequest validRequest = new ConfirmMfaRequest("valid-token", "123456");
        Set<ConstraintViolation<ConfirmMfaRequest>> validViolations = validator.validate(validRequest);
        assertThat(validViolations).isEmpty();
    }

    @Test
    @DisplayName("SavePaymentMethodRequest should validate expiration month, year, and 4-digit lastFour")
    void savePaymentMethodRequest_Validation() {
        SavePaymentMethodRequest invalid = SavePaymentMethodRequest.builder()
                .stripePaymentMethodId("pm_123")
                .cardBrand("Visa")
                .lastFour("12") // Invalid: not 4 digits
                .expMonth(13)   // Invalid: > 12
                .expYear(2020)  // Invalid: < 2024
                .build();

        Set<ConstraintViolation<SavePaymentMethodRequest>> violations = validator.validate(invalid);
        assertThat(violations).hasSize(3);

        SavePaymentMethodRequest valid = SavePaymentMethodRequest.builder()
                .stripePaymentMethodId("pm_123")
                .cardBrand("Visa")
                .lastFour("4242")
                .expMonth(12)
                .expYear(2028)
                .build();

        Set<ConstraintViolation<SavePaymentMethodRequest>> validViolations = validator.validate(valid);
        assertThat(validViolations).isEmpty();
    }
}
