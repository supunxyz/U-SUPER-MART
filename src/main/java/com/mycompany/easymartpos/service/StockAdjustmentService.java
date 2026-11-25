package com.mycompany.easymartpos.service;

import com.mycompany.easymartpos.DBConnection;
import com.mycompany.easymartpos.model.StockAdjustmentBatch;
import com.mycompany.easymartpos.model.StockAdjustmentHistory;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Controlled, batch-aware manual stock corrections. All writes are transactional. */
public final class StockAdjustmentService {
    public static final String ADJUSTMENT_IN = "ADJUSTMENT_IN";
    public static final String ADJUSTMENT_OUT = "ADJUSTMENT_OUT";

    @FunctionalInterface
    interface ConnectionProvider { Connection get() throws SQLException; }

    public record AddRequest(int productId, BigDecimal quantity, BigDecimal costPrice, BigDecimal sellingPrice,
            BigDecimal retailPrice, LocalDate expiryDate, String reason, int userId) { }
    public record RemoveRequest(int productId, int batchId, BigDecimal quantity, String reason, int userId) { }

    private final ConnectionProvider connections;

    public StockAdjustmentService() { this(DBConnection::getConnection); }
    StockAdjustmentService(ConnectionProvider connections) {
        if (connections == null) throw new IllegalArgumentException("Connection provider is required.");
        this.connections = connections;
    }

    public BigDecimal currentBatchTotal(int productId) throws SQLException {
        requireProduct(productId);
        try (Connection connection = connections.get();
             PreparedStatement statement = connection.prepareStatement("SELECT COALESCE(SUM(quantity_available),0) FROM product_batches WHERE product_id=?")) {
            statement.setInt(1, productId);
            try (ResultSet results = statement.executeQuery()) {
                results.next();
                return results.getBigDecimal(1);
            }
        }
    }

    /** Includes expired and inactive batches deliberately; any positive physical batch may be disposed/corrected. */
    public List<StockAdjustmentBatch> findRemovableBatches(int productId) throws SQLException {
        requireProduct(productId);
        String sql = "SELECT batch_id, product_id, source_type, cost_price, selling_price, retail_price, expiry_date, quantity_available, active "
                + "FROM product_batches WHERE product_id=? AND quantity_available>0 "
                + "ORDER BY CASE WHEN expiry_date IS NULL THEN 1 ELSE 0 END, expiry_date, created_at, batch_id";
        try (Connection connection = connections.get(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, productId);
            try (ResultSet results = statement.executeQuery()) {
                List<StockAdjustmentBatch> batches = new ArrayList<>();
                while (results.next()) {
                    Date expiry = results.getDate("expiry_date");
                    batches.add(new StockAdjustmentBatch(results.getInt("batch_id"), results.getInt("product_id"),
                            results.getString("source_type"), results.getBigDecimal("cost_price"), results.getBigDecimal("selling_price"),
                            results.getBigDecimal("retail_price"), expiry == null ? null : expiry.toLocalDate(),
                            results.getBigDecimal("quantity_available"), results.getBoolean("active")));
                }
                return List.copyOf(batches);
            }
        }
    }

    public List<StockAdjustmentHistory> history() throws SQLException {
        String sql = "SELECT sm.movement_date, p.product_name, sm.movement_type, sm.quantity, sm.reason, "
                + "COALESCE(u.username, CONCAT('User #', sm.user_id)) "
                + "FROM stock_movements sm JOIN products p ON p.product_id=sm.product_id "
                + "LEFT JOIN users u ON u.user_id=sm.user_id "
                + "WHERE sm.movement_type IN ('ADJUSTMENT_IN','ADJUSTMENT_OUT') "
                + "ORDER BY sm.movement_date DESC, sm.movement_id DESC LIMIT 200";
        try (Connection connection = connections.get(); PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {
            List<StockAdjustmentHistory> rows = new ArrayList<>();
            while (results.next()) {
                java.sql.Timestamp moved = results.getTimestamp(1);
                rows.add(new StockAdjustmentHistory(moved == null ? null : moved.toLocalDateTime(), results.getString(2),
                        results.getString(3), results.getBigDecimal(4), results.getString(5), results.getString(6)));
            }
            return List.copyOf(rows);
        }
    }

    public void addStock(AddRequest request) throws SQLException {
        validateAdd(request);
        try (Connection connection = connections.get()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement batch = connection.prepareStatement("INSERT INTO product_batches (product_id, purchase_id, purchase_item_id, source_type, cost_price, selling_price, retail_price, expiry_date, quantity_received, quantity_available, active) VALUES (?, NULL, NULL, 'ADJUSTMENT', ?, ?, ?, ?, ?, ?, 1)")) {
                    batch.setInt(1, request.productId()); batch.setBigDecimal(2, request.costPrice()); batch.setBigDecimal(3, request.sellingPrice());
                    if (request.retailPrice() == null) batch.setNull(4, Types.DECIMAL); else batch.setBigDecimal(4, request.retailPrice());
                    if (request.expiryDate() == null) batch.setNull(5, Types.DATE); else batch.setDate(5, Date.valueOf(request.expiryDate()));
                    batch.setBigDecimal(6, request.quantity()); batch.setBigDecimal(7, request.quantity());
                    requireOne(batch.executeUpdate(), "Adjustment batch could not be created.");
                }
                try (PreparedStatement product = connection.prepareStatement("UPDATE products SET stock_quantity=stock_quantity+? WHERE product_id=?")) {
                    product.setBigDecimal(1, request.quantity()); product.setInt(2, request.productId());
                    requireOne(product.executeUpdate(), "Product stock could not be updated.");
                }
                insertMovement(connection, request.productId(), ADJUSTMENT_IN, request.quantity(), request.reason(), request.userId());
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback(); throw exception;
            } finally { connection.setAutoCommit(true); }
        }
    }

    public void removeStock(RemoveRequest request) throws SQLException {
        validateRemove(request);
        try (Connection connection = connections.get()) {
            connection.setAutoCommit(false);
            try {
                LockedBatch locked = lockBatch(connection, request.batchId());
                validateLockedBatch(locked, request.productId(), request.quantity());
                try (PreparedStatement batch = connection.prepareStatement("UPDATE product_batches SET quantity_available=quantity_available-? WHERE batch_id=? AND product_id=? AND quantity_available>=?")) {
                    batch.setBigDecimal(1, request.quantity()); batch.setInt(2, request.batchId()); batch.setInt(3, request.productId()); batch.setBigDecimal(4, request.quantity());
                    requireOne(batch.executeUpdate(), "Selected batch no longer has enough stock.");
                }
                try (PreparedStatement product = connection.prepareStatement("UPDATE products SET stock_quantity=stock_quantity-? WHERE product_id=? AND stock_quantity>=?")) {
                    product.setBigDecimal(1, request.quantity()); product.setInt(2, request.productId()); product.setBigDecimal(3, request.quantity());
                    requireOne(product.executeUpdate(), "Product stock could not be reduced safely.");
                }
                insertMovement(connection, request.productId(), ADJUSTMENT_OUT, request.quantity().negate(), request.reason(), request.userId());
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback(); throw exception;
            } finally { connection.setAutoCommit(true); }
        }
    }

    static void validateAdd(AddRequest request) {
        if (request == null) throw new IllegalArgumentException("Adjustment details are required.");
        requireProduct(request.productId()); requireUser(request.userId()); requirePositive(request.quantity(), "Quantity");
        requirePositive(request.costPrice(), "Cost price"); requirePositive(request.sellingPrice(), "Our selling price");
        if (request.retailPrice() != null && request.retailPrice().signum() < 0) throw new IllegalArgumentException("Retail price cannot be negative.");
        if (request.reason() == null || request.reason().isBlank()) throw new IllegalArgumentException("Reason is required when adding stock.");
    }

    static void validateRemove(RemoveRequest request) {
        if (request == null) throw new IllegalArgumentException("Adjustment details are required.");
        requireProduct(request.productId()); if (request.batchId() <= 0) throw new IllegalArgumentException("Select the exact batch to remove stock from.");
        requireUser(request.userId()); requirePositive(request.quantity(), "Quantity");
        if (request.reason() == null || request.reason().isBlank()) throw new IllegalArgumentException("Reason is required when removing stock.");
    }

    static void validateLockedBatch(LockedBatch batch, int productId, BigDecimal quantity) {
        if (batch == null || batch.productId() != productId || batch.available().compareTo(quantity) < 0)
            throw new IllegalArgumentException("Selected batch no longer has enough stock. Refresh and try again.");
    }

    private LockedBatch lockBatch(Connection connection, int batchId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT product_id, quantity_available FROM product_batches WHERE batch_id=? FOR UPDATE")) {
            statement.setInt(1, batchId);
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) throw new IllegalArgumentException("Selected batch no longer exists.");
                return new LockedBatch(results.getInt("product_id"), results.getBigDecimal("quantity_available"));
            }
        }
    }

    private void insertMovement(Connection connection, int productId, String type, BigDecimal quantity, String reason, int userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO stock_movements (product_id, movement_type, quantity, reference_id, user_id, reason) VALUES (?, ?, ?, NULL, ?, ?)")) {
            statement.setInt(1, productId); statement.setString(2, type); statement.setBigDecimal(3, quantity);
            statement.setInt(4, userId); statement.setString(5, blankToNull(reason));
            requireOne(statement.executeUpdate(), "Stock movement could not be recorded.");
        }
    }

    private static void requireProduct(int productId) { if (productId <= 0) throw new IllegalArgumentException("Select a product."); }
    private static void requireUser(int userId) { if (userId <= 0) throw new IllegalArgumentException("An authenticated user is required."); }
    private static void requirePositive(BigDecimal value, String name) { if (value == null || value.signum() <= 0) throw new IllegalArgumentException(name + " must be greater than zero."); }
    private static void requireOne(int affected, String message) { if (affected != 1) throw new IllegalStateException(message); }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    record LockedBatch(int productId, BigDecimal available) { }
}
