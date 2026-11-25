package com.mycompany.easymartpos.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Read-only audit entry sourced from stock_movements. */
public record StockAdjustmentHistory(LocalDateTime movedAt, String productName, String movementType,
        BigDecimal quantity, String reason, String username) { }
