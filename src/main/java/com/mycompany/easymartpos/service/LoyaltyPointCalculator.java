package com.mycompany.easymartpos.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Pure loyalty earning and redemption rules, kept separate from checkout UI. */
public final class LoyaltyPointCalculator {
    public static final int MINIMUM_REDEMPTION_BALANCE = 100;
    private static final BigDecimal SPEND_PER_POINT = new BigDecimal("100");
    private static final BigDecimal MAX_REDEMPTION_RATE = new BigDecimal("0.50");

    private LoyaltyPointCalculator() { }

    public static int earnedPoints(BigDecimal eligibleFinalAmount) {
        if (eligibleFinalAmount == null || eligibleFinalAmount.signum() < 0) throw new IllegalArgumentException("Eligible amount cannot be negative.");
        return eligibleFinalAmount.divide(SPEND_PER_POINT, 0, RoundingMode.DOWN).intValueExact();
    }

    /** Caps an excessive request at the available balance and 50% of the pre-redemption grand total. */
    public static int redeemedPoints(int currentBalance, BigDecimal grandTotalBeforeRedemption, int requestedPoints) {
        if (currentBalance < 0) throw new IllegalArgumentException("Customer loyalty balance is invalid.");
        if (requestedPoints < 0) throw new IllegalArgumentException("Redeemed points cannot be negative.");
        if (requestedPoints == 0) return 0;
        if (currentBalance < MINIMUM_REDEMPTION_BALANCE)
            throw new IllegalArgumentException("At least 100 loyalty points are required before redemption.");
        if (grandTotalBeforeRedemption == null || grandTotalBeforeRedemption.signum() < 0)
            throw new IllegalArgumentException("Grand total cannot be negative.");
        int saleCap = grandTotalBeforeRedemption.multiply(MAX_REDEMPTION_RATE).setScale(0, RoundingMode.DOWN).intValueExact();
        return Math.min(requestedPoints, Math.min(currentBalance, saleCap));
    }
}
