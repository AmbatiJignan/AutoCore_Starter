package com.autocore.dao;

import com.autocore.model.StockMovement;
import com.autocore.util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class StockMovementDAO {

    /**
     * Record a stock movement inside an ongoing database transaction.
     */
    public void recordMovement(Connection con, int productId, String movementType,
                               int quantityChange, int previousStock, int resultingStock,
                               String referenceId, String notes, Integer userId) throws SQLException {
        String sql = "INSERT INTO autocore_stock_movements (movement_id, product_id, movement_type, " +
                     "quantity_change, previous_stock, resulting_stock, reference_id, notes, user_id, created_at) " +
                     "VALUES (autocore_movement_seq.NEXTVAL, ?, ?, ?, ?, ?, ?, ?, ?, SYSTIMESTAMP)";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, productId);
            ps.setString(2, movementType);
            ps.setInt(3, quantityChange);
            ps.setInt(4, previousStock);
            ps.setInt(5, resultingStock);
            ps.setString(6, referenceId);
            ps.setString(7, notes);
            if (userId != null && userId > 0) {
                ps.setInt(8, userId);
            } else {
                ps.setNull(8, Types.NUMERIC);
            }
            ps.executeUpdate();
        }
    }

    /**
     * Perform an atomic manual stock adjustment with validation and movement logging.
     */
    public void recordManualAdjustment(int productId, int quantityChange, String reason, int userId) throws SQLException {
        if (quantityChange == 0) {
            throw new IllegalArgumentException("Adjustment quantity cannot be zero.");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("A reason is mandatory for manual stock adjustments.");
        }

        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                // 1. Lock and fetch current product stock
                int currentStock;
                String partNumber;
                String selectSql = "SELECT stock_quantity, part_number FROM autocore_products WHERE product_id = ? FOR UPDATE";
                try (PreparedStatement ps = con.prepareStatement(selectSql)) {
                    ps.setInt(1, productId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException("Product with ID " + productId + " not found.");
                        }
                        currentStock = rs.getInt("stock_quantity");
                        partNumber = rs.getString("part_number");
                    }
                }

                int resultingStock = currentStock + quantityChange;
                if (resultingStock < 0) {
                    throw new IllegalArgumentException("Adjustment would cause stock to become negative. Current stock: " 
                            + currentStock + ", Requested change: " + quantityChange);
                }

                // 2. Update product stock
                String updateSql = "UPDATE autocore_products SET stock_quantity = ? WHERE product_id = ?";
                try (PreparedStatement ps = con.prepareStatement(updateSql)) {
                    ps.setInt(1, resultingStock);
                    ps.setInt(2, productId);
                    ps.executeUpdate();
                }

                // 3. Log movement
                String refId = "ADJ-" + partNumber;
                recordMovement(con, productId, "MANUAL_ADJUSTMENT", quantityChange, currentStock, 
                               resultingStock, refId, reason.trim(), userId);

                con.commit();
            } catch (Exception e) {
                con.rollback();
                throw e;
            }
        }
    }

    public List<StockMovement> findAll(String search, String movementType, String fromDate, String toDate, int limit) throws SQLException {
        List<StockMovement> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT m.movement_id, m.product_id, p.part_number, p.product_name, m.movement_type, " +
                "m.quantity_change, m.previous_stock, m.resulting_stock, m.reference_id, m.notes, " +
                "m.user_id, u.full_name AS user_name, m.created_at " +
                "FROM autocore_stock_movements m " +
                "JOIN autocore_products p ON m.product_id = p.product_id " +
                "LEFT JOIN autocore_users u ON m.user_id = u.user_id " +
                "WHERE 1=1 ");

        List<Object> params = new ArrayList<>();

        if (movementType != null && !movementType.trim().isEmpty() && !"ALL".equalsIgnoreCase(movementType)) {
            sql.append("AND m.movement_type = ? ");
            params.add(movementType.trim());
        }

        if (search != null && !search.trim().isEmpty()) {
            sql.append("AND (LOWER(p.part_number) LIKE LOWER(?) ")
               .append("OR LOWER(p.product_name) LIKE LOWER(?) ")
               .append("OR LOWER(NVL(m.reference_id, '-')) LIKE LOWER(?) ")
               .append("OR LOWER(NVL(m.notes, '-')) LIKE LOWER(?)) ");
            String pat = "%" + search.trim() + "%";
            for (int i = 0; i < 4; i++) {
                params.add(pat);
            }
        }

        if (fromDate != null && !fromDate.trim().isEmpty()) {
            sql.append("AND TRUNC(m.created_at) >= TO_DATE(?, 'YYYY-MM-DD') ");
            params.add(fromDate.trim());
        }

        if (toDate != null && !toDate.trim().isEmpty()) {
            sql.append("AND TRUNC(m.created_at) <= TO_DATE(?, 'YYYY-MM-DD') ");
            params.add(toDate.trim());
        }

        sql.append("ORDER BY m.created_at DESC, m.movement_id DESC");

        if (limit > 0) {
            String outerSql = "SELECT * FROM (" + sql.toString() + ") WHERE ROWNUM <= ?";
            try (Connection con = DBConnection.getConnection();
                 PreparedStatement ps = con.prepareStatement(outerSql)) {
                int idx = 1;
                for (Object param : params) {
                    ps.setObject(idx++, param);
                }
                ps.setInt(idx, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(map(rs));
                    }
                }
            }
        } else {
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
        }

        return list;
    }

    public List<StockMovement> findRecent(int limit) throws SQLException {
        return findAll(null, null, null, null, limit);
    }

    public List<StockMovement> findByProductId(int productId) throws SQLException {
        List<StockMovement> list = new ArrayList<>();
        String sql = "SELECT m.movement_id, m.product_id, p.part_number, p.product_name, m.movement_type, " +
                     "m.quantity_change, m.previous_stock, m.resulting_stock, m.reference_id, m.notes, " +
                     "m.user_id, u.full_name AS user_name, m.created_at " +
                     "FROM autocore_stock_movements m " +
                     "JOIN autocore_products p ON m.product_id = p.product_id " +
                     "LEFT JOIN autocore_users u ON m.user_id = u.user_id " +
                     "WHERE m.product_id = ? " +
                     "ORDER BY m.created_at DESC";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        }
        return list;
    }

    private StockMovement map(ResultSet rs) throws SQLException {
        StockMovement m = new StockMovement();
        m.setMovementId(rs.getInt("movement_id"));
        m.setProductId(rs.getInt("product_id"));
        m.setPartNumber(rs.getString("part_number"));
        m.setProductName(rs.getString("product_name"));
        m.setMovementType(rs.getString("movement_type"));
        m.setQuantityChange(rs.getInt("quantity_change"));
        m.setPreviousStock(rs.getInt("previous_stock"));
        m.setResultingStock(rs.getInt("resulting_stock"));
        m.setReferenceId(rs.getString("reference_id"));
        m.setNotes(rs.getString("notes"));
        int uid = rs.getInt("user_id");
        if (!rs.wasNull()) {
            m.setUserId(uid);
        }
        m.setUserName(rs.getString("user_name"));
        m.setCreatedAt(rs.getTimestamp("created_at"));
        return m;
    }
}
