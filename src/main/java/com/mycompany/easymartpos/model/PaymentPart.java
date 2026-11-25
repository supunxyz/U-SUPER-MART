package com.mycompany.easymartpos.model;

import java.math.BigDecimal;

/** One actual tender type applied to a sale. SPLIT is a checkout choice, never a payment part. */
public record PaymentPart(PaymentMethod method, BigDecimal amount, String reference,
        String bankName, BigDecimal tenderedAmount, BigDecimal changeAmount) {
    public PaymentPart {
        if (method == null || method == PaymentMethod.SPLIT) {
            throw new IllegalArgumentException("A payment part must be cash, card, or transfer.");
        }
    }
    public static PaymentPart cash(BigDecimal amount, BigDecimal tendered, BigDecimal change) {
        return new PaymentPart(PaymentMethod.CASH, amount, null, null, tendered, change);
    }
    public static PaymentPart card(BigDecimal amount, String reference) {
        return card(amount, reference, null);
    }
    public static PaymentPart card(BigDecimal amount, String reference, String bankName) {
        return new PaymentPart(PaymentMethod.CARD, amount, reference, bankName, null, null);
    }
    public static PaymentPart transfer(BigDecimal amount, String reference, String bankName) {
        return new PaymentPart(PaymentMethod.TRANSFER, amount, reference, bankName, null, null);
    }
}
