package com.autocore.filter;

import com.autocore.model.User;
import com.autocore.util.CsrfUtil;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

@WebFilter("/*")
public class AuthFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {}

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        // Apply baseline security headers to all responses
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "SAMEORIGIN");
        response.setHeader("X-XSS-Protection", "1; mode=block");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

        String contextPath = request.getContextPath();
        String uri = request.getRequestURI();
        String path = uri.substring(contextPath.length());

        // Allow public static assets and authentication endpoints
        if (isPublicPath(path)) {
            chain.doFilter(req, res);
            return;
        }

        // Check authentication
        HttpSession session = request.getSession(false);
        User currentUser = (session != null) ? (User) session.getAttribute("currentUser") : null;

        if (currentUser == null) {
            // Unauthenticated user attempting to access protected route: redirect to login
            response.sendRedirect(contextPath + "/login");
            return;
        }

        // Check if user is still active in session
        if (!currentUser.isActiveUser()) {
            if (session != null) {
                session.invalidate();
            }
            response.sendRedirect(contextPath + "/login?error=inactive");
            return;
        }

        // Authenticated responses must not be cached by browser
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);

        // Ensure CSRF token is available in request scope for easy JSP rendering
        String csrfToken = CsrfUtil.getToken(session);
        request.setAttribute("csrfToken", csrfToken);

        // Role-based authorization checks
        if (path.startsWith("/users") || path.startsWith("/admin")) {
            if (!currentUser.isAdmin()) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied: Administrator privileges required.");
                return;
            }
        }

        chain.doFilter(req, res);
    }

    private boolean isPublicPath(String path) {
        if (path == null || path.isEmpty() || "/".equals(path)) {
            return false;
        }
        return path.equals("/login")
                || path.equals("/login/")
                || path.startsWith("/css/")
                || path.startsWith("/js/")
                || path.startsWith("/images/")
                || path.equals("/favicon.ico");
    }


    @Override
    public void destroy() {}
}
