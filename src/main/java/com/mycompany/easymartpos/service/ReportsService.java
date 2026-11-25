package com.mycompany.easymartpos.service;

import com.mycompany.easymartpos.DBConnection;
import com.mycompany.easymartpos.model.ReportChartPoint;
import com.mycompany.easymartpos.model.ReportChartSeries;
import com.mycompany.easymartpos.model.ReportMetric;
import com.mycompany.easymartpos.model.ReportResult;
import com.mycompany.easymartpos.model.ReportType;
import com.mycompany.easymartpos.util.Money;
import com.mycompany.easymartpos.util.SchemaSupport;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Read-only reporting queries and lightweight chart/KPI data for the owner reporting centre. */
public final class ReportsService {
    private static final int NEAR_EXPIRY_DAYS = 30;

    public record DashboardSummary(BigDecimal todaySales, long todayTransactions, long lowStockCount, BigDecimal todayExpenses) { }
    /** The one authoritative store-wide COMPLETED-sales aggregate used by Reports and the admin dashboard. */
    public record StoreWideSalesTotals(long transactions, BigDecimal subtotal, BigDecimal discount,
            BigDecimal netSales, BigDecimal cashTotal, BigDecimal cardTotal, BigDecimal transferTotal) { }

    /** Store-wide owner dashboard values; intentionally independent of user and shift state. */
    public DashboardSummary storeWideDashboardSummary(LocalDate date) throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            DateRange range = DateRange.of(date, date);
            StoreWideSalesTotals sales = storeWideSalesTotals(connection, range);
            try (PreparedStatement expenses = connection.prepareStatement("SELECT COALESCE(SUM(amount), 0) FROM expenses WHERE expense_date>=? AND expense_date<?");
                 PreparedStatement low = connection.prepareStatement("SELECT COUNT(*) FROM products p LEFT JOIN " + batchInventoryAggregate() + " ON b.product_id=p.product_id WHERE p.active=TRUE AND COALESCE(b.sellable_stock,0)<=p.minimum_stock")) {
                bindRange(expenses, range);
                try (ResultSet expenseRows = expenses.executeQuery(); ResultSet lowRows = low.executeQuery()) {
                    expenseRows.next(); lowRows.next();
                    return new DashboardSummary(sales.netSales(), sales.transactions(), lowRows.getLong(1), money(expenseRows.getBigDecimal(1)));
                }
            }
        }
    }

    public DashboardSummary dashboardSummary(LocalDate date) throws SQLException { return storeWideDashboardSummary(date); }
    public StoreWideSalesTotals storeWideDailySales(LocalDate date) throws SQLException {
        try (Connection connection = DBConnection.getConnection()) { return storeWideSalesTotals(connection, DateRange.of(date, date)); }
    }

    public ReportResult generate(ReportType type, LocalDate from, LocalDate to) throws SQLException { return generate(type, from, to, NEAR_EXPIRY_DAYS); }

    /** Near-expiry threshold is supplied by the UI while preserving the 30-day operational default. */
    public ReportResult generate(ReportType type, LocalDate from, LocalDate to, int nearExpiryDays) throws SQLException {
        int threshold = Math.max(1, nearExpiryDays);
        return switch (type) {
            case LOW_STOCK -> lowStock();
            case PRODUCT_STOCK -> productStock();
            case EXPIRED_ITEMS -> expiredItems();
            case NEAR_EXPIRY_ITEMS -> nearExpiryItems(threshold);
            case DAILY_SALES -> dailySales(from);
            case DAILY_PROFIT -> dailyProfit(DateRange.of(from, to));
            case DATE_RANGE_SALES -> salesByDate("Date Range Sales Report", DateRange.of(from, to));
            case SALES_TREND -> salesByDate("Sales Trend", DateRange.of(from, to));
            case PURCHASES -> purchases(DateRange.of(from, to));
            case EXPENSES -> expenses(DateRange.of(from, to));
            case PROFIT_SUMMARY -> profit(DateRange.of(from, to));
            case STOCK_MOVEMENTS -> stockMovements(DateRange.of(from, to));
            case PAYMENTS -> payments(DateRange.of(from, to));
            case TOP_SELLING_PRODUCTS -> topSellingProducts(DateRange.of(from, to));
            case PRODUCT_WISE_SALES -> productWiseSales(DateRange.of(from, to));
            case CATEGORY_WISE_SALES -> categoryWiseSales(DateRange.of(from, to));
            case SLOW_MOVING_PRODUCTS -> slowMovingProducts();
            case OUT_OF_STOCK -> outOfStock();
            case STOCK_VALUATION -> stockValuation();
            case PRODUCT_PROFITABILITY -> productProfitability(DateRange.of(from, to));
            case CASHIER_WISE_SALES -> cashierWiseSales(DateRange.of(from, to));
            case VOID_SALES -> voidSales(DateRange.of(from, to));
            case DISCOUNT_REPORT -> discountReport(DateRange.of(from, to));
            case CUSTOMER_PURCHASES -> customerPurchases(DateRange.of(from, to));
            case LOYALTY_POINTS -> loyaltyPoints();
            case CUSTOMER_SAVINGS -> customerSavings(DateRange.of(from, to));
            case SUPPLIER_PURCHASES -> supplierPurchases(DateRange.of(from, to));
            case INVENTORY_OVERVIEW -> inventoryOverview();
            case BUSINESS_OVERVIEW -> businessOverview(DateRange.of(from, to));
        };
    }

    public List<String> columnsFor(ReportType type) {
        return switch (type) {
            case DAILY_SALES -> List.of("Transactions", "Subtotal", "Discount", "Net Sales", "Cash Total", "Card Total", "Transfer Total");
            case DAILY_PROFIT -> List.of("Date", "Transactions", "Net Sales", "Cost of Goods Sold", "Gross Profit", "Expenses", "Net Profit");
            case DATE_RANGE_SALES -> salesColumns();
            case SALES_TREND -> List.of("Date", "Transactions", "Net Sales");
            case PURCHASES -> List.of("Date", "Supplier", "Invoice No.", "Product", "Quantity", "Cost Price", "Subtotal", "Payment Status");
            case EXPENSES -> List.of("Date", "Type", "Description", "Amount");
            case PROFIT_SUMMARY -> List.of("Metric", "Amount");
            case PRODUCT_STOCK -> List.of("Product ID", "Barcode", "Product Name", "Category", "Total Physical Stock", "Sellable Stock", "Minimum Stock", "Nearest Expiry", "Stock Status");
            case LOW_STOCK -> List.of("Product", "Barcode", "Sellable Stock", "Total Physical Stock", "Minimum Stock", "Status");
            case EXPIRED_ITEMS -> List.of("Batch ID", "Barcode", "Product", "Expiry Date", "Days Expired", "Remaining Qty", "Cost Price", "Stock Value", "Status");
            case NEAR_EXPIRY_ITEMS -> List.of("Batch ID", "Barcode", "Product", "Our Price", "Retail Price", "Expiry Date", "Days Left", "Remaining Qty", "Source", "Status");
            case STOCK_MOVEMENTS -> List.of("Date / Time", "Product", "Barcode", "Movement Type", "Qty In", "Qty Out", "Reference", "User", "Reason");
            case PAYMENTS -> List.of("Date", "Invoice", "Customer", "Payment Method", "Amount", "Reference");
            case TOP_SELLING_PRODUCTS -> List.of("Rank", "Product Name", "Barcode", "Quantity Sold", "Net Sales Contribution");
            case PRODUCT_WISE_SALES -> List.of("Product Name", "Barcode", "Qty Sold", "Net Sales", "COGS", "Gross Profit", "Margin %");
            case CATEGORY_WISE_SALES -> List.of("Category", "Qty Sold", "Net Sales", "Gross Profit");
            case SLOW_MOVING_PRODUCTS -> List.of("Product", "Barcode", "Current Stock", "Last Sale Date", "Days Since Last Sale", "Stock Cost Value", "Status");
            case OUT_OF_STOCK -> List.of("Product ID", "Barcode", "Product Name", "Category", "Sellable Stock", "Total Physical Stock", "Minimum Stock", "Status");
            case STOCK_VALUATION -> List.of("Product", "Category", "Stock", "Cost Price", "Selling Price", "Cost Value", "Potential Sales Value", "Potential Gross Profit");
            case PRODUCT_PROFITABILITY -> List.of("Product", "Barcode", "Qty Sold", "Net Sales", "COGS", "Gross Profit", "Margin %");
            case CASHIER_WISE_SALES -> List.of("Cashier", "Transactions", "Net Sales", "Cash Sales", "Card Sales", "Transfer Sales", "Average Bill", "Void Count");
            case VOID_SALES -> List.of("Invoice", "Cashier", "Customer", "Original Sale Date", "Void Date", "Amount", "Void Reason", "Status");
            case DISCOUNT_REPORT -> List.of("Invoice", "Date", "Cashier", "Customer", "Gross Amount", "Discount", "Net Sale");
            case CUSTOMER_PURCHASES -> List.of("Customer", "Transactions", "Total Spend", "Average Bill", "Last Purchase");
            case LOYALTY_POINTS -> List.of("Customer", "Current Points", "Earned", "Redeemed");
            case CUSTOMER_SAVINGS -> List.of("Customer", "Customer Saving", "Transactions");
            case SUPPLIER_PURCHASES -> List.of("Supplier", "Purchase Count", "Purchase Total", "Last Purchase", "Average Purchase");
            case INVENTORY_OVERVIEW, BUSINESS_OVERVIEW -> List.of("Metric", "Value");
        };
    }

    private static List<String> salesColumns() { return List.of("Date", "Transactions", "Subtotal", "Discount", "Net Sales", "Cash Total", "Card Total", "Transfer Total"); }

    private ReportResult dailySales(LocalDate date) throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            DateRange range = DateRange.of(date, date);
            StoreWideSalesTotals totals = storeWideSalesTotals(connection, range);
            return report("Daily Sales Report", columnsFor(ReportType.DAILY_SALES), List.of(row(totals.transactions(), totals.subtotal(), totals.discount(), totals.netSales(), totals.cashTotal(), totals.cardTotal(), totals.transferTotal())),
                    List.of(metric("Net Sales", totals.netSales()), metric("Transactions", totals.transactions()), metric("Cash Total", totals.cashTotal()), metric("Card Total", totals.cardTotal()), metric("Transfer Total", totals.transferTotal())),
                    List.of(new ReportChartSeries("Net Sales", hourlySales(connection, range))), false, summary("Net Sales", totals.netSales()), "");
        }
    }

    /**
     * One row per day: completed sales, their recorded cost, the gross margin, that day's expenses and the net result.
     * Uses the same definitions as the Profit Summary, so the daily rows add up to its totals.
     */
    private ReportResult dailyProfit(DateRange range) throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            Map<LocalDate, BigDecimal> netSales = new TreeMap<>();
            Map<LocalDate, Long> transactions = new TreeMap<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT DATE(sale_date), COUNT(*), COALESCE(SUM(total_amount),0) FROM sales "
                    + "WHERE sale_status='COMPLETED' AND sale_date>=? AND sale_date<? GROUP BY DATE(sale_date)")) {
                bindRange(statement, range);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        LocalDate day = rows.getDate(1).toLocalDate();
                        transactions.put(day, rows.getLong(2));
                        netSales.put(day, money(rows.getBigDecimal(3)));
                    }
                }
            }
            if (!SchemaSupport.hasColumn(connection, "sale_items", "cost_price")) {
                return unavailableDailyProfit(netSales, transactions);
            }
            Map<LocalDate, BigDecimal> costs = new TreeMap<>();
            long missingCosts = 0;
            try (PreparedStatement statement = connection.prepareStatement("SELECT DATE(s.sale_date), COALESCE(SUM(CASE WHEN si.cost_price IS NULL THEN 0 ELSE si.quantity*si.cost_price END),0), "
                    + "SUM(CASE WHEN si.cost_price IS NULL THEN 1 ELSE 0 END) FROM sale_items si JOIN sales s ON s.sale_id=si.sale_id "
                    + "WHERE s.sale_status='COMPLETED' AND s.sale_date>=? AND s.sale_date<? GROUP BY DATE(s.sale_date)")) {
                bindRange(statement, range);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        costs.put(rows.getDate(1).toLocalDate(), money(rows.getBigDecimal(2)));
                        missingCosts += rows.getLong(3);
                    }
                }
            }
            Map<LocalDate, BigDecimal> expenses = dailyTotals(connection, "SELECT DATE(expense_date), COALESCE(SUM(amount),0) FROM expenses "
                    + "WHERE expense_date>=? AND expense_date<? GROUP BY DATE(expense_date)", range);

            java.util.Set<LocalDate> days = new java.util.TreeSet<>(netSales.keySet());
            days.addAll(expenses.keySet());
            List<List<Object>> rows = new ArrayList<>();
            Map<LocalDate, BigDecimal> netProfitByDay = new TreeMap<>();
            for (LocalDate day : days) {
                BigDecimal sales = netSales.getOrDefault(day, Money.zero());
                BigDecimal cost = costs.getOrDefault(day, Money.zero());
                BigDecimal gross = money(sales.subtract(cost));
                BigDecimal expense = expenses.getOrDefault(day, Money.zero());
                BigDecimal net = money(gross.subtract(expense));
                rows.add(row(day, transactions.getOrDefault(day, 0L), sales, cost, gross, expense, net));
                netProfitByDay.put(day, net);
            }
            BigDecimal netTotal = sum(rows, 6);
            String notice = missingCosts == 0 ? "" : "Some sales have no cost snapshot, so their cost is not included and those days may show higher profit.";
            return report("Daily Profit Report", columnsFor(ReportType.DAILY_PROFIT), rows,
                    List.of(metric("Net Profit", netTotal), metric("Gross Profit", sum(rows, 4)), metric("Expenses", sum(rows, 5)), metric("Net Sales", sum(rows, 2)), metric("Days", rows.size())),
                    List.of(new ReportChartSeries("Net Profit", points(netProfitByDay))), false, summary("Net Profit", netTotal), notice);
        }
    }

    private ReportResult unavailableDailyProfit(Map<LocalDate, BigDecimal> netSales, Map<LocalDate, Long> transactions) {
        List<List<Object>> rows = new ArrayList<>();
        for (Map.Entry<LocalDate, BigDecimal> day : netSales.entrySet()) {
            rows.add(row(day.getKey(), transactions.getOrDefault(day.getKey(), 0L), day.getValue(), "Unavailable", "Unavailable", "Unavailable", "Unavailable"));
        }
        return report("Daily Profit Report", columnsFor(ReportType.DAILY_PROFIT), rows,
                List.of(metric("Net Sales", sum(rows, 2)), metric("Net Profit", "Unavailable"), metric("Days", rows.size())), List.of(), false,
                List.of("Net Profit: Unavailable"), "Cost snapshots are not available in this database, so profit cannot be calculated.");
    }

    private ReportResult salesByDate(String title, DateRange range) throws SQLException {
        String sql = "SELECT DATE(s.sale_date) report_date, COUNT(*) transactions, COALESCE(SUM(s.subtotal),0) subtotal, COALESCE(SUM(s.discount),0) discount, COALESCE(SUM(s.total_amount),0) net_sales, "
                + "COALESCE(SUM(p.cash_total),0) cash_total, COALESCE(SUM(p.card_total),0) card_total, COALESCE(SUM(p.transfer_total),0) transfer_total "
                + "FROM sales s LEFT JOIN (SELECT sale_id, SUM(CASE WHEN payment_method='CASH' THEN amount ELSE 0 END) cash_total, SUM(CASE WHEN payment_method='CARD' THEN amount ELSE 0 END) card_total, SUM(CASE WHEN payment_method='TRANSFER' THEN amount ELSE 0 END) transfer_total FROM sale_payments WHERE payment_status='COMPLETED' GROUP BY sale_id) p ON p.sale_id=s.sale_id WHERE s.sale_status='COMPLETED' AND s.sale_date>=? AND s.sale_date<? GROUP BY DATE(s.sale_date) ORDER BY report_date";
        List<List<Object>> rows = query(sql, range, 8);
        BigDecimal netSales = sum(rows, 4), cash = sum(rows, 5), card = sum(rows, 6), transfer = sum(rows, 7);
        return report(title, salesColumns(), rows,
                List.of(metric("Net Sales", netSales), metric("Transactions", sumLong(rows, 1)), metric("Discount", sum(rows, 3)), metric("Cash Total", cash), metric("Card Total", card), metric("Transfer Total", transfer)),
                List.of(new ReportChartSeries("Net Sales", dateSeries(rows, 0, 4))), false, summary("Net Sales", netSales), "");
    }

    private StoreWideSalesTotals storeWideSalesTotals(Connection connection, DateRange range) throws SQLException {
        String sql = "SELECT COUNT(*) transaction_count, COALESCE(SUM(s.subtotal),0) subtotal, COALESCE(SUM(s.discount),0) discount, COALESCE(SUM(s.total_amount),0) net_sales, "
                + "COALESCE(SUM(p.cash_total),0) cash_total, COALESCE(SUM(p.card_total),0) card_total, COALESCE(SUM(p.transfer_total),0) transfer_total "
                + "FROM sales s LEFT JOIN (SELECT sale_id, SUM(CASE WHEN payment_method='CASH' THEN amount ELSE 0 END) cash_total, SUM(CASE WHEN payment_method='CARD' THEN amount ELSE 0 END) card_total, SUM(CASE WHEN payment_method='TRANSFER' THEN amount ELSE 0 END) transfer_total FROM sale_payments WHERE payment_status='COMPLETED' GROUP BY sale_id) p ON p.sale_id=s.sale_id WHERE s.sale_status='COMPLETED' AND s.sale_date>=? AND s.sale_date<?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindRange(statement, range);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return new StoreWideSalesTotals(result.getLong("transaction_count"), money(result.getBigDecimal("subtotal")), money(result.getBigDecimal("discount")), money(result.getBigDecimal("net_sales")), money(result.getBigDecimal("cash_total")), money(result.getBigDecimal("card_total")), money(result.getBigDecimal("transfer_total")));
            }
        }
    }

    private List<ReportChartPoint> hourlySales(Connection connection, DateRange range) throws SQLException {
        String sql = "SELECT HOUR(sale_date), COALESCE(SUM(total_amount),0) FROM sales WHERE sale_status='COMPLETED' AND sale_date>=? AND sale_date<? GROUP BY HOUR(sale_date) ORDER BY HOUR(sale_date)";
        List<ReportChartPoint> points = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindRange(statement, range);
            try (ResultSet result = statement.executeQuery()) { while (result.next()) points.add(new ReportChartPoint(String.format("%02d:00", result.getInt(1)), money(result.getBigDecimal(2)))); }
        }
        return points;
    }

    private ReportResult purchases(DateRange range) throws SQLException {
        String sql = "SELECT sp.purchase_date, s.supplier_name, sp.invoice_number, p.product_name, pi.quantity, pi.cost_price, pi.subtotal, sp.payment_status FROM suppliers_purchases sp JOIN suppliers s ON s.supplier_id=sp.supplier_id JOIN purchase_items pi ON pi.purchase_id=sp.purchase_id JOIN products p ON p.product_id=pi.product_id WHERE sp.purchase_date>=? AND sp.purchase_date<? ORDER BY sp.purchase_date DESC, sp.purchase_id DESC";
        List<List<Object>> rows = query(sql, range, 8); BigDecimal total = sum(rows, 6);
        return report("Purchase Report", columnsFor(ReportType.PURCHASES), rows,
                List.of(metric("Purchase Value", total), metric("Purchase Lines", rows.size()), metric("Suppliers", distinctCount(rows, 1)), metric("Status", rows.isEmpty() ? "No purchases" : "Loaded")),
                List.of(new ReportChartSeries("Purchases", dateSeries(rows, 0, 6))), false, summary("Purchase Total", total), "");
    }

    private ReportResult expenses(DateRange range) throws SQLException {
        String sql = "SELECT expense_date, expense_type, description, amount FROM expenses WHERE expense_date>=? AND expense_date<? ORDER BY expense_date DESC, expense_id DESC";
        List<List<Object>> rows = query(sql, range, 4); BigDecimal total = sum(rows, 3);
        return report("Expense Report", columnsFor(ReportType.EXPENSES), rows,
                List.of(metric("Total Expenses", total), metric("Expense Records", rows.size()), metric("Average Expense", rows.isEmpty() ? Money.zero() : total.divide(BigDecimal.valueOf(rows.size()), 2, RoundingMode.HALF_UP)), metric("Status", rows.isEmpty() ? "No expenses" : "Loaded")),
                List.of(new ReportChartSeries("Expenses", dateSeries(rows, 0, 3))), false, summary("Total Expenses", total), "");
    }

    private ReportResult lowStock() throws SQLException {
        String sql = "SELECT p.product_name, p.barcode, COALESCE(b.sellable_stock,0), COALESCE(b.total_physical_stock,0), p.minimum_stock, "
                + "CASE WHEN COALESCE(b.sellable_stock,0)<=0 THEN 'OUT OF STOCK' ELSE 'LOW STOCK' END "
                + "FROM products p LEFT JOIN " + batchInventoryAggregate() + " ON b.product_id=p.product_id "
                + "WHERE p.active=TRUE AND COALESCE(b.sellable_stock,0)<=p.minimum_stock ORDER BY COALESCE(b.sellable_stock,0), p.product_name";
        List<List<Object>> rows = query(sql, null, 6);
        try (Connection connection = DBConnection.getConnection()) {
            return report("Low Stock Report", columnsFor(ReportType.LOW_STOCK), rows, inventoryMetrics(connection, "Low Stock Items", rows.size(), Money.zero()), List.of(), true, List.of("Low Stock Items: " + rows.size()), rows.isEmpty() ? "No low-stock products require attention." : "Sellable stock at or below minimum is highlighted for action.");
        }
    }

    private ReportResult productStock() throws SQLException {
        String sql = "SELECT p.product_id, p.barcode, p.product_name, COALESCE(c.category_name,'Uncategorised'), "
                + "COALESCE(b.total_physical_stock,0), COALESCE(b.sellable_stock,0), p.minimum_stock, b.nearest_expiry, "
                + "CASE WHEN COALESCE(b.sellable_stock,0)<=0 THEN 'OUT OF STOCK' "
                + "WHEN COALESCE(b.sellable_stock,0)<=p.minimum_stock THEN 'LOW STOCK' ELSE 'OK' END "
                + "FROM products p LEFT JOIN categories c ON c.category_id=p.category_id "
                + "LEFT JOIN " + batchInventoryAggregate() + " ON b.product_id=p.product_id "
                + "WHERE p.active=TRUE ORDER BY p.product_name";
        List<List<Object>> rows = query(sql, null, 9);
        return report("Product Stock Report", columnsFor(ReportType.PRODUCT_STOCK), rows,
                List.of(metric("Products", rows.size()), quantityMetric("Total Physical Stock", sum(rows, 4)), quantityMetric("Sellable Stock", sum(rows, 5)), metric("Low Stock", rows.stream().filter(row -> !"OK".equals(row.get(8))).count())),
                List.of(), true, List.of("Products: " + rows.size()), "Physical stock includes all remaining batches; sellable stock excludes expired, inactive and zero-quantity batches.");
    }

    private ReportResult expiredItems() throws SQLException {
        String sql = "SELECT pb.batch_id, p.barcode, p.product_name, pb.expiry_date, DATEDIFF(CURDATE(),pb.expiry_date), "
                + "pb.quantity_available, pb.cost_price, (pb.quantity_available*pb.cost_price), 'EXPIRED' "
                + "FROM product_batches pb JOIN products p ON p.product_id=pb.product_id "
                + "WHERE pb.active=TRUE AND pb.quantity_available>0 AND pb.expiry_date<CURDATE() ORDER BY pb.expiry_date, p.product_name, pb.batch_id";
        List<List<Object>> rows = query(sql, null, 9); BigDecimal value = sum(rows, 7);
        return report("Expired Items Report", columnsFor(ReportType.EXPIRED_ITEMS), rows,
                List.of(metric("Expired Batches", rows.size()), metric("Expired Stock Value", value), quantityMetric("Stock Units", sum(rows, 5)), metric("Status", rows.isEmpty() ? "Clear" : "Immediate action")),
                List.of(), true, summary("Expired Stock Value", value), rows.isEmpty() ? "No active batches are expired." : "Expired batch inventory is highlighted in red and is informational only.");
    }

    private ReportResult nearExpiryItems(int threshold) throws SQLException {
        String sql = "SELECT pb.batch_id, p.barcode, p.product_name, pb.selling_price, pb.retail_price, pb.expiry_date, "
                + "DATEDIFF(pb.expiry_date,CURDATE()), pb.quantity_available, pb.source_type, "
                + "CASE WHEN pb.expiry_date=CURDATE() THEN 'TODAY' ELSE 'NEAR EXPIRY' END "
                + "FROM product_batches pb JOIN products p ON p.product_id=pb.product_id "
                + "WHERE pb.active=TRUE AND pb.quantity_available>0 AND pb.expiry_date BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL " + threshold + " DAY) "
                + "ORDER BY pb.expiry_date, p.product_name, pb.batch_id";
        List<List<Object>> rows = query(sql, null, 10);
        return report("Near Expiry Items Report", columnsFor(ReportType.NEAR_EXPIRY_ITEMS), rows,
                List.of(metric("Near Expiry Batches", rows.size()), metric("Within", threshold + " days"), quantityMetric("Stock Units", sum(rows, 7)), metric("Status", rows.isEmpty() ? "Clear" : "Review soon")),
                List.of(), true, List.of("Near Expiry Batches: " + rows.size()), rows.isEmpty() ? "No active batches expire in the next " + threshold + " days." : "Near-expiry batch inventory is highlighted in amber.");
    }

    private ReportResult payments(DateRange range) throws SQLException {
        List<List<Object>> rows;
        try (Connection connection = DBConnection.getConnection()) {
            String reference = SchemaSupport.hasColumn(connection, "sale_payments", "payment_reference") ? "COALESCE(p.payment_reference, p.card_reference)" : "p.card_reference";
            String sql = "SELECT s.sale_date, s.invoice_number, COALESCE(c.customer_name,'Walk-in Customer'), COALESCE(p.payment_method,s.payment_method), COALESCE(p.amount,s.total_amount), " + reference + " FROM sales s LEFT JOIN sale_payments p ON p.sale_id=s.sale_id AND p.payment_status='COMPLETED' LEFT JOIN customers c ON c.customer_id=s.customer_id WHERE s.sale_status='COMPLETED' AND s.sale_date>=? AND s.sale_date<? ORDER BY s.sale_date DESC, s.sale_id DESC, p.payment_id";
            rows = query(connection, sql, range, 6);
        }
        BigDecimal cash = totalFor(rows, 3, "CASH", 4), card = totalFor(rows, 3, "CARD", 4), transfer = totalFor(rows, 3, "TRANSFER", 4), total = sum(rows, 4);
        return report("Payment Report", columnsFor(ReportType.PAYMENTS), rows,
                List.of(metric("Cash Total", cash), metric("Card Total", card), metric("Transfer Total", transfer), metric("Total Payments", total)),
                List.of(new ReportChartSeries("Payments", List.of(new ReportChartPoint("Cash", cash), new ReportChartPoint("Card", card), new ReportChartPoint("Transfer", transfer)))), true, summary("Total Payments", total), "");
    }

    private ReportResult stockMovements(DateRange range) throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            String timestamp = SchemaSupport.hasColumn(connection, "stock_movements", "moved_at") ? "sm.moved_at" : "sm.movement_date";
            String sql = "SELECT " + timestamp + ", p.product_name, p.barcode, sm.movement_type, CASE WHEN sm.quantity>0 THEN sm.quantity ELSE 0 END, CASE WHEN sm.quantity<0 THEN ABS(sm.quantity) ELSE 0 END, sm.reference_id, COALESCE(u.username, CONCAT('User #', sm.user_id)), sm.reason FROM stock_movements sm JOIN products p ON p.product_id=sm.product_id LEFT JOIN users u ON u.user_id=sm.user_id WHERE " + timestamp + ">=? AND " + timestamp + "<? ORDER BY " + timestamp + " DESC, sm.movement_id DESC";
            List<List<Object>> rows = query(connection, sql, range, 9);
            return report("Stock Movement Report", columnsFor(ReportType.STOCK_MOVEMENTS), rows,
                    List.of(metric("Movements", rows.size()), quantityMetric("Qty In", sum(rows, 4)), quantityMetric("Qty Out", sum(rows, 5)), metric("Status", rows.isEmpty() ? "No movements" : "Loaded")),
                    List.of(), false, List.of("Movements: " + rows.size()), "");
        }
    }

    private ReportResult topSellingProducts(DateRange range) throws SQLException {
        String sql = "SELECT p.product_name, p.barcode, COALESCE(SUM(si.quantity),0), COALESCE(SUM(si.subtotal-CASE WHEN s.subtotal=0 THEN 0 ELSE (s.discount*si.subtotal/s.subtotal) END),0) FROM sale_items si JOIN sales s ON s.sale_id=si.sale_id JOIN products p ON p.product_id=si.product_id WHERE s.sale_status='COMPLETED' AND s.sale_date>=? AND s.sale_date<? GROUP BY p.product_id, p.product_name, p.barcode ORDER BY 3 DESC, 4 DESC LIMIT 10";
        List<List<Object>> raw = query(sql, range, 4); List<List<Object>> rows = new ArrayList<>(); int rank = 1;
        for (List<Object> line : raw) rows.add(row(rank++, line.get(0), line.get(1), line.get(2), line.get(3)));
        BigDecimal total = sum(rows, 4);
        return report("Top Selling Products", columnsFor(ReportType.TOP_SELLING_PRODUCTS), rows,
                List.of(metric("Top Products", rows.size()), quantityMetric("Quantity Sold", sum(rows, 3)), metric("Sales Contribution", total), metric("Top Product", rows.isEmpty() ? "—" : String.valueOf(rows.get(0).get(1)))),
                List.of(new ReportChartSeries("Sales Contribution", categorySeries(rows, 1, 4))), true, summary("Sales Contribution", total), "");
    }

    private ReportResult productWiseSales(DateRange range) throws SQLException { return salesByProduct("Product-wise Sales", ReportType.PRODUCT_WISE_SALES, range); }
    private ReportResult productProfitability(DateRange range) throws SQLException { return salesByProduct("Product Profitability", ReportType.PRODUCT_PROFITABILITY, range); }

    private ReportResult salesByProduct(String title, ReportType type, DateRange range) throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            boolean snapshots = SchemaSupport.hasColumn(connection, "sale_items", "cost_price");
            String costs = snapshots ? "COALESCE(SUM(si.quantity*si.cost_price),0)" : "NULL";
            String sql = "SELECT p.product_name, p.barcode, COALESCE(SUM(si.quantity),0), COALESCE(SUM(si.subtotal-CASE WHEN s.subtotal=0 THEN 0 ELSE (s.discount*si.subtotal/s.subtotal) END),0), " + costs
                    + " FROM sale_items si JOIN sales s ON s.sale_id=si.sale_id JOIN products p ON p.product_id=si.product_id WHERE s.sale_status='COMPLETED' AND s.sale_date>=? AND s.sale_date<? GROUP BY p.product_id, p.product_name, p.barcode ORDER BY 4 DESC";
            List<List<Object>> raw = query(connection, sql, range, 5); List<List<Object>> rows = new ArrayList<>();
            for (List<Object> item : raw) { BigDecimal net = decimal(item.get(3)); Object cost = item.get(4); BigDecimal cogs = cost == null ? null : decimal(cost); BigDecimal gross = cogs == null ? null : money(net.subtract(cogs)); Object margin = gross == null || net.signum() == 0 ? "Unavailable" : gross.multiply(BigDecimal.valueOf(100)).divide(net, 1, RoundingMode.HALF_UP).toPlainString() + "%"; rows.add(row(item.get(0), item.get(1), item.get(2), net, cogs, gross, margin)); }
            BigDecimal net = sum(rows, 3), gross = sum(rows, 5);
            String notice = snapshots ? "" : "Cost snapshots are unavailable for historical sales; COGS and gross-profit values are not shown.";
            return report(title, columnsFor(type), rows, List.of(metric("Net Sales", net), quantityMetric("Qty Sold", sum(rows, 2)), metric("Gross Profit", gross), metric("Products", rows.size())), List.of(new ReportChartSeries("Net Sales", categorySeries(rows, 0, 3))), true, summary("Net Sales", net), notice);
        }
    }

    private ReportResult categoryWiseSales(DateRange range) throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            boolean snapshots = SchemaSupport.hasColumn(connection, "sale_items", "cost_price"); String costs = snapshots ? "COALESCE(SUM(si.quantity*si.cost_price),0)" : "NULL";
            String sql = "SELECT COALESCE(c.category_name,'Uncategorised'), COALESCE(SUM(si.quantity),0), COALESCE(SUM(si.subtotal-CASE WHEN s.subtotal=0 THEN 0 ELSE (s.discount*si.subtotal/s.subtotal) END),0), " + costs
                    + " FROM sale_items si JOIN sales s ON s.sale_id=si.sale_id JOIN products p ON p.product_id=si.product_id LEFT JOIN categories c ON c.category_id=p.category_id WHERE s.sale_status='COMPLETED' AND s.sale_date>=? AND s.sale_date<? GROUP BY c.category_id, c.category_name ORDER BY 3 DESC";
            List<List<Object>> raw = query(connection, sql, range, 4); List<List<Object>> rows = new ArrayList<>();
            for (List<Object> item : raw) { Object cogs = item.get(3); rows.add(row(item.get(0), item.get(1), item.get(2), cogs == null ? "Unavailable" : money(decimal(item.get(2)).subtract(decimal(cogs))))); }
            BigDecimal net = sum(rows, 2); return report("Category-wise Sales", columnsFor(ReportType.CATEGORY_WISE_SALES), rows, List.of(metric("Net Sales", net), quantityMetric("Qty Sold", sum(rows, 1)), metric("Categories", rows.size()), metric("Gross Profit", sum(rows, 3))), List.of(new ReportChartSeries("Net Sales", categorySeries(rows, 0, 2))), true, summary("Net Sales", net), snapshots ? "" : "Cost snapshots are unavailable for historical sales; gross-profit values are not shown.");
        }
    }

    private ReportResult slowMovingProducts() throws SQLException {
        String sql = "SELECT p.product_name, p.barcode, b.total_physical_stock, MAX(s.sale_date), DATEDIFF(CURDATE(), MAX(s.sale_date)), b.stock_cost_value, 'SLOW MOVING' "
                + "FROM products p JOIN " + batchInventoryAggregate() + " ON b.product_id=p.product_id "
                + "JOIN sale_items si ON si.product_id=p.product_id JOIN sales s ON s.sale_id=si.sale_id AND s.sale_status='COMPLETED' "
                + "WHERE p.active=TRUE AND b.total_physical_stock>0 "
                + "GROUP BY p.product_id, p.product_name, p.barcode, b.total_physical_stock, b.stock_cost_value "
                + "HAVING MAX(s.sale_date)<DATE_SUB(CURDATE(), INTERVAL 30 DAY) ORDER BY 5 DESC, 3 DESC";
        List<List<Object>> rows = query(sql, null, 7); BigDecimal value = sum(rows, 5);
        return report("Slow Moving Products", columnsFor(ReportType.SLOW_MOVING_PRODUCTS), rows, List.of(metric("Slow Moving", rows.size()), metric("Stock Cost Value", value), metric("Review Window", "30 days"), metric("Status", rows.isEmpty() ? "Healthy" : "Review")), List.of(), true, summary("Stock Cost Value", value), rows.isEmpty() ? "No products with recorded sales are slow-moving over the last 30 days." : "Only products with a recorded sales history are included; newly added products are not classified as slow-moving.");
    }

    private ReportResult outOfStock() throws SQLException {
        String sql = "SELECT p.product_id, p.barcode, p.product_name, COALESCE(c.category_name,'Uncategorised'), "
                + "COALESCE(b.sellable_stock,0), COALESCE(b.total_physical_stock,0), p.minimum_stock, 'OUT OF STOCK' "
                + "FROM products p LEFT JOIN categories c ON c.category_id=p.category_id "
                + "LEFT JOIN " + batchInventoryAggregate() + " ON b.product_id=p.product_id "
                + "WHERE p.active=TRUE AND COALESCE(b.sellable_stock,0)<=0 ORDER BY p.product_name";
        List<List<Object>> rows = query(sql, null, 8);
        return report("Out of Stock Report", columnsFor(ReportType.OUT_OF_STOCK), rows, List.of(metric("Out of Stock Items", rows.size()), metric("Affected Categories", distinctCount(rows, 3)), metric("Active Products", activeProductCount()), metric("Status", rows.isEmpty() ? "Healthy" : "Action required")), List.of(), true, List.of("Out of Stock Items: " + rows.size()), rows.isEmpty() ? "All active products currently have stock." : "Out-of-stock products are highlighted as a priority.");
    }

    private ReportResult stockValuation() throws SQLException {
        String sql = "SELECT p.product_name, COALESCE(c.category_name,'Uncategorised'), COALESCE(b.total_physical_stock,0), "
                + "CASE WHEN COALESCE(b.total_physical_stock,0)=0 THEN 0 ELSE b.stock_cost_value/b.total_physical_stock END, "
                + "CASE WHEN COALESCE(b.total_physical_stock,0)=0 THEN 0 ELSE b.potential_sales_value/b.total_physical_stock END, "
                + "COALESCE(b.stock_cost_value,0), COALESCE(b.potential_sales_value,0), COALESCE(b.potential_gross_profit,0) "
                + "FROM products p LEFT JOIN categories c ON c.category_id=p.category_id "
                + "LEFT JOIN " + batchInventoryAggregate() + " ON b.product_id=p.product_id WHERE p.active=TRUE ORDER BY p.product_name";
        List<List<Object>> rows = query(sql, null, 8); BigDecimal cost = sum(rows, 5), sales = sum(rows, 6), gross = sum(rows, 7);
        return report("Stock Valuation", columnsFor(ReportType.STOCK_VALUATION), rows, List.of(metric("Stock Cost Value", cost), metric("Potential Sales Value", sales), metric("Potential Gross Profit", gross), quantityMetric("Stock Units", sum(rows, 2))), List.of(new ReportChartSeries("Potential Sales Value", categorySeries(rows.stream().limit(10).toList(), 0, 6))), true, summary("Total Stock Cost Value", cost), "");
    }

    private ReportResult cashierWiseSales(DateRange range) throws SQLException {
        String sql = "SELECT COALESCE(u.username, CONCAT('User #',s.user_id)), COUNT(*), COALESCE(SUM(s.total_amount),0), COALESCE(SUM(p.cash_sales),0), COALESCE(SUM(p.card_sales),0), COALESCE(SUM(p.transfer_sales),0), COALESCE(AVG(s.total_amount),0), (SELECT COUNT(*) FROM sales vs WHERE vs.user_id=s.user_id AND vs.sale_status='VOIDED' AND vs.voided_at>=? AND vs.voided_at<?) FROM sales s LEFT JOIN users u ON u.user_id=s.user_id LEFT JOIN (SELECT sale_id, SUM(CASE WHEN payment_method='CASH' THEN amount ELSE 0 END) cash_sales, SUM(CASE WHEN payment_method='CARD' THEN amount ELSE 0 END) card_sales, SUM(CASE WHEN payment_method='TRANSFER' THEN amount ELSE 0 END) transfer_sales FROM sale_payments WHERE payment_status='COMPLETED' GROUP BY sale_id) p ON p.sale_id=s.sale_id WHERE s.sale_status='COMPLETED' AND s.sale_date>=? AND s.sale_date<? GROUP BY s.user_id, u.username ORDER BY 3 DESC";
        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            bindRange(statement, range); bindRangeAt(statement, range, 3); List<List<Object>> rows = resultRows(statement, 8); BigDecimal total = sum(rows, 2);
            return report("Cashier-wise Sales", columnsFor(ReportType.CASHIER_WISE_SALES), rows, List.of(metric("Net Sales", total), metric("Transactions", sumLong(rows, 1)), metric("Cash Sales", sum(rows, 3)), metric("Card Sales", sum(rows, 4)), metric("Transfer Sales", sum(rows, 5))), List.of(new ReportChartSeries("Net Sales", categorySeries(rows, 0, 2))), true, summary("Net Sales", total), "");
        }
    }

    private ReportResult voidSales(DateRange range) throws SQLException {
        String sql = "SELECT s.invoice_number, COALESCE(u.username, CONCAT('User #',s.user_id)), COALESCE(c.customer_name,'Walk-in Customer'), s.sale_date, s.voided_at, s.total_amount, s.void_reason, 'VOIDED' FROM sales s LEFT JOIN users u ON u.user_id=s.user_id LEFT JOIN customers c ON c.customer_id=s.customer_id WHERE s.sale_status='VOIDED' AND s.voided_at>=? AND s.voided_at<? ORDER BY s.voided_at DESC";
        List<List<Object>> rows = query(sql, range, 8); BigDecimal total = sum(rows, 5);
        return report("Void Sales Audit", columnsFor(ReportType.VOID_SALES), rows, List.of(metric("Voided Sales", rows.size()), metric("Voided Amount", total), metric("Status", rows.isEmpty() ? "Clear" : "Review"), metric("Period", "Selected range")), List.of(), true, summary("Voided Amount", total), rows.isEmpty() ? "No voided sales found for the selected period." : "Voided sales are shown as an audit record.");
    }

    private ReportResult discountReport(DateRange range) throws SQLException {
        String sql = "SELECT s.invoice_number, s.sale_date, COALESCE(u.username, CONCAT('User #',s.user_id)), COALESCE(c.customer_name,'Walk-in Customer'), s.subtotal, s.discount, s.total_amount FROM sales s LEFT JOIN users u ON u.user_id=s.user_id LEFT JOIN customers c ON c.customer_id=s.customer_id WHERE s.sale_status='COMPLETED' AND s.discount>0 AND s.sale_date>=? AND s.sale_date<? ORDER BY s.sale_date DESC";
        List<List<Object>> rows = query(sql, range, 7); BigDecimal discount = sum(rows, 5);
        return report("Discount Report", columnsFor(ReportType.DISCOUNT_REPORT), rows, List.of(metric("Total Discounts", discount), metric("Discounted Transactions", rows.size()), metric("Average Discount", rows.isEmpty() ? Money.zero() : discount.divide(BigDecimal.valueOf(rows.size()), 2, RoundingMode.HALF_UP)), metric("Net Sales", sum(rows, 6))), List.of(new ReportChartSeries("Discount", dateSeries(rows, 1, 5))), false, summary("Total Discounts", discount), rows.isEmpty() ? "No discounted transactions for the selected period." : "");
    }

    private ReportResult customerPurchases(DateRange range) throws SQLException {
        String sql = "SELECT COALESCE(c.customer_name,'Walk-in Customer'), COUNT(*), COALESCE(SUM(s.total_amount),0), COALESCE(AVG(s.total_amount),0), MAX(s.sale_date) FROM sales s LEFT JOIN customers c ON c.customer_id=s.customer_id WHERE s.sale_status='COMPLETED' AND s.sale_date>=? AND s.sale_date<? GROUP BY s.customer_id, c.customer_name ORDER BY 3 DESC";
        List<List<Object>> rows = query(sql, range, 5); BigDecimal spend = sum(rows, 2);
        return report("Customer Purchase Report", columnsFor(ReportType.CUSTOMER_PURCHASES), rows, List.of(metric("Customer Spend", spend), metric("Customers", rows.size()), metric("Transactions", sumLong(rows, 1)), metric("Top Customer", rows.isEmpty() ? "—" : String.valueOf(rows.get(0).get(0)))), List.of(new ReportChartSeries("Total Spend", categorySeries(rows.stream().limit(10).toList(), 0, 2))), true, summary("Customer Spend", spend), rows.isEmpty() ? "No registered customer activity found for the selected period." : "");
    }

    private ReportResult loyaltyPoints() throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            if (!SchemaSupport.hasColumn(connection, "customers", "loyalty_points")) return unavailableReport("Loyalty Points Report", ReportType.LOYALTY_POINTS, "Loyalty point balances are not available in the current database.");
            String earned = SchemaSupport.hasTable(connection, "sale_loyalty") ? "COALESCE(SUM(sl.earned_points),0)" : "0"; String redeemed = SchemaSupport.hasTable(connection, "sale_loyalty") ? "COALESCE(SUM(sl.redeemed_points),0)" : "0"; String join = SchemaSupport.hasTable(connection, "sale_loyalty") ? " LEFT JOIN sale_loyalty sl ON sl.sale_id=s.sale_id" : "";
            String sql = "SELECT c.customer_name, c.loyalty_points, " + earned + ", " + redeemed + " FROM customers c LEFT JOIN sales s ON s.customer_id=c.customer_id" + join + " GROUP BY c.customer_id, c.customer_name, c.loyalty_points ORDER BY c.loyalty_points DESC";
            List<List<Object>> rows = query(connection, sql, null, 4); return report("Loyalty Points Report", columnsFor(ReportType.LOYALTY_POINTS), rows, List.of(metric("Loyalty Customers", rows.size()), quantityMetric("Current Points", sum(rows, 1)), quantityMetric("Earned", sum(rows, 2)), quantityMetric("Redeemed", sum(rows, 3))), List.of(), false, List.of("Loyalty customers: " + rows.size()), rows.isEmpty() ? "No loyalty customer activity found." : "");
        }
    }

    private ReportResult customerSavings(DateRange range) throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            if (!SchemaSupport.hasColumn(connection, "sale_items", "retail_price")) return unavailableReport("Customer Saving Report", ReportType.CUSTOMER_SAVINGS, "Retail-price snapshots are unavailable, so historic customer savings cannot be calculated reliably.");
            String sql = "SELECT COALESCE(c.customer_name,'Walk-in Customer'), COALESCE(SUM(GREATEST(si.retail_price-si.unit_price,0)*si.quantity),0), COUNT(DISTINCT s.sale_id) FROM sales s JOIN sale_items si ON si.sale_id=s.sale_id LEFT JOIN customers c ON c.customer_id=s.customer_id WHERE s.sale_status='COMPLETED' AND s.sale_date>=? AND s.sale_date<? GROUP BY s.customer_id, c.customer_name ORDER BY 2 DESC";
            List<List<Object>> rows = query(connection, sql, range, 3); BigDecimal saving = sum(rows, 1); return report("Customer Saving Report", columnsFor(ReportType.CUSTOMER_SAVINGS), rows, List.of(metric("Customer Saving", saving), metric("Customers", rows.size()), metric("Transactions", sumLong(rows, 2)), metric("Status", rows.isEmpty() ? "No activity" : "Loaded")), List.of(new ReportChartSeries("Customer Saving", categorySeries(rows.stream().limit(10).toList(), 0, 1))), true, summary("Customer Saving", saving), "Savings use retail-price and selling-price snapshots only; they are not shop profit.");
        }
    }

    private ReportResult supplierPurchases(DateRange range) throws SQLException {
        String sql = "SELECT s.supplier_name, COUNT(*), COALESCE(SUM(sp.total_amount),0), MAX(sp.purchase_date), COALESCE(AVG(sp.total_amount),0) FROM suppliers_purchases sp JOIN suppliers s ON s.supplier_id=sp.supplier_id WHERE sp.purchase_date>=? AND sp.purchase_date<? GROUP BY s.supplier_id, s.supplier_name ORDER BY 3 DESC";
        List<List<Object>> rows = query(sql, range, 5); BigDecimal total = sum(rows, 2); return report("Supplier-wise Purchases", columnsFor(ReportType.SUPPLIER_PURCHASES), rows, List.of(metric("Purchase Total", total), metric("Suppliers", rows.size()), metric("Purchase Count", sumLong(rows, 1)), metric("Average Purchase", rows.isEmpty() ? Money.zero() : total.divide(BigDecimal.valueOf(sumLong(rows, 1)), 2, RoundingMode.HALF_UP))), List.of(new ReportChartSeries("Purchase Total", categorySeries(rows, 0, 2))), true, summary("Purchase Total", total), "");
    }

    private ReportResult inventoryOverview() throws SQLException {
        String sql = "SELECT COUNT(*) AS total_products, "
                + "COALESCE(SUM(COALESCE(b.total_physical_stock,0)),0) AS total_stock_units, "
                + "COALESCE(SUM(COALESCE(b.stock_cost_value,0)),0) AS stock_cost_value, "
                + "COALESCE(SUM(COALESCE(b.potential_sales_value,0)),0) AS potential_sales_value, "
                + "COALESCE(SUM(COALESCE(b.potential_gross_profit,0)),0) AS potential_gross_profit, "
                + "COALESCE(SUM(CASE WHEN COALESCE(b.sellable_stock,0)<=p.minimum_stock THEN 1 ELSE 0 END),0) AS low_stock_count, "
                + "COALESCE(SUM(CASE WHEN COALESCE(b.sellable_stock,0)<=0 THEN 1 ELSE 0 END),0) AS out_of_stock_count, "
                + "COALESCE(SUM(COALESCE(b.near_expiry_batch_count,0)),0) AS near_expiry_count, "
                + "COALESCE(SUM(COALESCE(b.expired_batch_count,0)),0) AS expired_count "
                + "FROM products p LEFT JOIN " + batchInventoryAggregate() + " ON b.product_id=p.product_id WHERE p.active=1";
        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql); ResultSet result = statement.executeQuery()) {
            result.next();
            long totalProducts = result.getLong("total_products"); BigDecimal totalUnits = money(result.getBigDecimal("total_stock_units")); BigDecimal stockCost = money(result.getBigDecimal("stock_cost_value")); BigDecimal potentialSales = money(result.getBigDecimal("potential_sales_value")); BigDecimal potentialGross = money(result.getBigDecimal("potential_gross_profit"));
            long lowStock = result.getLong("low_stock_count"); long outOfStock = result.getLong("out_of_stock_count"); long nearExpiry = result.getLong("near_expiry_count"); long expired = result.getLong("expired_count");
            List<List<Object>> rows = List.of(row("Total Products", totalProducts), row("Total Stock Units", totalUnits), row("Total Stock Cost Value", stockCost), row("Potential Sales Value", potentialSales), row("Potential Gross Profit", potentialGross), row("Low Stock Count", lowStock), row("Out of Stock Count", outOfStock), row("Near Expiry Count", nearExpiry), row("Expired Count", expired));
            List<ReportChartPoint> status = List.of(new ReportChartPoint("Healthy", BigDecimal.valueOf(Math.max(0, totalProducts - lowStock))), new ReportChartPoint("Low Stock", BigDecimal.valueOf(lowStock)), new ReportChartPoint("Out of Stock", BigDecimal.valueOf(outOfStock)), new ReportChartPoint("Near Expiry", BigDecimal.valueOf(nearExpiry)), new ReportChartPoint("Expired", BigDecimal.valueOf(expired)));
            return report("Inventory Overview", columnsFor(ReportType.INVENTORY_OVERVIEW), rows, List.of(metric("Total Products", totalProducts), quantityMetric("Total Stock Units", totalUnits), metric("Stock Cost Value", stockCost), metric("Potential Sales Value", potentialSales)), List.of(new ReportChartSeries("Inventory Status", status)), true, List.of("Inventory metrics generated from active products."), "Potential Gross Profit: " + currency(potentialGross) + "   •   Low Stock: " + lowStock + "   •   Out of Stock: " + outOfStock + "   •   Near Expiry: " + nearExpiry + "   •   Expired: " + expired);
        }
    }

    private ReportResult businessOverview(DateRange range) throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            StoreWideSalesTotals sales = storeWideSalesTotals(connection, range); BigDecimal expenses = scalar(connection, "SELECT COALESCE(SUM(amount),0) FROM expenses WHERE expense_date>=? AND expense_date<?", range); BigDecimal purchases = scalar(connection, "SELECT COALESCE(SUM(total_amount),0) FROM suppliers_purchases WHERE purchase_date>=? AND purchase_date<?", range);
            boolean snapshots = SchemaSupport.hasColumn(connection, "sale_items", "cost_price"); BigDecimal cogs = snapshots ? scalar(connection, "SELECT COALESCE(SUM(si.quantity*si.cost_price),0) FROM sale_items si JOIN sales s ON s.sale_id=si.sale_id WHERE s.sale_status='COMPLETED' AND s.sale_date>=? AND s.sale_date<? AND si.cost_price IS NOT NULL", range) : Money.zero(); BigDecimal gross = snapshots ? money(sales.netSales().subtract(cogs)) : null; BigDecimal netProfit = gross == null ? null : money(gross.subtract(expenses));
            String top = topProductName(connection, range); List<List<Object>> rows = new ArrayList<>(); rows.add(row("Net Sales", sales.netSales())); rows.add(row("Transactions", sales.transactions())); rows.add(row("Average Bill Value", sales.transactions() == 0 ? Money.zero() : sales.netSales().divide(BigDecimal.valueOf(sales.transactions()), 2, RoundingMode.HALF_UP))); rows.add(row("Gross Profit", gross == null ? "Unavailable" : gross)); rows.add(row("Expenses", expenses)); rows.add(row("Net Profit", netProfit == null ? "Unavailable" : netProfit)); rows.add(row("Purchase Total", purchases)); rows.add(row("Cash Sales", sales.cashTotal())); rows.add(row("Card Sales", sales.cardTotal())); rows.add(row("Transfer Sales", sales.transferTotal())); rows.add(row("Top Selling Product", top));
            return report("Business Overview", columnsFor(ReportType.BUSINESS_OVERVIEW), rows, List.of(metric("Net Sales", sales.netSales()), metric("Transactions", sales.transactions()), metric("Gross Profit", gross == null ? "Unavailable" : currency(gross)), metric("Net Profit", netProfit == null ? "Unavailable" : currency(netProfit))), List.of(new ReportChartSeries("Net Sales", dateSeries(query(connection, "SELECT DATE(sale_date), COUNT(*), COALESCE(SUM(subtotal),0), COALESCE(SUM(discount),0), COALESCE(SUM(total_amount),0), 0, 0 FROM sales WHERE sale_status='COMPLETED' AND sale_date>=? AND sale_date<? GROUP BY DATE(sale_date) ORDER BY 1", range, 7), 0, 4))), false, summary("Net Sales", sales.netSales()), snapshots ? "" : "Cost snapshots are unavailable, so gross and net profit are not shown.");
        }
    }

    private ReportResult unavailableReport(String title, ReportType type, String message) { return report(title, columnsFor(type), List.of(), List.of(metric("Availability", "Unavailable"), metric("Status", "No data"), metric("Report", title), metric("Action", "Review database support")), List.of(), false, List.of(message), message); }
    private long activeProductCount() throws SQLException { try (Connection connection = DBConnection.getConnection()) { return scalarLong(connection, "SELECT COUNT(*) FROM products WHERE active=TRUE"); } }
    private String topProductName(Connection connection, DateRange range) throws SQLException { String sql = "SELECT p.product_name FROM sale_items si JOIN sales s ON s.sale_id=si.sale_id JOIN products p ON p.product_id=si.product_id WHERE s.sale_status='COMPLETED' AND s.sale_date>=? AND s.sale_date<? GROUP BY p.product_id, p.product_name ORDER BY SUM(si.quantity) DESC LIMIT 1"; try (PreparedStatement statement = connection.prepareStatement(sql)) { bindRange(statement, range); try (ResultSet result = statement.executeQuery()) { return result.next() ? result.getString(1) : "—"; } } }

    private ReportResult profit(DateRange range) throws SQLException {
        try (Connection connection = DBConnection.getConnection()) {
            BigDecimal netSales = scalar(connection, "SELECT COALESCE(SUM(total_amount),0) FROM sales WHERE sale_status='COMPLETED' AND sale_date>=? AND sale_date<?", range);
            BigDecimal expenses = scalar(connection, "SELECT COALESCE(SUM(amount),0) FROM expenses WHERE expense_date>=? AND expense_date<?", range);
            String historicalNotice = "Some historical sales are missing cost snapshots. Available profit values are shown where possible.";
            if (!SchemaSupport.hasColumn(connection, "sale_items", "cost_price")) return unavailableProfit(netSales, expenses, historicalNotice);
            long missing = count(connection, "SELECT COUNT(*) FROM sale_items si JOIN sales s ON s.sale_id=si.sale_id WHERE s.sale_status='COMPLETED' AND s.sale_date>=? AND s.sale_date<? AND si.cost_price IS NULL", range);
            BigDecimal cogs = scalar(connection, "SELECT COALESCE(SUM(si.quantity*si.cost_price),0) FROM sale_items si JOIN sales s ON s.sale_id=si.sale_id WHERE s.sale_status='COMPLETED' AND s.sale_date>=? AND s.sale_date<? AND si.cost_price IS NOT NULL", range);
            ProfitCalculator.ProfitResult values = ProfitCalculator.calculate(netSales, cogs, expenses);
            return report("Profit Summary", columnsFor(ReportType.PROFIT_SUMMARY), List.of(row("Net Sales", values.netSales()), row("Cost of Goods Sold", values.costOfGoodsSold()), row("Gross Profit", values.grossProfit()), row("Expenses", values.expenses()), row("Net Profit", values.netProfit())),
                    List.of(metric("Net Sales", values.netSales()), metric("Gross Profit", values.grossProfit()), metric("Expenses", values.expenses()), metric("Net Profit", values.netProfit())), profitSeries(connection, range, missing == 0), false, summary("Net Profit", values.netProfit()), missing == 0 ? "" : historicalNotice);
        }
    }

    private ReportResult unavailableProfit(BigDecimal netSales, BigDecimal expenses, String notice) {
        return report("Profit Summary", columnsFor(ReportType.PROFIT_SUMMARY), List.of(row("Net Sales", netSales), row("Cost of Goods Sold", "Unavailable"), row("Gross Profit", "Unavailable"), row("Expenses", expenses), row("Net Profit", "Unavailable")),
                List.of(metric("Net Sales", netSales), metric("Cost of Goods Sold", "Unavailable"), metric("Expenses", expenses), metric("Net Profit", "Unavailable")), List.of(), false, summary("Net Sales", netSales), notice);
    }

    private List<ReportChartSeries> profitSeries(Connection connection, DateRange range, boolean includeProfit) throws SQLException {
        Map<LocalDate, BigDecimal> sales = dailyTotals(connection, "SELECT DATE(sale_date), COALESCE(SUM(total_amount),0) FROM sales WHERE sale_status='COMPLETED' AND sale_date>=? AND sale_date<? GROUP BY DATE(sale_date)", range);
        List<ReportChartSeries> series = new ArrayList<>(); series.add(new ReportChartSeries("Net Sales", points(sales)));
        if (!includeProfit) return series;
        Map<LocalDate, BigDecimal> costs = dailyTotals(connection, "SELECT DATE(s.sale_date), COALESCE(SUM(si.quantity*si.cost_price),0) FROM sale_items si JOIN sales s ON s.sale_id=si.sale_id WHERE s.sale_status='COMPLETED' AND s.sale_date>=? AND s.sale_date<? GROUP BY DATE(s.sale_date)", range);
        Map<LocalDate, BigDecimal> grossProfit = new TreeMap<>();
        for (Map.Entry<LocalDate, BigDecimal> entry : sales.entrySet()) grossProfit.put(entry.getKey(), money(entry.getValue().subtract(costs.getOrDefault(entry.getKey(), Money.zero()))));
        series.add(new ReportChartSeries("Gross Profit", points(grossProfit))); return series;
    }

    private Map<LocalDate, BigDecimal> dailyTotals(Connection connection, String sql, DateRange range) throws SQLException {
        Map<LocalDate, BigDecimal> totals = new TreeMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindRange(statement, range);
            try (ResultSet result = statement.executeQuery()) { while (result.next()) totals.put(result.getDate(1).toLocalDate(), money(result.getBigDecimal(2))); }
        }
        return totals;
    }

    private List<ReportMetric> inventoryMetrics(Connection connection, String title, int count, BigDecimal shortage) throws SQLException {
        long products = scalarLong(connection, "SELECT COUNT(*) FROM products WHERE active=TRUE");
        long expired = scalarLong(connection, "SELECT COUNT(*) FROM product_batches WHERE active=TRUE AND quantity_available>0 AND expiry_date<CURDATE()");
        long nearExpiry = scalarLong(connection, "SELECT COUNT(*) FROM product_batches WHERE active=TRUE AND quantity_available>0 AND expiry_date BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL " + NEAR_EXPIRY_DAYS + " DAY)");
        return List.of(metric("Total Products", products), metric(title, count), metric("Expired Items", expired), metric("Near Expiry Items", nearExpiry + (shortage.signum() > 0 ? " • shortage " + number(shortage) : "")));
    }

    /** One-row-per-product batch facts; reporting never reads products.stock_quantity for inventory values. */
    private static String batchInventoryAggregate() {
        return "(SELECT pb.product_id, COALESCE(SUM(pb.quantity_available),0) total_physical_stock, "
                + "COALESCE(SUM(CASE WHEN pb.active=TRUE AND pb.quantity_available>0 "
                + "AND (pb.expiry_date IS NULL OR pb.expiry_date>=CURDATE()) THEN pb.quantity_available ELSE 0 END),0) sellable_stock, "
                + "MIN(CASE WHEN pb.active=TRUE AND pb.quantity_available>0 AND pb.expiry_date>=CURDATE() THEN pb.expiry_date ELSE NULL END) nearest_expiry, "
                + "COALESCE(SUM(pb.quantity_available*pb.cost_price),0) stock_cost_value, "
                + "COALESCE(SUM(pb.quantity_available*pb.selling_price),0) potential_sales_value, "
                + "COALESCE(SUM(pb.quantity_available*(pb.selling_price-pb.cost_price)),0) potential_gross_profit, "
                + "COALESCE(SUM(CASE WHEN pb.active=TRUE AND pb.quantity_available>0 AND pb.expiry_date BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL " + NEAR_EXPIRY_DAYS + " DAY) THEN 1 ELSE 0 END),0) near_expiry_batch_count, "
                + "COALESCE(SUM(CASE WHEN pb.active=TRUE AND pb.quantity_available>0 AND pb.expiry_date<CURDATE() THEN 1 ELSE 0 END),0) expired_batch_count "
                + "FROM product_batches pb GROUP BY pb.product_id) b";
    }

    private List<List<Object>> query(String sql, DateRange range, int columns) throws SQLException { try (Connection connection = DBConnection.getConnection()) { return query(connection, sql, range, columns); } }
    private List<List<Object>> query(Connection connection, String sql, DateRange range, int columns) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (range != null) bindRange(statement, range);
            try (ResultSet result = statement.executeQuery()) { List<List<Object>> rows = new ArrayList<>(); while (result.next()) { List<Object> line = new ArrayList<>(); for (int index = 1; index <= columns; index++) line.add(result.getObject(index)); rows.add(line); } return rows; }
        }
    }
    private List<List<Object>> resultRows(PreparedStatement statement, int columns) throws SQLException {
        try (ResultSet result = statement.executeQuery()) { List<List<Object>> rows = new ArrayList<>(); while (result.next()) { List<Object> line = new ArrayList<>(); for (int index = 1; index <= columns; index++) line.add(result.getObject(index)); rows.add(line); } return rows; }
    }

    private ReportResult report(String title, List<String> columns, List<List<Object>> rows, List<ReportMetric> metrics, List<ReportChartSeries> series, boolean barChart, List<String> summary, String notice) { return new ReportResult(title, columns, rows, summary, metrics, series, barChart, notice); }
    private static List<Object> row(Object... values) { return new ArrayList<>(Arrays.asList(values)); }
    private static BigDecimal sum(List<List<Object>> rows, int column) { BigDecimal total = Money.zero(); for (List<Object> row : rows) total = total.add(decimal(row.get(column))); return money(total); }
    private static long sumLong(List<List<Object>> rows, int column) { long total = 0; for (List<Object> row : rows) total += decimal(row.get(column)).longValue(); return total; }
    private static int distinctCount(List<List<Object>> rows, int column) { return (int) rows.stream().map(row -> String.valueOf(row.get(column))).distinct().count(); }
    private static BigDecimal totalFor(List<List<Object>> rows, int labelColumn, String label, int valueColumn) { BigDecimal total = Money.zero(); for (List<Object> row : rows) if (label.equalsIgnoreCase(String.valueOf(row.get(labelColumn)))) total = total.add(decimal(row.get(valueColumn))); return money(total); }
    private static List<ReportChartPoint> dateSeries(List<List<Object>> rows, int dateColumn, int valueColumn) { Map<LocalDate, BigDecimal> values = new TreeMap<>(); for (List<Object> row : rows) { LocalDate date = localDate(row.get(dateColumn)); if (date != null) values.merge(date, decimal(row.get(valueColumn)), BigDecimal::add); } return points(values); }
    private static List<ReportChartPoint> categorySeries(List<List<Object>> rows, int labelColumn, int valueColumn) { List<ReportChartPoint> values = new ArrayList<>(); for (List<Object> row : rows) values.add(new ReportChartPoint(String.valueOf(row.get(labelColumn)), money(decimal(row.get(valueColumn))))); return values; }
    private static List<ReportChartPoint> points(Map<LocalDate, BigDecimal> values) { List<ReportChartPoint> points = new ArrayList<>(); for (Map.Entry<LocalDate, BigDecimal> entry : values.entrySet()) points.add(new ReportChartPoint(entry.getKey().toString(), money(entry.getValue()))); return points; }
    private BigDecimal scalar(Connection connection, String sql, DateRange range) throws SQLException { try (PreparedStatement statement = connection.prepareStatement(sql)) { bindRange(statement, range); try (ResultSet result = statement.executeQuery()) { result.next(); return money(result.getBigDecimal(1)); } } }
    private long count(Connection connection, String sql, DateRange range) throws SQLException { try (PreparedStatement statement = connection.prepareStatement(sql)) { bindRange(statement, range); try (ResultSet result = statement.executeQuery()) { result.next(); return result.getLong(1); } } }
    private long scalarLong(Connection connection, String sql) throws SQLException { try (PreparedStatement statement = connection.prepareStatement(sql); ResultSet result = statement.executeQuery()) { result.next(); return result.getLong(1); } }
    private static ReportMetric metric(String label, BigDecimal value) { return new ReportMetric(label, currency(value)); }
    private static ReportMetric metric(String label, long value) { return new ReportMetric(label, Long.toString(value)); }
    private static ReportMetric metric(String label, String value) { return new ReportMetric(label, value); }
    private static ReportMetric quantityMetric(String label, BigDecimal value) { return new ReportMetric(label, number(value)); }
    private static String number(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }
    private static List<String> summary(String label, BigDecimal amount) { return List.of(label + ": " + currency(amount)); }
    private static BigDecimal decimal(Object value) { if (value instanceof BigDecimal decimal) return decimal; if (value instanceof Number number) return new BigDecimal(number.toString()); if (value == null) return Money.zero(); try { return new BigDecimal(value.toString()); } catch (NumberFormatException ignored) { return Money.zero(); } }
    private static LocalDate localDate(Object value) { if (value instanceof Date date) return date.toLocalDate(); if (value instanceof Timestamp timestamp) return timestamp.toLocalDateTime().toLocalDate(); if (value instanceof java.util.Date date) return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate(); if (value instanceof LocalDate date) return date; try { return value == null ? null : LocalDate.parse(value.toString()); } catch (Exception ignored) { return null; } }
    private static BigDecimal money(BigDecimal value) { return value == null ? Money.zero() : value.setScale(2, RoundingMode.HALF_UP); }
    private static String currency(BigDecimal value) { return "LKR " + money(value).toPlainString(); }
    private static void bindRange(PreparedStatement statement, DateRange range) throws SQLException { bindRangeAt(statement, range, 1); }
    private static void bindRangeAt(PreparedStatement statement, DateRange range, int index) throws SQLException { statement.setTimestamp(index, Timestamp.valueOf(range.from().atStartOfDay())); statement.setTimestamp(index + 1, Timestamp.valueOf(range.to().plusDays(1).atStartOfDay())); }
    private record DateRange(LocalDate from, LocalDate to) { static DateRange of(LocalDate from, LocalDate to) { if (from == null || to == null) throw new IllegalArgumentException("Enter both dates in yyyy-MM-dd format."); if (to.isBefore(from)) throw new IllegalArgumentException("To date cannot be before From date."); return new DateRange(from, to); } }
}
