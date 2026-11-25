package com.mycompany.easymartpos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ShiftCalculatorTest {
    @Test void calculatesExpectedCashAndDifferenceWithBigDecimal() {
        BigDecimal expected = ShiftCalculator.expectedCash(new BigDecimal("5000.00"), new BigDecimal("1250.75"));
        assertEquals(new BigDecimal("6250.75"), expected);
        assertEquals(new BigDecimal("-50.75"), ShiftCalculator.cashDifference(new BigDecimal("6200.00"), expected));
    }
}
