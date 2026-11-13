package com.autocore.dao;

import com.autocore.model.Supplier;
import com.autocore.util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SupplierDAO {

    public List<Supplier> findAll(String search, Boolean activeOnly) throws SQLException {
        List<Supplier> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT supplier_id, supplier_name, contact_person, phone, email, address, tax_id, is_active, created_at " +
                "FROM autocore_suppliers WHERE 1=1 ");

        if (activeOnly != null && activeOnly) {
            sql.append("AND is_active = 'Y' ");
        }

        if (search != null && !search.trim().isEmpty()) {
            sql.append("AND (LOWER(supplier_name) LIKE LOWER(?) ")
               .append("OR LOWER(NVL(contact_person, '-')) LIKE LOWER(?) ")
               .append("OR LOWER(NVL(phone, '-')) LIKE LOWER(?) ")
               .append("OR LOWER(NVL(tax_id, '-')) LIKE LOWER(?)) ");
        }

        sql.append("ORDER BY supplier_name ASC");

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {

            if (search != null && !search.trim().isEmpty()) {
                String pattern = "%" + search.trim() + "%";
                for (int i = 1; i <= 4; i++) {
                    ps.setString(i, pattern);
                }
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        }
        return list;
    }

    public Supplier findById(int id) throws SQLException {
        String sql = "SELECT supplier_id, supplier_name, contact_person, phone, email, address, tax_id, is_active, created_at " +
                     "FROM autocore_suppliers WHERE supplier_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    public void insert(Supplier s) throws SQLException {
        String sql = "INSERT INTO autocore_suppliers (supplier_id, supplier_name, contact_person, phone, email, address, tax_id, is_active, created_at) " +
                     "VALUES (autocore_supplier_seq.NEXTVAL, ?, ?, ?, ?, ?, ?, ?, SYSDATE)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            bind(ps, s, 1);
            ps.setString(7, s.getIsActive() != null ? s.getIsActive() : "Y");
            ps.executeUpdate();
        }
    }

    public void update(Supplier s) throws SQLException {
        String sql = "UPDATE autocore_suppliers SET supplier_name = ?, contact_person = ?, phone = ?, email = ?, " +
                     "address = ?, tax_id = ?, is_active = ? WHERE supplier_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            bind(ps, s, 1);
            ps.setString(7, s.getIsActive() != null ? s.getIsActive() : "Y");
            ps.setInt(8, s.getSupplierId());
            if (ps.executeUpdate() == 0) {
                throw new SQLException("Supplier not found for update");
            }
        }
    }

    public void setActiveStatus(int id, boolean active) throws SQLException {
        String sql = "UPDATE autocore_suppliers SET is_active = ? WHERE supplier_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, active ? "Y" : "N");
            ps.setInt(2, id);
            if (ps.executeUpdate() == 0) {
                throw new SQLException("Supplier not found");
            }
        }
    }

    public boolean hasPurchaseOrders(int supplierId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM autocore_purchase_orders WHERE supplier_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, supplierId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    public void delete(int id) throws SQLException {
        if (hasPurchaseOrders(id)) {
            throw new SQLException("Cannot delete supplier with recorded purchase orders. Deactivate the supplier instead.");
        }
        String sql = "DELETE FROM autocore_suppliers WHERE supplier_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    public int countActive() throws SQLException {
        String sql = "SELECT COUNT(*) FROM autocore_suppliers WHERE is_active = 'Y'";
        try (Connection con = DBConnection.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private void bind(PreparedStatement ps, Supplier s, int i) throws SQLException {
        ps.setString(i++, s.getSupplierName());
        ps.setString(i++, s.getContactPerson());
        ps.setString(i++, s.getPhone());
        ps.setString(i++, s.getEmail());
        ps.setString(i++, s.getAddress());
        ps.setString(i++, s.getTaxId());
    }

    private Supplier map(ResultSet rs) throws SQLException {
        Supplier s = new Supplier();
        s.setSupplierId(rs.getInt("supplier_id"));
        s.setSupplierName(rs.getString("supplier_name"));
        s.setContactPerson(rs.getString("contact_person"));
        s.setPhone(rs.getString("phone"));
        s.setEmail(rs.getString("email"));
        s.setAddress(rs.getString("address"));
        s.setTaxId(rs.getString("tax_id"));
        s.setIsActive(rs.getString("is_active"));
        s.setCreatedAt(rs.getDate("created_at"));
        return s;
    }
}
