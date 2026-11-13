package com.autocore.controller;

import com.autocore.dao.ProductDAO;
import com.autocore.dao.StockMovementDAO;
import com.autocore.model.Product;
import com.autocore.model.StockMovement;
import com.autocore.model.User;
import com.autocore.util.CsrfUtil;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

@WebServlet("/movements")
public class StockMovementServlet extends HttpServlet {

    private StockMovementDAO stockMovementDAO;
    private ProductDAO productDAO;

    @Override
    public void init() {
        this.stockMovementDAO = new StockMovementDAO();
        this.productDAO = new ProductDAO();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {

        req.setCharacterEncoding("UTF-8");

        String q = req.getParameter("q");
        String type = req.getParameter("type");
        String fromDate = req.getParameter("fromDate");
        String toDate = req.getParameter("toDate");

        try {
            List<StockMovement> movements = stockMovementDAO.findAll(q, type, fromDate, toDate, 100);
            List<Product> products = productDAO.findAll(null);

            req.setAttribute("movements", movements);
            req.setAttribute("products", products);
            req.setAttribute("search", q == null ? "" : q);
            req.setAttribute("selectedType", type == null ? "ALL" : type);
            req.setAttribute("fromDate", fromDate == null ? "" : fromDate);
            req.setAttribute("toDate", toDate == null ? "" : toDate);

            req.getRequestDispatcher("/WEB-INF/views/movements.jsp").forward(req, res);

        } catch (SQLException e) {
            throw new ServletException("Database operation failed loading stock movements.", e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {

        req.setCharacterEncoding("UTF-8");

        if (!CsrfUtil.isValid(req)) {
            res.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid or missing security token (CSRF).");
            return;
        }

        User currentUser = getCurrentUser(req);
        int userId = (currentUser != null) ? currentUser.getUserId() : 1;
        String action = req.getParameter("action");

        try {
            if ("adjust".equals(action)) {
                int productId = positiveId(req.getParameter("productId"));
                int change = Integer.parseInt(required(req.getParameter("quantityChange"), "Adjustment quantity is required."));
                String reason = required(req.getParameter("reason"), "A reason is mandatory for manual stock adjustments.");

                stockMovementDAO.recordManualAdjustment(productId, change, reason, userId);
                res.sendRedirect(req.getContextPath() + "/movements?message=adjusted");
                return;
            }
        } catch (IllegalArgumentException e) {
            req.setAttribute("formError", e.getMessage());
            try {
                req.setAttribute("movements", stockMovementDAO.findRecent(100));
                req.setAttribute("products", productDAO.findAll(null));
            } catch (SQLException ignored) {}
            req.getRequestDispatcher("/WEB-INF/views/movements.jsp").forward(req, res);
        } catch (SQLException e) {
            req.setAttribute("formError", "Database error: " + e.getMessage());
            try {
                req.setAttribute("movements", stockMovementDAO.findRecent(100));
                req.setAttribute("products", productDAO.findAll(null));
            } catch (SQLException ignored) {}
            req.getRequestDispatcher("/WEB-INF/views/movements.jsp").forward(req, res);
        }
    }

    private String required(String s, String m) {
        if (s == null || s.trim().isEmpty()) {
            throw new IllegalArgumentException(m);
        }
        return s.trim();
    }

    private int positiveId(String s) {
        try {
            int n = Integer.parseInt(s);
            if (n < 1) throw new Exception();
            return n;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid ID parameter");
        }
    }

    private User getCurrentUser(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        return (s != null) ? (User) s.getAttribute("currentUser") : null;
    }
}
