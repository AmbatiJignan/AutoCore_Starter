package com.autocore.dao;

import com.autocore.model.PurchaseOrder;
import com.autocore.model.PurchaseOrderItem;
import com.autocore.util.DBConnection;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class PurchaseOrderDAO {

    private final StockMovementDAO stockMovementDAO = new StockMovementDAO();

    public List<PurchaseOrder> findAll(String search, String status) throws SQLException {
        List<PurchaseOrder> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT po.po_id, po.po_number, po.supplier_id, s.supplier_name, po.order_date, " +
                "po.expected_date, po.received_date, po.status, po.total_amount, po.notes, " +
                "po.created_by, u.full_name AS creator_name, po.created_at " +
                "FROM autocore_purchase_orders po " +
                "JOIN autocore_suppliers s ON po.supplier_id = s.supplier_id " +
                "LEFT JOIN autocore_users u ON po.created_by = u.user_id " +
                "WHERE 1=1 ");

        List<Object> params = new ArrayList<>();

        if (status != null && !status.trim().isEmpty() && !"ALL".equalsIgnoreCase(status)) {
            sql.append("AND po.status = ? ");
            params.add(status.trim());
        }

        if (search != null && !search.trim().isEmpty()) {
            sql.append("AND (LOWER(po.po_number) LIKE LOWER(?) ")
               .append("OR LOWER(s.supplier_name) LIKE LOWER(?) ")
               .append("OR LOWER(NVL(po.notes, '-')) LIKE LOWER(?)) ");
            String pat = "%" + search.trim() + "%";
            for (int i = 0; i < 3; i++) {
                params.add(pat);
            }
        }

        sql.append("ORDER BY po.created_at DESC, po.po_id DESC");

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

    public PurchaseOrder findById(int poId) throws SQLException {
        String sql = "SELECT po.po_id, po.po_number, po.supplier_id, s.supplier_name, po.order_date, " +
                     "po.expected_date, po.received_date, po.status, po.total_amount, po.notes, " +
                     "po.created_by, u.full_name AS creator_name, po.created_at " +
                     "FROM autocore_purchase_orders po " +
                     "JOIN autocore_suppliers s ON po.supplier_id = s.supplier_id " +
                     "LEFT JOIN autocore_users u ON po.created_by = u.user_id " +
                     "WHERE po.po_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, poId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    PurchaseOrder po = map(rs);
                    po.setItems(findItemsByPoId(poId));
                    return po;
                }
            }
        }
        return null;
    }

    public List<PurchaseOrderItem> findItemsByPoId(int poId) throws SQLException {
        List<PurchaseOrderItem> items = new ArrayList<>();
        String sql = "SELECT poi.po_item_id, poi.po_id, poi.product_id, p.part_number, p.product_name, " +
                     "poi.quantity, poi.unit_price, poi.line_total " +
                     "FROM autocore_po_items poi " +
                     "JOIN autocore_products p ON poi.product_id = p.product_id " +
                     "WHERE poi.po_id = ? " +
                     "ORDER BY poi.po_item_id ASC";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, poId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    PurchaseOrderItem item = new PurchaseOrderItem();
                    item.setPoItemId(rs.getInt("po_item_id"));
                    item.setPoId(rs.getInt("po_id"));
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
     * Create a purchase order with multi-part lines in an atomic transaction.
     * Note: Stock is NOT modified at creation time; only upon goods receipt.
     */
    public int createPurchaseOrder(PurchaseOrder po, List<PurchaseOrderItem> items, int userId) throws SQLException {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Purchase order must contain at least one spare-part line item.");
        }

        BigDecimal calculatedTotal = BigDecimal.ZERO;
        for (PurchaseOrderItem item : items) {
            if (item.getQuantity() <= 0) {
                throw new IllegalArgumentException("Order quantity must be greater than zero for part ID " + item.getProductId());
            }
            if (item.getUnitPrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Unit price cannot be negative.");
            }
            BigDecimal lineTotal = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            item.setLineTotal(lineTotal);
            calculatedTotal = calculatedTotal.add(lineTotal);
        }
        po.setTotalAmount(calculatedTotal);

        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                // 1. Get next sequence value for PO
                int poId;
                try (Statement st = con.createStatement();
                     ResultSet rs = st.executeQuery("SELECT autocore_po_seq.NEXTVAL FROM dual")) {
                    if (!rs.next()) throw new SQLException("Unable to generate PO sequence");
                    poId = rs.getInt(1);
                }

                String poNumber = "PO-2026-" + poId;
                po.setPoId(poId);
                po.setPoNumber(poNumber);

                // 2. Insert PO Header
                String insertPoSql = "INSERT INTO autocore_purchase_orders (po_id, po_number, supplier_id, " +
                        "order_date, expected_date, status, total_amount, notes, created_by, created_at) " +
                        "VALUES (?, ?, ?, SYSDATE, ?, ?, ?, ?, ?, SYSDATE)";
                try (PreparedStatement ps = con.prepareStatement(insertPoSql)) {
                    ps.setInt(1, poId);
                    ps.setString(2, poNumber);
                    ps.setInt(3, po.getSupplierId());
                    if (po.getExpectedDate() != null) {
                        ps.setDate(4, new java.sql.Date(po.getExpectedDate().getTime()));
                    } else {
                        ps.setNull(4, Types.DATE);
                    }
                    ps.setString(5, po.getStatus() != null ? po.getStatus() : "ORDERED");
                    ps.setBigDecimal(6, po.getTotalAmount());
                    ps.setString(7, po.getNotes());
                    ps.setInt(8, userId);
                    ps.executeUpdate();
                }

                // 3. Insert PO Line Items
                String insertItemSql = "INSERT INTO autocore_po_items (po_item_id, po_id, product_id, " +
                        "quantity, unit_price, line_total) " +
                        "VALUES (autocore_po_item_seq.NEXTVAL, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = con.prepareStatement(insertItemSql)) {
                    for (PurchaseOrderItem item : items) {
                        ps.setInt(1, poId);
                        ps.setInt(2, item.getProductId());
                        ps.setInt(3, item.getQuantity());
                        ps.setBigDecimal(4, item.getUnitPrice());
                        ps.setBigDecimal(5, item.getLineTotal());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                con.commit();
                return poId;
            } catch (Exception e) {
                con.rollback();
                throw e;
            }
        }
    }

    /**
     * Atomically receive goods for a purchase order.
     * Prevents duplicate execution and increments stock with audit log.
     */
    public void receiveOrder(int poId, int userId) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                // 1. Lock and verify PO status
                String selectPoSql = "SELECT po_number, status, supplier_id FROM autocore_purchase_orders WHERE po_id = ? FOR UPDATE";
                String poNumber;
                String currentStatus;
                try (PreparedStatement ps = con.prepareStatement(selectPoSql)) {
                    ps.setInt(1, poId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException("Purchase order with ID " + poId + " not found.");
                        }
                        poNumber = rs.getString("po_number");
                        currentStatus = rs.getString("status");
                    }
                }

                if ("RECEIVED".equalsIgnoreCase(currentStatus)) {
                    throw new IllegalStateException("Purchase order " + poNumber + " has already been received. Duplicate receipt prohibited.");
                }
                if ("CANCELLED".equalsIgnoreCase(currentStatus)) {
                    throw new IllegalStateException("Cannot receive a cancelled purchase order.");
                }

                // 2. Fetch items for this PO
                List<PurchaseOrderItem> items = new ArrayList<>();
                String itemsSql = "SELECT product_id, quantity, unit_price FROM autocore_po_items WHERE po_id = ?";
                try (PreparedStatement ps = con.prepareStatement(itemsSql)) {
                    ps.setInt(1, poId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            PurchaseOrderItem item = new PurchaseOrderItem();
                            item.setProductId(rs.getInt("product_id"));
                            item.setQuantity(rs.getInt("quantity"));
                            item.setUnitPrice(rs.getBigDecimal("unit_price"));
                            items.add(item);
                        }
                    }
                }

                if (items.isEmpty()) {
                    throw new IllegalStateException("Cannot receive a purchase order with zero items.");
                }

                // 3. For each item: lock product row, update stock, log stock movement
                for (PurchaseOrderItem item : items) {
                    int prevStock;
                    String prodLockSql = "SELECT stock_quantity, purchase_price FROM autocore_products WHERE product_id = ? FOR UPDATE";
                    try (PreparedStatement ps = con.prepareStatement(prodLockSql)) {
                        ps.setInt(1, item.getProductId());
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) {
                                throw new SQLException("Product ID " + item.getProductId() + " not found in catalogue.");
                            }
                            prevStock = rs.getInt("stock_quantity");
                        }
                    }

                    int newStock = prevStock + item.getQuantity();

                    // Update product stock and optionally update purchase_price to latest PO cost
                    String updateProdSql = "UPDATE autocore_products SET stock_quantity = ?, purchase_price = ? WHERE product_id = ?";
                    try (PreparedStatement ps = con.prepareStatement(updateProdSql)) {
                        ps.setInt(1, newStock);
                        ps.setBigDecimal(2, item.getUnitPrice());
                        ps.setInt(3, item.getProductId());
                        ps.executeUpdate();
                    }

                    // Record movement
                    String notes = "Goods received from " + poNumber;
                    stockMovementDAO.recordMovement(con, item.getProductId(), "PURCHASE_RECEIPT",
                            item.getQuantity(), prevStock, newStock, poNumber, notes, userId);
                }

                // 4. Update PO status to RECEIVED with received_date = SYSDATE
                String updatePoSql = "UPDATE autocore_purchase_orders SET status = 'RECEIVED', received_date = SYSDATE WHERE po_id = ?";
                try (PreparedStatement ps = con.prepareStatement(updatePoSql)) {
                    ps.setInt(1, poId);
                    ps.executeUpdate();
                }

                con.commit();
            } catch (Exception e) {
                con.rollback();
                throw e;
            }
        }
    }

    /**
     * Cancel an unreceived purchase order.
     */
    public void cancelOrder(int poId, int userId, String reason) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                String selectPoSql = "SELECT po_number, status FROM autocore_purchase_orders WHERE po_id = ? FOR UPDATE";
                String currentStatus;
                try (PreparedStatement ps = con.prepareStatement(selectPoSql)) {
                    ps.setInt(1, poId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException("Purchase order with ID " + poId + " not found.");
                        }
                        currentStatus = rs.getString("status");
                    }
                }

                if ("RECEIVED".equalsIgnoreCase(currentStatus)) {
                    throw new IllegalStateException("Cannot cancel a purchase order that has already been received.");
                }

                String updatePoSql = "UPDATE autocore_purchase_orders SET status = 'CANCELLED', notes = NVL(notes, '') || ' [Cancelled: ' || ? || ']' WHERE po_id = ?";
                try (PreparedStatement ps = con.prepareStatement(updatePoSql)) {
                    ps.setString(1, reason != null ? reason : "Order cancelled by user");
                    ps.setInt(2, poId);
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
        String sql = "SELECT COUNT(*) FROM autocore_purchase_orders";
        try (Connection con = DBConnection.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    public BigDecimal totalSpend() throws SQLException {
        String sql = "SELECT NVL(SUM(total_amount), 0) FROM autocore_purchase_orders WHERE status = 'RECEIVED'";
        try (Connection con = DBConnection.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO;
        }
    }

    public List<PurchaseOrder> findRecent(int limit) throws SQLException {
        List<PurchaseOrder> list = new ArrayList<>();
        String sql = "SELECT * FROM (SELECT po.po_id, po.po_number, po.supplier_id, s.supplier_name, po.order_date, " +
                     "po.expected_date, po.received_date, po.status, po.total_amount, po.notes, " +
                     "po.created_by, u.full_name AS creator_name, po.created_at " +
                     "FROM autocore_purchase_orders po " +
                     "JOIN autocore_suppliers s ON po.supplier_id = s.supplier_id " +
                     "LEFT JOIN autocore_users u ON po.created_by = u.user_id " +
                     "ORDER BY po.created_at DESC) WHERE ROWNUM <= ?";
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

    private PurchaseOrder map(ResultSet rs) throws SQLException {
        PurchaseOrder po = new PurchaseOrder();
        po.setPoId(rs.getInt("po_id"));
        po.setPoNumber(rs.getString("po_number"));
        po.setSupplierId(rs.getInt("supplier_id"));
        po.setSupplierName(rs.getString("supplier_name"));
        po.setOrderDate(rs.getDate("order_date"));
        po.setExpectedDate(rs.getDate("expected_date"));
        po.setReceivedDate(rs.getDate("received_date"));
        po.setStatus(rs.getString("status"));
        po.setTotalAmount(rs.getBigDecimal("total_amount"));
        po.setNotes(rs.getString("notes"));
        int uid = rs.getInt("created_by");
        if (!rs.wasNull()) {
            po.setCreatedBy(uid);
        }
        po.setCreatorName(rs.getString("creator_name"));
        po.setCreatedAt(rs.getDate("created_at"));
        return po;
    }
}
