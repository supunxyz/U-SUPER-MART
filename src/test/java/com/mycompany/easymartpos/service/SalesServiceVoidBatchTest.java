package com.mycompany.easymartpos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SalesServiceVoidBatchTest {

    @Test
    void restoresEachHistoricalBatchByItsExactAllocationQuantity() throws Exception {
        List<Update> updates = new ArrayList<>();
        var restore = SalesService.class.getDeclaredMethod("restoreExactBatchStock", Connection.class, int.class, List.class);
        restore.setAccessible(true);

        restore.invoke(new SalesService(), restoreConnection(Map.of(
                46, new BatchRow(1, new BigDecimal("4"), new BigDecimal("5")),
                63, new BatchRow(1, new BigDecimal("8"), new BigDecimal("10"))), updates), 1,
                List.of(new SalesService.BatchRestoreAllocation(46, BigDecimal.ONE),
                        new SalesService.BatchRestoreAllocation(63, BigDecimal.ONE)));

        assertEquals(2, updates.size());
        assertEquals(new BigDecimal("1"), updates.get(0).values().get(1));
        assertEquals(46, updates.get(0).values().get(2));
        assertEquals(63, updates.get(1).values().get(2));
        assertEquals(true, updates.stream().allMatch(update -> update.sql().startsWith("UPDATE product_batches")));
    }

    @Test
    void productMasterStockRestoreExecutesOnlyOncePerSaleItem() throws Exception {
        List<Update> updates = new ArrayList<>();
        var restore = SalesService.class.getDeclaredMethod("restoreProductStock", Connection.class, int.class, BigDecimal.class);
        restore.setAccessible(true);

        restore.invoke(new SalesService(), restoreConnection(Map.of(), updates), 1, new BigDecimal("2"));

        assertEquals(1, updates.size());
        assertEquals("UPDATE products SET stock_quantity=stock_quantity+? WHERE product_id=?", updates.get(0).sql());
        assertEquals(new BigDecimal("2"), updates.get(0).values().get(1));
    }

    @Test
    void batchRestoreDoesNotAlterAllocationAuditRows() throws Exception {
        List<Update> updates = new ArrayList<>();
        var restore = SalesService.class.getDeclaredMethod("restoreExactBatchStock", Connection.class, int.class, List.class);
        restore.setAccessible(true);

        restore.invoke(new SalesService(), restoreConnection(Map.of(46, new BatchRow(1, new BigDecimal("4"), new BigDecimal("5"))), updates), 1,
                List.of(new SalesService.BatchRestoreAllocation(46, BigDecimal.ONE)));

        assertEquals(true, updates.stream().noneMatch(update -> update.sql().contains("sale_item_batch_allocations")));
    }

    @Test
    void alreadyVoidedSaleCannotPassTheExistingCompletedStatusGuard() {
        assertThrows(IllegalArgumentException.class, () -> SalesService.validateVoidStatus("VOID"));
    }

    @Test
    void invalidOrOverRestoredBatchIsRejectedBeforeUpdate() {
        assertThrows(IllegalStateException.class, () -> SalesService.validateBatchRestore(1,
                new SalesService.BatchRestoreAllocation(46, new BigDecimal("2")), 1,
                new BigDecimal("4"), new BigDecimal("5")));
        assertThrows(IllegalStateException.class, () -> SalesService.validateBatchRestore(1,
                new SalesService.BatchRestoreAllocation(46, BigDecimal.ONE), 2,
                new BigDecimal("4"), new BigDecimal("5")));
    }

    @Test
    void legacyItemWithNoAllocationRowsNeedsNoBatchRestore() throws Exception {
        List<Update> updates = new ArrayList<>();
        var restore = SalesService.class.getDeclaredMethod("restoreExactBatchStock", Connection.class, int.class, List.class);
        restore.setAccessible(true);

        restore.invoke(new SalesService(), restoreConnection(Map.of(), updates), 1, List.of());

        assertEquals(0, updates.size());
    }

    @Test
    void legacyVoidCreatesAnAdjustmentBatchSoBatchInventoryStaysInSync() throws Exception {
        List<Update> updates = new ArrayList<>();
        var restore = SalesService.class.getDeclaredMethod("restoreLegacyBatchStock", Connection.class, int.class, BigDecimal.class);
        restore.setAccessible(true);

        restore.invoke(new SalesService(), restoreConnection(Map.of(1, new BatchRow(1, new BigDecimal("100"), new BigDecimal("200"))), updates),
                1, new BigDecimal("2"));

        assertEquals(1, updates.size());
        assertTrue(updates.get(0).sql().startsWith("INSERT INTO product_batches"));
        assertEquals(1, updates.get(0).values().get(1));
        assertEquals(new BigDecimal("2"), updates.get(0).values().get(6));
        assertEquals(new BigDecimal("2"), updates.get(0).values().get(7));
    }

    private Connection restoreConnection(Map<Integer, BatchRow> batches, List<Update> updates) {
        return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if (!"prepareStatement".equals(method.getName())) return defaultValue(method.getReturnType());
                    String sql = (String) args[0];
                    Map<Integer, Object> values = new LinkedHashMap<>();
                    return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{PreparedStatement.class}, (statement, call, callArgs) -> {
                        if (call.getName().startsWith("set")) values.put((Integer) callArgs[0], "setNull".equals(call.getName()) ? null : callArgs[1]);
                        if ("executeQuery".equals(call.getName())) return batchResult(batches.get(values.get(1)));
                        if ("executeUpdate".equals(call.getName())) { updates.add(new Update(sql, new LinkedHashMap<>(values))); return 1; }
                        return defaultValue(call.getReturnType());
                    });
                });
    }

    private ResultSet batchResult(BatchRow row) {
        return (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ResultSet.class}, new java.lang.reflect.InvocationHandler() {
            private boolean advanced;
            @Override public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
                if ("next".equals(method.getName())) { boolean next = row != null && !advanced; advanced = true; return next; }
                if ("getInt".equals(method.getName())) return row.productId();
                if ("getBigDecimal".equals(method.getName())) return "quantity_available".equals(args[0]) ? row.available() : row.received();
                return defaultValue(method.getReturnType());
            }
        });
    }

    private record BatchRow(int productId, BigDecimal available, BigDecimal received) { }
    private record Update(String sql, Map<Integer, Object> values) { }

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
