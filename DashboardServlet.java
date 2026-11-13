package com.autocore.controller;

import com.autocore.dao.*;
import com.autocore.model.Product;
import com.autocore.model.PurchaseOrder;
import com.autocore.model.Sale;
import com.autocore.model.StockMovement;
import com.autocore.model.User;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

@WebServlet("/dashboard")
public class DashboardServlet extends HttpServlet {

    private ProductDAO productDAO;
    private UserDAO userDAO;
    private SupplierDAO supplierDAO;
    private PurchaseOrderDAO purchaseOrderDAO;
    private SaleDAO saleDAO;
    private StockMovementDAO stockMovementDAO;

    @Override
    public void init() {
        this.productDAO = new ProductDAO();
        this.userDAO = new UserDAO();
        this.supplierDAO = new SupplierDAO();
        this.purchaseOrderDAO = new PurchaseOrderDAO();
        this.saleDAO = new SaleDAO();
        this.stockMovementDAO = new StockMovementDAO();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {

        try {
            // Inventory metrics
            int totalProducts = productDAO.countTotal();
            int lowStockCount = productDAO.countLowStock();
            int outOfStockCount = productDAO.countOutOfStock();
            long totalUnits = productDAO.totalStockUnits();
            BigDecimal purchaseValuation = productDAO.totalPurchaseValuation();
            BigDecimal sellingValuation = productDAO.totalSellingValuation();
            BigDecimal potentialMargin = sellingValuation.subtract(purchaseValuation);
            List<Product> lowStockList = productDAO.findLowStockProducts(6);

            // Procurement & Sales metrics
            int totalSuppliers = supplierDAO.countActive();
            int totalSales = saleDAO.countTotal();
            BigDecimal totalRevenue = saleDAO.totalRevenue();
            int totalPurchases = purchaseOrderDAO.countTotal();
            BigDecimal totalSpend = purchaseOrderDAO.totalSpend();

            // Recent activity lists
            List<StockMovement> recentMovements = stockMovementDAO.findRecent(8);
            List<Sale> recentSales = saleDAO.findRecent(5);
            List<PurchaseOrder> recentPurchases = purchaseOrderDAO.findRecent(5);

            req.setAttribute("totalProducts", totalProducts);
            req.setAttribute("lowStockCount", lowStockCount);
            req.setAttribute("outOfStockCount", outOfStockCount);
            req.setAttribute("totalStockUnits", totalUnits);
            req.setAttribute("purchaseValuation", purchaseValuation);
            req.setAttribute("sellingValuation", sellingValuation);
            req.setAttribute("totalInventoryValue", sellingValuation); // backward compatibility
            req.setAttribute("potentialMargin", potentialMargin);
            req.setAttribute("lowStockList", lowStockList);

            req.setAttribute("totalSuppliers", totalSuppliers);
            req.setAttribute("totalSales", totalSales);
            req.setAttribute("totalRevenue", totalRevenue);
            req.setAttribute("totalPurchases", totalPurchases);
            req.setAttribute("totalSpend", totalSpend);

            req.setAttribute("recentMovements", recentMovements);
            req.setAttribute("recentSales", recentSales);
            req.setAttribute("recentPurchases", recentPurchases);

            HttpSession session = req.getSession(false);
            User currentUser = (session != null) ? (User) session.getAttribute("currentUser") : null;
            if (currentUser != null && currentUser.isAdmin()) {
                req.setAttribute("totalUsers", userDAO.findAll().size());
                req.setAttribute("activeAdminCount", userDAO.countActiveAdmins());
            }

            req.getRequestDispatcher("/WEB-INF/views/dashboard.jsp").forward(req, res);

        } catch (SQLException e) {
            throw new ServletException("Failed to load dashboard metrics from Oracle database.", e);
        }
    }
}
