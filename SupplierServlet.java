package com.autocore.controller;

import com.autocore.dao.PurchaseOrderDAO;
import com.autocore.dao.SupplierDAO;
import com.autocore.model.PurchaseOrder;
import com.autocore.model.Supplier;
import com.autocore.model.User;
import com.autocore.util.CsrfUtil;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@WebServlet("/suppliers")
public class SupplierServlet extends HttpServlet {

    private SupplierDAO supplierDAO;
    private PurchaseOrderDAO purchaseOrderDAO;

    @Override
    public void init() {
        this.supplierDAO = new SupplierDAO();
        this.purchaseOrderDAO = new PurchaseOrderDAO();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {

        req.setCharacterEncoding("UTF-8");
        String action = req.getParameter("action");

        try {
            if ("edit".equals(action)) {
                int id = positiveId(req.getParameter("id"));
                Supplier s = supplierDAO.findById(id);
                if (s == null) {
                    res.sendError(HttpServletResponse.SC_NOT_FOUND, "Supplier not found");
                    return;
                }
                req.setAttribute("editSupplier", s);
            } else if ("view".equals(action)) {
                int id = positiveId(req.getParameter("id"));
                Supplier s = supplierDAO.findById(id);
                if (s == null) {
                    res.sendError(HttpServletResponse.SC_NOT_FOUND, "Supplier not found");
                    return;
                }
                req.setAttribute("viewSupplier", s);

                // Fetch supplier purchase order history
                List<PurchaseOrder> allOrders = purchaseOrderDAO.findAll(null, null);
                List<PurchaseOrder> supplierOrders = new ArrayList<>();
                for (PurchaseOrder po : allOrders) {
                    if (po.getSupplierId() == id) {
                        supplierOrders.add(po);
                    }
                }
                req.setAttribute("supplierOrders", supplierOrders);
            }

            String q = req.getParameter("q");
            String filter = req.getParameter("filter");
            Boolean activeOnly = "active".equalsIgnoreCase(filter) ? Boolean.TRUE : null;

            List<Supplier> suppliers = supplierDAO.findAll(q, activeOnly);
            req.setAttribute("search", q == null ? "" : q);
            req.setAttribute("filter", filter == null ? "all" : filter);
            req.setAttribute("suppliers", suppliers);

            req.getRequestDispatcher("/WEB-INF/views/suppliers.jsp").forward(req, res);

        } catch (IllegalArgumentException e) {
            res.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (SQLException e) {
            throw new ServletException("Database operation failed while loading suppliers.", e);
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
        String action = req.getParameter("action");

        try {
            if ("toggleStatus".equals(action)) {
                int id = positiveId(req.getParameter("id"));
                boolean active = "Y".equalsIgnoreCase(req.getParameter("status"));
                supplierDAO.setActiveStatus(id, active);
                res.sendRedirect(req.getContextPath() + "/suppliers?message=" + (active ? "activated" : "deactivated"));
                return;
            }

            if ("delete".equals(action)) {
                if (currentUser == null || !currentUser.isAdmin()) {
                    res.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied: Only administrators may delete suppliers.");
                    return;
                }
                int id = positiveId(req.getParameter("id"));
                supplierDAO.delete(id);
                res.sendRedirect(req.getContextPath() + "/suppliers?message=deleted");
                return;
            }

            Supplier s = read(req);

            if ("update".equals(action)) {
                int id = positiveId(req.getParameter("supplierId"));
                s.setSupplierId(id);
                supplierDAO.update(s);
                res.sendRedirect(req.getContextPath() + "/suppliers?message=updated");
            } else {
                supplierDAO.insert(s);
                res.sendRedirect(req.getContextPath() + "/suppliers?message=created");
            }

        } catch (IllegalArgumentException e) {
            req.setAttribute("formError", e.getMessage());
            try {
                req.setAttribute("suppliers", supplierDAO.findAll(req.getParameter("q"), null));
            } catch (SQLException ignored) {}
            req.getRequestDispatcher("/WEB-INF/views/suppliers.jsp").forward(req, res);
        } catch (SQLException e) {
            req.setAttribute("formError", "Database error: " + e.getMessage());
            try {
                req.setAttribute("suppliers", supplierDAO.findAll(req.getParameter("q"), null));
            } catch (SQLException ignored) {}
            req.getRequestDispatcher("/WEB-INF/views/suppliers.jsp").forward(req, res);
        }
    }

    private Supplier read(HttpServletRequest r) {
        Supplier s = new Supplier();
        s.setSupplierName(required(r.getParameter("supplierName"), "Supplier name is required"));
        s.setContactPerson(blank(r.getParameter("contactPerson")));
        s.setPhone(blank(r.getParameter("phone")));
        s.setEmail(blank(r.getParameter("email")));
        s.setAddress(blank(r.getParameter("address")));
        s.setTaxId(blank(r.getParameter("taxId")));
        s.setIsActive("N".equalsIgnoreCase(r.getParameter("isActive")) ? "N" : "Y");
        return s;
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

    private int positiveId(String s) {
        try {
            int n = Integer.parseInt(s);
            if (n < 1) throw new Exception();
            return n;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid supplier ID");
        }
    }

    private User getCurrentUser(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        return (s != null) ? (User) s.getAttribute("currentUser") : null;
    }
}
