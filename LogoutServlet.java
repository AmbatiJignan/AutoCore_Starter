package com.autocore.controller;

import com.autocore.util.CsrfUtil;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;

@WebServlet("/logout")
public class LogoutServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        // Enforce POST-based logout to prevent CSRF logout attacks via link prefetching
        res.sendRedirect(req.getContextPath() + "/login");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {

        // Validate CSRF token on logout
        if (CsrfUtil.isValid(req)) {
            HttpSession session = req.getSession(false);
            if (session != null) {
                session.invalidate();
            }
        }

        res.sendRedirect(req.getContextPath() + "/login?logout=1");
    }
}
