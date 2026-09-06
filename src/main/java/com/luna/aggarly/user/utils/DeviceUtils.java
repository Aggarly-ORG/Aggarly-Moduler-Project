package com.luna.aggarly.user.utils;

import jakarta.servlet.http.HttpServletRequest;

public final class DeviceUtils {

    private DeviceUtils() {}

    /**
     * Parse User-Agent into a clean, human-readable device/browser name.
     * e.g. "Chrome on Windows 10/11", "Safari on iPhone", "Firefox on macOS"
     */
    public static String parseDeviceName(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Unknown Device";
        }

        String os = "Unknown OS";
        if (userAgent.contains("iPhone")) {
            os = "iPhone";
        } else if (userAgent.contains("iPad")) {
            os = "iPad";
        } else if (userAgent.contains("Android")) {
            os = "Android";
        } else if (userAgent.contains("Windows NT 10.0")) {
            os = "Windows 10/11";
        } else if (userAgent.contains("Windows NT")) {
            os = "Windows";
        } else if (userAgent.contains("Macintosh") || userAgent.contains("Mac OS X")) {
            os = "macOS";
        } else if (userAgent.contains("Linux")) {
            os = "Linux";
        }

        String browser = "Browser";
        if (userAgent.contains("Edg/") || userAgent.contains("Edge/")) {
            browser = "Edge";
        } else if (userAgent.contains("Chrome/") && !userAgent.contains("Edg/")) {
            browser = "Chrome";
        } else if (userAgent.contains("Safari/") && !userAgent.contains("Chrome/")) {
            browser = "Safari";
        } else if (userAgent.contains("Firefox/")) {
            browser = "Firefox";
        } else if (userAgent.contains("OPR/") || userAgent.contains("Opera/")) {
            browser = "Opera";
        }

        return browser + " on " + os;
    }

    /**
     * Extract real client IP taking into account common reverse proxies and load balancers.
     */
    public static String extractClientIp(HttpServletRequest request) {
        if (request == null) {
            return "Unknown IP";
        }

        String[] headers = {
                "X-Forwarded-For",
                "CF-Connecting-IP",
                "X-Real-IP",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP"
        };

        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip.trim())) {
                // If comma-separated, the first IP is the original client
                if (ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                return normalizeIp(ip);
            }
        }

        return normalizeIp(request.getRemoteAddr());
    }

    /**
     * Resolve coarse location description based on IP.
     */
    public static String resolveLocation(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank() || "Unknown IP".equalsIgnoreCase(ipAddress)) {
            return "Unknown Location";
        }

        if ("127.0.0.1".equals(ipAddress) || "localhost".equalsIgnoreCase(ipAddress)) {
            return "Localhost / Dev";
        }

        if (ipAddress.startsWith("192.168.") || ipAddress.startsWith("10.") || ipAddress.startsWith("172.")) {
            return "Private Network";
        }

        return "Unknown Location";
    }

    private static String normalizeIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return "Unknown IP";
        }
        String cleanIp = ip.trim();
        if ("0:0:0:0:0:0:0:1".equals(cleanIp) || "::1".equals(cleanIp)) {
            return "127.0.0.1";
        }
        return cleanIp;
    }
}
