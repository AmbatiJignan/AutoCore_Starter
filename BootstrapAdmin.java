
package com.autocore.util;

import com.autocore.dao.UserDAO;

public class BootstrapAdmin {

    public static void main(String[] args) {
        String username = System.getenv("AUTOCORE_ADMIN_USERNAME");
        String fullName = System.getenv("AUTOCORE_ADMIN_NAME");
        String password = System.getenv("AUTOCORE_ADMIN_PASSWORD");

        if (isBlank(username) || isBlank(fullName) || isBlank(password)) {
            System.err.println(
                "Set AUTOCORE_ADMIN_USERNAME, AUTOCORE_ADMIN_NAME, " +
                "and AUTOCORE_ADMIN_PASSWORD in the run configuration."
            );
            return;
        }

        if (password.length() < 12) {
            System.err.println("Use an initial password of at least 12 characters.");
            return;
        }

        if (!username.matches("[A-Za-z0-9._-]{3,50}")) {
            System.err.println("Username must be 3-50 valid characters.");
            return;
        }

        try {
            UserDAO dao = new UserDAO();

            if (dao.hasAdmin()) {
                System.out.println(
                    "An administrator already exists. Bootstrap refused."
                );
                return;
            }

            boolean created = dao.createUser(
                username,
                password,
                fullName,
                "ADMIN"
            );

            System.out.println(created
                ? "Initial administrator created successfully."
                : "Administrator was not created.");

        } catch (Exception e) {
            System.err.println("Bootstrap failed: " + e.getMessage());
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
