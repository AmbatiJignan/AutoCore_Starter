package com.autocore.controller;

import com.autocore.dao.ProductDAO;
import com.autocore.dao.StockMovementDAO;
import com.autocore.model.Product;
import com.autocore.model.User;
import com.autocore.util.CsrfUtil;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

@WebServlet("/products")
public class ProductServlet extends HttpServlet {

    private ProductDAO dao;
    private StockMovementDAO stockMovementDAO;

    @Override
    public void init() {
        this.dao = new ProductDAO();
        this.stockMovementDAO = new StockMovementDAO();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {

        req.setCharacterEncoding("UTF-8");
        String action = req.getParameter("action");

        try {
            if ("edit".equals(action)) {
                int id = positiveId(req.getParameter("id"));
                Product p = dao.findById(id);
                if (p == null) {
                    res.sendError(HttpServletResponse.SC_NOT_FOUND, "Product not found");
                    return;
                }
                req.setAttribute("editProduct", p);
            }

            String q = req.getParameter("q");
            String category = req.getParameter("category");
            String stockFilter = req.getParameter("stockFilter");
            
            int page = 1;
            try {
                if (req.getParameter("page") != null) {
                    page = Integer.parseInt(req.getParameter("page"));
                }
            } catch (NumberFormatException ignored) {}

            int pageSize = 12;

            int totalCount = dao.countFiltered(q, category, stockFilter);
            int totalPages = Math.max(1, (int) Math.ceil((double) totalCount / pageSize));
            if (page > totalPages) page = totalPages;

            List<Product> products = dao.findAllFiltered(q, category, stockFilter, page, pageSize);
            List<String> categories = dao.distinctCategories();

            req.setAttribute("search", q == null ? "" : q);
            req.setAttribute("selectedCategory", category == null ? "" : category);
            req.setAttribute("selectedStockFilter", stockFilter == null ? "" : stockFilter);
            req.setAttribute("page", page);
            req.setAttribute("pageSize", pageSize);
            req.setAttribute("totalCount", totalCount);
            req.setAttribute("totalPages", totalPages);
            req.setAttribute("products", products);
            req.setAttribute("categories", categories);

            req.getRequestDispatcher("/WEB-INF/views/products.jsp").forward(req, res);

        } catch (IllegalArgumentException e) {
            res.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (SQLException e) {
            throw new ServletException("Database operation failed. Verify DBConnection and Oracle XE status.", e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {

        req.setCharacterEncoding("UTF-8");

        // Validate CSRF token for all state-changing inventory operations
        if (!CsrfUtil.isValid(req)) {
            res.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid or missing security token (CSRF protection).");
            return;
        }

        User currentUser = getCurrentUser(req);
        String action = req.getParameter("action");

        try {
            if ("delete".equals(action)) {
                // Role check: Only ADMIN may delete products
                if (currentUser == null || !currentUser.isAdmin()) {
                    res.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied: Staff members cannot delete products.");
                    return;
                }
                dao.delete(positiveId(req.getParameter("id")));
                res.sendRedirect(req.getContextPath() + "/products?message=deleted");
                return;
            }

            if ("adjust".equals(action)) {
                // Manual stock adjustment
                int productId = positiveId(req.getParameter("productId"));
                int change = Integer.parseInt(required(req.getParameter("quantityChange"), "Adjustment quantity is required."));
                String reason = required(req.getParameter("reason"), "A reason is mandatory for manual stock adjustments.");

                int userId = (currentUser != null) ? currentUser.getUserId() : 1;
                stockMovementDAO.recordManualAdjustment(productId, change, reason, userId);
                res.sendRedirect(req.getContextPath() + "/products?message=adjusted");
                return;
            }

            Product p = read(req);

            if ("update".equals(action)) {
                int productId = positiveId(req.getParameter("productId"));
                p.setProductId(productId);

                if (dao.isPartNumberTaken(p.getPartNumber(), productId)) {
                    throw new IllegalArgumentException("Part number '" + p.getPartNumber() + "' is already in use by another item.");
                }

                dao.update(p);
                res.sendRedirect(req.getContextPath() + "/products?message=updated");
            } else {
                if (dao.isPartNumberTaken(p.getPartNumber(), 0)) {
                    throw new IllegalArgumentException("Part number '" + p.getPartNumber() + "' already exists in inventory.");
                }

                int userId = (currentUser != null) ? currentUser.getUserId() : 1;
                dao.insert(p, userId);
                res.sendRedirect(req.getContextPath() + "/products?message=created");
            }

        } catch (IllegalArgumentException e) {
            req.setAttribute("formError", e.getMessage());
            try {
                req.setAttribute("products", dao.findAll(req.getParameter("q")));
                req.setAttribute("categories", dao.distinctCategories());
            } catch (SQLException ignored) {}
            req.getRequestDispatcher("/WEB-INF/views/products.jsp").forward(req, res);
        } catch (SQLException e) {
            req.setAttribute("formError", e.getMessage());
            try {
                req.setAttribute("products", dao.findAll(req.getParameter("q")));
                req.setAttribute("categories", dao.distinctCategories());
            } catch (SQLException ignored) {}
            req.getRequestDispatcher("/WEB-INF/views/products.jsp").forward(req, res);
        }
    }

    private Product read(HttpServletRequest r) {
        Product p = new Product();
        p.setPartNumber(required(r.getParameter("partNumber"), "Part number is required"));
        p.setProductName(required(r.getParameter("productName"), "Part name is required"));
        p.setBrand(blank(r.getParameter("brand")));
        p.setVehicleModel(blank(r.getParameter("vehicleModel")));
        p.setCategory(blank(r.getParameter("category")));
        p.setPurchasePrice(money(r.getParameter("purchasePrice")));
        p.setSellingPrice(money(r.getParameter("sellingPrice")));
        p.setStockQuantity(number(r.getParameter("stockQuantity")));
        p.setMinimumStock(number(r.getParameter("minimumStock")));
        return p;
    }

    private String required(String s, String m) {
        if (s == null || s.trim().isEmpty()) {
            throw new IllegalArgumentException(m);
        }
        return s.trim();
    }

    private String blank(String s) {
        return (s == null || s.trim().isEmpty()) ? null : s.trim();
    }

    private BigDecimal money(String s) {
        try {
            BigDecimal b = new BigDecimal(required(s, "Price is required"));
            if (b.signum() < 0) {
                throw new IllegalArgumentException("Prices cannot be negative");
            }
            return b;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Price must be a valid number");
        }
    }

    private int number(String s) {
        try {
            int n = Integer.parseInt(required(s, "Stock values are required"));
            if (n < 0) {
                throw new IllegalArgumentException("Stock cannot be negative");
            }
            return n;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Stock must be a whole number");
        }
    }

    private int positiveId(String s) {
        try {
            int n = Integer.parseInt(s);
            if (n < 1) throw new Exception();
            return n;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid product ID");
        }
    }

    private User getCurrentUser(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        return (s != null) ? (User) s.getAttribute("currentUser") : null;
    }
}
