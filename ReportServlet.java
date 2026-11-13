package com.autocore.controller;

import com.autocore.dao.*;
import com.autocore.model.Product;
import com.autocore.model.PurchaseOrder;
import com.autocore.model.Sale;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

@WebServlet("/reports")
public class ReportServlet extends HttpServlet {

    private ReportDAO reportDAO;
    private ProductDAO productDAO;
    private SaleDAO saleDAO;
    private PurchaseOrderDAO purchaseOrderDAO;

    @Override
    public void init() {
        this.reportDAO = new ReportDAO();
        this.productDAO = new ProductDAO();
        this.saleDAO = new SaleDAO();
        this.purchaseOrderDAO = new PurchaseOrderDAO();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {

        req.setCharacterEncoding("UTF-8");

        String tab = req.getParameter("tab");
        if (tab == null || tab.trim().isEmpty()) {
            tab = "valuation";
        }

        String export = req.getParameter("export");
        if ("csv".equalsIgnoreCase(export)) {
            handleCsvExport(req, res, tab);
            return;
        }

        String fromDate = req.getParameter("fromDate");
        String toDate = req.getParameter("toDate");

        try {
            // Overall summary cards
            BigDecimal purchaseValuation = productDAO.totalPurchaseValuation();
            BigDecimal sellingValuation = productDAO.totalSellingValuation();
            BigDecimal potentialMargin = sellingValuation.subtract(purchaseValuation);
            long totalUnits = productDAO.totalStockUnits();
            int totalProducts = productDAO.countTotal();

            req.setAttribute("purchaseValuation", purchaseValuation);
            req.setAttribute("sellingValuation", sellingValuation);
            req.setAttribute("potentialMargin", potentialMargin);
            req.setAttribute("totalUnits", totalUnits);
            req.setAttribute("totalProducts", totalProducts);

            if ("valuation".equals(tab)) {
                List<Map<String, Object>> categoryValuations = reportDAO.getCategoryValuation();
                List<Product> allProducts = productDAO.findAll(null);
                req.setAttribute("categoryValuations", categoryValuations);
                req.setAttribute("allProducts", allProducts);

            } else if ("lowstock".equals(tab)) {
                List<Product> lowStockItems = productDAO.findLowStockProducts(100);
                req.setAttribute("lowStockItems", lowStockItems);

            } else if ("sales".equals(tab)) {
                Map<String, Object> salesSummary = reportDAO.getSalesDateSummary(fromDate, toDate);
                List<Sale> salesList = saleDAO.findAll(null, "COMPLETED");
                req.setAttribute("salesSummary", salesSummary);
                req.setAttribute("salesList", salesList);

            } else if ("purchases".equals(tab)) {
                Map<String, Object> purchaseSummary = reportDAO.getPurchaseDateSummary(fromDate, toDate);
                List<PurchaseOrder> purchaseList = purchaseOrderDAO.findAll(null, null);
                req.setAttribute("purchaseSummary", purchaseSummary);
                req.setAttribute("purchaseList", purchaseList);

            } else if ("bestsellers".equals(tab)) {
                List<Map<String, Object>> bestSellers = reportDAO.getBestSellingProducts(15);
                req.setAttribute("bestSellers", bestSellers);

            } else if ("suppliers".equals(tab)) {
                List<Map<String, Object>> supplierSpend = reportDAO.getSupplierSpendSummary();
                req.setAttribute("supplierSpend", supplierSpend);
            }

            req.setAttribute("activeTab", tab);
            req.setAttribute("fromDate", fromDate == null ? "" : fromDate);
            req.setAttribute("toDate", toDate == null ? "" : toDate);

            req.getRequestDispatcher("/WEB-INF/views/reports.jsp").forward(req, res);

        } catch (SQLException e) {
            throw new ServletException("Database operation failed loading reports.", e);
        }
    }

    private void handleCsvExport(HttpServletRequest req, HttpServletResponse res, String tab) throws IOException {
        String filename = "autocore_" + tab + "_report_" + new SimpleDateFormat("yyyyMMdd").format(new Date()) + ".csv";
        res.setContentType("text/csv; charset=UTF-8");
        res.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

        PrintWriter writer = res.getWriter();

        try {
            if ("valuation".equals(tab) || "inventory".equals(tab)) {
                writer.println("Part Number,Product Name,Brand,Model,Category,Stock Quantity,Purchase Price,Selling Price,Purchase Valuation,Selling Valuation,Margin");
                List<Product> list = productDAO.findAll(null);
                for (Product p : list) {
                    writer.printf("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",%d,%.2f,%.2f,%.2f,%.2f,%.2f%n",
                            escapeCsv(p.getPartNumber()),
                            escapeCsv(p.getProductName()),
                            escapeCsv(p.getBrand()),
                            escapeCsv(p.getVehicleModel()),
                            escapeCsv(p.getCategory()),
                            p.getStockQuantity(),
                            p.getPurchasePrice(),
                            p.getSellingPrice(),
                            p.getPurchaseValuation(),
                            p.getSellingValuation(),
                            p.getGrossMargin());
                }
            } else if ("sales".equals(tab)) {
                writer.println("Invoice Number,Date,Customer Name,Phone,Total Amount,Status");
                List<Sale> sales = saleDAO.findAll(null, null);
                for (Sale s : sales) {
                    writer.printf("\"%s\",\"%s\",\"%s\",\"%s\",%.2f,\"%s\"%n",
                            escapeCsv(s.getInvoiceNumber()),
                            s.getSaleDate(),
                            escapeCsv(s.getCustomerName()),
                            escapeCsv(s.getCustomerPhone()),
                            s.getTotalAmount(),
                            s.getStatus());
                }
            } else {
                writer.println("Notice");
                writer.println("Export not supported for requested tab.");
            }
        } catch (SQLException e) {
            writer.println("Error generating CSV export: " + e.getMessage());
        }

        writer.flush();
    }

    private String escapeCsv(String val) {
        if (val == null) return "";
        return val.replace("\"", "\"\"");
    }
}
