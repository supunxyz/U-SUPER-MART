package com.mycompany.easymartpos.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.mycompany.easymartpos.model.PaymentPart;
import com.mycompany.easymartpos.model.PaymentRequest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PaymentPartValidationTest {
    private static final BigDecimal TOTAL = new BigDecimal("3000.00");
    private static PaymentPart cash(String value) { return PaymentPart.cash(new BigDecimal(value), null, BigDecimal.ZERO); }
    private static PaymentPart card(String value) { return PaymentPart.card(new BigDecimal(value), "CARD4582"); }
    private static PaymentPart transfer(String value) { return PaymentPart.transfer(new BigDecimal(value), "TXN98341", "Bank"); }

    @Test void acceptsEverySupportedTenderCombination() {
        assertDoesNotThrow(() -> CheckoutCalculator.validatePaymentParts(List.of(cash("3000.00")), TOTAL, false));
        assertDoesNotThrow(() -> CheckoutCalculator.validatePaymentParts(List.of(card("3000.00")), TOTAL, false));
        assertDoesNotThrow(() -> CheckoutCalculator.validatePaymentParts(List.of(transfer("3000.00")), TOTAL, false));
        assertDoesNotThrow(() -> CheckoutCalculator.validatePaymentParts(List.of(cash("1000.00"), card("2000.00")), TOTAL, true));
        assertDoesNotThrow(() -> CheckoutCalculator.validatePaymentParts(List.of(cash("1000.00"), transfer("2000.00")), TOTAL, true));
        assertDoesNotThrow(() -> CheckoutCalculator.validatePaymentParts(List.of(card("1200.00"), transfer("1800.00")), TOTAL, true));
        assertDoesNotThrow(() -> CheckoutCalculator.validatePaymentParts(List.of(cash("1000.00"), card("1200.00"), transfer("800.00")), TOTAL, true));
    }

    @Test void rejectsUnsafeSplitValuesAndMissingReferences() {
        assertThrows(IllegalArgumentException.class, () -> CheckoutCalculator.validatePaymentParts(List.of(cash("1000.00"), card("1000.00")), TOTAL, true));
        assertThrows(IllegalArgumentException.class, () -> CheckoutCalculator.validatePaymentParts(List.of(cash("2000.00"), card("2000.00")), TOTAL, true));
        assertThrows(IllegalArgumentException.class, () -> CheckoutCalculator.validatePaymentParts(List.of(cash("-1.00"), card("3001.00")), TOTAL, true));
        assertThrows(IllegalArgumentException.class, () -> CheckoutCalculator.validatePaymentParts(List.of(cash("1000.00"), PaymentPart.card(new BigDecimal("2000.00"), "")), TOTAL, true));
        assertThrows(IllegalArgumentException.class, () -> CheckoutCalculator.validatePaymentParts(List.of(cash("1000.00"), PaymentPart.transfer(new BigDecimal("2000.00"), "", null)), TOTAL, true));
    }

    @Test void keepsAnOptionalBankNameWithCardPayments() {
        PaymentPart card = PaymentPart.card(TOTAL, "CARD4582", "Commercial Bank");
        PaymentRequest request = PaymentRequest.card("CARD4582", "Commercial Bank");

        assertEquals("Commercial Bank", card.bankName());
        assertEquals("Commercial Bank", request.bankName());
        assertDoesNotThrow(() -> CheckoutCalculator.validatePaymentParts(List.of(card), TOTAL, false));
    }
}
