package com.mycompany.easymartpos.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A proposed read-only allocation; it is not persisted by batch resolution. */
public record BatchAllocation(int batchId, BigDecimal quantity, BigDecimal costPrice,
        BigDecimal sellingPrice, BigDecimal retailPrice, LocalDate expiryDate) {

    public BatchAllocation {
        if (batchId <= 0) throw new IllegalArgumentException("Batch ID must be positive.");
        if (quantity == null || quantity.signum() <= 0) throw new IllegalArgumentException("Allocation quantity must be greater than zero.");
        if (costPrice == null || sellingPrice == null) throw new IllegalArgumentException("Allocation prices are required.");
    }
}
