package com.mycompany.easymartpos.service;

import com.mycompany.easymartpos.model.BatchAllocation;
import com.mycompany.easymartpos.model.BatchResolution;
import com.mycompany.easymartpos.model.BatchResolutionStatus;
import com.mycompany.easymartpos.model.BatchStock;
import com.mycompany.easymartpos.repository.BatchRepository;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Resolves proposed FEFO allocations without changing any stock or sale data. */
public final class BatchResolver {

    @FunctionalInterface
    public interface BatchLookup {
        List<BatchStock> findSellableBatches(int productId) throws SQLException;
    }

    private final BatchLookup batches;

    public BatchResolver() {
        this(new BatchRepository()::findSellableBatches);
    }

    public BatchResolver(BatchLookup batches) {
        if (batches == null) throw new IllegalArgumentException("Batch lookup is required.");
        this.batches = batches;
    }

    /** Builds a FEFO allocation only when all currently sellable batches have one selling price. */
    public BatchResolution resolve(int productId, BigDecimal requestedQuantity) throws SQLException {
        validateRequest(productId, requestedQuantity);
        List<BatchStock> sellable = findCurrentSellable(productId);
        BigDecimal available = totalAvailable(sellable);
        if (available.compareTo(requestedQuantity) < 0)
            return noStock(requestedQuantity, available, sellable);
        if (hasMultipleSellingPrices(sellable))
            return multiPrice(requestedQuantity, available, sellable);

        List<BatchAllocation> allocations = allocateFefo(sellable, requestedQuantity);
        return new BatchResolution(BatchResolutionStatus.SINGLE_PRICE_AUTO, requestedQuantity, available, allocations, sellable);
    }

    /** Resolves FEFO using precisely one cashier-selected selling-price group. */
    public BatchResolution resolveForPrice(int productId, BigDecimal requestedQuantity, BigDecimal sellingPrice) throws SQLException {
        validateRequest(productId, requestedQuantity);
        if (sellingPrice == null) throw new IllegalArgumentException("Selling price is required.");

        List<BatchStock> atPrice = findCurrentSellable(productId).stream()
                .filter(batch -> batch.sellingPrice().compareTo(sellingPrice) == 0)
                .toList();
        BigDecimal available = totalAvailable(atPrice);
        if (available.compareTo(requestedQuantity) < 0)
            return noStock(requestedQuantity, available, atPrice);

        List<BatchAllocation> allocations = allocateFefo(atPrice, requestedQuantity);
        return new BatchResolution(BatchResolutionStatus.SINGLE_PRICE_AUTO, requestedQuantity, available, allocations, atPrice);
    }

    private List<BatchStock> findCurrentSellable(int productId) throws SQLException {
        LocalDate today = LocalDate.now();
        return batches.findSellableBatches(productId).stream()
                // The repository enforces active status; these checks preserve expiry/quantity safety for any lookup implementation.
                .filter(batch -> batch.productId() == productId)
                .filter(batch -> batch.quantityAvailable().signum() > 0)
                .filter(batch -> batch.expiryDate() == null || !batch.expiryDate().isBefore(today))
                .toList();
    }

    private List<BatchAllocation> allocateFefo(List<BatchStock> batches, BigDecimal requestedQuantity) {
        List<BatchAllocation> allocations = new ArrayList<>();
        BigDecimal remaining = requestedQuantity;
        for (BatchStock batch : batches) {
            if (remaining.signum() == 0) break;
            BigDecimal allocated = batch.quantityAvailable().min(remaining);
            allocations.add(allocation(batch, allocated));
            remaining = remaining.subtract(allocated);
        }
        return List.copyOf(allocations);
    }

    private BatchAllocation allocation(BatchStock batch, BigDecimal quantity) {
        return new BatchAllocation(batch.batchId(), quantity, batch.costPrice(), batch.sellingPrice(),
                batch.retailPrice(), batch.expiryDate());
    }

    private BatchResolution noStock(BigDecimal requestedQuantity, BigDecimal availableQuantity, List<BatchStock> sellable) {
        return new BatchResolution(BatchResolutionStatus.NO_STOCK, requestedQuantity, availableQuantity, List.of(), sellable);
    }

    private BatchResolution multiPrice(BigDecimal requestedQuantity, BigDecimal availableQuantity, List<BatchStock> sellable) {
        return new BatchResolution(BatchResolutionStatus.MULTI_PRICE_SELECTION_REQUIRED, requestedQuantity, availableQuantity, List.of(), sellable);
    }

    private BigDecimal totalAvailable(List<BatchStock> batches) {
        return batches.stream().map(BatchStock::quantityAvailable).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Uses compareTo so numerically identical values such as 200 and 200.00 remain one price group. */
    private boolean hasMultipleSellingPrices(List<BatchStock> batches) {
        BigDecimal firstPrice = null;
        for (BatchStock batch : batches) {
            if (firstPrice == null) firstPrice = batch.sellingPrice();
            else if (firstPrice.compareTo(batch.sellingPrice()) != 0) return true;
        }
        return false;
    }

    private void validateRequest(int productId, BigDecimal requestedQuantity) {
        if (productId <= 0) throw new IllegalArgumentException("Product ID must be positive.");
        if (requestedQuantity == null || requestedQuantity.signum() <= 0)
            throw new IllegalArgumentException("Requested quantity must be greater than zero.");
    }
}
