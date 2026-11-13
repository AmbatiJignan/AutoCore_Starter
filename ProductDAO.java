package com.autocore.dao;

import com.autocore.model.Product;
import com.autocore.util.DBConnection;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ProductDAO {

    public List<Product> findAll(String search) throws SQLException {
        return findAllFiltered(search, null, null, 1, 1000);
    }

    public List<Product> findAllFiltered(String search, String category, String stockFilter, int page, int pageSize) throws SQLException {
        List<Product> out = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT product_id, part_number, product_name, brand, vehicle_model, " +
                "category, purchase_price, selling_price, stock_quantity, minimum_stock " +
                "FROM autocore_products WHERE 1=1 ");

        List<Object> params = new ArrayList<>();

        if (search != null && !search.trim().isEmpty()) {
            sql.append("AND (LOWER(part_number) LIKE LOWER(?) ")
               .append("OR LOWER(product_name) LIKE LOWER(?) ")
               .append("OR LOWER(NVL(brand, '-')) LIKE LOWER(?) ")
               .append("OR LOWER(NVL(vehicle_model, '-')) LIKE LOWER(?) ")
               .append("OR LOWER(NVL(category, '-')) LIKE LOWER(?)) ");
            String q = "%" + search.trim() + "%";
            for (int i = 0; i < 5; i++) {
                params.add(q);
            }
        }

        if (category != null && !category.trim().isEmpty() && !"ALL".equalsIgnoreCase(category)) {
            sql.append("AND category = ? ");
            params.add(category.trim());
        }

        if (stockFilter != null && !stockFilter.trim().isEmpty()) {
            if ("LOW".equalsIgnoreCase(stockFilter)) {
                sql.append("AND stock_quantity <= minimum_stock AND stock_quantity > 0 ");
            } else if ("OUT".equalsIgnoreCase(stockFilter)) {
                sql.append("AND stock_quantity = 0 ");
            } else if ("HEALTHY".equalsIgnoreCase(stockFilter)) {
                sql.append("AND stock_quantity > minimum_stock ");
            }
        }

        sql.append("ORDER BY product_name ASC");

        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, pageSize);
        int startRow = (safePage - 1) * safePageSize + 1;
        int endRow = safePage * safePageSize;

        // Oracle 11g compatible 3-tier pagination query
        String pagedSql = "SELECT * FROM ( " +
                          "  SELECT a.*, ROWNUM rnum FROM ( " + sql.toString() + " ) a " +
                          "  WHERE ROWNUM <= ? " +
                          ") WHERE rnum >= ?";

        try (Connection c = DBConnection.getConnection();
             PreparedStatement p = c.prepareStatement(pagedSql)) {
            int idx = 1;
            for (Object param : params) {
                p.setObject(idx++, param);
            }
            p.setInt(idx++, endRow);
            p.setInt(idx, startRow);

            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    out.add(map(r));
                }
            }
        }
        return out;
    }

    public int countFiltered(String search, String category, String stockFilter) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM autocore_products WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (search != null && !search.trim().isEmpty()) {
            sql.append("AND (LOWER(part_number) LIKE LOWER(?) ")
               .append("OR LOWER(product_name) LIKE LOWER(?) ")
               .append("OR LOWER(NVL(brand, '-')) LIKE LOWER(?) ")
               .append("OR LOWER(NVL(vehicle_model, '-')) LIKE LOWER(?) ")
               .append("OR LOWER(NVL(category, '-')) LIKE LOWER(?)) ");
            String q = "%" + search.trim() + "%";
            for (int i = 0; i < 5; i++) {
                params.add(q);
            }
        }

        if (category != null && !category.trim().isEmpty() && !"ALL".equalsIgnoreCase(category)) {
            sql.append("AND category = ? ");
            params.add(category.trim());
        }

        if (stockFilter != null && !stockFilter.trim().isEmpty()) {
            if ("LOW".equalsIgnoreCase(stockFilter)) {
                sql.append("AND stock_quantity <= minimum_stock AND stock_quantity > 0 ");
            } else if ("OUT".equalsIgnoreCase(stockFilter)) {
                sql.append("AND stock_quantity = 0 ");
            } else if ("HEALTHY".equalsIgnoreCase(stockFilter)) {
                sql.append("AND stock_quantity > minimum_stock ");
            }
        }

        try (Connection c = DBConnection.getConnection();
             PreparedStatement p = c.prepareStatement(sql.toString())) {
            int idx = 1;
            for (Object param : params) {
                p.setObject(idx++, param);
            }
            try (ResultSet r = p.executeQuery()) {
                return r.next() ? r.getInt(1) : 0;
            }
        }
    }

    public List<String> distinctCategories() throws SQLException {
        List<String> list = new ArrayList<>();
        String sql = "SELECT DISTINCT category FROM autocore_products WHERE category IS NOT NULL ORDER BY category ASC";
        try (Connection c = DBConnection.getConnection();
             Statement s = c.createStatement();
             ResultSet r = s.executeQuery(sql)) {
            while (r.next()) {
                list.add(r.getString(1));
            }
        }
        return list;
    }

    public Product findById(int id) throws SQLException {
        String sql = "SELECT product_id, part_number, product_name, brand, vehicle_model, " +
                "category, purchase_price, selling_price, stock_quantity, minimum_stock " +
                "FROM autocore_products WHERE product_id = ?";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement p = c.prepareStatement(sql)) {
            p.setInt(1, id);
            try (ResultSet r = p.executeQuery()) {
                return r.next() ? map(r) : null;
            }
        }
    }

    public void insert(Product x) throws SQLException {
        insert(x, null);
    }

    public void insert(Product x, Integer userId) throws SQLException {
        try (Connection c = DBConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                int newProdId;
                try (Statement st = c.createStatement();
                     ResultSet rs = st.executeQuery("SELECT autocore_product_seq.NEXTVAL FROM dual")) {
                    if (!rs.next()) throw new SQLException("Unable to generate product ID");
                    newProdId = rs.getInt(1);
                }
                x.setProductId(newProdId);

                String s = "INSERT INTO autocore_products (product_id, part_number, product_name, " +
                        "brand, vehicle_model, category, purchase_price, selling_price, " +
                        "stock_quantity, minimum_stock, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, SYSDATE)";
                try (PreparedStatement p = c.prepareStatement(s)) {
                    p.setInt(1, newProdId);
                    bind(p, x, 2);
                    p.executeUpdate();
                }

                // If initial stock > 0, log opening stock movement
                if (x.getStockQuantity() > 0) {
                    StockMovementDAO smDao = new StockMovementDAO();
                    smDao.recordMovement(c, newProdId, "OPENING_STOCK", x.getStockQuantity(), 0, 
                            x.getStockQuantity(), "INIT-" + x.getPartNumber(), "Initial catalog stock registered", userId);
                }

                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    public void update(Product x) throws SQLException {
        String s = "UPDATE autocore_products SET part_number = ?, product_name = ?, brand = ?, " +
                "vehicle_model = ?, category = ?, purchase_price = ?, selling_price = ?, " +
                "stock_quantity = ?, minimum_stock = ? WHERE product_id = ?";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement p = c.prepareStatement(s)) {
            bind(p, x, 1);
            p.setInt(10, x.getProductId());
            if (p.executeUpdate() == 0) {
                throw new SQLException("Product not found");
            }
        }
    }

    public void delete(int id) throws SQLException {
        // Check if referenced in purchases, sales, or movements
        try (Connection c = DBConnection.getConnection()) {
            String checkPo = "SELECT COUNT(*) FROM autocore_po_items WHERE product_id = ?";
            try (PreparedStatement ps = c.prepareStatement(checkPo)) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        throw new SQLException("Cannot delete part: It is referenced in historical purchase orders. Keep it in inventory or set stock to 0.");
                    }
                }
            }
            String checkSale = "SELECT COUNT(*) FROM autocore_sale_items WHERE product_id = ?";
            try (PreparedStatement ps = c.prepareStatement(checkSale)) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        throw new SQLException("Cannot delete part: It is referenced in customer sales invoices. Historical transaction integrity must be preserved.");
                    }
                }
            }

            // Remove orphan movements if purely temporary or delete movements
            String delMov = "DELETE FROM autocore_stock_movements WHERE product_id = ?";
            try (PreparedStatement ps = c.prepareStatement(delMov)) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }

            String s = "DELETE FROM autocore_products WHERE product_id = ?";
            try (PreparedStatement p = c.prepareStatement(s)) {
                p.setInt(1, id);
                if (p.executeUpdate() == 0) {
                    throw new SQLException("Product not found");
                }
            }
        }
    }

    public boolean isPartNumberTaken(String partNumber, int excludeProductId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM autocore_products WHERE LOWER(part_number) = LOWER(?) AND product_id != ?";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, partNumber.trim());
            p.setInt(2, excludeProductId);
            try (ResultSet r = p.executeQuery()) {
                return r.next() && r.getInt(1) > 0;
            }
        }
    }

    public int countTotal() throws SQLException {
        String sql = "SELECT COUNT(*) FROM autocore_products";
        try (Connection c = DBConnection.getConnection();
             Statement s = c.createStatement();
             ResultSet r = s.executeQuery(sql)) {
            return r.next() ? r.getInt(1) : 0;
        }
    }

    public int countLowStock() throws SQLException {
        String sql = "SELECT COUNT(*) FROM autocore_products WHERE stock_quantity <= minimum_stock AND stock_quantity > 0";
        try (Connection c = DBConnection.getConnection();
             Statement s = c.createStatement();
             ResultSet r = s.executeQuery(sql)) {
            return r.next() ? r.getInt(1) : 0;
        }
    }

    public int countOutOfStock() throws SQLException {
        String sql = "SELECT COUNT(*) FROM autocore_products WHERE stock_quantity = 0";
        try (Connection c = DBConnection.getConnection();
             Statement s = c.createStatement();
             ResultSet r = s.executeQuery(sql)) {
            return r.next() ? r.getInt(1) : 0;
        }
    }

    public long totalStockUnits() throws SQLException {
        String sql = "SELECT NVL(SUM(stock_quantity), 0) FROM autocore_products";
        try (Connection c = DBConnection.getConnection();
             Statement s = c.createStatement();
             ResultSet r = s.executeQuery(sql)) {
            return r.next() ? r.getLong(1) : 0;
        }
    }

    public BigDecimal totalInventoryValue() throws SQLException {
        return totalSellingValuation();
    }

    public BigDecimal totalPurchaseValuation() throws SQLException {
        String sql = "SELECT NVL(SUM(stock_quantity * purchase_price), 0) FROM autocore_products";
        try (Connection c = DBConnection.getConnection();
             Statement s = c.createStatement();
             ResultSet r = s.executeQuery(sql)) {
            return r.next() ? r.getBigDecimal(1) : BigDecimal.ZERO;
        }
    }

    public BigDecimal totalSellingValuation() throws SQLException {
        String sql = "SELECT NVL(SUM(stock_quantity * selling_price), 0) FROM autocore_products";
        try (Connection c = DBConnection.getConnection();
             Statement s = c.createStatement();
             ResultSet r = s.executeQuery(sql)) {
            return r.next() ? r.getBigDecimal(1) : BigDecimal.ZERO;
        }
    }

    public List<Product> findLowStockProducts(int limit) throws SQLException {
        List<Product> list = new ArrayList<>();
        String sql = "SELECT * FROM (SELECT product_id, part_number, product_name, brand, " +
                "vehicle_model, category, purchase_price, selling_price, stock_quantity, minimum_stock " +
                "FROM autocore_products WHERE stock_quantity <= minimum_stock ORDER BY stock_quantity ASC) " +
                "WHERE ROWNUM <= ?";
        try (Connection c = DBConnection.getConnection();
             PreparedStatement p = c.prepareStatement(sql)) {
            p.setInt(1, limit);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    list.add(map(r));
                }
            }
        }
        return list;
    }

    private void bind(PreparedStatement p, Product x, int i) throws SQLException {
        p.setString(i++, x.getPartNumber());
        p.setString(i++, x.getProductName());
        p.setString(i++, x.getBrand());
        p.setString(i++, x.getVehicleModel());
        p.setString(i++, x.getCategory());
        p.setBigDecimal(i++, x.getPurchasePrice());
        p.setBigDecimal(i++, x.getSellingPrice());
        p.setInt(i++, x.getStockQuantity());
        p.setInt(i, x.getMinimumStock());
    }

    private Product map(ResultSet r) throws SQLException {
        Product x = new Product();
        x.setProductId(r.getInt("product_id"));
        x.setPartNumber(r.getString("part_number"));
        x.setProductName(r.getString("product_name"));
        x.setBrand(r.getString("brand"));
        x.setVehicleModel(r.getString("vehicle_model"));
        x.setCategory(r.getString("category"));
        x.setPurchasePrice(r.getBigDecimal("purchase_price"));
        x.setSellingPrice(r.getBigDecimal("selling_price"));
        x.setStockQuantity(r.getInt("stock_quantity"));
        x.setMinimumStock(r.getInt("minimum_stock"));
        return x;
    }
}
