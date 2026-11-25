package com.mycompany.easymartpos.service;

import com.mycompany.easymartpos.model.CartItem;
import com.mycompany.easymartpos.model.PaymentMethod;
import com.mycompany.easymartpos.model.PaymentPart;
import com.mycompany.easymartpos.util.Money;
import java.math.BigDecimal;
import java.util.List;

public final class CheckoutCalculator {
    private CheckoutCalculator() { }
    public static BigDecimal subtotal(List<CartItem> cart) {
        return cart.stream().map(CartItem::subtotal).reduce(Money.zero(), BigDecimal::add);
    }
    /**
     * Converts the cashier's discount entry into an LKR amount. "250" is a fixed LKR discount;
     * "10%" is 10 percent of the subtotal, rounded to cents. Blank means no discount.
     */
    public static BigDecimal discountAmount(String entry, BigDecimal subtotal) {
        String text = entry == null ? "" : entry.trim();
        if (text.isEmpty()) return Money.zero();
        if (!text.endsWith("%")) return Money.parse(text, "discount");
        BigDecimal percent = Money.parse(text.substring(0, text.length() - 1).trim(), "discount percentage");
        if (percent.compareTo(BigDecimal.valueOf(100)) > 0) throw new IllegalArgumentException("Discount percentage cannot exceed 100%.");
        return subtotal.multiply(percent).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
    }
    public static BigDecimal total(BigDecimal subtotal, BigDecimal discount) {
        BigDecimal safeDiscount = discount == null ? Money.zero() : discount;
        if (safeDiscount.signum() < 0 || safeDiscount.compareTo(subtotal) > 0)
            throw new IllegalArgumentException("Discount must be between zero and the subtotal.");
        return subtotal.subtract(safeDiscount);
    }
    public static BigDecimal cashChange(BigDecimal tendered, BigDecimal total) {
        if (tendered == null || tendered.compareTo(total) < 0)
            throw new IllegalArgumentException("Tendered cash must cover the total.");
        return tendered.subtract(total);
    }
    public static void validateCardReference(String reference) {
        if (reference == null || reference.trim().length() < 4)
            throw new IllegalArgumentException("Enter the terminal reference or final four card digits.");
    }
    public static void validateTransferReference(String reference) {
        if (reference == null || reference.trim().length() < 4)
            throw new IllegalArgumentException("Transfer reference is required.");
    }
    /** Validates bill portions, deliberately distinct from cash tender/change. */
    public static void validatePaymentParts(List<PaymentPart> parts, BigDecimal total, boolean split) {
        if (parts == null || parts.isEmpty()) throw new IllegalArgumentException("Enter a payment amount.");
        int active = 0; BigDecimal paid = Money.zero();
        for (PaymentPart part : parts) {
            if (part == null || part.method() == null || part.method() == PaymentMethod.SPLIT)
                throw new IllegalArgumentException("Select a valid payment method.");
            BigDecimal amount = part.amount();
            if (amount == null || amount.signum() < 0) throw new IllegalArgumentException("Payment amount cannot be negative.");
            if (amount.signum() > 0) {
                active++; paid = paid.add(amount);
                if (part.method() == PaymentMethod.CARD) validateCardReference(part.reference());
                if (part.method() == PaymentMethod.TRANSFER) validateTransferReference(part.reference());
            }
        }
        if (split && active < 2) throw new IllegalArgumentException("Split payment requires at least two payment methods.");
        if (paid.compareTo(total) != 0) throw new IllegalArgumentException("Cash, card and transfer payments must equal the grand total.");
    }
    public static BigDecimal customerSaving(List<CartItem> cart) {
        return cart.stream().map(item -> {
            if (item.retailPrice() == null || item.retailPrice().compareTo(item.ourPrice()) <= 0) return Money.zero();
            return item.retailPrice().subtract(item.ourPrice()).multiply(item.quantity()).setScale(2, java.math.RoundingMode.HALF_UP);
        }).reduce(Money.zero(), BigDecimal::add);
    }
}
