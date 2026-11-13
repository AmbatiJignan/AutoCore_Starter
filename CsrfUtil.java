package com.autocore.util;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.security.MessageDigest;
import java.security.SecureRandom;

public final class CsrfUtil {

    public static final String CSRF_PARAM_NAME = "_csrf";
    public static final String CSRF_HEADER_NAME = "X-CSRF-Token";
    public static final String CSRF_SESSION_ATTR = "_autocore_csrf_token";

    private static final SecureRandom RANDOM = new SecureRandom();

    private CsrfUtil() {}

    /**
     * Gets the current CSRF token from the session, creating one if not present.
     */
    public static String getToken(HttpSession session) {
        if (session == null) {
            return "";
        }
        synchronized (session) {
            String token = (String) session.getAttribute(CSRF_SESSION_ATTR);
            if (token == null || token.trim().isEmpty()) {
                token = generateRandomToken();
                session.setAttribute(CSRF_SESSION_ATTR, token);
            }
            return token;
        }
    }

    /**
     * Refreshes the CSRF token stored in the session.
     */
    public static String refreshToken(HttpSession session) {
        if (session == null) {
            return "";
        }
        synchronized (session) {
            String token = generateRandomToken();
            session.setAttribute(CSRF_SESSION_ATTR, token);
            return token;
        }
    }

    /**
     * Validates that the request contains a CSRF token matching the session token.
     */
    public static boolean isValid(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        HttpSession session = request.getSession(false);
        if (session == null) {
            return false;
        }
        String sessionToken = (String) session.getAttribute(CSRF_SESSION_ATTR);
        if (sessionToken == null || sessionToken.isEmpty()) {
            return false;
        }

        String requestToken = request.getParameter(CSRF_PARAM_NAME);
        if (requestToken == null || requestToken.isEmpty()) {
            requestToken = request.getHeader(CSRF_HEADER_NAME);
        }

        if (requestToken == null || requestToken.isEmpty()) {
            return false;
        }

        return MessageDigest.isEqual(
            sessionToken.getBytes(java.nio.charset.StandardCharsets.UTF_8),
            requestToken.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
    }

    private static String generateRandomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(64);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
