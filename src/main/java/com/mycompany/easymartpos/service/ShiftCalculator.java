package com.mycompany.easymartpos.service;

import com.mycompany.easymartpos.util.Money;
import java.math.BigDecimal;

public final class ShiftCalculator {
    private ShiftCalculator() { }
    public static BigDecimal expectedCash(BigDecimal openingCash, BigDecimal cashSales) {
        return safe(openingCash).add(safe(cashSales));
    }
    public static BigDecimal cashDifference(BigDecimal closingCash, BigDecimal expectedCash) {
        if (closingCash == null) throw new IllegalArgumentException("Closing cash is required.");
        return closingCash.subtract(safe(expectedCash));
    }
    private static BigDecimal safe(BigDecimal value) { return value == null ? Money.zero() : value; }
}
