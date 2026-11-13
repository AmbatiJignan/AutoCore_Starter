package com.autocore.dao;

import com.autocore.model.Sale;
import com.autocore.model.SaleItem;
import com.autocore.util.DBConnection;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SaleDAO {

    private final StockMovementDAO stockMovementDAO = new StockMovementDAO();

    public List<Sale> findAll(String search, String status) throws SQLException {
        List<Sale> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT s.sale_id, s.invoice_number, s.customer_name, s.customer_phone, " +
                "s.sale_date, s.total_amount, s.status, s.notes, s.created_by, " +
                "u.full_name AS creator_name, s.created_at " +
                "FROM autocore_sales s " +
                "LEFT JOIN autocore_users u ON s.created_by = u.user_id " +
                "WHERE 1=1 ");

        List<Object> params = new ArrayList<>();

        if (status != null && !status.trim().isEmpty() && !"ALL".equalsIgnoreCase(status)) {
            sql.append("AND s.status = ? ");
            params.add(status.trim());
        }

        if (search != null && !search.trim().isEmpty()) {
            sql.append("AND (LOWER(s.invoice_number) LIKE LOWER(?) ")
               .append("OR LOWER(NVL(s.customer_name, '-')) LIKE LOWER(?) ")
               .append("OR LOWER(NVL(s.customer_phone, '-')) LIKE LOWER(?) ")
               .append("OR LOWER(NVL(s.notes, '-')) LIKE LOWER(?)) ");
            String pat = "%" + search.trim() + "%";
            for (int i = 0; i < 4; i++) {
                params.add(pat);
            }
        }

        sql.append("ORDER BY s.created_at DESC, s.sale_id DESC");

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {
            int idx = 1;
            for (Object param : params) {
                ps.setObject(idx++, param);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        }
        return list;
    }

    public Sale findById(int saleId) throws SQLException {
        String sql = "SELECT s.sale_id, s.invoice_number, s.customer_name, s.customer_phone, " +
                     "s.sale_date, s.total_amount, s.status, s.notes, s.created_by, " +
                     "u.full_name AS creator_name, s.created_at " +
                     "FROM autocore_sales s " +
                     "LEFT JOIN autocore_users u ON s.created_by = u.user_id " +
                     "WHERE s.sale_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, saleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Sale sale = map(rs);
                    sale.setItems(findItemsBySaleId(saleId));
                    return sale;
                }
            }
        }
        return null;
    }

    public Sale findByInvoiceNumber(String invoiceNumber) throws SQLException {
        String sql = "SELECT s.sale_id, s.invoice_number, s.customer_name, s.customer_phone, " +
                     "s.sale_date, s.total_amount, s.status, s.notes, s.created_by, " +
                     "u.full_name AS creator_name, s.created_at " +
                     "FROM autocore_sales s " +
                     "LEFT JOIN autocore_users u ON s.created_by = u.user_id " +
                     "WHERE s.invoice_number = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, invoiceNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Sale sale = map(rs);
                    sale.setItems(findItemsBySaleId(sale.getSaleId()));
                    return sale;
                }
            }
        }
        return null;
    }

    public List<SaleItem> findItemsBySaleId(int saleId) throws SQLException {
        List<SaleItem> items = new ArrayList<>();
        String sql = "SELECT si.sale_item_id, si.sale_id, si.product_id, p.part_number, p.product_name, " +
                     "si.quantity, si.unit_price, si.line_total " +
                     "FROM autocore_sale_items si " +
                     "JOIN autocore_products p ON si.product_id = p.product_id " +
                     "WHERE si.sale_id = ? " +
                     "ORDER BY si.sale_item_id ASC";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, saleId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    SaleItem item = new SaleItem();
                    item.setSaleItemId(rs.getInt("sale_item_id"));
                    item.setSaleId(rs.getInt("sale_id"));
                    item.setProductId(rs.getInt("product_id"));
                    item.setPartNumber(rs.getString("part_number"));
                    item.setProductName(rs.getString("product_name"));
                    item.setQuantity(rs.getInt("quantity"));
                    item.setUnitPrice(rs.getBigDecimal("unit_price"));
                    item.setLineTotal(rs.getBigDecimal("line_total"));
                    items.add(item);
                }
            }
        }
        return items;
    }

    /**
     * Atomically creates a sale, verifies sufficient inventory using row locking,
     * decrements stock, logs stock movement records, and inserts the invoice.
     */
    public int createSale(Sale sale, List<SaleItem> items, int userId) throws SQLException {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Invoice must contain at least one spare-part item.");
        }

        BigDecimal calculatedTotal = BigDecimal.ZERO;
        for (SaleItem item : items) {
            if (item.getQuantity() <= 0) {
                throw new IllegalArgumentException("Quantity must be greater than zero.");
            }
            if (item.getUnitPrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Unit price cannot be negative.");
            }
            BigDecimal lineTotal = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            item.setLineTotal(lineTotal);
            calculatedTotal = calculatedTotal.add(lineTotal);
        }
        sale.setTotalAmount(calculatedTotal);

        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                // 1. Generate next Sale sequence ID and Invoice Number
                int saleId;
                try (Statement st = con.createStatement();
                     ResultSet rs = st.executeQuery("SELECT autocore_sale_seq.NEXTVAL FROM dual")) {
                    if (!rs.next()) throw new SQLException("Unable to generate Sale sequence");
                    saleId = rs.getInt(1);
                }

                String invoiceNumber = "INV-2026-" + saleId;
                sale.setSaleId(saleId);
                sale.setInvoiceNumber(invoiceNumber);

                // 2. Insert Sale Header
                String insertSaleSql = "INSERT INTO autocore_sales (sale_id, invoice_number, customer_name, " +
                        "customer_phone, sale_date, total_amount, status, notes, created_by, created_at) " +
                        "VALUES (?, ?, ?, ?, SYSDATE, ?, 'COMPLETED', ?, ?, SYSDATE)";
                try (PreparedStatement ps = con.prepareStatement(insertSaleSql)) {
                    ps.setInt(1, saleId);
                    ps.setString(2, invoiceNumber);
                    ps.setString(3, sale.getCustomerName());
                    ps.setString(4, sale.getCustomerPhone());
                    ps.setBigDecimal(5, sale.getTotalAmount());
                    ps.setString(6, sale.getNotes());
                    ps.setInt(7, userId);
                    ps.executeUpdate();
                }

                // 3. For each item: Lock product row, check sufficient stock, decrement stock, log movement
                String checkStockSql = "SELECT stock_quantity, product_name, part_number FROM autocore_products WHERE product_id = ? FOR UPDATE";
                String updateStockSql = "UPDATE autocore_products SET stock_quantity = ? WHERE product_id = ?";
                String insertItemSql = "INSERT INTO autocore_sale_items (sale_item_id, sale_id, product_id, " +
                        "quantity, unit_price, line_total) " +
                        "VALUES (autocore_sale_item_seq.NEXTVAL, ?, ?, ?, ?, ?)";

                for (SaleItem item : items) {
                    int currentStock;
                    String prodName;
                    String partNum;

                    try (PreparedStatement psLock = con.prepareStatement(checkStockSql)) {
                        psLock.setInt(1, item.getProductId());
                        try (ResultSet rs = psLock.executeQuery()) {
                            if (!rs.next()) {
                                throw new SQLException("Product with ID " + item.getProductId() + " not found.");
                            }
                            currentStock = rs.getInt("stock_quantity");
                            prodName = rs.getString("product_name");
                            partNum = rs.getString("part_number");
                        }
                    }

                    if (currentStock < item.getQuantity()) {
                        throw new IllegalArgumentException("Insufficient inventory for part '" + prodName + 
                                "' (" + partNum + "). Available: " + currentStock + ", Requested: " + item.getQuantity());
                    }

                    int newStock = currentStock - item.getQuantity();

                    // Update stock
                    try (PreparedStatement psUpd = con.prepareStatement(updateStockSql)) {
                        psUpd.setInt(1, newStock);
                        psUpd.setInt(2, item.getProductId());
                        psUpd.executeUpdate();
                    }

                    // Record stock movement
                    String movementNotes = "Sold to " + (sale.getCustomerName() != null && !sale.getCustomerName().isEmpty() ? sale.getCustomerName() : "Walk-in Customer");
                    stockMovementDAO.recordMovement(con, item.getProductId(), "SALE", -item.getQuantity(), 
                            currentStock, newStock, invoiceNumber, movementNotes, userId);

                    // Insert sale item row
                    try (PreparedStatement psItem = con.prepareStatement(insertItemSql)) {
                        psItem.setInt(1, saleId);
                        psItem.setInt(2, item.getProductId());
                        psItem.setInt(3, item.getQuantity());
                        psItem.setBigDecimal(4, item.getUnitPrice());
                        psItem.setBigDecimal(5, item.getLineTotal());
                        psItem.executeUpdate();
                    }
                }

                con.commit();
                return saleId;
            } catch (Exception e) {
                con.rollback();
                throw e;
            }
        }
    }

    /**
     * Atomically cancel a sale: restores inventory to previous levels and records SALE_CANCEL movement.
     */
    public void cancelSale(int saleId, int userId, String reason) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                // 1. Lock and check sale status
                String lockSql = "SELECT invoice_number, status FROM autocore_sales WHERE sale_id = ? FOR UPDATE";
                String invoiceNumber;
                String currentStatus;
                try (PreparedStatement ps = con.prepareStatement(lockSql)) {
                    ps.setInt(1, saleId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException("Sale with ID " + saleId + " not found.");
                        }
                        invoiceNumber = rs.getString("invoice_number");
                        currentStatus = rs.getString("status");
                    }
                }

                if ("CANCELLED".equalsIgnoreCase(currentStatus)) {
                    throw new IllegalStateException("Sale " + invoiceNumber + " is already cancelled.");
                }

                // 2. Fetch sale items
                List<SaleItem> items = findItemsBySaleId(saleId);

                // 3. For each item: restore stock and log stock movement
                for (SaleItem item : items) {
                    int prevStock;
                    String prodLockSql = "SELECT stock_quantity FROM autocore_products WHERE product_id = ? FOR UPDATE";
                    try (PreparedStatement ps = con.prepareStatement(prodLockSql)) {
                        ps.setInt(1, item.getProductId());
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) {
                                throw new SQLException("Product ID " + item.getProductId() + " not found.");
                            }
                            prevStock = rs.getInt("stock_quantity");
                        }
                    }

                    int newStock = prevStock + item.getQuantity();

                    String updateProdSql = "UPDATE autocore_products SET stock_quantity = ? WHERE product_id = ?";
                    try (PreparedStatement ps = con.prepareStatement(updateProdSql)) {
                        ps.setInt(1, newStock);
                        ps.setInt(2, item.getProductId());
                        ps.executeUpdate();
                    }

                    String movNotes = "Sale cancelled / return: " + (reason != null ? reason : "Customer return");
                    stockMovementDAO.recordMovement(con, item.getProductId(), "SALE_CANCEL",
                            item.getQuantity(), prevStock, newStock, invoiceNumber, movNotes, userId);
                }

                // 4. Mark sale as CANCELLED
                String updateSaleSql = "UPDATE autocore_sales SET status = 'CANCELLED', notes = NVL(notes, '') || ' [Cancelled: ' || ? || ']' WHERE sale_id = ?";
                try (PreparedStatement ps = con.prepareStatement(updateSaleSql)) {
                    ps.setString(1, reason != null ? reason : "Sale cancelled by user");
                    ps.setInt(2, saleId);
                    ps.executeUpdate();
                }

                con.commit();
            } catch (Exception e) {
                con.rollback();
                throw e;
            }
        }
    }

    public int countTotal() throws SQLException {
        String sql = "SELECT COUNT(*) FROM autocore_sales";
        try (Connection con = DBConnection.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    public BigDecimal totalRevenue() throws SQLException {
        String sql = "SELECT NVL(SUM(total_amount), 0) FROM autocore_sales WHERE status = 'COMPLETED'";
        try (Connection con = DBConnection.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO;
        }
    }

    public List<Sale> findRecent(int limit) throws SQLException {
        List<Sale> list = new ArrayList<>();
        String sql = "SELECT * FROM (SELECT s.sale_id, s.invoice_number, s.customer_name, s.customer_phone, " +
                     "s.sale_date, s.total_amount, s.status, s.notes, s.created_by, " +
                     "u.full_name AS creator_name, s.created_at " +
                     "FROM autocore_sales s " +
                     "LEFT JOIN autocore_users u ON s.created_by = u.user_id " +
                     "ORDER BY s.created_at DESC) WHERE ROWNUM <= ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        }
        return list;
    }

    private Sale map(ResultSet rs) throws SQLException {
        Sale s = new Sale();
        s.setSaleId(rs.getInt("sale_id"));
        s.setInvoiceNumber(rs.getString("invoice_number"));
        s.setCustomerName(rs.getString("customer_name"));
        s.setCustomerPhone(rs.getString("customer_phone"));
        s.setSaleDate(rs.getDate("sale_date"));
        s.setTotalAmount(rs.getBigDecimal("total_amount"));
        s.setStatus(rs.getString("status"));
        s.setNotes(rs.getString("notes"));
        int uid = rs.getInt("created_by");
        if (!rs.wasNull()) {
            s.setCreatedBy(uid);
        }
        s.setCreatorName(rs.getString("creator_name"));
        s.setCreatedAt(rs.getDate("created_at"));
        return s;
    }
}
