package com.mycompany.easymartpos.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Read-only batch data shown from Product Management. */
public record ProductBatchInventory(int batchId, String sourceType, Integer purchaseId,
        BigDecimal costPrice, BigDecimal sellingPrice, BigDecimal retailPrice, LocalDate expiryDate,
        BigDecimal quantityReceived, BigDecimal quantityAvailable, boolean active, LocalDateTime createdAt) { }
