package com.mycompany.easymartpos.service;

import com.mycompany.easymartpos.DBConnection;
import com.mycompany.easymartpos.model.CashierShift;
import com.mycompany.easymartpos.model.ShiftTotals;
import com.mycompany.easymartpos.util.Money;
import com.mycompany.easymartpos.util.SchemaSupport;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Optional;

/** Transactional open/close and sales-total handling for the logged-in cashier. */
public final class ShiftService {
    public static final String SETUP_MESSAGE = "Cashier Shift setup is not complete. Apply the documented database upgrade before starting cashier shifts.";

    public boolean isSchemaReady() throws SQLException {
        try (Connection connection = DBConnection.getConnection()) { return isSchemaReady(connection); }
    }

    public Optional<CashierShift> currentOpenShiftForCurrentUser() throws SQLException {
        return currentOpenShift(Session.currentUserId());
    }

    public Optional<CashierShift> currentOpenShift(int userId) throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            if (!isSchemaReady(connection)) return Optional.empty();
            return findOpenShift(connection, userId, false);
        }
    }

    public CashierShift startCurrentCashierShift(BigDecimal openingCash) throws SQLException {
        requireCashier();
        if (openingCash == null || openingCash.signum() < 0) throw new IllegalArgumentException("Opening cash cannot be negative.");
        int userId = Session.currentUserId();
        try (Connection connection = DBConnection.getConnection()) {
            requireSchema(connection); connection.setAutoCommit(false);
            try {
                CashierShift existing = findOpenShift(connection, userId, true).orElse(null);
                if (existing != null) throw new IllegalStateException("You already have an open shift started at " + existing.openedAt() + ".");
                try (PreparedStatement statement = connection.prepareStatement("INSERT INTO cashier_shifts (user_id, opening_cash, opened_at, status) VALUES (?, ?, CURRENT_TIMESTAMP, 'OPEN')", Statement.RETURN_GENERATED_KEYS)) {
                    statement.setInt(1, userId); statement.setBigDecimal(2, openingCash); statement.executeUpdate();
                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("Shift ID was not generated.");
                        int id = keys.getInt(1); connection.commit();
                        return findById(connection, id).orElseThrow(() -> new SQLException("Started shift could not be read."));
                    }
                }
            } catch (SQLException | RuntimeException exception) { connection.rollback(); throw exception; }
            finally { connection.setAutoCommit(true); }
        }
    }

    public ShiftTotals totalsForShift(int shiftId) throws SQLException {
        try (Connection connection = DBConnection.getConnection()) { requireSchema(connection); return totalsForShift(connection, shiftId); }
    }

    public ShiftTotals currentTotalsForCurrentUser() throws SQLException {
        CashierShift shift = currentOpenShiftForCurrentUser().orElseThrow(() -> new IllegalStateException("Start a shift before making sales."));
        return totalsForShift(shift.shiftId());
    }

    public void closeCurrentCashierShift(BigDecimal closingCash) throws SQLException {
        requireCashier();
        if (closingCash == null || closingCash.signum() < 0) throw new IllegalArgumentException("Closing cash cannot be negative.");
        int userId = Session.currentUserId();
        try (Connection connection = DBConnection.getConnection()) {
            requireSchema(connection); connection.setAutoCommit(false);
            try {
                CashierShift shift = findOpenShift(connection, userId, true).orElseThrow(() -> new IllegalStateException("You do not have an open shift to close."));
                ShiftTotals totals = totalsForShift(connection, shift.shiftId());
                BigDecimal expected = ShiftCalculator.expectedCash(shift.openingCash(), totals.cashSales());
                BigDecimal difference = ShiftCalculator.cashDifference(closingCash, expected);
                try (PreparedStatement statement = connection.prepareStatement("UPDATE cashier_shifts SET closed_at=CURRENT_TIMESTAMP, closing_cash=?, expected_cash=?, cash_difference=?, status='CLOSED' WHERE shift_id=? AND user_id=? AND status='OPEN'")) {
                    statement.setBigDecimal(1, closingCash); statement.setBigDecimal(2, expected); statement.setBigDecimal(3, difference); statement.setInt(4, shift.shiftId()); statement.setInt(5, userId);
                    if (statement.executeUpdate() != 1) throw new IllegalStateException("The shift was already closed or is no longer available.");
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) { connection.rollback(); throw exception; }
            finally { connection.setAutoCommit(true); }
        }
    }

    public int requireOpenShiftIdForCurrentCashier() throws SQLException {
        requireCashier();
        if (!isSchemaReady()) throw new IllegalStateException(SETUP_MESSAGE);
        return currentOpenShiftForCurrentUser().orElseThrow(() -> new IllegalStateException("Start a shift before completing a sale.")).shiftId();
    }

    private ShiftTotals totalsForShift(Connection connection, int shiftId) throws SQLException {
        String sql = "SELECT COALESCE(SUM(s.total_amount),0) sales, COUNT(*) transactions, COALESCE(SUM(p.cash_sales),0) cash_sales, COALESCE(SUM(p.card_sales),0) card_sales, COALESCE(SUM(p.transfer_sales),0) transfer_sales "
                + "FROM sales s LEFT JOIN (SELECT sale_id, SUM(CASE WHEN payment_method='CASH' THEN amount ELSE 0 END) cash_sales, SUM(CASE WHEN payment_method='CARD' THEN amount ELSE 0 END) card_sales, SUM(CASE WHEN payment_method='TRANSFER' THEN amount ELSE 0 END) transfer_sales FROM sale_payments WHERE payment_status='COMPLETED' GROUP BY sale_id) p ON p.sale_id=s.sale_id "
                + "WHERE s.shift_id=? AND s.sale_status='COMPLETED'";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, shiftId);
            try (ResultSet result = statement.executeQuery()) { result.next(); return new ShiftTotals(money(result.getBigDecimal("sales")), result.getLong("transactions"), money(result.getBigDecimal("cash_sales")), money(result.getBigDecimal("card_sales")), money(result.getBigDecimal("transfer_sales"))); }
        }
    }

    private Optional<CashierShift> findOpenShift(Connection connection, int userId, boolean lock) throws SQLException {
        String sql = "SELECT shift_id, user_id, opened_at, opening_cash FROM cashier_shifts WHERE user_id=? AND status='OPEN' AND closed_at IS NULL ORDER BY shift_id DESC LIMIT 1" + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            try (ResultSet result = statement.executeQuery()) { return result.next() ? Optional.of(map(result)) : Optional.empty(); }
        }
    }

    private Optional<CashierShift> findById(Connection connection, int shiftId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT shift_id, user_id, opened_at, opening_cash FROM cashier_shifts WHERE shift_id=?")) {
            statement.setInt(1, shiftId); try (ResultSet result = statement.executeQuery()) { return result.next() ? Optional.of(map(result)) : Optional.empty(); }
        }
    }

    private CashierShift map(ResultSet result) throws SQLException { return new CashierShift(result.getInt("shift_id"), result.getInt("user_id"), result.getTimestamp("opened_at").toLocalDateTime(), money(result.getBigDecimal("opening_cash"))); }
    private boolean isSchemaReady(Connection connection) throws SQLException { return SchemaSupport.hasTable(connection, "cashier_shifts") && SchemaSupport.hasColumn(connection, "cashier_shifts", "status") && SchemaSupport.hasColumn(connection, "cashier_shifts", "expected_cash") && SchemaSupport.hasColumn(connection, "cashier_shifts", "cash_difference") && SchemaSupport.hasColumn(connection, "sales", "shift_id"); }
    private void requireSchema(Connection connection) throws SQLException { if (!isSchemaReady(connection)) throw new IllegalStateException(SETUP_MESSAGE); }
    private void requireCashier() { if (!"CASHIER".equals(Session.currentRole())) throw new SecurityException("Cashier shifts are available only to CASHIER users."); }
    private BigDecimal money(BigDecimal value) { return value == null ? Money.zero() : value.setScale(2, java.math.RoundingMode.HALF_UP); }
}
