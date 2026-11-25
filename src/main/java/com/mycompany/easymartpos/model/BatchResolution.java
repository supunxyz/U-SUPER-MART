package com.mycompany.easymartpos.model;

import java.math.BigDecimal;
import java.util.List;

/** Read-only result returned before a sale has been committed. */
public record BatchResolution(BatchResolutionStatus status, BigDecimal requestedQuantity,
        BigDecimal availableQuantity, List<BatchAllocation> allocations, List<BatchStock> sellableBatches) {

    public BatchResolution {
        if (status == null || requestedQuantity == null || availableQuantity == null)
            throw new IllegalArgumentException("Resolution status and quantities are required.");
        allocations = List.copyOf(allocations == null ? List.of() : allocations);
        sellableBatches = List.copyOf(sellableBatches == null ? List.of() : sellableBatches);
    }

    public boolean hasSufficientStock() {
        return availableQuantity.compareTo(requestedQuantity) >= 0;
    }
}
