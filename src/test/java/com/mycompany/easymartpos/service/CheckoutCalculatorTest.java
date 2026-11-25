package com.mycompany.easymartpos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.math.BigDecimal;
import java.util.List;
import com.mycompany.easymartpos.model.CartItem;
import com.mycompany.easymartpos.model.Product;
import org.junit.jupiter.api.Test;

class CheckoutCalculatorTest {
    @Test void calculatesCashChange() {
        assertEquals(new BigDecimal("150.00"), CheckoutCalculator.cashChange(new BigDecimal("1000.00"), new BigDecimal("850.00")));
    }
    @Test void rejectsInsufficientCash() {
        assertThrows(IllegalArgumentException.class, () -> CheckoutCalculator.cashChange(new BigDecimal("849.99"), new BigDecimal("850.00")));
    }
    @Test void rejectsDiscountAboveSubtotal() {
        assertThrows(IllegalArgumentException.class, () -> CheckoutCalculator.total(new BigDecimal("100.00"), new BigDecimal("100.01")));
    }
    @Test void calculatesTotalAfterDiscount() {
        assertEquals(new BigDecimal("85.00"), CheckoutCalculator.total(new BigDecimal("100.00"), new BigDecimal("15.00")));
    }
    @Test void calculatesOnlyPositiveCustomerSaving() {
        Product reduced = new Product(1, "A", "Reduced", 1, "", BigDecimal.ZERO, new BigDecimal("80.00"), new BigDecimal("100.00"), BigDecimal.ONE, BigDecimal.ZERO, null, true);
        Product noSaving = new Product(2, "B", "Normal", 1, "", BigDecimal.ZERO, new BigDecimal("100.00"), new BigDecimal("90.00"), BigDecimal.ONE, BigDecimal.ZERO, null, true);
        assertEquals(new BigDecimal("40.00"), CheckoutCalculator.customerSaving(List.of(new CartItem(reduced, new BigDecimal("2")), new CartItem(noSaving, BigDecimal.ONE))));
    }
    @Test void readsFixedAmountDiscountEntry() {
        assertEquals(new BigDecimal("250.00"), CheckoutCalculator.discountAmount("250", new BigDecimal("1000.00")));
        assertEquals(new BigDecimal("0.00"), CheckoutCalculator.discountAmount("  ", new BigDecimal("1000.00")));
    }
    @Test void readsPercentageDiscountFromTheBill() {
        assertEquals(new BigDecimal("100.00"), CheckoutCalculator.discountAmount("10%", new BigDecimal("1000.00")));
        assertEquals(new BigDecimal("41.67"), CheckoutCalculator.discountAmount("12.5 %", new BigDecimal("333.33")));
    }
    @Test void rejectsInvalidDiscountEntries() {
        assertThrows(IllegalArgumentException.class, () -> CheckoutCalculator.discountAmount("101%", new BigDecimal("1000.00")));
        assertThrows(IllegalArgumentException.class, () -> CheckoutCalculator.discountAmount("-5", new BigDecimal("1000.00")));
        assertThrows(IllegalArgumentException.class, () -> CheckoutCalculator.discountAmount("ten%", new BigDecimal("1000.00")));
    }
    @Test void requiresUsefulCardReference() {
        assertThrows(IllegalArgumentException.class, () -> CheckoutCalculator.validateCardReference("123"));
    }
}
