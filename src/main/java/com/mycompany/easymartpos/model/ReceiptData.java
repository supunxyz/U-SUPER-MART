package com.mycompany.easymartpos.model;

import com.mycompany.easymartpos.util.Money;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Immutable receipt snapshot loaded from one committed sale. */
public record ReceiptData(
        int saleId,
        String invoiceNumber,
        String cashier,
        LocalDateTime saleDateTime,
        String customerName,
        Integer loyaltyPoints,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal grandTotal,
        List<ReceiptItem> items,
        List<ReceiptPayment> payments,
        BigDecimal saving,
        BigDecimal totalQuantity) {

    public ReceiptData {
        items = List.copyOf(items == null ? List.of() : items);
        payments = List.copyOf(payments == null ? List.of() : payments);
        saving = saving == null ? Money.zero() : saving;
        totalQuantity = totalQuantity == null ? Money.zero() : totalQuantity;
    }

    /** Refuse an incomplete snapshot before a print job is submitted. */
    public void validateForPrint() {
        if (saleId <= 0 || blank(invoiceNumber) || blank(cashier) || saleDateTime == null || blank(customerName))
            throw new IllegalStateException("The saved sale receipt is missing sale information.");
        if (subtotal == null || discount == null || grandTotal == null)
            throw new IllegalStateException("The saved sale receipt is missing totals.");
        if (items.isEmpty()) throw new IllegalStateException("The saved sale receipt has no sale items.");
        if (payments.isEmpty()) throw new IllegalStateException("The saved sale receipt has no payment rows.");
        for (ReceiptItem item : items) {
            if (blank(item.productName()) || item.quantity() == null || item.finalUnitPrice() == null || item.itemTotal() == null)
                throw new IllegalStateException("The saved sale receipt contains an incomplete item.");
        }
        for (ReceiptPayment payment : payments) {
            if (blank(payment.method()) || payment.amount() == null)
                throw new IllegalStateException("The saved sale receipt contains an incomplete payment.");
        }
    }

    public boolean hasLoyaltyDetails() {
        return loyaltyPoints != null && !"walk-in customer".equalsIgnoreCase(customerName == null ? "" : customerName.trim());
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }

    public record ReceiptItem(String productName, BigDecimal quantity, BigDecimal retailPrice,
            BigDecimal regularUnitPrice, BigDecimal finalUnitPrice, BigDecimal itemTotal) { }

    public record ReceiptPayment(String method, BigDecimal amount, BigDecimal tenderedAmount,
            BigDecimal changeAmount, String cardReference, String transferReference, String bankName) { }
}
