package com.mycompany.easymartpos.service;

import com.mycompany.easymartpos.DBConnection;
import com.mycompany.easymartpos.model.CartItem;
import com.mycompany.easymartpos.model.CustomerChoice;
import com.mycompany.easymartpos.util.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Checkout-only evaluator for the existing promotions table.  It never alters product prices. */
public final class PromotionService {
    /** Refreshes the cart in one batched lookup; JTable rendering never performs a promotion query. */
    public void applyEligiblePromotions(List<CartItem> cart, CustomerChoice customer, LocalDateTime now) throws Exception {
        if (cart == null || cart.isEmpty()) return;
        cart.forEach(CartItem::clearPromotion);
        List<Integer> productIds = cart.stream().map(CartItem::productId).distinct().toList();
        Map<Integer, List<Promotion>> candidates = loadCandidates(productIds, now == null ? LocalDateTime.now() : now);
        boolean loyaltyCustomer = customer != null && !isWalkIn(customer);
        for (CartItem item : cart) {
            Promotion selected = candidates.getOrDefault(item.productId(), List.of()).stream()
                    .filter(promotion -> appliesToCustomer(promotion.customerScope(), loyaltyCustomer))
                    .map(promotion -> promotion.withDiscount(discountPerUnit(item.ourPrice(), promotion)))
                    // Existing promotions have no priority field: choose the best eligible unit discount, then newest ID.
                    .max(Comparator.comparing(Promotion::discountPerUnit).thenComparing(Promotion::id))
                    .orElse(null);
            if (selected != null && selected.discountPerUnit().signum() > 0) {
                item.applyPromotion(selected.id(), selected.name(), selected.type(), selected.value(), selected.discountPerUnit());
            }
        }
    }

    /** One currently running promotion, for read-only display on the checkout screen. */
    public record ActivePromotion(String name, String productName, String discount, String customerScope, LocalDateTime endsAt) { }

    public List<ActivePromotion> listActive(LocalDateTime now) throws Exception {
        String sql = "SELECT pr.promotion_name, p.product_name, pr.discount_type, pr.discount_value, pr.customer_scope, pr.end_datetime "
                + "FROM promotions pr JOIN products p ON p.product_id=pr.product_id "
                + "WHERE pr.active=TRUE AND pr.start_datetime<=? AND pr.end_datetime>=? ORDER BY p.product_name, pr.promotion_name";
        List<ActivePromotion> result = new ArrayList<>();
        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            Timestamp stamp = Timestamp.valueOf(now == null ? LocalDateTime.now() : now);
            statement.setTimestamp(1, stamp); statement.setTimestamp(2, stamp);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    BigDecimal value = rows.getBigDecimal("discount_value");
                    String discount = "PERCENTAGE".equalsIgnoreCase(rows.getString("discount_type"))
                            ? value.stripTrailingZeros().toPlainString() + "% OFF"
                            : "LKR " + value.setScale(2, RoundingMode.HALF_UP).toPlainString() + " OFF";
                    result.add(new ActivePromotion(rows.getString("promotion_name"), rows.getString("product_name"), discount,
                            "ALL".equalsIgnoreCase(rows.getString("customer_scope")) ? "All Customers" : "Loyalty Customers Only",
                            rows.getTimestamp("end_datetime").toLocalDateTime()));
                }
            }
        }
        return result;
    }

    private Map<Integer, List<Promotion>> loadCandidates(Collection<Integer> ids, LocalDateTime now) throws Exception {
        if (ids.isEmpty()) return Map.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        String sql = "SELECT promotion_id, promotion_name, product_id, discount_type, discount_value, customer_scope "
                + "FROM promotions WHERE active=TRUE AND start_datetime<=? AND end_datetime>=? AND product_id IN (" + placeholders + ")";
        Map<Integer, List<Promotion>> result = new HashMap<>();
        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.valueOf(now)); statement.setTimestamp(2, Timestamp.valueOf(now));
            int index = 3;
            for (Integer id : ids) statement.setInt(index++, id);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    Promotion promotion = new Promotion(rows.getInt("promotion_id"), rows.getString("promotion_name"), rows.getInt("product_id"),
                            rows.getString("discount_type"), rows.getBigDecimal("discount_value"), rows.getString("customer_scope"), Money.zero());
                    result.computeIfAbsent(promotion.productId(), ignored -> new ArrayList<>()).add(promotion);
                }
            }
        }
        return result;
    }

    private boolean appliesToCustomer(String scope, boolean loyaltyCustomer) {
        return "ALL".equalsIgnoreCase(scope) || ("LOYALTY".equalsIgnoreCase(scope) && loyaltyCustomer);
    }

    private boolean isWalkIn(CustomerChoice customer) {
        return customer.name() == null || "walk-in customer".equalsIgnoreCase(customer.name().trim());
    }

    private BigDecimal discountPerUnit(BigDecimal ourPrice, Promotion promotion) {
        BigDecimal base = ourPrice == null ? Money.zero() : ourPrice;
        BigDecimal value = promotion.value() == null ? Money.zero() : promotion.value().max(Money.zero());
        BigDecimal discount = "PERCENTAGE".equalsIgnoreCase(promotion.type())
                ? base.multiply(value).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP) : value.setScale(2, RoundingMode.HALF_UP);
        return discount.max(Money.zero()).min(base).setScale(2, RoundingMode.HALF_UP);
    }

    private record Promotion(int id, String name, int productId, String type, BigDecimal value, String customerScope,
            BigDecimal discountPerUnit) {
        private Promotion withDiscount(BigDecimal value) { return new Promotion(id, name, productId, type, this.value, customerScope, value); }
    }
}
