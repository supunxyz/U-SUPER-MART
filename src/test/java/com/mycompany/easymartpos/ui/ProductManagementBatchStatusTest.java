package com.mycompany.easymartpos.ui;

import com.mycompany.easymartpos.model.Product;
import com.mycompany.easymartpos.model.ProductBatchInventory;
import com.mycompany.easymartpos.model.ProductInventory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductManagementBatchStatusTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 23);

    @Test
    void classifiesAvailableExpiredNearExpiryOutOfStockAndInactiveBatchesInPriorityOrder() {
        assertEquals("AVAILABLE", ProductManagementFrame.batchStatus(batch(true, "2", null), TODAY));
        assertEquals("EXPIRED", ProductManagementFrame.batchStatus(batch(true, "2", TODAY.minusDays(1)), TODAY));
        assertEquals("NEAR EXPIRY", ProductManagementFrame.batchStatus(batch(true, "2", TODAY.plusDays(30)), TODAY));
        assertEquals("OUT OF STOCK", ProductManagementFrame.batchStatus(batch(true, "0", TODAY.minusDays(1)), TODAY));
        assertEquals("INACTIVE", ProductManagementFrame.batchStatus(batch(false, "2", TODAY.minusDays(1)), TODAY));
    }

    @Test
    void usesBatchDerivedTotalForTheProductLowStockStatus() {
        assertEquals("LOW STOCK", ProductManagementFrame.productStatus(inventory(true, "3", "3")));
        assertEquals("OUT OF STOCK", ProductManagementFrame.productStatus(inventory(true, "0", "3")));
        assertEquals("ARCHIVED", ProductManagementFrame.productStatus(inventory(false, "9", "3")));
    }

    @Test
    void expiredStockIsFlaggedEvenWhenTheProductStillHasPhysicalStock() {
        Product product = inventory(true, "9", "3").product();
        assertEquals("EXPIRED", ProductManagementFrame.productStatus(new ProductInventory(product, amount("9"), null, amount("4"))));
        assertEquals("ACTIVE", ProductManagementFrame.productStatus(new ProductInventory(product, amount("9"), null, BigDecimal.ZERO)));
    }

    @Test
    void givesStatusAndCreatedDateColumnsEnoughSpaceAndKeepsHorizontalScrollingPossible() {
        JTable table = new JTable(new DefaultTableModel(0, 11));
        ProductManagementFrame.configureBatchTableColumns(table);

        assertEquals(JTable.AUTO_RESIZE_OFF, table.getAutoResizeMode());
        assertTrue(table.getColumnModel().getColumn(9).getPreferredWidth() >= 130);
        assertTrue(table.getColumnModel().getColumn(10).getPreferredWidth() >= 155);
    }

    private ProductBatchInventory batch(boolean active, String available, LocalDate expiry) {
        return new ProductBatchInventory(46, "PURCHASE", 11, amount("100"), amount("200"), amount("250"), expiry,
                amount("5"), amount(available), active, null);
    }

    private ProductInventory inventory(boolean active, String totalStock, String minimumStock) {
        Product product = new Product(10, "P-10", "Pepsi", 1, "Drinks", amount("100"), amount("200"), amount("250"),
                amount("999"), amount(minimumStock), null, active);
        return new ProductInventory(product, amount(totalStock), null);
    }

    private BigDecimal amount(String value) {
        return new BigDecimal(value);
    }
}
