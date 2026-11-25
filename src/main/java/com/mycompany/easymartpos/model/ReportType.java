package com.mycompany.easymartpos.model;

public enum ReportType {
    DAILY_SALES("Daily Sales"),
    DAILY_PROFIT("Daily Profit"),
    DATE_RANGE_SALES("Date Range Sales"),
    PURCHASES("Purchases"),
    EXPENSES("Expenses"),
    PROFIT_SUMMARY("Profit Summary"),
    LOW_STOCK("Low Stock"),
    PRODUCT_STOCK("Product Stock"),
    STOCK_MOVEMENTS("Stock Movements"),
    PAYMENTS("Payments"),
    EXPIRED_ITEMS("Expired Items"),
    NEAR_EXPIRY_ITEMS("Near Expiry Items"),
    SALES_TREND("Sales Trend"),
    TOP_SELLING_PRODUCTS("Top Selling Products"),
    PRODUCT_WISE_SALES("Product-wise Sales"),
    CATEGORY_WISE_SALES("Category-wise Sales"),
    SLOW_MOVING_PRODUCTS("Slow Moving Products"),
    OUT_OF_STOCK("Out of Stock"),
    STOCK_VALUATION("Stock Valuation"),
    PRODUCT_PROFITABILITY("Product Profitability"),
    CASHIER_WISE_SALES("Cashier-wise Sales"),
    VOID_SALES("Void Sales"),
    DISCOUNT_REPORT("Discount Report"),
    CUSTOMER_PURCHASES("Customer Purchase Report"),
    LOYALTY_POINTS("Loyalty Points Report"),
    CUSTOMER_SAVINGS("Customer Saving Report"),
    SUPPLIER_PURCHASES("Supplier-wise Purchases"),
    INVENTORY_OVERVIEW("Inventory Overview"),
    BUSINESS_OVERVIEW("Business Overview");

    private final String label;
    ReportType(String label) { this.label = label; }
    @Override public String toString() { return label; }
}
