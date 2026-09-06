package com.luna.aggarly.common.expression;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Tokenizer {

    public static List<String> splitArguments(String argsStr) {
        List<String> args = new ArrayList<>();
        if (argsStr == null || argsStr.isBlank()) {
            return args;
        }

        StringBuilder current = new StringBuilder();
        int parenDepth = 0;
        int bracketDepth = 0;
        int curlyDepth = 0;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean isEscaped = false;

        for (int i = 0; i < argsStr.length(); i++) {
            char c = argsStr.charAt(i);

            if (isEscaped) {
                current.append(c);
                isEscaped = false;
                continue;
            }

            if (c == '\\') {
                isEscaped = true;
                current.append(c);
                continue;
            }

            if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
                current.append(c);
            } else if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
                current.append(c);
            } else if (!inSingleQuote && !inDoubleQuote) {
                if (c == '(') {
                    parenDepth++;
                    current.append(c);
                } else if (c == ')') {
                    parenDepth--;
                    current.append(c);
                } else if (c == '[') {
                    bracketDepth++;
                    current.append(c);
                } else if (c == ']') {
                    bracketDepth--;
                    current.append(c);
                } else if (c == '{') {
                    curlyDepth++;
                    current.append(c);
                } else if (c == '}') {
                    curlyDepth--;
                    current.append(c);
                } else if (c == ',' && parenDepth == 0 && bracketDepth == 0 && curlyDepth == 0) {
                    args.add(current.toString().trim());
                    current.setLength(0);
                } else {
                    current.append(c);
                }
            } else {
                current.append(c);
            }
        }

        if (current.length() > 0) {
            String lastArg = current.toString().trim();
            if (!lastArg.isEmpty()) {
                args.add(lastArg);
            }
        }

        return args;
    }

    public static String unquote(String str) {
        if (str == null) {
            return null;
        }
        String trimmed = str.trim();
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) ||
            (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            if (trimmed.length() >= 2) {
                String inner = trimmed.substring(1, trimmed.length() - 1);
                return inner.replace("\\\"", "\"").replace("\\'", "'").replace("\\\\", "\\");
            }
        }
        return str;
    }

    public static boolean isQuoted(String str) {
        if (str == null) return false;
        String trimmed = str.trim();
        return (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) ||
               (trimmed.startsWith("'") && trimmed.endsWith("'") && trimmed.length() >= 2);
    }
}
