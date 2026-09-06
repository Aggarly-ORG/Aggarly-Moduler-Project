package com.luna.aggarly.common.expression.library;

import com.luna.aggarly.common.expression.annotation.ExpressionFunction;
import com.luna.aggarly.common.expression.annotation.ExpressionFunctionLibrary;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

@Component
@ExpressionFunctionLibrary(value = "security", prefix = "SecurityUtils")
public class SecurityUtilityFunctionLibrary {

    @ExpressionFunction(value = "uuid", description = "Generates a new random UUID v4")
    public String uuid() {
        return UUID.randomUUID().toString();
    }

    @ExpressionFunction(value = "mask_email", description = "Masks email address for privacy (e.g. j***e@example.com)")
    public String maskEmail(String email) {
        if (email == null || !email.contains("@")) return email != null ? email : "";
        int atIndex = email.indexOf('@');
        String name = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        if (name.length() <= 2) {
            return name.charAt(0) + "***" + domain;
        }
        return name.charAt(0) + "***" + name.charAt(name.length() - 1) + domain;
    }

    @ExpressionFunction(value = "mask_phone", description = "Masks phone number (e.g. ***-***-1234)")
    public String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) return "****";
        String last4 = phone.substring(phone.length() - 4);
        return "***-***-" + last4;
    }

    @ExpressionFunction(value = "base64_encode", description = "Encodes string to Base64")
    public String base64Encode(String input) {
        if (input == null) return "";
        return Base64.getEncoder().encodeToString(input.getBytes(StandardCharsets.UTF_8));
    }

    @ExpressionFunction(value = "base64_decode", description = "Decodes Base64 string")
    public String base64Decode(String input) {
        if (input == null) return "";
        try {
            return new String(Base64.getDecoder().decode(input), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return input;
        }
    }

    @ExpressionFunction(value = "hash_sha256", description = "Computes SHA-256 hex digest of string")
    public String hashSha256(String input) {
        if (input == null) return "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception ex) {
            return "";
        }
    }

    @ExpressionFunction(value = "is_null", description = "Returns true if value is null or string 'null'")
    public boolean isNull(Object val) {
        return val == null || "null".equalsIgnoreCase(String.valueOf(val).trim());
    }

    @ExpressionFunction(value = "is_not_null", description = "Returns true if value is not null")
    public boolean isNotNull(Object val) {
        return !isNull(val);
    }

    @ExpressionFunction(value = "coalesce", description = "Returns first non-null, non-blank value")
    public Object coalesce(Object... items) {
        if (items == null) return null;
        for (Object item : items) {
            if (item != null && !(item instanceof String s && s.isBlank()) && !"null".equalsIgnoreCase(String.valueOf(item))) {
                return item;
            }
        }
        return null;
    }

    @ExpressionFunction(value = "ternary", description = "Conditional expression: condition ? ifTrue : ifFalse")
    public Object ternary(Object condition, Object ifTrue, Object ifFalse) {
        boolean cond = isTruthy(condition);
        return cond ? ifTrue : ifFalse;
    }

    private boolean isTruthy(Object condition) {
        if (condition == null) return false;
        if (condition instanceof Boolean b) return b;
        if (condition instanceof Number num) return num.doubleValue() != 0.0;
        String s = String.valueOf(condition).trim().toLowerCase();
        return "true".equals(s) || "1".equals(s) || "yes".equals(s);
    }
}
