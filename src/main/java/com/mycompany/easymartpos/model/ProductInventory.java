package com.mycompany.easymartpos.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Product-master data paired with read-only stock facts calculated from its batches.
 * expiredStock is physical stock whose batch expiry has passed; it is never sellable.
 */
public record ProductInventory(Product product, BigDecimal totalBatchStock, LocalDate nearestExpiry, BigDecimal expiredStock) {

    public ProductInventory {
        expiredStock = expiredStock == null ? BigDecimal.ZERO : expiredStock;
    }

    public ProductInventory(Product product, BigDecimal totalBatchStock, LocalDate nearestExpiry) {
        this(product, totalBatchStock, nearestExpiry, BigDecimal.ZERO);
    }
}
