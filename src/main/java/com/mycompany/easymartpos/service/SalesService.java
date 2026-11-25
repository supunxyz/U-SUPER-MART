package com.mycompany.easymartpos.service;

import com.mycompany.easymartpos.DBConnection;
import com.mycompany.easymartpos.model.CartItem;
import com.mycompany.easymartpos.model.BatchAllocation;
import com.mycompany.easymartpos.model.PaymentMethod;
import com.mycompany.easymartpos.model.PaymentRequest;
import com.mycompany.easymartpos.model.PaymentPart;
import com.mycompany.easymartpos.model.SaleHistoryRow;
import com.mycompany.easymartpos.model.SaleResult;
import com.mycompany.easymartpos.util.Money;
import com.mycompany.easymartpos.util.SchemaSupport;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SalesService {
    static final String BATCH_PLAN_INVALID_MESSAGE = "Batch stock plan is no longer valid. Please re-add the item.";
    public String nextInvoiceNumber() {
    String sql = "SELECT MAX(sale_id) AS last_id FROM sales";

    try (Connection con = DBConnection.getConnection();
         PreparedStatement pst = con.prepareStatement(sql);
         ResultSet rs = pst.executeQuery()) {

        int nextId = 1;

        if (rs.next()) {
            nextId = rs.getInt("last_id") + 1;
        }

        return String.format("INV-%05d", nextId);

    } catch (SQLException e) {
        throw new RuntimeException("Failed to generate invoice number.", e);
    }
}
    

    public SaleResult completeSale(String invoiceNumber, int customerId, int userId, List<CartItem> cart,
            BigDecimal discount, PaymentRequest payment) throws SQLException {
        return completeSale(invoiceNumber, customerId, userId, cart, discount, payment, null);
    }

    public SaleResult completeSale(String invoiceNumber, int customerId, int userId, List<CartItem> cart,
            BigDecimal discount, PaymentRequest payment, Integer shiftId) throws SQLException {
        return completeSale(invoiceNumber, customerId, userId, cart, discount, payment, shiftId, 0);
    }

    /**
     * Completes a sale with optional loyalty redemption.  Checkout currently calls the zero-redemption
     * overload, keeping its locked layout unchanged; this overload is the transactional loyalty API.
     */
    public SaleResult completeSale(String invoiceNumber, int customerId, int userId, List<CartItem> cart,
            BigDecimal discount, PaymentRequest payment, Integer shiftId, int requestedRedemptionPoints) throws SQLException {
        if (invoiceNumber == null || invoiceNumber.isBlank()) throw new IllegalArgumentException("Invoice number is required.");
        if (customerId <= 0) throw new IllegalArgumentException("Select a customer.");
        if (cart == null || cart.isEmpty()) throw new IllegalArgumentException("Add at least one item to the cart.");
        if (requestedRedemptionPoints < 0) throw new IllegalArgumentException("Redeemed points cannot be negative.");
        if ("CASHIER".equals(Session.currentRole()) && shiftId == null) shiftId = new ShiftService().requireOpenShiftIdForCurrentCashier();
        BigDecimal subtotal = CheckoutCalculator.subtotal(cart);
        BigDecimal safeDiscount = discount == null ? Money.zero() : discount;
        BigDecimal totalBeforeRedemption = CheckoutCalculator.total(subtotal, safeDiscount);

        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (shiftId != null) lockAndValidateShift(connection, shiftId, userId);
                LoyaltyChange loyalty = lockAndCalculateLoyalty(connection, customerId, totalBeforeRedemption, requestedRedemptionPoints);
                BigDecimal total = totalBeforeRedemption.subtract(loyalty.redemptionValue());
                List<PaymentPart> paymentParts = paymentParts(payment, total);
                validatePayment(payment, paymentParts, total);
                BigDecimal change = paymentParts.size() == 1 && paymentParts.get(0).method() == PaymentMethod.CASH
                        ? CheckoutCalculator.cashChange(paymentParts.get(0).tenderedAmount(), total) : Money.zero();
                // Lock the master-stock compatibility rows and every exact batch before making any sale writes.
                List<List<BatchAllocation>> lockedBatchPlans = new ArrayList<>();
                for (CartItem item : cart) {
                    lockAndValidateStock(connection, item);
                    lockedBatchPlans.add(lockAndValidateBatchPlan(connection, item));
                }
                validatePaymentSchema(connection, paymentParts);
                // Reserve a unique placeholder, then derive the public number from the committed row ID.
                // The number shown before checkout is only a preview and may be stale on another terminal.
                int saleId = insertSale(connection, "TMP-" + UUID.randomUUID(), customerId, userId, subtotal, safeDiscount, total, paymentDisplayMethod(paymentParts), shiftId);
                String finalInvoice = String.format("INV-%05d", saleId);
                try (PreparedStatement rename = connection.prepareStatement("UPDATE sales SET invoice_number=? WHERE sale_id=?")) {
                    rename.setString(1, finalInvoice); rename.setInt(2, saleId);
                    if (rename.executeUpdate() != 1) throw new SQLException("Invoice number could not be assigned.");
                }
                for (int index = 0; index < cart.size(); index++) {
                    CartItem item = cart.get(index);
                    List<BatchAllocation> lockedPlan = lockedBatchPlans.get(index);
                    int saleItemId = insertSaleItem(connection, saleId, item, weightedAverageCost(lockedPlan));
                    insertSaleItemBatchAllocations(connection, saleItemId, lockedPlan);
                    deductBatchStock(connection, lockedPlan);
                    deductStock(connection, item);
                    insertMovement(connection, item.productId(), "SALE", item.quantity().negate(), saleId, userId, "Completed sale " + finalInvoice);
                }
                insertPayments(connection, saleId, paymentParts, change);
                applyLoyaltyChange(connection, saleId, loyalty);
                connection.commit();
                return new SaleResult(saleId, finalInvoice, total, change);
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally { connection.setAutoCommit(true); }
        }
    }

    public List<SaleHistoryRow> searchSales(String text) throws SQLException {
        String term = "%" + (text == null ? "" : text.trim()) + "%";
        boolean cashier = "CASHIER".equals(Session.currentRole());
        String sql = "SELECT s.sale_id, s.invoice_number, COALESCE(c.customer_name, 'Walk-in Customer') customer_name, "
                + "s.sale_date, s.total_amount, COALESCE(p.payment_method, s.payment_method) payment_method, s.sale_status "
                + "FROM sales s LEFT JOIN customers c ON c.customer_id=s.customer_id "
                + "LEFT JOIN (SELECT sale_id, CASE WHEN COUNT(DISTINCT payment_method)>=2 THEN 'SPLIT' ELSE MAX(payment_method) END payment_method "
                + "FROM sale_payments WHERE payment_status='COMPLETED' GROUP BY sale_id) p ON p.sale_id=s.sale_id "
                + "WHERE (s.invoice_number LIKE ? OR COALESCE(c.customer_name, '') LIKE ?) "
                + (cashier ? "AND s.user_id=? " : "") + "ORDER BY s.sale_date DESC";
        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, term); statement.setString(2, term);
            if (cashier) statement.setInt(3, Session.currentUserId());
            try (ResultSet results = statement.executeQuery()) {
                List<SaleHistoryRow> rows = new ArrayList<>();
                while (results.next()) rows.add(new SaleHistoryRow(results.getInt("sale_id"), results.getString("invoice_number"),
                        results.getString("customer_name"), results.getTimestamp("sale_date").toLocalDateTime(),
                        results.getBigDecimal("total_amount"), results.getString("payment_method"), results.getString("sale_status")));
                return rows;
            }
        }
    }

    public void voidSale(int saleId, String reason, int userId) throws SQLException {
        Authorization.requireAdmin();
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("A void reason is required.");
        try (Connection connection = DBConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String invoice = lockSale(connection, saleId);
                List<VoidSaleItem> items = lockSaleItems(connection, saleId);
                for (VoidSaleItem item : items) {
                    List<BatchRestoreAllocation> allocations = loadBatchAllocations(connection, item.saleItemId());
                    if (allocations.isEmpty()) restoreLegacyBatchStock(connection, item.productId(), item.quantity());
                    else restoreExactBatchStock(connection, item.productId(), allocations);
                    restoreProductStock(connection, item.productId(), item.quantity());
                    insertMovement(connection, item.productId(), "VOID", item.quantity(), saleId, userId, reason.trim());
                }
                try (PreparedStatement payment = connection.prepareStatement("UPDATE sale_payments SET payment_status='VOID' WHERE sale_id=?")) {
                    payment.setInt(1, saleId); payment.executeUpdate();
                }
                reverseLoyaltyForVoid(connection, saleId);
                try (PreparedStatement sale = connection.prepareStatement("UPDATE sales SET sale_status='VOID', void_reason=?, voided_by=?, voided_at=CURRENT_TIMESTAMP WHERE sale_id=?")) {
                    sale.setString(1, reason.trim()); sale.setInt(2, userId); sale.setInt(3, saleId); sale.executeUpdate();
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback(); throw exception;
            } finally { connection.setAutoCommit(true); }
        }
    }

    /** Returns the historical allocation audit exactly as recorded; no allocation row is changed during a void. */
    private List<BatchRestoreAllocation> loadBatchAllocations(Connection connection, int saleItemId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT batch_id, quantity FROM sale_item_batch_allocations WHERE sale_item_id=?")) {
            statement.setInt(1, saleItemId);
            try (ResultSet results = statement.executeQuery()) {
                List<BatchRestoreAllocation> allocations = new ArrayList<>();
                while (results.next()) allocations.add(new BatchRestoreAllocation(results.getInt("batch_id"), results.getBigDecimal("quantity")));
                return List.copyOf(allocations);
            }
        }
    }

    /** Restores only the physical batches recorded on this historical sale item. */
    private void restoreExactBatchStock(Connection connection, int expectedProductId, List<BatchRestoreAllocation> allocations) throws SQLException {
        for (BatchRestoreAllocation allocation : allocations) {
            try (PreparedStatement locked = connection.prepareStatement(
                    "SELECT product_id, quantity_available, quantity_received FROM product_batches WHERE batch_id=? FOR UPDATE")) {
                locked.setInt(1, allocation.batchId());
                try (ResultSet batch = locked.executeQuery()) {
                    if (!batch.next()) throw batchRestoreInvalid();
                    validateBatchRestore(expectedProductId, allocation, batch.getInt("product_id"),
                            batch.getBigDecimal("quantity_available"), batch.getBigDecimal("quantity_received"));
                }
            }
            try (PreparedStatement restore = connection.prepareStatement(
                    "UPDATE product_batches SET quantity_available=quantity_available+? WHERE batch_id=? AND product_id=?")) {
                restore.setBigDecimal(1, allocation.quantity()); restore.setInt(2, allocation.batchId()); restore.setInt(3, expectedProductId);
                if (restore.executeUpdate() != 1) throw batchRestoreInvalid();
            }
        }
    }

    static void validateBatchRestore(int expectedProductId, BatchRestoreAllocation allocation, int actualProductId,
            BigDecimal quantityAvailable, BigDecimal quantityReceived) {
        if (allocation == null || allocation.quantity() == null || allocation.quantity().signum() <= 0
                || actualProductId != expectedProductId || quantityAvailable == null || quantityReceived == null
                || quantityAvailable.add(allocation.quantity()).compareTo(quantityReceived) > 0) throw batchRestoreInvalid();
    }

    private static IllegalStateException batchRestoreInvalid() {
        return new IllegalStateException("Batch stock cannot be restored safely for this sale.");
    }

    private void restoreProductStock(Connection connection, int productId, BigDecimal quantity) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement("UPDATE products SET stock_quantity=stock_quantity+? WHERE product_id=?")) {
            update.setBigDecimal(1, quantity); update.setInt(2, productId);
            if (update.executeUpdate() != 1) throw new IllegalStateException("Product stock cannot be restored safely for this sale.");
        }
    }

    /**
     * Older sales have no batch-allocation audit.  Restoring only products.stock_quantity
     * makes the master quantity disagree with the batch inventory shown at checkout.  A
     * new adjustment batch preserves a sellable, auditable quantity without pretending we
     * know the historical source batch.
     */
    private void restoreLegacyBatchStock(Connection connection, int productId, BigDecimal quantity) throws SQLException {
        String select = "SELECT cost_price, selling_price, retail_price, expiry_date FROM products WHERE product_id=? FOR UPDATE";
        try (PreparedStatement product = connection.prepareStatement(select)) {
            product.setInt(1, productId);
            try (ResultSet row = product.executeQuery()) {
                if (!row.next()) throw new IllegalStateException("Product stock cannot be restored safely for this sale.");
                try (PreparedStatement batch = connection.prepareStatement(
                        "INSERT INTO product_batches (product_id, purchase_id, purchase_item_id, source_type, cost_price, selling_price, retail_price, expiry_date, quantity_received, quantity_available, active) VALUES (?, NULL, NULL, 'ADJUSTMENT', ?, ?, ?, ?, ?, ?, TRUE)")) {
                    batch.setInt(1, productId); batch.setBigDecimal(2, row.getBigDecimal("cost_price")); batch.setBigDecimal(3, row.getBigDecimal("selling_price"));
                    BigDecimal retail = row.getBigDecimal("retail_price");
                    if (retail == null) batch.setNull(4, java.sql.Types.DECIMAL); else batch.setBigDecimal(4, retail);
                    java.sql.Date expiry = row.getDate("expiry_date");
                    if (expiry == null) batch.setNull(5, java.sql.Types.DATE); else batch.setDate(5, expiry);
                    batch.setBigDecimal(6, quantity); batch.setBigDecimal(7, quantity);
                    if (batch.executeUpdate() != 1) throw new IllegalStateException("Product stock cannot be restored safely for this sale.");
                }
            }
        }
    }

    private void validatePayment(PaymentRequest payment, List<PaymentPart> parts, BigDecimal total) {
        if (payment == null || payment.method() == null) throw new IllegalArgumentException("Select a payment method.");
        if (payment.method() == PaymentMethod.CASH) CheckoutCalculator.cashChange(payment.tenderedAmount(), total);
        CheckoutCalculator.validatePaymentParts(parts, total, payment.method() == PaymentMethod.SPLIT);
    }

    private List<PaymentPart> paymentParts(PaymentRequest payment, BigDecimal total) {
        if (payment == null || payment.method() == null) throw new IllegalArgumentException("Select a payment method.");
        if (!payment.parts().isEmpty()) return payment.parts();
        return switch (payment.method()) {
            case CASH -> List.of(PaymentPart.cash(total, payment.tenderedAmount(), CheckoutCalculator.cashChange(payment.tenderedAmount(), total)));
            case CARD -> List.of(PaymentPart.card(total, payment.cardReference(), payment.bankName()));
            case TRANSFER -> List.of(PaymentPart.transfer(total, payment.transferReference(), payment.bankName()));
            case SPLIT -> throw new IllegalArgumentException("Enter split payment amounts.");
        };
    }

    private PaymentMethod paymentDisplayMethod(List<PaymentPart> parts) {
        return parts.stream().map(PaymentPart::method).distinct().count() > 1 ? PaymentMethod.SPLIT : parts.get(0).method();
    }

    private void validatePaymentSchema(Connection connection, List<PaymentPart> parts) throws SQLException {
        if (parts.size() > 1 && SchemaSupport.hasUniqueIndexOnColumn(connection, "sale_payments", "sale_id"))
            throw new IllegalStateException("Split payments require the documented sale_payments sale_id unique constraint to be removed.");
        boolean transfer = parts.stream().anyMatch(part -> part.method() == PaymentMethod.TRANSFER);
        if (transfer && (!SchemaSupport.hasColumn(connection, "sale_payments", "transfer_reference")
                || !SchemaSupport.hasColumn(connection, "sale_payments", "bank_name")))
            throw new IllegalStateException("Transfer payments require sale_payments.transfer_reference and sale_payments.bank_name.");
    }

    private void lockAndValidateStock(Connection connection, CartItem item) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT product_name, stock_quantity, cost_price, active FROM products WHERE product_id=? FOR UPDATE")) {
            statement.setInt(1, item.productId());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next() || !result.getBoolean("active")) throw new IllegalArgumentException("Product is no longer available: " + item.productName());
                if (result.getBigDecimal("stock_quantity").compareTo(item.quantity()) < 0)
                    throw new IllegalArgumentException("Insufficient stock for " + item.productName() + ".");
            }
        }
    }

    /** Locks the planned physical batches and replaces only their costs with the committed database snapshots. */
    private List<BatchAllocation> lockAndValidateBatchPlan(Connection connection, CartItem item) throws SQLException {
        validateBatchPlan(item);
        List<BatchAllocation> locked = new ArrayList<>();
        String sql = "SELECT product_id, cost_price, selling_price, retail_price, expiry_date, quantity_available, active "
                + "FROM product_batches WHERE batch_id=? FOR UPDATE";
        for (BatchAllocation allocation : item.batchAllocations()) {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, allocation.batchId());
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) throw batchPlanInvalid();
                    java.sql.Date expiry = result.getDate("expiry_date");
                    LocalDate expiryDate = expiry == null ? null : expiry.toLocalDate();
                    BigDecimal available = result.getBigDecimal("quantity_available");
                    BigDecimal sellingPrice = result.getBigDecimal("selling_price");
                    BigDecimal costPrice = result.getBigDecimal("cost_price");
                    validateLockedBatch(item.productId(), item.ourPrice(), allocation, result.getInt("product_id"),
                            result.getBoolean("active"), available, sellingPrice, costPrice, expiryDate);
                    locked.add(new BatchAllocation(allocation.batchId(), allocation.quantity(), costPrice, sellingPrice,
                            result.getBigDecimal("retail_price"), expiryDate));
                }
            }
        }
        return List.copyOf(locked);
    }

    static void validateBatchPlan(CartItem item) {
        if (item == null || item.quantity() == null || item.quantity().signum() <= 0
                || item.batchAllocations() == null || item.batchAllocations().isEmpty()) throw batchPlanInvalid();
        BigDecimal planned = BigDecimal.ZERO;
        for (BatchAllocation allocation : item.batchAllocations()) {
            if (allocation == null || allocation.quantity() == null || allocation.quantity().signum() <= 0) throw batchPlanInvalid();
            planned = planned.add(allocation.quantity());
        }
        if (planned.compareTo(item.quantity()) != 0) throw batchPlanInvalid();
    }

    static void validateLockedBatch(int expectedProductId, BigDecimal expectedSellingPrice, BatchAllocation allocation,
            int actualProductId, boolean active, BigDecimal available, BigDecimal sellingPrice, BigDecimal costPrice,
            LocalDate expiryDate) {
        if (allocation == null || actualProductId != expectedProductId || !active || available == null
                || sellingPrice == null || costPrice == null || expectedSellingPrice == null
                || available.compareTo(allocation.quantity()) < 0 || sellingPrice.compareTo(expectedSellingPrice) != 0
                || (expiryDate != null && expiryDate.isBefore(LocalDate.now()))) throw batchPlanInvalid();
    }

    static BigDecimal weightedAverageCost(List<BatchAllocation> allocations) {
        if (allocations == null || allocations.isEmpty()) throw batchPlanInvalid();
        BigDecimal totalQuantity = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;
        for (BatchAllocation allocation : allocations) {
            if (allocation == null || allocation.quantity() == null || allocation.quantity().signum() <= 0 || allocation.costPrice() == null)
                throw batchPlanInvalid();
            totalQuantity = totalQuantity.add(allocation.quantity());
            totalCost = totalCost.add(allocation.quantity().multiply(allocation.costPrice()));
        }
        if (totalQuantity.signum() <= 0) throw batchPlanInvalid();
        return totalCost.divide(totalQuantity, 2, RoundingMode.HALF_UP);
    }

    private void insertSaleItemBatchAllocations(Connection connection, int saleItemId, List<BatchAllocation> allocations) throws SQLException {
        String sql = "INSERT INTO sale_item_batch_allocations (sale_item_id, batch_id, quantity, cost_price) VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (BatchAllocation allocation : allocations) {
                statement.setInt(1, saleItemId); statement.setInt(2, allocation.batchId());
                statement.setBigDecimal(3, allocation.quantity()); statement.setBigDecimal(4, allocation.costPrice());
                if (statement.executeUpdate() != 1) throw new SQLException("Sale item batch allocation was not saved.");
            }
        }
    }

    private void deductBatchStock(Connection connection, List<BatchAllocation> allocations) throws SQLException {
        String sql = "UPDATE product_batches SET quantity_available=quantity_available-? "
                + "WHERE batch_id=? AND active=1 AND quantity_available>=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (BatchAllocation allocation : allocations) {
                statement.setBigDecimal(1, allocation.quantity()); statement.setInt(2, allocation.batchId());
                statement.setBigDecimal(3, allocation.quantity());
                if (statement.executeUpdate() != 1) throw batchPlanInvalid();
            }
        }
    }

    private static IllegalStateException batchPlanInvalid() {
        return new IllegalStateException(BATCH_PLAN_INVALID_MESSAGE);
    }

    private void lockAndValidateShift(Connection connection, int shiftId, int userId) throws SQLException {
        if (!SchemaSupport.hasColumn(connection, "sales", "shift_id")) throw new IllegalStateException(ShiftService.SETUP_MESSAGE);
        try (PreparedStatement statement = connection.prepareStatement("SELECT user_id, status, closed_at FROM cashier_shifts WHERE shift_id=? FOR UPDATE")) {
            statement.setInt(1, shiftId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next() || result.getInt("user_id") != userId || !"OPEN".equals(result.getString("status")) || result.getTimestamp("closed_at") != null)
                    throw new IllegalStateException("The selected cashier shift is no longer open.");
            }
        }
    }

    private int insertSale(Connection connection, String invoice, int customerId, int userId, BigDecimal subtotal,
            BigDecimal discount, BigDecimal total, PaymentMethod method, Integer shiftId) throws SQLException {
        boolean hasShift = SchemaSupport.hasColumn(connection, "sales", "shift_id");
        String sql = hasShift
                ? "INSERT INTO sales (invoice_number, customer_id, user_id, shift_id, subtotal, discount, total_amount, payment_method, sale_status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'COMPLETED')"
                : "INSERT INTO sales (invoice_number, customer_id, user_id, subtotal, discount, total_amount, payment_method, sale_status) VALUES (?, ?, ?, ?, ?, ?, ?, 'COMPLETED')";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, invoice); statement.setInt(2, customerId); statement.setInt(3, userId);
            int index = 4;
            if (hasShift) { if (shiftId == null) statement.setNull(index++, java.sql.Types.INTEGER); else statement.setInt(index++, shiftId); }
            statement.setBigDecimal(index++, subtotal); statement.setBigDecimal(index++, discount); statement.setBigDecimal(index++, total);
            statement.setString(index, method.name()); statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Sale ID was not generated.");
                return keys.getInt(1);
            }
        }
    }

    private int insertSaleItem(Connection connection, int saleId, CartItem item, BigDecimal costSnapshot) throws SQLException {
        boolean hasRetailPrice = SchemaSupport.hasColumn(connection, "sale_items", "retail_price");
        boolean hasCostSnapshot = SchemaSupport.hasColumn(connection, "sale_items", "cost_price");
        boolean hasRegularPriceSnapshot = SchemaSupport.hasColumn(connection, "sale_items", "regular_unit_price");
        StringBuilder columns = new StringBuilder("sale_id, product_id, quantity, unit_price");
        StringBuilder placeholders = new StringBuilder("?, ?, ?, ?");
        if (hasRetailPrice) { columns.append(", retail_price"); placeholders.append(", ?"); }
        if (hasCostSnapshot) { columns.append(", cost_price"); placeholders.append(", ?"); }
        if (hasRegularPriceSnapshot) { columns.append(", regular_unit_price"); placeholders.append(", ?"); }
        columns.append(", subtotal"); placeholders.append(", ?");
        String sql = "INSERT INTO sale_items (" + columns + ") VALUES (" + placeholders + ")";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            int index = 1;
            statement.setInt(index++, saleId); statement.setInt(index++, item.productId()); statement.setBigDecimal(index++, item.quantity());
            statement.setBigDecimal(index++, item.finalUnitPrice());
            if (hasRetailPrice) {
                if (item.retailPrice() == null) statement.setNull(index++, java.sql.Types.DECIMAL); else statement.setBigDecimal(index++, item.retailPrice());
            }
            if (hasCostSnapshot) {
                if (costSnapshot == null) statement.setNull(index++, java.sql.Types.DECIMAL); else statement.setBigDecimal(index++, costSnapshot);
            }
            // Keep the normal "Our Price" used at checkout so a reprint never depends on today's product price.
            if (hasRegularPriceSnapshot) statement.setBigDecimal(index++, item.ourPrice());
            statement.setBigDecimal(index, item.subtotal());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Sale item ID was not generated.");
                int saleItemId = keys.getInt(1);
                if (saleItemId <= 0) throw new SQLException("Sale item ID was not generated.");
                return saleItemId;
            }
        }
    }

    private void deductStock(Connection connection, CartItem item) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE products SET stock_quantity=stock_quantity-? WHERE product_id=? AND stock_quantity>=?")) {
            statement.setBigDecimal(1, item.quantity()); statement.setInt(2, item.productId()); statement.setBigDecimal(3, item.quantity());
            if (statement.executeUpdate() != 1) throw new IllegalArgumentException("Stock changed while completing the sale for " + item.productName() + ". The sale was cancelled.");
        }
    }

    private LoyaltyChange lockAndCalculateLoyalty(Connection connection, int customerId, BigDecimal totalBeforeRedemption,
            int requestedRedemptionPoints) throws SQLException {
        if (!SchemaSupport.hasColumn(connection, "customers", "loyalty_points")
                || !SchemaSupport.hasColumn(connection, "customers", "loyalty_customer")) {
            if (requestedRedemptionPoints > 0) throw new IllegalStateException("Loyalty customer columns are not available.");
            return LoyaltyChange.none();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT customer_name, loyalty_points, loyalty_customer FROM customers WHERE customer_id=? FOR UPDATE")) {
            statement.setInt(1, customerId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new IllegalArgumentException("Customer was not found.");
                boolean loyaltyCustomer = result.getBoolean("loyalty_customer")
                        && !"walk-in customer".equalsIgnoreCase(result.getString("customer_name") == null ? "" : result.getString("customer_name").trim());
                if (!loyaltyCustomer) {
                    if (requestedRedemptionPoints > 0) throw new IllegalArgumentException("Only loyalty customers can redeem points.");
                    return LoyaltyChange.none();
                }
                requireLoyaltyAuditSchema(connection);
                int pointsBefore = result.getInt("loyalty_points");
                if (pointsBefore < 0) throw new IllegalStateException("Customer loyalty balance is invalid.");
                int redeemed = LoyaltyPointCalculator.redeemedPoints(pointsBefore, totalBeforeRedemption, requestedRedemptionPoints);
                BigDecimal redemptionValue = BigDecimal.valueOf(redeemed).setScale(2);
                BigDecimal eligibleAmount = totalBeforeRedemption.subtract(redemptionValue);
                int earned = LoyaltyPointCalculator.earnedPoints(eligibleAmount);
                return new LoyaltyChange(customerId, pointsBefore, earned, redeemed, redemptionValue, pointsBefore - redeemed + earned);
            }
        }
    }

    private void applyLoyaltyChange(Connection connection, int saleId, LoyaltyChange loyalty) throws SQLException {
        if (!loyalty.applies()) return;
        try (PreparedStatement customer = connection.prepareStatement("UPDATE customers SET loyalty_points=? WHERE customer_id=? AND loyalty_points=?")) {
            customer.setInt(1, loyalty.pointsAfter()); customer.setInt(2, loyalty.customerId()); customer.setInt(3, loyalty.pointsBefore());
            if (customer.executeUpdate() != 1) throw new IllegalStateException("Customer loyalty balance changed while completing the sale.");
        }
        try (PreparedStatement audit = connection.prepareStatement(
                "INSERT INTO sale_loyalty (sale_id, customer_id, customer_points_before, earned_points, redeemed_points, redemption_value, customer_points_after) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            audit.setInt(1, saleId); audit.setInt(2, loyalty.customerId()); audit.setInt(3, loyalty.pointsBefore()); audit.setInt(4, loyalty.earnedPoints());
            audit.setInt(5, loyalty.redeemedPoints()); audit.setBigDecimal(6, loyalty.redemptionValue()); audit.setInt(7, loyalty.pointsAfter()); audit.executeUpdate();
        }
    }

    /** Reverses exactly the recorded earning/redemption once, within the already-locked void transaction. */
    private void reverseLoyaltyForVoid(Connection connection, int saleId) throws SQLException {
        if (!SchemaSupport.hasTable(connection, "sale_loyalty")) return;
        try (PreparedStatement audit = connection.prepareStatement(
                "SELECT customer_id, earned_points, redeemed_points FROM sale_loyalty WHERE sale_id=? FOR UPDATE")) {
            audit.setInt(1, saleId);
            try (ResultSet row = audit.executeQuery()) {
                if (!row.next()) return;
                int customerId = row.getInt("customer_id"); int earned = row.getInt("earned_points"); int redeemed = row.getInt("redeemed_points");
                try (PreparedStatement customer = connection.prepareStatement("SELECT loyalty_points FROM customers WHERE customer_id=? FOR UPDATE")) {
                    customer.setInt(1, customerId);
                    try (ResultSet balance = customer.executeQuery()) {
                        if (!balance.next()) throw new IllegalStateException("Loyalty customer for this sale no longer exists.");
                        int afterVoid = balance.getInt("loyalty_points") - earned + redeemed;
                        if (afterVoid < 0) throw new IllegalStateException("Cannot void sale because it would make loyalty points negative.");
                        try (PreparedStatement update = connection.prepareStatement("UPDATE customers SET loyalty_points=? WHERE customer_id=?")) {
                            update.setInt(1, afterVoid); update.setInt(2, customerId); update.executeUpdate();
                        }
                    }
                }
            }
        }
    }

    private void requireLoyaltyAuditSchema(Connection connection) throws SQLException {
        if (!SchemaSupport.hasTable(connection, "sale_loyalty"))
            throw new IllegalStateException("Loyalty points require db/migration/V2__sale_loyalty.sql to be applied.");
    }

    private record LoyaltyChange(int customerId, int pointsBefore, int earnedPoints, int redeemedPoints,
            BigDecimal redemptionValue, int pointsAfter) {
        static LoyaltyChange none() { return new LoyaltyChange(0, 0, 0, 0, Money.zero(), 0); }
        boolean applies() { return customerId > 0; }
    }

    private void insertPayments(Connection connection, int saleId, List<PaymentPart> parts, BigDecimal cashChange) throws SQLException {
        String sql = "INSERT INTO sale_payments (sale_id, payment_method, amount, tendered_amount, change_amount, "
                + "card_reference, transfer_reference, bank_name, payment_status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'COMPLETED')";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (PaymentPart payment : parts) {
                statement.setInt(1, saleId); statement.setString(2, payment.method().name()); statement.setBigDecimal(3, payment.amount());
                if (payment.method() == PaymentMethod.CASH && payment.tenderedAmount() != null) statement.setBigDecimal(4, payment.tenderedAmount()); else statement.setNull(4, java.sql.Types.DECIMAL);
                statement.setBigDecimal(5, payment.method() == PaymentMethod.CASH && parts.size() == 1 ? cashChange : Money.zero());
                setNullableText(statement, 6, payment.method() == PaymentMethod.CARD ? payment.reference() : null);
                setNullableText(statement, 7, payment.method() == PaymentMethod.TRANSFER ? payment.reference() : null);
                setNullableText(statement, 8, payment.method() == PaymentMethod.CARD || payment.method() == PaymentMethod.TRANSFER ? payment.bankName() : null);
                statement.executeUpdate();
            }
        }
    }

    private void setNullableText(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null || value.isBlank()) statement.setNull(index, java.sql.Types.VARCHAR);
        else statement.setString(index, value.trim());
    }

    private void insertMovement(Connection connection, int productId, String type, BigDecimal quantity, int referenceId, int userId, String reason) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO stock_movements (product_id, movement_type, quantity, reference_id, user_id, reason) VALUES (?, ?, ?, ?, ?, ?)")) {
            statement.setInt(1, productId); statement.setString(2, type); statement.setBigDecimal(3, quantity);
            statement.setInt(4, referenceId); statement.setInt(5, userId); statement.setString(6, reason); statement.executeUpdate();
        }
    }

    private String lockSale(Connection connection, int saleId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT invoice_number, sale_status FROM sales WHERE sale_id=? FOR UPDATE")) {
            statement.setInt(1, saleId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new IllegalArgumentException("Sale not found.");
                validateVoidStatus(result.getString("sale_status"));
                return result.getString("invoice_number");
            }
        }
    }

    private List<VoidSaleItem> lockSaleItems(Connection connection, int saleId) throws SQLException {
        String sql = "SELECT si.sale_item_id, si.product_id, p.product_name, si.quantity FROM sale_items si JOIN products p ON p.product_id=si.product_id WHERE si.sale_id=? FOR UPDATE";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, saleId);
            try (ResultSet result = statement.executeQuery()) {
                List<VoidSaleItem> items = new ArrayList<>();
                while (result.next()) {
                    items.add(new VoidSaleItem(result.getInt("sale_item_id"), result.getInt("product_id"),
                            result.getString("product_name"), result.getBigDecimal("quantity")));
                }
                return items;
            }
        }
    }

    static record BatchRestoreAllocation(int batchId, BigDecimal quantity) { }
    private record VoidSaleItem(int saleItemId, int productId, String productName, BigDecimal quantity) { }

    static void validateVoidStatus(String status) {
        if (!"COMPLETED".equals(status)) throw new IllegalArgumentException("Only completed sales can be voided.");
    }
}
