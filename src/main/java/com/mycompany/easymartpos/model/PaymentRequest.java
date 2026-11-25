package com.mycompany.easymartpos.model;

import java.math.BigDecimal;
import java.util.List;

/** Checkout instruction. The persisted truth is its list of payment parts. */
public record PaymentRequest(PaymentMethod method, BigDecimal tenderedAmount, String cardReference,
        String transferReference, String bankName, List<PaymentPart> parts) {
    public PaymentRequest {
        parts = parts == null ? List.of() : List.copyOf(parts);
    }
    /** Compatibility constructor retained for existing cash/card callers. */
    public PaymentRequest(PaymentMethod method, BigDecimal tenderedAmount, String cardReference) {
        this(method, tenderedAmount, cardReference, null, null, List.of());
    }
    public static PaymentRequest transfer(String reference, String bankName) {
        return new PaymentRequest(PaymentMethod.TRANSFER, null, null, reference, bankName, List.of());
    }
    public static PaymentRequest card(String reference, String bankName) {
        return new PaymentRequest(PaymentMethod.CARD, null, reference, null, bankName, List.of());
    }
    public static PaymentRequest split(List<PaymentPart> parts) {
        return new PaymentRequest(PaymentMethod.SPLIT, null, null, null, null, parts);
    }
}
