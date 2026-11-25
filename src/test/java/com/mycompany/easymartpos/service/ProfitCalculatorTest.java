package com.mycompany.easymartpos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProfitCalculatorTest {

    @Test
    void completedSaleItemCostSnapshotsProduceCogsAndExcludeVoidedSales() {
        ProfitCalculator.CogsResult result = ProfitCalculator.cogs(List.of(
                new ProfitCalculator.SaleItemCost("COMPLETED", new BigDecimal("2"), new BigDecimal("125.50")),
                new ProfitCalculator.SaleItemCost("COMPLETED", new BigDecimal("3"), new BigDecimal("10.00")),
                new ProfitCalculator.SaleItemCost("VOID", new BigDecimal("8"), new BigDecimal("99.99"))));

        assertEquals(new BigDecimal("281.00"), result.amount());
        assertEquals(0, result.missingSnapshotCount());
    }

    @Test
    void nullCompletedCostSnapshotMakesHistoricalCogsIncompleteInsteadOfAssumingCurrentCost() {
        ProfitCalculator.CogsResult result = ProfitCalculator.cogs(List.of(
                new ProfitCalculator.SaleItemCost("COMPLETED", BigDecimal.ONE, new BigDecimal("50.00")),
                new ProfitCalculator.SaleItemCost("COMPLETED", BigDecimal.ONE, null),
                new ProfitCalculator.SaleItemCost("CANCELLED", BigDecimal.ONE, null)));

        assertEquals(new BigDecimal("50.00"), result.amount());
        assertEquals(1, result.missingSnapshotCount());
    }

    @Test
    void calculatesGrossAndNetProfitUsingHistoricalCogsAndExpenses() {
        ProfitCalculator.ProfitResult result = ProfitCalculator.calculate(
                new BigDecimal("1000.00"), new BigDecimal("625.50"), new BigDecimal("125.25"));

        assertEquals(new BigDecimal("374.50"), result.grossProfit());
        assertEquals(new BigDecimal("249.25"), result.netProfit());
    }
}
