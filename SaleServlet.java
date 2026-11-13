package com.autocore.controller;

import com.autocore.dao.ProductDAO;
import com.autocore.dao.SaleDAO;
import com.autocore.model.Product;
import com.autocore.model.Sale;
import com.autocore.model.SaleItem;
import com.autocore.model.User;
import com.autocore.util.CsrfUtil;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@WebServlet("/sales")
public class SaleServlet extends HttpServlet {

    private SaleDAO saleDAO;
    private ProductDAO productDAO;

    @Override
    public void init() {
        this.saleDAO = new SaleDAO();
        this.productDAO = new ProductDAO();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {

        req.setCharacterEncoding("UTF-8");
        String action = req.getParameter("action");

        try {
            if ("new".equals(action)) {
                // Fetch all products with available stock
                List<Product> products = productDAO.findAll(null);
                req.setAttribute("products", products);
                req.getRequestDispatcher("/WEB-INF/views/sale-create.jsp").forward(req, res);
                return;
            }

            if ("invoice".equals(action) || "view".equals(action)) {
                int id = positiveId(req.getParameter("id"));
                Sale sale = saleDAO.findById(id);
                if (sale == null) {
                    res.sendError(HttpServletResponse.SC_NOT_FOUND, "Invoice not found");
                    return;
                }
                req.setAttribute("sale", sale);
                req.getRequestDispatcher("/WEB-INF/views/invoice.jsp").forward(req, res);
                return;
            }

            // Default: List sales invoices
            String q = req.getParameter("q");
            String status = req.getParameter("status");

            List<Sale> sales = saleDAO.findAll(q, status);
            req.setAttribute("sales", sales);
            req.setAttribute("search", q == null ? "" : q);
            req.setAttribute("selectedStatus", status == null ? "ALL" : status);

            req.getRequestDispatcher("/WEB-INF/views/sales.jsp").forward(req, res);

        } catch (IllegalArgumentException e) {
            res.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (SQLException e) {
            throw new ServletException("Database operation failed in sales module.", e);
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
            if ("cancel".equals(action)) {
                int saleId = positiveId(req.getParameter("id"));
                String reason = req.getParameter("reason");
                saleDAO.cancelSale(saleId, userId, reason);
                res.sendRedirect(req.getContextPath() + "/sales?action=invoice&id=" + saleId + "&message=cancelled");
                return;
            }

            if ("create".equals(action)) {
                String customerName = req.getParameter("customerName");
                String customerPhone = req.getParameter("customerPhone");
                String notes = req.getParameter("notes");

                String[] prodIds = req.getParameterValues("productId[]");
                String[] quantities = req.getParameterValues("quantity[]");
                String[] unitPrices = req.getParameterValues("unitPrice[]");

                if (prodIds == null || prodIds.length == 0) {
                    throw new IllegalArgumentException("Invoice must include at least one spare-part item.");
                }

                List<SaleItem> items = new ArrayList<>();
                for (int i = 0; i < prodIds.length; i++) {
                    if (prodIds[i] == null || prodIds[i].trim().isEmpty()) continue;
                    int pId = Integer.parseInt(prodIds[i].trim());
                    int qty = Integer.parseInt(quantities[i].trim());
                    BigDecimal price = new BigDecimal(unitPrices[i].trim());

                    SaleItem item = new SaleItem();
                    item.setProductId(pId);
                    item.setQuantity(qty);
                    item.setUnitPrice(price);
                    items.add(item);
                }

                Sale sale = new Sale();
                sale.setCustomerName(customerName != null && !customerName.trim().isEmpty() ? customerName.trim() : "Walk-in Customer");
                sale.setCustomerPhone(customerPhone != null ? customerPhone.trim() : "");
                sale.setNotes(notes);

                int saleId = saleDAO.createSale(sale, items, userId);
                res.sendRedirect(req.getContextPath() + "/sales?action=invoice&id=" + saleId + "&message=created");
                return;
            }

        } catch (IllegalArgumentException | IllegalStateException e) {
            req.setAttribute("formError", e.getMessage());
            try {
                req.setAttribute("products", productDAO.findAll(null));
            } catch (SQLException ignored) {}
            req.getRequestDispatcher("/WEB-INF/views/sale-create.jsp").forward(req, res);
        } catch (SQLException e) {
            req.setAttribute("formError", "Database transaction failed: " + e.getMessage());
            try {
                req.setAttribute("products", productDAO.findAll(null));
            } catch (SQLException ignored) {}
            req.getRequestDispatcher("/WEB-INF/views/sale-create.jsp").forward(req, res);
        }
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
