package com.mycompany.easymartpos.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Positive-stock batch offered for an exact manual stock removal. */
public record StockAdjustmentBatch(int batchId, int productId, String sourceType, BigDecimal costPrice,
        BigDecimal sellingPrice, BigDecimal retailPrice, LocalDate expiryDate, BigDecimal quantityAvailable,
        boolean active) { }
