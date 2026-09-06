package com.luna.aggarly.common.expression.library;

import com.luna.aggarly.common.expression.annotation.ExpressionFunction;
import com.luna.aggarly.common.expression.annotation.ExpressionFunctionLibrary;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

@Component
@ExpressionFunctionLibrary(value = "math", prefix = "MathUtils")
public class MathFunctionLibrary {

    @ExpressionFunction(value = "round", description = "Rounds number to specified decimal places")
    public double round(double value, int decimals) {
        return BigDecimal.valueOf(value).setScale(Math.max(0, decimals), RoundingMode.HALF_UP).doubleValue();
    }

    @ExpressionFunction(value = "ceil", description = "Rounds number up to nearest integer")
    public double ceil(double value) {
        return Math.ceil(value);
    }

    @ExpressionFunction(value = "floor", description = "Rounds number down to nearest integer")
    public double floor(double value) {
        return Math.floor(value);
    }

    @ExpressionFunction(value = "abs", description = "Returns absolute value")
    public double abs(double value) {
        return Math.abs(value);
    }

    @ExpressionFunction(value = "min", description = "Returns minimum of arguments or list")
    public double min(Object... values) {
        if (values == null || values.length == 0) return 0.0;
        return Arrays.stream(values)
                .map(this::toDouble)
                .filter(Objects::nonNull)
                .min(Double::compare)
                .orElse(0.0);
    }

    @ExpressionFunction(value = "max", description = "Returns maximum of arguments or list")
    public double max(Object... values) {
        if (values == null || values.length == 0) return 0.0;
        return Arrays.stream(values)
                .map(this::toDouble)
                .filter(Objects::nonNull)
                .max(Double::compare)
                .orElse(0.0);
    }

    @ExpressionFunction(value = "sum", description = "Sums all numbers in collection or arguments")
    public double sum(Object values) {
        if (values == null) return 0.0;
        if (values instanceof Collection<?> col) {
            return col.stream().map(this::toDouble).filter(Objects::nonNull).mapToDouble(Double::doubleValue).sum();
        }
        if (values instanceof Object[] arr) {
            return Arrays.stream(arr).map(this::toDouble).filter(Objects::nonNull).mapToDouble(Double::doubleValue).sum();
        }
        Double d = toDouble(values);
        return d != null ? d : 0.0;
    }

    @ExpressionFunction(value = "avg", description = "Computes arithmetic average of numbers")
    public double avg(Object values) {
        if (values == null) return 0.0;
        if (values instanceof Collection<?> col) {
            return col.stream().map(this::toDouble).filter(Objects::nonNull).mapToDouble(Double::doubleValue).average().orElse(0.0);
        }
        if (values instanceof Object[] arr) {
            return Arrays.stream(arr).map(this::toDouble).filter(Objects::nonNull).mapToDouble(Double::doubleValue).average().orElse(0.0);
        }
        return toDouble(values) != null ? toDouble(values) : 0.0;
    }

    @ExpressionFunction(value = "clamp", description = "Clamps number between min and max bounds")
    public double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    @ExpressionFunction(value = "pow", description = "Calculates base raised to exponent")
    public double pow(double base, double exp) {
        return Math.pow(base, exp);
    }

    @ExpressionFunction(value = "percentage", description = "Calculates percentage (value / total * 100)")
    public double percentage(double value, double total) {
        if (total == 0.0) return 0.0;
        return round((value / total) * 100.0, 2);
    }

    @ExpressionFunction(value = "format_number", description = "Formats number with pattern (e.g. '#,##0.00')")
    public String formatNumber(double value, String pattern) {
        try {
            DecimalFormat df = new DecimalFormat((pattern != null && !pattern.isBlank()) ? pattern : "#,##0.00");
            return df.format(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }

    @ExpressionFunction(value = "format_currency", description = "Formats amount with currency symbol or code (e.g. '$1,250.00 USD')")
    public String formatCurrency(double amount, String currency) {
        String curr = (currency != null && !currency.isBlank()) ? currency.toUpperCase() : "USD";
        String symbol = switch (curr) {
            case "EUR" -> "€";
            case "GBP" -> "£";
            case "EGP" -> "EGP ";
            case "SAR", "AED" -> curr + " ";
            default -> "$";
        };
        DecimalFormat df = new DecimalFormat("#,##0.00");
        return symbol + df.format(amount);
    }

    private Double toDouble(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number num) return num.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(obj).trim());
        } catch (Exception ex) {
            return null;
        }
    }
}
