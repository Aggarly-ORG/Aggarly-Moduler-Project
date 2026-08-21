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
        if(value==null || value.isBlank())
            return false;
        try {
            Phonenumber.PhoneNumber phoneNumber = phoneNumberUtil.parse(value, null);
            return phoneNumberUtil.isValidNumber(phoneNumber);
        }
        catch (NumberParseException ex) {
            return false;
        }
    }
}
