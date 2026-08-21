package com.luna.aggarly.user.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneValidatorTest {

    private PhoneValidator phoneValidator;

    @BeforeEach
    void setUp() {
        phoneValidator = new PhoneValidator();
    }

    @Test
    @DisplayName("Should return false when phone number is null or blank")
    void shouldReturnFalseWhenPhoneIsNullOrBlank() {
        assertThat(phoneValidator.isValid(null, null)).isFalse();
        assertThat(phoneValidator.isValid("", null)).isFalse();
        assertThat(phoneValidator.isValid("   ", null)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "+12025550123",
            "+442071838750",
            "+201001234567"
    })
    @DisplayName("Should return true for valid E.164 international phone numbers")
    void shouldReturnTrueForValidInternationalPhoneNumbers(String validPhone) {
        assertThat(phoneValidator.isValid(validPhone, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "12345",
            "not-a-phone-number",
            "00000000000000000000"
    })
    @DisplayName("Should return false for invalid phone numbers")
    void shouldReturnFalseForInvalidPhoneNumbers(String invalidPhone) {
        assertThat(phoneValidator.isValid(invalidPhone, null)).isFalse();
    }
}
