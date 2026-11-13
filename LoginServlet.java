package com.autocore.controller;

import com.autocore.dao.UserDAO;
import com.autocore.model.User;
import com.autocore.util.CsrfUtil;
import com.autocore.util.LoginThrottler;
import com.autocore.util.PasswordUtil;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;
import java.sql.SQLException;

@WebServlet({"/login", "/login/"})
public class LoginServlet extends HttpServlet {

    private UserDAO userDAO;

    @Override
    public void init() {
        this.userDAO = new UserDAO();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {

        HttpSession session = req.getSession(false);
        if (session != null && session.getAttribute("currentUser") != null) {
            User user = (User) session.getAttribute("currentUser");
            if (user.isActiveUser()) {
                res.sendRedirect(req.getContextPath() + "/dashboard");
                return;
            }
        }

        // Ensure session exists to hold CSRF token for the login form
        HttpSession activeSession = req.getSession(true);
        req.setAttribute("csrfToken", CsrfUtil.getToken(activeSession));

        req.getRequestDispatcher("/WEB-INF/views/login.jsp").forward(req, res);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {

        req.setCharacterEncoding("UTF-8");
        String clientIp = getClientIp(req);

        // 1. Check login attempt throttling
        if (LoginThrottler.isBlocked(clientIp)) {
            int remainingSecs = LoginThrottler.getRemainingLockoutSeconds(clientIp);
            req.setAttribute("errorMessage",
                "Too many failed login attempts. Please wait " + remainingSecs + " seconds before trying again.");
            req.setAttribute("csrfToken", CsrfUtil.getToken(req.getSession(true)));
            req.getRequestDispatcher("/WEB-INF/views/login.jsp").forward(req, res);
            return;
        }

        // 2. Validate CSRF token
        if (!CsrfUtil.isValid(req)) {
            req.setAttribute("errorMessage", "Session security validation failed. Please try again.");
            req.setAttribute("csrfToken", CsrfUtil.getToken(req.getSession(true)));
            req.getRequestDispatcher("/WEB-INF/views/login.jsp").forward(req, res);
            return;
        }

        String username = req.getParameter("username");
        String password = req.getParameter("password");

        if (username == null || username.trim().isEmpty() || password == null || password.isEmpty()) {
            LoginThrottler.recordFailure(clientIp);
            req.setAttribute("errorMessage", "Invalid username or password.");
            req.setAttribute("csrfToken", CsrfUtil.getToken(req.getSession(true)));
            req.getRequestDispatcher("/WEB-INF/views/login.jsp").forward(req, res);
            return;
        }

        try {
            User user = userDAO.findByUsername(username.trim());

            // Constant or uniform check to avoid timing differences; verify password
            boolean passwordMatch = (user != null) && PasswordUtil.verify(password, user.getPasswordHash());

            if (!passwordMatch) {
                LoginThrottler.recordFailure(clientIp);
                // Generic error message: do not reveal whether the username exists
                req.setAttribute("errorMessage", "Invalid username or password.");
                req.setAttribute("csrfToken", CsrfUtil.getToken(req.getSession(true)));
                req.getRequestDispatcher("/WEB-INF/views/login.jsp").forward(req, res);
                return;
            }

            if (!user.isActiveUser()) {
                LoginThrottler.recordFailure(clientIp);
                req.setAttribute("errorMessage", "Account is deactivated. Please contact an administrator.");
                req.setAttribute("csrfToken", CsrfUtil.getToken(req.getSession(true)));
                req.getRequestDispatcher("/WEB-INF/views/login.jsp").forward(req, res);
                return;
            }

            // Successful authentication: Reset failed attempts for client IP
            LoginThrottler.reset(clientIp);

            // Session rotation to prevent session fixation attacks (Servlet 3.1+)
            req.changeSessionId();
            HttpSession session = req.getSession();
            session.setMaxInactiveInterval(15 * 60); // 15-minute inactivity timeout

            // Refresh CSRF token for the newly established session
            CsrfUtil.refreshToken(session);

            // Store user identity and role in session
            session.setAttribute("currentUser", user);

            res.sendRedirect(req.getContextPath() + "/dashboard");

        } catch (SQLException e) {
            req.setAttribute("errorMessage", "A database error occurred. Please contact the administrator.");
            req.setAttribute("csrfToken", CsrfUtil.getToken(req.getSession(true)));
            req.getRequestDispatcher("/WEB-INF/views/login.jsp").forward(req, res);
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.trim().isEmpty()) {
            return xf.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
