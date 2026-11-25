package com.mycompany.easymartpos.service;

import com.mycompany.easymartpos.model.ReportType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportsServiceBatchReportingTest {
    private final ReportsService reports = new ReportsService();

    @Test
    void productStockKeepsOneProductRowShapeWithPhysicalAndSellableStock() {
        assertEquals(List.of("Product ID", "Barcode", "Product Name", "Category", "Total Physical Stock", "Sellable Stock", "Minimum Stock", "Nearest Expiry", "Stock Status"),
                reports.columnsFor(ReportType.PRODUCT_STOCK));
    }

    @Test
    void expiryReportsUseBatchSpecificColumnsRatherThanProductMasterExpiry() {
        List<String> nearExpiry = reports.columnsFor(ReportType.NEAR_EXPIRY_ITEMS);
        List<String> expired = reports.columnsFor(ReportType.EXPIRED_ITEMS);

        assertEquals("Batch ID", nearExpiry.get(0));
        assertTrue(nearExpiry.containsAll(List.of("Our Price", "Retail Price", "Expiry Date", "Days Left", "Remaining Qty", "Source", "Status")));
        assertEquals("Batch ID", expired.get(0));
        assertTrue(expired.containsAll(List.of("Expiry Date", "Days Expired", "Remaining Qty", "Cost Price", "Stock Value", "Status")));
    }

    @Test
    void lowStockReportExposesSellableAndTotalPhysicalStockSeparately() {
        assertEquals(List.of("Product", "Barcode", "Sellable Stock", "Total Physical Stock", "Minimum Stock", "Status"),
                reports.columnsFor(ReportType.LOW_STOCK));
    }
}
