package com.mycompany.easymartpos.service;

import com.mycompany.easymartpos.util.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;

/** Pure historical-profit calculations shared with report tests. */
public final class ProfitCalculator {
    private ProfitCalculator() { }

    public record SaleItemCost(String saleStatus, BigDecimal quantity, BigDecimal costPrice) { }
    public record CogsResult(BigDecimal amount, long missingSnapshotCount) { }
    public record ProfitResult(BigDecimal netSales, BigDecimal costOfGoodsSold,
            BigDecimal grossProfit, BigDecimal expenses, BigDecimal netProfit) { }

    /** Only completed sales contribute; a missing snapshot is never treated as zero. */
    public static CogsResult cogs(Collection<SaleItemCost> saleItems) {
        BigDecimal total = Money.zero();
        long missing = 0;
        for (SaleItemCost item : saleItems) {
            if (item == null || !"COMPLETED".equals(item.saleStatus())) continue;
            if (item.costPrice() == null) { missing++; continue; }
            total = total.add(item.quantity().multiply(item.costPrice()));
        }
        return new CogsResult(money(total), missing);
    }

    public static ProfitResult calculate(BigDecimal netSales, BigDecimal cogs, BigDecimal expenses) {
        BigDecimal sales = money(netSales);
        BigDecimal cost = money(cogs);
        BigDecimal operatingExpenses = money(expenses);
        BigDecimal gross = sales.subtract(cost);
        return new ProfitResult(sales, cost, gross, operatingExpenses, gross.subtract(operatingExpenses));
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? Money.zero() : value).setScale(2, RoundingMode.HALF_UP);
    }
}
