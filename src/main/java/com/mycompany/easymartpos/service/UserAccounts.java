package com.mycompany.easymartpos.service;

import com.mycompany.easymartpos.DBConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Account operations shared by first-run setup, login, and account management. */
public final class UserAccounts {
    private static final String LEGACY_ADMIN_USER = "usupermart";
    private static final String LEGACY_ADMIN_PASSWORD = "usupermart123@";
    public record User(int id, String username, String role, boolean active) { }

    public boolean needsSetup() throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement query = connection.prepareStatement("SELECT 1 FROM users LIMIT 1");
             ResultSet rows = query.executeQuery()) { return !rows.next(); }
    }

    /** Older bundled installations receive a local password reset before anyone can use the shipped credential. */
    public boolean needsLegacyAdminReset() throws SQLException {
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement query = connection.prepareStatement("SELECT 1 FROM users WHERE username=? AND password=? AND role='ADMIN' AND active=TRUE")) {
            query.setString(1, LEGACY_ADMIN_USER); query.setString(2, LEGACY_ADMIN_PASSWORD);
            try (ResultSet rows = query.executeQuery()) { return rows.next(); }
        }
    }

    public void replaceLegacyAdminPassword(char[] replacement) throws SQLException {
        PasswordHasher.requireStrong(replacement);
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement update = connection.prepareStatement("UPDATE users SET password=? WHERE username=? AND password=? AND role='ADMIN' AND active=TRUE")) {
            update.setString(1, PasswordHasher.hash(replacement)); update.setString(2, LEGACY_ADMIN_USER);
            update.setString(3, LEGACY_ADMIN_PASSWORD);
            if (update.executeUpdate() != 1) throw new IllegalStateException("The legacy administrator account changed. Restart setup.");
        }
    }

    /** Hash every remaining plaintext account on startup, including accounts that rarely sign in. */
    public void upgradeLegacyPasswords() throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement query = connection.prepareStatement("SELECT user_id, password FROM users FOR UPDATE");
                 ResultSet rows = query.executeQuery()) {
                List<Integer> ids = new ArrayList<>();
                List<String> plain = new ArrayList<>();
                while (rows.next()) {
                    String stored = rows.getString("password");
                    if (!PasswordHasher.isHashed(stored)) { ids.add(rows.getInt("user_id")); plain.add(stored); }
                }
                try (PreparedStatement update = connection.prepareStatement("UPDATE users SET password=? WHERE user_id=? AND password=?")) {
                    for (int i = 0; i < ids.size(); i++) {
                        char[] password = plain.get(i).toCharArray();
                        try { update.setString(1, PasswordHasher.hashLegacy(password)); }
                        finally { Arrays.fill(password, '\0'); }
                        update.setInt(2, ids.get(i)); update.setString(3, plain.get(i));
                        if (update.executeUpdate() != 1) throw new SQLException("A legacy password changed during migration.");
                    }
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) { connection.rollback(); throw exception; }
            finally { connection.setAutoCommit(true); }
        }
    }

    public void createFirstAdmin(String username, char[] password) throws SQLException {
        validateUsername(username); PasswordHasher.requireStrong(password);
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                // Lock the table for setup on external servers as well as the bundled single-instance server.
                try (PreparedStatement lock = connection.prepareStatement("SELECT user_id FROM users FOR UPDATE");
                     ResultSet rows = lock.executeQuery()) {
                    if (rows.next()) throw new IllegalStateException("Administrator setup has already been completed.");
                }
                insert(connection, username, password, "ADMIN");
                connection.commit();
            } catch (SQLException | RuntimeException exception) { connection.rollback(); throw exception; }
            finally { connection.setAutoCommit(true); }
        }
    }

    public User authenticate(String username, char[] password) throws SQLException {
        if (username == null || password == null) return null;
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement query = connection.prepareStatement("SELECT user_id, username, password, role, active FROM users WHERE username=?")) {
            query.setString(1, username.trim());
            try (ResultSet rows = query.executeQuery()) {
                if (!rows.next()) return null;
                String stored = rows.getString("password");
                if (!rows.getBoolean("active") || !PasswordHasher.verify(password, stored)) return null;
                int id = rows.getInt("user_id");
                if (!PasswordHasher.isHashed(stored)) {
                    // Upgrade a legacy password only if it still holds the value we authenticated.
                    try (PreparedStatement upgrade = connection.prepareStatement("UPDATE users SET password=? WHERE user_id=? AND password=?")) {
                        upgrade.setString(1, PasswordHasher.hashLegacy(password)); upgrade.setInt(2, id);
                        upgrade.setString(3, stored);
                        if (upgrade.executeUpdate() != 1) return null;
                    }
                }
                return new User(id, rows.getString("username"), rows.getString("role"), true);
            }
        }
    }

    public void changeOwnPassword(char[] current, char[] replacement) throws SQLException {
        int id = Session.currentUserId(); PasswordHasher.requireStrong(replacement);
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement query = connection.prepareStatement("SELECT password FROM users WHERE user_id=? AND active=TRUE FOR UPDATE")) {
            connection.setAutoCommit(false);
            try {
                query.setInt(1, id);
                String stored;
                try (ResultSet rows = query.executeQuery()) {
                    if (!rows.next()) throw new SecurityException("Your account is unavailable.");
                    stored = rows.getString(1);
                }
                if (!PasswordHasher.verify(current, stored)) throw new IllegalArgumentException("Current password is incorrect.");
                if (PasswordHasher.verify(replacement, stored)) throw new IllegalArgumentException("Choose a different password.");
                updatePassword(connection, id, replacement);
                connection.commit();
            } catch (SQLException | RuntimeException exception) { connection.rollback(); throw exception; }
            finally { connection.setAutoCommit(true); }
        }
    }

    public List<User> listUsers() throws SQLException {
        Authorization.requireAdmin();
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement query = connection.prepareStatement("SELECT user_id, username, role, active FROM users ORDER BY username");
             ResultSet rows = query.executeQuery()) {
            List<User> users = new ArrayList<>();
            while (rows.next()) users.add(new User(rows.getInt(1), rows.getString(2), rows.getString(3), rows.getBoolean(4)));
            return List.copyOf(users);
        }
    }

    public void createUser(String username, char[] password, String role) throws SQLException {
        Authorization.requireAdmin(); validateUsername(username); PasswordHasher.requireStrong(password);
        if (!"ADMIN".equals(role) && !"CASHIER".equals(role)) throw new IllegalArgumentException("Choose ADMIN or CASHIER.");
        try (Connection connection = DBConnection.getConnection()) { insert(connection, username, password, role); }
    }

    public void resetPassword(int userId, char[] password) throws SQLException {
        Authorization.requireAdmin(); PasswordHasher.requireStrong(password);
        try (Connection connection = DBConnection.getConnection()) { updatePassword(connection, userId, password); }
    }

    public void setActive(int userId, boolean active) throws SQLException {
        Authorization.requireAdmin();
        if (userId == Session.currentUserId() && !active) throw new IllegalArgumentException("You cannot disable your own account.");
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (!active) {
                    try (PreparedStatement target = connection.prepareStatement("SELECT role FROM users WHERE user_id=? FOR UPDATE")) {
                        target.setInt(1, userId);
                        try (ResultSet row = target.executeQuery()) {
                            if (!row.next()) throw new IllegalArgumentException("User not found.");
                            if ("ADMIN".equals(row.getString(1))) {
                                try (PreparedStatement admins = connection.prepareStatement("SELECT user_id FROM users WHERE role='ADMIN' AND active=TRUE FOR UPDATE");
                                     ResultSet others = admins.executeQuery()) {
                                    int count = 0; while (others.next()) count++;
                                    if (count <= 1) throw new IllegalArgumentException("Keep at least one active administrator.");
                                }
                            }
                        }
                    }
                }
                try (PreparedStatement update = connection.prepareStatement("UPDATE users SET active=? WHERE user_id=?")) {
                    update.setBoolean(1, active); update.setInt(2, userId);
                    if (update.executeUpdate() != 1) throw new IllegalArgumentException("User not found.");
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) { connection.rollback(); throw exception; }
            finally { connection.setAutoCommit(true); }
        }
    }

    private static void insert(Connection connection, String username, char[] password, String role) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO users (username, password, role, active) VALUES (?, ?, ?, TRUE)")) {
            insert.setString(1, username.trim()); insert.setString(2, PasswordHasher.hash(password)); insert.setString(3, role);
            insert.executeUpdate();
        }
    }

    private static void updatePassword(Connection connection, int userId, char[] password) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement("UPDATE users SET password=? WHERE user_id=?")) {
            update.setString(1, PasswordHasher.hash(password)); update.setInt(2, userId);
            if (update.executeUpdate() != 1) throw new IllegalArgumentException("User not found.");
        }
    }

    private static void validateUsername(String username) {
        if (username == null || !username.trim().matches("[A-Za-z0-9._-]{3,50}"))
            throw new IllegalArgumentException("Username must be 3 to 50 letters, digits, dots, dashes, or underscores.");
    }
}
