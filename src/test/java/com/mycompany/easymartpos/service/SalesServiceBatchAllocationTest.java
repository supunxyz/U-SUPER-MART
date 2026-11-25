package com.mycompany.easymartpos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mycompany.easymartpos.model.BatchAllocation;
import com.mycompany.easymartpos.model.CartItem;
import com.mycompany.easymartpos.model.Product;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SalesServiceBatchAllocationTest {

    @Test
    void rejectsCartLineWhenAllocationQuantitiesDoNotEqualCartQuantity() {
        CartItem item = item("2", "200", List.of(allocation(1, "1", "180", "200")));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> SalesService.validateBatchPlan(item));

        assertEquals(SalesService.BATCH_PLAN_INVALID_MESSAGE, exception.getMessage());
    }

    @Test
    void calculatesWeightedAverageCostFromExactBatchAllocations() {
        BigDecimal cost = SalesService.weightedAverageCost(List.of(
                allocation(1, "3", "180", "200"),
                allocation(2, "2", "200", "200")));

        assertEquals(new BigDecimal("188.00"), cost);
    }

    @Test
    void rejectsInsufficientLockedBatchStockBeforeAnyDeduction() {
        BatchAllocation allocation = allocation(1, "3", "180", "200");

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                SalesService.validateLockedBatch(1, new BigDecimal("200"), allocation, 1, true,
                        new BigDecimal("2"), new BigDecimal("200"), new BigDecimal("180"), LocalDate.now().plusDays(1)));

        assertEquals(SalesService.BATCH_PLAN_INVALID_MESSAGE, exception.getMessage());
    }

    @Test
    void writesOneAllocationAuditRowForEachBatch() throws Exception {
        List<Map<Integer, Object>> rows = new ArrayList<>();
        Connection connection = recordingConnection(rows);
        var insert = SalesService.class.getDeclaredMethod("insertSaleItemBatchAllocations", Connection.class, int.class, List.class);
        insert.setAccessible(true);

        insert.invoke(new SalesService(), connection, 42, List.of(
                allocation(10, "3", "180", "200"), allocation(11, "2", "200", "200")));

        assertEquals(2, rows.size());
        assertEquals(42, rows.get(0).get(1));
        assertEquals(10, rows.get(0).get(2));
        assertEquals(new BigDecimal("3"), rows.get(0).get(3));
        assertEquals(new BigDecimal("180"), rows.get(0).get(4));
        assertEquals(11, rows.get(1).get(2));
    }

    @Test
    void sameProductAtDifferentPricesRemainsTwoIndependentSaleItemPlans() {
        CartItem first = item("1", "250", List.of(allocation(10, "1", "180", "250")));
        CartItem second = item("2", "260", List.of(allocation(11, "2", "200", "260")));

        SalesService.validateBatchPlan(first);
        SalesService.validateBatchPlan(second);

        assertEquals(2, List.of(first, second).size());
        assertNotEquals(0, first.ourPrice().compareTo(second.ourPrice()));
    }

    @Test
    void masterStockCompatibilityDeductionExecutesOnceForOneCartLine() throws Exception {
        List<Map<Integer, Object>> rows = new ArrayList<>();
        var deduct = SalesService.class.getDeclaredMethod("deductStock", Connection.class, CartItem.class);
        deduct.setAccessible(true);

        deduct.invoke(new SalesService(), recordingConnection(rows), item("2", "200", List.of(allocation(1, "2", "180", "200"))));

        assertEquals(1, rows.size());
        assertEquals(new BigDecimal("2"), rows.get(0).get(1));
    }

    private Connection recordingConnection(List<Map<Integer, Object>> executedRows) {
        return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if (!"prepareStatement".equals(method.getName())) return defaultValue(method.getReturnType());
                    Map<Integer, Object> values = new LinkedHashMap<>();
                    return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{PreparedStatement.class}, (statement, call, callArgs) -> {
                        if (call.getName().startsWith("set")) values.put((Integer) callArgs[0], "setNull".equals(call.getName()) ? null : callArgs[1]);
                        if ("executeUpdate".equals(call.getName())) { executedRows.add(new LinkedHashMap<>(values)); return 1; }
                        return defaultValue(call.getReturnType());
                    });
                });
    }

    private CartItem item(String quantity, String sellingPrice, List<BatchAllocation> allocations) {
        Product product = new Product(1, "BATCH-SALE", "Batch Sale Product", 1, "", new BigDecimal("100"),
                new BigDecimal(sellingPrice), new BigDecimal("300"), new BigDecimal("20"), BigDecimal.ZERO, null, true);
        return new CartItem(product, new BigDecimal(quantity), new BigDecimal(sellingPrice), new BigDecimal("300"), allocations);
    }

    private BatchAllocation allocation(int batchId, String quantity, String cost, String sellingPrice) {
        return new BatchAllocation(batchId, new BigDecimal(quantity), new BigDecimal(cost), new BigDecimal(sellingPrice),
                new BigDecimal("300"), LocalDate.now().plusDays(1));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0d;
        if (type == float.class) return 0f;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        if (type == char.class) return '\0';
        return null;
    }
}
