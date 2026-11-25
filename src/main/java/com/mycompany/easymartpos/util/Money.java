package com.mycompany.easymartpos.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {
    private Money() { }
    public static BigDecimal parse(String value, String fieldName) {
        try {
            BigDecimal amount = new BigDecimal(value.trim()).setScale(2, RoundingMode.HALF_UP);
            if (amount.signum() < 0) throw new IllegalArgumentException(fieldName + " cannot be negative.");
            return amount;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Enter a valid " + fieldName + ".");
        }
    }
    public static BigDecimal zero() { return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP); }
}
