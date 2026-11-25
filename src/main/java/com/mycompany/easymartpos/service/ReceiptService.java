package com.mycompany.easymartpos.service;

import com.mycompany.easymartpos.DBConnection;
import com.mycompany.easymartpos.model.ReceiptData;
import com.mycompany.easymartpos.model.ReceiptData.ReceiptItem;
import com.mycompany.easymartpos.model.ReceiptData.ReceiptPayment;
import com.mycompany.easymartpos.util.Money;
import com.mycompany.easymartpos.util.SchemaSupport;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Loads an immutable receipt snapshot exclusively from a committed sale. */
public class ReceiptService {
    public ReceiptData receiptFor(int saleId) throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            boolean points = SchemaSupport.hasColumn(connection, "customers", "loyalty_points");
            boolean loyaltySnapshot = SchemaSupport.hasTable(connection, "sale_loyalty");
            String sql = "SELECT s.invoice_number, s.user_id, s.sale_date, COALESCE(c.customer_name, 'Walk-in Customer') customer_name, "
                    + (loyaltySnapshot ? "sl.customer_points_after" : (points ? "c.loyalty_points" : "NULL")) + " loyalty_points, s.subtotal, s.discount, s.total_amount "
                    + "FROM sales s LEFT JOIN customers c ON c.customer_id=s.customer_id "
                    + (loyaltySnapshot ? "LEFT JOIN sale_loyalty sl ON sl.sale_id=s.sale_id " : "")
                    + "WHERE s.sale_id=?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, saleId);
                try (ResultSet row = statement.executeQuery()) {
                    if (!row.next()) throw new IllegalArgumentException("Sale not found.");
                    LocalDateTime dateTime = row.getTimestamp("sale_date").toLocalDateTime();
                    List<ReceiptItem> items = loadItems(connection, saleId);
                    List<ReceiptPayment> payments = loadPayments(connection, saleId);
                    BigDecimal saving = ReceiptBenefitCalculator.calculate(items, row.getBigDecimal("discount"));
                    ReceiptData data = new ReceiptData(saleId, row.getString("invoice_number"), cashierName(connection, row.getInt("user_id")),
                            dateTime, row.getString("customer_name"), row.getObject("loyalty_points") == null ? null : row.getInt("loyalty_points"),
                            row.getBigDecimal("subtotal"), row.getBigDecimal("discount"), row.getBigDecimal("total_amount"),
                            items, payments, saving, totalQuantity(connection, saleId));
                    data.validateForPrint();
                    return data;
                }
            }
        }
    }

    private List<ReceiptPayment> loadPayments(Connection connection, int saleId) throws SQLException {
        String sql = "SELECT payment_method, amount, tendered_amount, change_amount, card_reference, transfer_reference, bank_name "
                + "FROM sale_payments WHERE sale_id=? AND payment_status='COMPLETED' ORDER BY payment_id";
        List<ReceiptPayment> parts = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, saleId); try (ResultSet rows = statement.executeQuery()) { while (rows.next()) {
                parts.add(new ReceiptPayment(rows.getString(1), rows.getBigDecimal(2), rows.getBigDecimal(3), rows.getBigDecimal(4),
                        rows.getString(5), rows.getString(6), rows.getString(7)));
            } }
        }
        return parts;
    }

    private List<ReceiptItem> loadItems(Connection connection, int saleId) throws SQLException {
        boolean itemRetail = SchemaSupport.hasColumn(connection, "sale_items", "retail_price");
        boolean productRetail = SchemaSupport.hasColumn(connection, "products", "retail_price");
        boolean itemName = SchemaSupport.hasColumn(connection, "sale_items", "product_name");
        boolean regularPriceSnapshot = SchemaSupport.hasColumn(connection, "sale_items", "regular_unit_price");
        // A saved retail price wins. Older sales keep the established product retail-price fallback.
        String name = itemName ? "COALESCE(si.product_name, p.product_name)" : "p.product_name";
        String retail = itemRetail && productRetail ? "COALESCE(si.retail_price, p.retail_price)"
                : itemRetail ? "si.retail_price" : productRetail ? "p.retail_price" : "NULL";
        // New sales have a normal-price snapshot. Older sales retain the established product-price
        // fallback, which is used only when that sale-time snapshot is unavailable.
        String regular = regularPriceSnapshot ? "COALESCE(si.regular_unit_price, p.selling_price)" : "p.selling_price";
        String sql = "SELECT " + name + " product_name, si.quantity, " + retail + " retail_price, " + regular + " regular_unit_price, si.unit_price, "
                + "si.subtotal FROM sale_items si JOIN products p ON p.product_id=si.product_id WHERE si.sale_id=? ORDER BY si.sale_item_id";
        List<ReceiptItem> items = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) { statement.setInt(1, saleId); try (ResultSet rows = statement.executeQuery()) { while (rows.next())
            items.add(new ReceiptItem(rows.getString("product_name"), rows.getBigDecimal("quantity"), rows.getBigDecimal("retail_price"),
                    rows.getBigDecimal("regular_unit_price"), rows.getBigDecimal("unit_price"), rows.getBigDecimal("subtotal")));
        } }
        return items;
    }
    private BigDecimal totalQuantity(Connection c,int id)throws SQLException { try(PreparedStatement s=c.prepareStatement("SELECT COALESCE(SUM(quantity),0) FROM sale_items WHERE sale_id=?")){s.setInt(1,id);try(ResultSet r=s.executeQuery()){r.next();return r.getBigDecimal(1);}} }
    private String cashierName(Connection c,int id)throws SQLException { if(id>0&&SchemaSupport.hasTable(c,"users")&&SchemaSupport.hasColumn(c,"users","username")){try(PreparedStatement s=c.prepareStatement("SELECT username FROM users WHERE user_id=?")){s.setInt(1,id);try(ResultSet r=s.executeQuery()){if(r.next())return r.getString(1);}}}return Session.currentCashierName(); }
}
