package com.autocore.controller;

import com.autocore.dao.UserDAO;
import com.autocore.model.User;
import com.autocore.util.CsrfUtil;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

@WebServlet("/users")
public class UserServlet extends HttpServlet {

    private UserDAO userDAO;

    @Override
    public void init() {
        this.userDAO = new UserDAO();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {

        User currentUser = getCurrentUser(req);
        if (currentUser == null || !currentUser.isAdmin()) {
            res.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied.");
            return;
        }

        try {
            List<User> userList = userDAO.findAll();
            req.setAttribute("users", userList);
            req.setAttribute("activeAdminCount", userDAO.countActiveAdmins());
            req.getRequestDispatcher("/WEB-INF/views/users.jsp").forward(req, res);
        } catch (SQLException e) {
            throw new ServletException("Failed to retrieve user list from Oracle database.", e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {

        req.setCharacterEncoding("UTF-8");

        User currentUser = getCurrentUser(req);
        if (currentUser == null || !currentUser.isAdmin()) {
            res.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied.");
            return;
        }

        // Validate CSRF token on all user management modifications
        if (!CsrfUtil.isValid(req)) {
            res.sendRedirect(req.getContextPath() + "/users?error=csrf");
            return;
        }

        String action = req.getParameter("action");
        if (action == null) {
            res.sendRedirect(req.getContextPath() + "/users");
            return;
        }

        try {
            switch (action) {
                case "create":
                    handleCreateUser(req, res);
                    break;
                case "toggle-status":
                    handleToggleStatus(req, res);
                    break;
                case "change-role":
                    handleChangeRole(req, res);
                    break;
                case "reset-password":
                    handleResetPassword(req, res);
                    break;
                default:
                    res.sendRedirect(req.getContextPath() + "/users");
            }
        } catch (IllegalArgumentException e) {
            req.getSession().setAttribute("userActionError", e.getMessage());
            res.sendRedirect(req.getContextPath() + "/users");
        } catch (SQLException e) {
            throw new ServletException("Database error during user management operation.", e);
        }
    }

    private void handleCreateUser(HttpServletRequest req, HttpServletResponse res)
            throws SQLException, IOException {

        String username = req.getParameter("username");
        String fullName = req.getParameter("fullName");
        String password = req.getParameter("password");
        String role = req.getParameter("role");

        if (username == null || !username.trim().matches("^[A-Za-z0-9._-]{3,50}$")) {
            throw new IllegalArgumentException("Username must be between 3 and 50 alphanumeric characters (._- allowed).");
        }
        if (fullName == null || fullName.trim().isEmpty() || fullName.trim().length() > 100) {
            throw new IllegalArgumentException("Full name is required (max 100 characters).");
        }
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters in length.");
        }
        if (!"ADMIN".equalsIgnoreCase(role) && !"STAFF".equalsIgnoreCase(role)) {
            role = "STAFF";
        }

        // Check if username is already taken
        if (userDAO.findByUsername(username.trim()) != null) {
            throw new IllegalArgumentException("The username '" + username.trim() + "' is already in use.");
        }

        userDAO.createUser(username.trim(), password, fullName.trim(), role.toUpperCase());
        res.sendRedirect(req.getContextPath() + "/users?message=created");
    }

    private void handleToggleStatus(HttpServletRequest req, HttpServletResponse res)
            throws SQLException, IOException {

        int userId = parsePositiveId(req.getParameter("userId"));
        User target = userDAO.findById(userId);
        if (target == null) {
            throw new IllegalArgumentException("User record not found.");
        }

        // Guard: Prevent deactivating the last active administrator
        if (target.isAdmin() && target.isActiveUser()) {
            int activeAdmins = userDAO.countActiveAdmins();
            if (activeAdmins <= 1) {
                throw new IllegalArgumentException("Cannot deactivate the only remaining active administrator account.");
            }
        }

        String newStatus = target.isActiveUser() ? "N" : "Y";
        userDAO.updateStatus(userId, newStatus);
        res.sendRedirect(req.getContextPath() + "/users?message=status_updated");
    }

    private void handleChangeRole(HttpServletRequest req, HttpServletResponse res)
            throws SQLException, IOException {

        int userId = parsePositiveId(req.getParameter("userId"));
        String newRole = req.getParameter("role");

        if (!"ADMIN".equalsIgnoreCase(newRole) && !"STAFF".equalsIgnoreCase(newRole)) {
            throw new IllegalArgumentException("Invalid role selected.");
        }

        User target = userDAO.findById(userId);
        if (target == null) {
            throw new IllegalArgumentException("User record not found.");
        }

        // Guard: Prevent demoting the last active administrator to STAFF
        if (target.isAdmin() && target.isActiveUser() && "STAFF".equalsIgnoreCase(newRole)) {
            int activeAdmins = userDAO.countActiveAdmins();
            if (activeAdmins <= 1) {
                throw new IllegalArgumentException("Cannot change role: System requires at least one active administrator.");
            }
        }

        userDAO.updateRole(userId, newRole.toUpperCase());
        res.sendRedirect(req.getContextPath() + "/users?message=role_updated");
    }

    private void handleResetPassword(HttpServletRequest req, HttpServletResponse res)
            throws SQLException, IOException {

        int userId = parsePositiveId(req.getParameter("userId"));
        String newPassword = req.getParameter("newPassword");

        if (newPassword == null || newPassword.length() < 8) {
            throw new IllegalArgumentException("New password must be at least 8 characters.");
        }

        User target = userDAO.findById(userId);
        if (target == null) {
            throw new IllegalArgumentException("User record not found.");
        }

        userDAO.updatePassword(userId, newPassword);
        res.sendRedirect(req.getContextPath() + "/users?message=password_reset");
    }

    private int parsePositiveId(String val) {
        try {
            int id = Integer.parseInt(val);
            if (id < 1) throw new IllegalArgumentException("Invalid ID");
            return id;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid user identifier provided.");
        }
    }

    private User getCurrentUser(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        return (s != null) ? (User) s.getAttribute("currentUser") : null;
    }
}
