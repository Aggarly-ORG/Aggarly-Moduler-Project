package com.luna.aggarly.user.utils;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PhoneValidator implements ConstraintValidator<ValidPhone,String> {
    private final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String cleaned = value.trim();
        try {
            Phonenumber.PhoneNumber phoneNumber = phoneNumberUtil.parse(
                    cleaned.startsWith("+") ? cleaned : "+" + cleaned, null
            );
            if (phoneNumberUtil.isValidNumber(phoneNumber)) {
                return true;
            }
        } catch (NumberParseException ignored) {}

        try {
            Phonenumber.PhoneNumber phoneNumber = phoneNumberUtil.parse(cleaned, "EG");
            if (phoneNumberUtil.isValidNumber(phoneNumber)) {
                return true;
            }
        } catch (NumberParseException ignored) {}

        return false;
    }
}
