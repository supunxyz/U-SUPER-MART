package com.mycompany.easymartpos.service;

import com.mycompany.easymartpos.model.ReceiptData.ReceiptItem;
import com.mycompany.easymartpos.util.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Informational customer benefit only; it never changes a sale total. */
final class ReceiptBenefitCalculator {
    private ReceiptBenefitCalculator() { }

    static BigDecimal calculate(List<ReceiptItem> items, BigDecimal manualDiscount) {
        BigDecimal benefit = Money.zero();
        for (ReceiptItem item : items) {
            BigDecimal quantity = value(item.quantity());
            BigDecimal finalPrice = value(item.finalUnitPrice());
            BigDecimal regular = item.regularUnitPrice();
            if (regular == null) {
                // Legacy rows lack a normal-price snapshot. Their saved retail-to-final difference is
                // still the exact total item benefit, but cannot safely be split using today's prices.
                if (item.retailPrice() != null) benefit = benefit.add(item.retailPrice().subtract(finalPrice).max(Money.zero()).multiply(quantity));
                continue;
            }
            // Retail benefit is against the saved regular price, so a promotion is never counted twice.
            if (item.retailPrice() != null) benefit = benefit.add(item.retailPrice().subtract(regular).max(Money.zero()).multiply(quantity));
            // Compare the two saved sale prices; never re-evaluate a promotion.
            benefit = benefit.add(regular.subtract(finalPrice).max(Money.zero()).multiply(quantity));
        }
        benefit = benefit.add(value(manualDiscount).max(Money.zero()));
        return benefit.max(Money.zero()).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal value(BigDecimal value) { return value == null ? Money.zero() : value; }
}
