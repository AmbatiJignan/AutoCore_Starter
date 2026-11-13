package com.autocore.controller;

import com.autocore.dao.ProductDAO;
import com.autocore.dao.PurchaseOrderDAO;
import com.autocore.dao.SupplierDAO;
import com.autocore.model.Product;
import com.autocore.model.PurchaseOrder;
import com.autocore.model.PurchaseOrderItem;
import com.autocore.model.Supplier;
import com.autocore.model.User;
import com.autocore.util.CsrfUtil;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@WebServlet("/purchases")
public class PurchaseServlet extends HttpServlet {

    private PurchaseOrderDAO purchaseOrderDAO;
    private SupplierDAO supplierDAO;
    private ProductDAO productDAO;

    @Override
    public void init() {
        this.purchaseOrderDAO = new PurchaseOrderDAO();
        this.supplierDAO = new SupplierDAO();
        this.productDAO = new ProductDAO();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {

        req.setCharacterEncoding("UTF-8");
        String action = req.getParameter("action");

        try {
            if ("new".equals(action)) {
                List<Supplier> suppliers = supplierDAO.findAll(null, true);
                List<Product> products = productDAO.findAll(null);
                req.setAttribute("suppliers", suppliers);
                req.setAttribute("products", products);
                req.getRequestDispatcher("/WEB-INF/views/purchase-create.jsp").forward(req, res);
                return;
            }

            if ("view".equals(action)) {
                int id = positiveId(req.getParameter("id"));
                PurchaseOrder po = purchaseOrderDAO.findById(id);
                if (po == null) {
                    res.sendError(HttpServletResponse.SC_NOT_FOUND, "Purchase order not found");
                    return;
                }
                req.setAttribute("po", po);
                req.getRequestDispatcher("/WEB-INF/views/purchase-detail.jsp").forward(req, res);
                return;
            }

            // Default: List purchase orders
            String q = req.getParameter("q");
            String status = req.getParameter("status");

            List<PurchaseOrder> orders = purchaseOrderDAO.findAll(q, status);
            req.setAttribute("orders", orders);
            req.setAttribute("search", q == null ? "" : q);
            req.setAttribute("selectedStatus", status == null ? "ALL" : status);

            req.getRequestDispatcher("/WEB-INF/views/purchases.jsp").forward(req, res);

        } catch (IllegalArgumentException e) {
            res.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (SQLException e) {
            throw new ServletException("Database operation failed in purchase orders.", e);
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
            if ("receive".equals(action)) {
                int poId = positiveId(req.getParameter("id"));
                purchaseOrderDAO.receiveOrder(poId, userId);
                res.sendRedirect(req.getContextPath() + "/purchases?action=view&id=" + poId + "&message=received");
                return;
            }

            if ("cancel".equals(action)) {
                int poId = positiveId(req.getParameter("id"));
                String reason = req.getParameter("reason");
                purchaseOrderDAO.cancelOrder(poId, userId, reason);
                res.sendRedirect(req.getContextPath() + "/purchases?action=view&id=" + poId + "&message=cancelled");
                return;
            }

            if ("create".equals(action)) {
                int supplierId = positiveId(req.getParameter("supplierId"));
                String notes = req.getParameter("notes");
                String expectedDateStr = req.getParameter("expectedDate");
                Date expectedDate = null;
                if (expectedDateStr != null && !expectedDateStr.trim().isEmpty()) {
                    try {
                        expectedDate = new SimpleDateFormat("yyyy-MM-dd").parse(expectedDateStr.trim());
                    } catch (Exception ignored) {}
                }

                String[] prodIds = req.getParameterValues("productId[]");
                String[] quantities = req.getParameterValues("quantity[]");
                String[] unitPrices = req.getParameterValues("unitPrice[]");

                if (prodIds == null || prodIds.length == 0) {
                    throw new IllegalArgumentException("At least one spare-part item line is required.");
                }

                List<PurchaseOrderItem> items = new ArrayList<>();
                for (int i = 0; i < prodIds.length; i++) {
                    if (prodIds[i] == null || prodIds[i].trim().isEmpty()) continue;
                    int pId = Integer.parseInt(prodIds[i].trim());
                    int qty = Integer.parseInt(quantities[i].trim());
                    BigDecimal price = new BigDecimal(unitPrices[i].trim());

                    PurchaseOrderItem item = new PurchaseOrderItem();
                    item.setProductId(pId);
                    item.setQuantity(qty);
                    item.setUnitPrice(price);
                    items.add(item);
                }

                PurchaseOrder po = new PurchaseOrder();
                po.setSupplierId(supplierId);
                po.setNotes(notes);
                po.setExpectedDate(expectedDate);
                po.setStatus("ORDERED");

                int poId = purchaseOrderDAO.createPurchaseOrder(po, items, userId);
                res.sendRedirect(req.getContextPath() + "/purchases?action=view&id=" + poId + "&message=created");
                return;
            }

        } catch (IllegalArgumentException | IllegalStateException e) {
            req.setAttribute("formError", e.getMessage());
            try {
                req.setAttribute("suppliers", supplierDAO.findAll(null, true));
                req.setAttribute("products", productDAO.findAll(null));
            } catch (SQLException ignored) {}
            req.getRequestDispatcher("/WEB-INF/views/purchase-create.jsp").forward(req, res);
        } catch (SQLException e) {
            req.setAttribute("formError", "Database error: " + e.getMessage());
            try {
                req.setAttribute("suppliers", supplierDAO.findAll(null, true));
                req.setAttribute("products", productDAO.findAll(null));
            } catch (SQLException ignored) {}
            req.getRequestDispatcher("/WEB-INF/views/purchase-create.jsp").forward(req, res);
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
