package com.mycompany.easymartpos.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Read-only, reconciled values for one cashier shift. */
public record ShiftHistoryRow(int shiftId, String cashier, LocalDateTime openedAt, LocalDateTime closedAt,
        BigDecimal openingCash, long transactions, BigDecimal cashSales, BigDecimal cardSales, BigDecimal transferSales,
        BigDecimal totalSales, BigDecimal expectedCash, BigDecimal closingCash,
        BigDecimal difference, String status) { }
