package com.mycompany.easymartpos.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class CartItem {
    private final int productId;
    private final String barcode;
    private final String productName;
    /** Normal shop selling price; never mutated by a checkout promotion. */
    private final BigDecimal ourPrice;
    private final BigDecimal unitPrice;
    private final BigDecimal retailPrice;
    private BigDecimal quantity;
    private Integer promotionId;
    private String promotionName;
    private String promotionType;
    private BigDecimal promotionValue = BigDecimal.ZERO;
    private BigDecimal promotionDiscountPerUnit = BigDecimal.ZERO;
    private BigDecimal finalUnitPrice;
    /** Read-only batch plan retained in memory until a later sale-allocation implementation consumes it. */
    private List<BatchAllocation> batchAllocations = List.of();

    public CartItem(Product product, BigDecimal quantity) {
        this(product, quantity, product.sellingPrice(), product.retailPrice(), List.of());
    }

    /** Creates a line using the resolved batch price instead of changing the product master price. */
    public CartItem(Product product, BigDecimal quantity, BigDecimal ourPrice, BigDecimal retailPrice,
            List<BatchAllocation> batchAllocations) {
        productId = product.id(); barcode = product.barcode(); productName = product.name();
        if (ourPrice == null) throw new IllegalArgumentException("Cart price is required.");
        this.ourPrice = ourPrice; unitPrice = ourPrice; this.retailPrice = retailPrice; this.quantity = quantity;
        this.batchAllocations = List.copyOf(batchAllocations == null ? List.of() : batchAllocations);
        finalUnitPrice = unitPrice;
    }
    public int productId() { return productId; }
    public String barcode() { return barcode; }
    public String productName() { return productName; }
    public BigDecimal ourPrice() { return ourPrice; }
    /** Actual unit price charged and persisted for this sale. */
    public BigDecimal finalUnitPrice() { return finalUnitPrice; }
    public BigDecimal unitPrice() { return unitPrice; }
    public BigDecimal retailPrice() { return retailPrice; }
    public BigDecimal quantity() { return quantity; }
    public void addQuantity(BigDecimal value) { quantity = quantity.add(value); }
    public List<BatchAllocation> batchAllocations() { return batchAllocations; }
    /** Replaces, rather than appends, the plan whenever a cart quantity is re-resolved. */
    public void updateQuantityAndBatchAllocations(BigDecimal quantity, List<BatchAllocation> batchAllocations) {
        if (quantity == null || quantity.signum() <= 0) throw new IllegalArgumentException("Cart quantity must be greater than zero.");
        this.quantity = quantity;
        this.batchAllocations = List.copyOf(batchAllocations == null ? List.of() : batchAllocations);
    }
    public Integer promotionId() { return promotionId; }
    public String promotionName() { return promotionName; }
    public String promotionType() { return promotionType; }
    public BigDecimal promotionValue() { return promotionValue; }
    public BigDecimal promotionDiscountPerUnit() { return promotionDiscountPerUnit; }
    public boolean hasPromotion() { return promotionId != null && promotionDiscountPerUnit.signum() > 0; }
    public BigDecimal baseSubtotal() { return ourPrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP); }
    public BigDecimal promotionSaving() { return promotionDiscountPerUnit.multiply(quantity).setScale(2, RoundingMode.HALF_UP); }
    public BigDecimal subtotal() { return finalUnitPrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP); }

    public String promotionLabel() {
        if (!hasPromotion()) return "-";
        if ("PERCENTAGE".equals(promotionType)) return promotionValue.stripTrailingZeros().toPlainString() + "% OFF";
        return "LKR " + promotionValue.setScale(2, RoundingMode.HALF_UP).toPlainString() + " OFF";
    }

    public void applyPromotion(int id, String name, String type, BigDecimal value, BigDecimal discountPerUnit) {
        promotionId = id; promotionName = name; promotionType = type; promotionValue = value;
        promotionDiscountPerUnit = discountPerUnit.max(BigDecimal.ZERO).min(ourPrice).setScale(2, RoundingMode.HALF_UP);
        finalUnitPrice = ourPrice.subtract(promotionDiscountPerUnit).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    public void clearPromotion() {
        promotionId = null; promotionName = null; promotionType = null; promotionValue = BigDecimal.ZERO;
        promotionDiscountPerUnit = BigDecimal.ZERO; finalUnitPrice = ourPrice;
    }
}
