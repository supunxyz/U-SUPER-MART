package com.mycompany.easymartpos.service;

import com.mycompany.easymartpos.DBConnection;
import com.mycompany.easymartpos.model.CashierOption;
import com.mycompany.easymartpos.model.ShiftDetails;
import com.mycompany.easymartpos.model.ShiftHistoryRow;
import com.mycompany.easymartpos.model.ShiftHistorySummary;
import com.mycompany.easymartpos.model.ShiftSaleRow;
import com.mycompany.easymartpos.util.Money;
import com.mycompany.easymartpos.util.SchemaSupport;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Read-only owner reporting for completed/open cashier shifts and reconciliation values. */
public final class ShiftHistoryService {
    private static final String SHIFT_TOTALS = " LEFT JOIN ("
            + "SELECT s.shift_id, COUNT(*) transactions, COALESCE(SUM(s.total_amount),0) total_sales, "
            + "COALESCE(SUM(p.cash_sales),0) cash_sales, COALESCE(SUM(p.card_sales),0) card_sales, COALESCE(SUM(p.transfer_sales),0) transfer_sales "
            + "FROM sales s LEFT JOIN ("
            + "SELECT sale_id, "
            + "COALESCE(SUM(CASE WHEN payment_method='CASH' THEN amount ELSE 0 END),0) cash_sales, "
            + "COALESCE(SUM(CASE WHEN payment_method='CARD' THEN amount ELSE 0 END),0) card_sales, "
            + "COALESCE(SUM(CASE WHEN payment_method='TRANSFER' THEN amount ELSE 0 END),0) transfer_sales "
            + "FROM sale_payments WHERE payment_status='COMPLETED' GROUP BY sale_id"
            + ") p ON p.sale_id=s.sale_id "
            + "WHERE s.shift_id IS NOT NULL AND s.sale_status='COMPLETED' GROUP BY s.shift_id"
            + ") totals ON totals.shift_id=cs.shift_id ";

    private static final String SELECT = "SELECT cs.shift_id, u.username, cs.opened_at, cs.closed_at, cs.opening_cash, cs.closing_cash, cs.status, "
            + "COALESCE(totals.transactions,0) transactions, COALESCE(totals.cash_sales,0) cash_sales, "
            + "COALESCE(totals.card_sales,0) card_sales, COALESCE(totals.transfer_sales,0) transfer_sales, COALESCE(totals.total_sales,0) total_sales "
            + "FROM cashier_shifts cs JOIN users u ON u.user_id=cs.user_id " + SHIFT_TOTALS;

    public List<CashierOption> cashiers() throws SQLException {
        Authorization.requireAdmin();
        try (Connection connection = DBConnection.getConnection()) {
            requireSchema(connection);
            List<CashierOption> options = new ArrayList<>();
            options.add(new CashierOption(null, "All Cashiers"));
            String sql = "SELECT u.user_id, u.username FROM users u WHERE UPPER(u.role)='CASHIER' "
                    + "OR EXISTS (SELECT 1 FROM cashier_shifts cs WHERE cs.user_id=u.user_id) ORDER BY u.username";
            try (PreparedStatement statement = connection.prepareStatement(sql); ResultSet result = statement.executeQuery()) {
                while (result.next()) options.add(new CashierOption(result.getInt("user_id"), result.getString("username")));
            }
            return options;
        }
    }

    public ShiftHistoryResult load(LocalDate from, LocalDate to, Integer cashierId, String status) throws SQLException {
        Authorization.requireAdmin();
        if (from == null || to == null || to.isBefore(from)) throw new IllegalArgumentException("To Date cannot be before From Date.");
        try (Connection connection = DBConnection.getConnection()) {
            requireSchema(connection);
            StringBuilder sql = new StringBuilder(SELECT)
                    .append("WHERE cs.opened_at>=? AND cs.opened_at<? ");
            List<Object> values = new ArrayList<>();
            values.add(Timestamp.valueOf(from.atStartOfDay()));
            values.add(Timestamp.valueOf(to.plusDays(1).atStartOfDay()));
            if (cashierId != null) { sql.append("AND cs.user_id=? "); values.add(cashierId); }
            if ("OPEN".equals(status) || "CLOSED".equals(status)) { sql.append("AND cs.status=? "); values.add(status); }
            sql.append("ORDER BY cs.opened_at DESC, cs.shift_id DESC");
            try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
                bind(statement, values);
                try (ResultSet result = statement.executeQuery()) {
                    List<ShiftHistoryRow> rows = new ArrayList<>();
                    while (result.next()) rows.add(map(result));
                    return new ShiftHistoryResult(rows, summary(rows));
                }
            }
        }
    }

    public ShiftDetails details(int shiftId) throws SQLException {
        Authorization.requireAdmin();
        try (Connection connection = DBConnection.getConnection()) {
            requireSchema(connection);
            ShiftHistoryRow shift;
            try (PreparedStatement statement = connection.prepareStatement(SELECT + "WHERE cs.shift_id=?")) {
                statement.setInt(1, shiftId);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) throw new IllegalArgumentException("Cashier shift not found.");
                    shift = map(result);
                }
            }
            String salesSql = "SELECT s.invoice_number, s.sale_date, COALESCE(p.payment_method, s.payment_method) payment_method, s.total_amount "
                    + "FROM sales s LEFT JOIN (SELECT sale_id, CASE WHEN COUNT(DISTINCT payment_method)>=2 THEN 'SPLIT' ELSE MAX(payment_method) END payment_method FROM sale_payments "
                    + "WHERE payment_status='COMPLETED' GROUP BY sale_id) p ON p.sale_id=s.sale_id "
                    + "WHERE s.shift_id=? AND s.sale_status='COMPLETED' ORDER BY s.sale_date, s.sale_id";
            List<ShiftSaleRow> sales = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(salesSql)) {
                statement.setInt(1, shiftId);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) sales.add(new ShiftSaleRow(result.getString("invoice_number"),
                            result.getTimestamp("sale_date").toLocalDateTime(), result.getString("payment_method"), money(result.getBigDecimal("total_amount"))));
                }
            }
            return new ShiftDetails(shift, sales);
        }
    }

    private ShiftHistoryRow map(ResultSet result) throws SQLException {
        BigDecimal opening = money(result.getBigDecimal("opening_cash"));
        BigDecimal cash = money(result.getBigDecimal("cash_sales"));
        BigDecimal expected = opening.add(cash).setScale(2, RoundingMode.HALF_UP);
        BigDecimal closing = result.getBigDecimal("closing_cash");
        closing = closing == null ? null : money(closing);
        BigDecimal difference = closing == null ? null : closing.subtract(expected).setScale(2, RoundingMode.HALF_UP);
        Timestamp closedAt = result.getTimestamp("closed_at");
        return new ShiftHistoryRow(result.getInt("shift_id"), result.getString("username"),
                result.getTimestamp("opened_at").toLocalDateTime(), closedAt == null ? null : closedAt.toLocalDateTime(),
                opening, result.getLong("transactions"), cash, money(result.getBigDecimal("card_sales")), money(result.getBigDecimal("transfer_sales")),
                money(result.getBigDecimal("total_sales")), expected, closing, difference, result.getString("status"));
    }

    private ShiftHistorySummary summary(List<ShiftHistoryRow> rows) {
        long transactions = 0;
        BigDecimal total = Money.zero(), cash = Money.zero(), card = Money.zero(), transfer = Money.zero(), difference = Money.zero();
        for (ShiftHistoryRow row : rows) {
            transactions += row.transactions(); total = total.add(row.totalSales()); cash = cash.add(row.cashSales()); card = card.add(row.cardSales()); transfer = transfer.add(row.transferSales());
            if (row.difference() != null) difference = difference.add(row.difference());
        }
        return new ShiftHistorySummary(rows.size(), transactions, money(total), money(cash), money(card), money(transfer), money(difference));
    }

    private void bind(PreparedStatement statement, List<Object> values) throws SQLException {
        for (int index = 0; index < values.size(); index++) {
            Object value = values.get(index);
            if (value instanceof Timestamp timestamp) statement.setTimestamp(index + 1, timestamp);
            else if (value instanceof Integer integer) statement.setInt(index + 1, integer);
            else statement.setObject(index + 1, value);
        }
    }

    private void requireSchema(Connection connection) throws SQLException {
        if (!SchemaSupport.hasTable(connection, "cashier_shifts") || !SchemaSupport.hasColumn(connection, "cashier_shifts", "status")
                || !SchemaSupport.hasColumn(connection, "cashier_shifts", "opening_cash") || !SchemaSupport.hasColumn(connection, "cashier_shifts", "closing_cash")
                || !SchemaSupport.hasColumn(connection, "sales", "shift_id") || !SchemaSupport.hasTable(connection, "sale_payments")) {
            throw new IllegalStateException(ShiftService.SETUP_MESSAGE);
        }
    }

    private BigDecimal money(BigDecimal value) { return value == null ? Money.zero() : value.setScale(2, RoundingMode.HALF_UP); }

    public record ShiftHistoryResult(List<ShiftHistoryRow> rows, ShiftHistorySummary summary) { }
}
