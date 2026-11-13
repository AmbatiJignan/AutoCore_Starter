package com.autocore.dao;

import com.autocore.util.DBConnection;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReportDAO {

    /**
     * Category valuation breakdown: Category, Item Count, Total Units, Purchase Valuation, Selling Valuation, Margin
     */
    public List<Map<String, Object>> getCategoryValuation() throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT NVL(category, 'Uncategorized') AS cat_name, " +
                     "COUNT(*) AS item_count, " +
                     "NVL(SUM(stock_quantity), 0) AS total_units, " +
                     "NVL(SUM(stock_quantity * purchase_price), 0) AS purchase_val, " +
                     "NVL(SUM(stock_quantity * selling_price), 0) AS selling_val " +
                     "FROM autocore_products " +
                     "GROUP BY category " +
                     "ORDER BY selling_val DESC";
        try (Connection con = DBConnection.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("category", rs.getString("cat_name"));
                row.put("itemCount", rs.getInt("item_count"));
                row.put("totalUnits", rs.getLong("total_units"));
                BigDecimal pVal = rs.getBigDecimal("purchase_val");
                BigDecimal sVal = rs.getBigDecimal("selling_val");
                row.put("purchaseValuation", pVal);
                row.put("sellingValuation", sVal);
                row.put("potentialMargin", sVal.subtract(pVal));
                list.add(row);
            }
        }
        return list;
    }

    /**
     * Best-selling products aggregated from recorded sales.
     */
    public List<Map<String, Object>> getBestSellingProducts(int limit) throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT * FROM ( " +
                     "  SELECT p.product_id, p.part_number, p.product_name, p.category, " +
                     "         NVL(SUM(si.quantity), 0) AS units_sold, " +
                     "         NVL(SUM(si.line_total), 0) AS total_revenue " +
                     "  FROM autocore_products p " +
                     "  JOIN autocore_sale_items si ON p.product_id = si.product_id " +
                     "  JOIN autocore_sales s ON si.sale_id = s.sale_id " +
                     "  WHERE s.status = 'COMPLETED' " +
                     "  GROUP BY p.product_id, p.part_number, p.product_name, p.category " +
                     "  ORDER BY units_sold DESC, total_revenue DESC " +
                     ") WHERE ROWNUM <= ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, limit > 0 ? limit : 10);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("productId", rs.getInt("product_id"));
                    row.put("partNumber", rs.getString("part_number"));
                    row.put("productName", rs.getString("product_name"));
                    row.put("category", rs.getString("category"));
                    row.put("unitsSold", rs.getInt("units_sold"));
                    row.put("totalRevenue", rs.getBigDecimal("total_revenue"));
                    list.add(row);
                }
            }
        }
        return list;
    }

    /**
     * Supplier spend summary aggregated from recorded purchase orders.
     */
    public List<Map<String, Object>> getSupplierSpendSummary() throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT s.supplier_id, s.supplier_name, s.contact_person, s.phone, " +
                     "COUNT(po.po_id) AS total_orders, " +
                     "NVL(SUM(CASE WHEN po.status = 'RECEIVED' THEN po.total_amount ELSE 0 END), 0) AS total_received_spend, " +
                     "NVL(SUM(po.total_amount), 0) AS total_ordered_spend " +
                     "FROM autocore_suppliers s " +
                     "LEFT JOIN autocore_purchase_orders po ON s.supplier_id = po.supplier_id " +
                     "GROUP BY s.supplier_id, s.supplier_name, s.contact_person, s.phone " +
                     "ORDER BY total_received_spend DESC";
        try (Connection con = DBConnection.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("supplierId", rs.getInt("supplier_id"));
                row.put("supplierName", rs.getString("supplier_name"));
                row.put("contactPerson", rs.getString("contact_person"));
                row.put("phone", rs.getString("phone"));
                row.put("totalOrders", rs.getInt("total_orders"));
                row.put("totalReceivedSpend", rs.getBigDecimal("total_received_spend"));
                row.put("totalOrderedSpend", rs.getBigDecimal("total_ordered_spend"));
                list.add(row);
            }
        }
        return list;
    }

    /**
     * Sales performance filtered by date range.
     */
    public Map<String, Object> getSalesDateSummary(String fromDate, String toDate) throws SQLException {
        Map<String, Object> result = new HashMap<>();
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) AS sale_count, " +
                "NVL(SUM(total_amount), 0) AS total_revenue, " +
                "NVL(AVG(total_amount), 0) AS avg_sale " +
                "FROM autocore_sales WHERE status = 'COMPLETED' ");

        List<String> params = new ArrayList<>();
        if (fromDate != null && !fromDate.trim().isEmpty()) {
            sql.append("AND TRUNC(sale_date) >= TO_DATE(?, 'YYYY-MM-DD') ");
            params.add(fromDate.trim());
        }
        if (toDate != null && !toDate.trim().isEmpty()) {
            sql.append("AND TRUNC(sale_date) <= TO_DATE(?, 'YYYY-MM-DD') ");
            params.add(toDate.trim());
        }

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {
            int idx = 1;
            for (String param : params) {
                ps.setString(idx++, param);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    result.put("saleCount", rs.getInt("sale_count"));
                    result.put("totalRevenue", rs.getBigDecimal("total_revenue"));
                    result.put("avgSale", rs.getBigDecimal("avg_sale"));
                }
            }
        }
        return result;
    }

    /**
     * Purchase performance filtered by date range.
     */
    public Map<String, Object> getPurchaseDateSummary(String fromDate, String toDate) throws SQLException {
        Map<String, Object> result = new HashMap<>();
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) AS po_count, " +
                "NVL(SUM(total_amount), 0) AS total_spend, " +
                "NVL(AVG(total_amount), 0) AS avg_order " +
                "FROM autocore_purchase_orders WHERE status = 'RECEIVED' ");

        List<String> params = new ArrayList<>();
        if (fromDate != null && !fromDate.trim().isEmpty()) {
            sql.append("AND TRUNC(order_date) >= TO_DATE(?, 'YYYY-MM-DD') ");
            params.add(fromDate.trim());
        }
        if (toDate != null && !toDate.trim().isEmpty()) {
            sql.append("AND TRUNC(order_date) <= TO_DATE(?, 'YYYY-MM-DD') ");
            params.add(toDate.trim());
        }

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {
            int idx = 1;
            for (String param : params) {
                ps.setString(idx++, param);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    result.put("poCount", rs.getInt("po_count"));
                    result.put("totalSpend", rs.getBigDecimal("total_spend"));
                    result.put("avgOrder", rs.getBigDecimal("avg_order"));
                }
            }
        }
        return result;
    }
}
