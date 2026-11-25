package com.mycompany.easymartpos.model;

import java.math.BigDecimal;

/** Totals across the currently visible admin shift-history rows. */
public record ShiftHistorySummary(long totalShifts, long totalTransactions, BigDecimal totalSales,
        BigDecimal cashSales, BigDecimal cardSales, BigDecimal transferSales, BigDecimal totalCashDifference) { }
