
package com.autocore.dao;

import com.autocore.model.User;
import com.autocore.util.DBConnection;
import com.autocore.util.PasswordUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class UserDAO {

    public User findByUsername(String username) throws SQLException {
        if (username == null || username.trim().isEmpty()) {
            return null;
        }
        String sql = "SELECT user_id, username, password_hash, " +
                "full_name, role, is_active, created_at " +
                "FROM autocore_users WHERE LOWER(username) = LOWER(?)";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, username.trim());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return map(rs);
                }
            }
        }
        return null;
    }

    public User findById(int userId) throws SQLException {
        String sql = "SELECT user_id, username, password_hash, " +
                "full_name, role, is_active, created_at " +
                "FROM autocore_users WHERE user_id = ?";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, userId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return map(rs);
                }
            }
        }
        return null;
    }

    public List<User> findAll() throws SQLException {
        List<User> list = new ArrayList<>();
        String sql = "SELECT user_id, username, password_hash, " +
                "full_name, role, is_active, created_at " +
                "FROM autocore_users ORDER BY user_id ASC";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                list.add(map(rs));
            }
        }
        return list;
    }

    public boolean createUser(
            String username,
            String password,
            String fullName,
            String role) throws SQLException {

        String sql = "INSERT INTO autocore_users " +
                "(user_id, username, password_hash, full_name, role, is_active) " +
                "VALUES (autocore_user_seq.NEXTVAL, ?, ?, ?, ?, 'Y')";

        String hash = PasswordUtil.hash(password);

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, username.trim());
            ps.setString(2, hash);
            ps.setString(3, fullName.trim());
            ps.setString(4, role != null ? role.toUpperCase() : "STAFF");

            return ps.executeUpdate() == 1;
        }
    }

    public boolean updateStatus(int userId, String isActive) throws SQLException {
        String sql = "UPDATE autocore_users SET is_active = ? WHERE user_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, "Y".equalsIgnoreCase(isActive) ? "Y" : "N");
            ps.setInt(2, userId);

            return ps.executeUpdate() == 1;
        }
    }

    public boolean updateRole(int userId, String role) throws SQLException {
        String sql = "UPDATE autocore_users SET role = ? WHERE user_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, "ADMIN".equalsIgnoreCase(role) ? "ADMIN" : "STAFF");
            ps.setInt(2, userId);

            return ps.executeUpdate() == 1;
        }
    }

    public boolean updatePassword(int userId, String newPassword) throws SQLException {
        String sql = "UPDATE autocore_users SET password_hash = ? WHERE user_id = ?";
        String hash = PasswordUtil.hash(newPassword);

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, hash);
            ps.setInt(2, userId);

            return ps.executeUpdate() == 1;
        }
    }

    public boolean hasAdmin() throws SQLException {
        String sql = "SELECT COUNT(*) FROM autocore_users WHERE role = 'ADMIN'";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            return rs.next() && rs.getInt(1) > 0;
        }
    }

    public int countActiveAdmins() throws SQLException {
        String sql = "SELECT COUNT(*) FROM autocore_users WHERE role = 'ADMIN' AND is_active = 'Y'";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    public boolean isUsernameTaken(String username, int excludeUserId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM autocore_users WHERE LOWER(username) = LOWER(?) AND user_id != ?";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, username.trim());
            ps.setInt(2, excludeUserId);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private User map(ResultSet rs) throws SQLException {
        User user = new User();
        user.setUserId(rs.getInt("user_id"));
        user.setUsername(rs.getString("username"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setFullName(rs.getString("full_name"));
        user.setRole(rs.getString("role"));
        user.setActive(rs.getString("is_active"));
        user.setCreatedAt(rs.getTimestamp("created_at"));
        return user;
    }
}

