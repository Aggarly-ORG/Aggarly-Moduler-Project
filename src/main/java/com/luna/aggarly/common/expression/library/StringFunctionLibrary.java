package com.luna.aggarly.common.expression.library;

import com.luna.aggarly.common.expression.annotation.ExpressionFunction;
import com.luna.aggarly.common.expression.annotation.ExpressionFunctionLibrary;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
@ExpressionFunctionLibrary(value = "string", prefix = "StringUtils")
public class StringFunctionLibrary {

    @ExpressionFunction(value = "upper", description = "Converts string to uppercase")
    public String upper(Object input) {
        return input != null ? String.valueOf(input).toUpperCase() : "";
    }

    @ExpressionFunction(value = "lower", description = "Converts string to lowercase")
    public String lower(Object input) {
        return input != null ? String.valueOf(input).toLowerCase() : "";
    }

    @ExpressionFunction(value = "trim", description = "Strips leading and trailing whitespace")
    public String trim(Object input) {
        return input != null ? String.valueOf(input).trim() : "";
    }

    @ExpressionFunction(value = "capitalize", description = "Capitalizes the first character of each word")
    public String capitalize(String input) {
        if (input == null || input.isBlank()) return "";
        return Arrays.stream(input.split("\\s+"))
                .map(word -> word.isEmpty() ? word : Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }

    @ExpressionFunction(value = "concat", description = "Concatenates all arguments into a single string")
    public String concat(Object... items) {
        if (items == null) return "";
        return Arrays.stream(items)
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .collect(Collectors.joining());
    }

    @ExpressionFunction(value = "substring", description = "Extracts substring from start to optional end index")
    public String substring(String str, int start, int end) {
        if (str == null) return "";
        int len = str.length();
        int s = Math.max(0, Math.min(start, len));
        int e = (end <= 0 || end > len) ? len : end;
        return (s <= e) ? str.substring(s, e) : "";
    }

    @ExpressionFunction(value = "replace", description = "Replaces all occurrences of target with replacement")
    public String replace(String str, String target, String replacement) {
        if (str == null) return "";
        return str.replace(target != null ? target : "", replacement != null ? replacement : "");
    }

    @ExpressionFunction(value = "split", description = "Splits string by delimiter into a list")
    public List<String> split(String str, String delimiter) {
        if (str == null || str.isBlank()) return List.of();
        String regex = (delimiter != null && !delimiter.isEmpty()) ? java.util.regex.Pattern.quote(delimiter) : "\\s+";
        return Arrays.asList(str.split(regex));
    }

    @ExpressionFunction(value = "length", description = "Returns length of string")
    public int length(Object input) {
        return input != null ? String.valueOf(input).length() : 0;
    }

    @ExpressionFunction(value = "contains", description = "Checks if string contains substring (case-insensitive)")
    public boolean contains(String str, String search) {
        if (str == null || search == null) return false;
        return str.toLowerCase().contains(search.toLowerCase());
    }

    @ExpressionFunction(value = "starts_with", description = "Checks if string starts with prefix")
    public boolean startsWith(String str, String prefix) {
        if (str == null || prefix == null) return false;
        return str.startsWith(prefix);
    }

    @ExpressionFunction(value = "ends_with", description = "Checks if string ends with suffix")
    public boolean endsWith(String str, String suffix) {
        if (str == null || suffix == null) return false;
        return str.endsWith(suffix);
    }

    @ExpressionFunction(value = "pad_left", description = "Pads string on the left to target length")
    public String padLeft(String str, int length, String padChar) {
        String base = (str != null) ? str : "";
        char c = (padChar != null && !padChar.isEmpty()) ? padChar.charAt(0) : ' ';
        if (base.length() >= length) return base;
        return String.valueOf(c).repeat(length - base.length()) + base;
    }

    @ExpressionFunction(value = "pad_right", description = "Pads string on the right to target length")
    public String padRight(String str, int length, String padChar) {
        String base = (str != null) ? str : "";
        char c = (padChar != null && !padChar.isEmpty()) ? padChar.charAt(0) : ' ';
        if (base.length() >= length) return base;
        return base + String.valueOf(c).repeat(length - base.length());
    }

    @ExpressionFunction(value = "slugify", description = "Converts string to a URL-friendly slug")
    public String slugify(String input) {
        if (input == null || input.isBlank()) return "";
        return input.trim().toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-");
    }

    @ExpressionFunction(value = "regex_replace", description = "Replaces regex pattern in string")
    public String regexReplace(String str, String regex, String replacement) {
        if (str == null || regex == null) return str != null ? str : "";
        return str.replaceAll(regex, replacement != null ? replacement : "");
    }

    @ExpressionFunction(value = "default_if_blank", description = "Returns default value if input is null or blank")
    public String defaultIfBlank(String str, String defaultValue) {
        return (str != null && !str.isBlank()) ? str : defaultValue;
    }
}
