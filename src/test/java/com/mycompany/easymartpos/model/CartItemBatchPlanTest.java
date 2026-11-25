package com.mycompany.easymartpos.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CartItemBatchPlanTest {

    @Test
    void replacesTheInMemoryPlanWhenTheCartQuantityIsReResolved() {
        Product product = new Product(1, "BATCH-1", "Batch Product", 1, "", BigDecimal.ONE,
                new BigDecimal("999"), new BigDecimal("250"), BigDecimal.TEN, BigDecimal.ZERO, null, true);
        BatchAllocation first = allocation(10, "2", "200");
        CartItem item = new CartItem(product, new BigDecimal("2"), new BigDecimal("200"),
                new BigDecimal("220"), List.of(first));

        BatchAllocation updated = allocation(11, "3", "200");
        item.updateQuantityAndBatchAllocations(new BigDecimal("3"), List.of(updated));

        assertEquals(new BigDecimal("200"), item.ourPrice());
        assertEquals(new BigDecimal("220"), item.retailPrice());
        assertEquals(new BigDecimal("3"), item.quantity());
        assertEquals(List.of(updated), item.batchAllocations());
    }

    private BatchAllocation allocation(int batchId, String quantity, String sellingPrice) {
        return new BatchAllocation(batchId, new BigDecimal(quantity), new BigDecimal("100"),
                new BigDecimal(sellingPrice), new BigDecimal("220"), LocalDate.now().plusDays(1));
    }
}
