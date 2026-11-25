package com.mycompany.easymartpos.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Immutable sellable-stock snapshot for one inventory batch. */
public record BatchStock(int batchId, int productId, BigDecimal costPrice, BigDecimal sellingPrice,
        BigDecimal retailPrice, LocalDate expiryDate, BigDecimal quantityAvailable) {

    public BatchStock {
        if (batchId <= 0 || productId <= 0) throw new IllegalArgumentException("Batch and product IDs must be positive.");
        if (costPrice == null || sellingPrice == null || quantityAvailable == null)
            throw new IllegalArgumentException("Batch cost, selling price and available quantity are required.");
        if (quantityAvailable.signum() < 0) throw new IllegalArgumentException("Available quantity cannot be negative.");
    }
}
