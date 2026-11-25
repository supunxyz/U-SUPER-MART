package com.mycompany.easymartpos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class LoyaltyPointCalculatorTest {
    @Test void earnsOnlyWholePointsFromEligibleFinalAmount() {
        assertEquals(24, LoyaltyPointCalculator.earnedPoints(new BigDecimal("2450.00")));
    }

    @Test void redemptionLeavesOnlyPostRedemptionSpendEligibleForPoints() {
        int redeemed = LoyaltyPointCalculator.redeemedPoints(150, new BigDecimal("1000.00"), 100);
        int earned = LoyaltyPointCalculator.earnedPoints(new BigDecimal("1000.00").subtract(BigDecimal.valueOf(redeemed)));
        assertEquals(100, redeemed); assertEquals(9, earned); assertEquals(59, 150 - redeemed + earned);
    }

    @Test void rejectsRedemptionBelowMinimumBalance() {
        assertThrows(IllegalArgumentException.class, () -> LoyaltyPointCalculator.redeemedPoints(80, new BigDecimal("1000.00"), 1));
    }

    @Test void capsRedemptionAtHalfOfGrandTotal() {
        assertEquals(250, LoyaltyPointCalculator.redeemedPoints(1000, new BigDecimal("500.00"), 400));
    }
}
