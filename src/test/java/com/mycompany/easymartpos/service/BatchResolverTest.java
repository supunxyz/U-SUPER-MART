package com.mycompany.easymartpos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mycompany.easymartpos.model.BatchResolution;
import com.mycompany.easymartpos.model.BatchResolutionStatus;
import com.mycompany.easymartpos.model.BatchStock;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class BatchResolverTest {

    @Test
    void oneBatchWithEnoughStockAutomaticallyAllocates() throws Exception {
        BatchResolution result = resolver(List.of(batch(1, "10", "200", LocalDate.now().plusDays(5))))
                .resolve(10, decimal("4"));

        assertEquals(BatchResolutionStatus.SINGLE_PRICE_AUTO, result.status());
        assertEquals(1, result.allocations().size());
        assertEquals(0, result.allocations().get(0).quantity().compareTo(decimal("4")));
    }

    @Test
    void samePriceBatchesAllocateAcrossFefoOrder() throws Exception {
        LocalDate today = LocalDate.now();
        BatchResolution result = resolver(List.of(
                batch(1, "3", "200", today.plusDays(2)),
                batch(2, "10", "200.00", today.plusDays(10))))
                .resolve(10, decimal("5"));

        assertEquals(BatchResolutionStatus.SINGLE_PRICE_AUTO, result.status());
        assertEquals(List.of(1, 2), result.allocations().stream().map(allocation -> allocation.batchId()).toList());
        assertEquals(0, result.allocations().get(0).quantity().compareTo(decimal("3")));
        assertEquals(0, result.allocations().get(1).quantity().compareTo(decimal("2")));
    }

    @Test
    void multipleSellablePricesRequireSelectionEvenForOneUnit() throws Exception {
        LocalDate today = LocalDate.now();
        BatchResolution result = resolver(List.of(
                batch(1, "10", "200", today.plusDays(2)),
                batch(2, "10", "220", today.plusDays(10))))
                .resolve(10, decimal("1"));

        assertEquals(BatchResolutionStatus.MULTI_PRICE_SELECTION_REQUIRED, result.status());
        assertTrue(result.allocations().isEmpty());
        assertEquals(2, result.sellableBatches().size());
    }

    @Test
    void multipleSellablePricesRequireSelectionWhenRequestCrossesPrices() throws Exception {
        LocalDate today = LocalDate.now();
        BatchResolution result = resolver(List.of(
                batch(1, "3", "200", today.plusDays(2)),
                batch(2, "10", "220", today.plusDays(10))))
                .resolve(10, decimal("5"));

        assertEquals(BatchResolutionStatus.MULTI_PRICE_SELECTION_REQUIRED, result.status());
        assertTrue(result.allocations().isEmpty());
        assertEquals(2, result.sellableBatches().size());
    }

    @Test
    void expiredBatchesAreIgnoredEvenWhenLookupReturnsThem() throws Exception {
        LocalDate today = LocalDate.now();
        BatchResolution result = resolver(List.of(
                batch(1, "10", "200", today.minusDays(1)),
                batch(2, "2", "200", today.plusDays(1))))
                .resolve(10, decimal("2"));

        assertEquals(BatchResolutionStatus.SINGLE_PRICE_AUTO, result.status());
        assertEquals(List.of(2), result.sellableBatches().stream().map(BatchStock::batchId).toList());
        assertEquals(0, result.availableQuantity().compareTo(decimal("2")));
    }

    @Test
    void nullExpiryIsSellableAfterDatedBatchInRepositoryProvidedFefoOrder() throws Exception {
        LocalDate today = LocalDate.now();
        BatchResolution result = resolver(List.of(
                batch(1, "1", "200", today.plusDays(1)),
                batch(2, "5", "200", null)))
                .resolve(10, decimal("2"));

        assertEquals(BatchResolutionStatus.SINGLE_PRICE_AUTO, result.status());
        assertEquals(List.of(1, 2), result.allocations().stream().map(allocation -> allocation.batchId()).toList());
    }

    @Test
    void insufficientSellableQuantityReturnsNoStock() throws Exception {
        BatchResolution result = resolver(List.of(batch(1, "2", "200", null)))
                .resolve(10, decimal("3"));

        assertEquals(BatchResolutionStatus.NO_STOCK, result.status());
        assertEquals(0, result.requestedQuantity().compareTo(decimal("3")));
        assertEquals(0, result.availableQuantity().compareTo(decimal("2")));
        assertTrue(result.allocations().isEmpty());
    }

    @Test
    void resolveForPriceUsesOnlyTheSelectedPriceGroup() throws Exception {
        LocalDate today = LocalDate.now();
        BatchResolution result = resolver(List.of(
                batch(1, "3", "200", today.plusDays(1)),
                batch(2, "10", "220", today.plusDays(2))))
                .resolveForPrice(10, decimal("4"), decimal("220.00"));

        assertEquals(BatchResolutionStatus.SINGLE_PRICE_AUTO, result.status());
        assertEquals(0, result.availableQuantity().compareTo(decimal("10")));
        assertEquals(List.of(2), result.sellableBatches().stream().map(BatchStock::batchId).toList());
        assertEquals(List.of(2), result.allocations().stream().map(allocation -> allocation.batchId()).toList());
    }

    private BatchResolver resolver(List<BatchStock> batches) {
        return new BatchResolver(productId -> batches);
    }

    private BatchStock batch(int id, String quantity, String sellingPrice, LocalDate expiry) {
        return new BatchStock(id, 10, decimal("100"), decimal(sellingPrice), decimal("250"), expiry, decimal(quantity));
    }

    private BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
