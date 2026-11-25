package com.mycompany.easymartpos.service;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StockAdjustmentServiceTest {

    @Test
    void addCreatesOneAdjustmentBatchAndIncreasesMasterStockByTheSameQuantity() throws Exception {
        List<Execution> executions = new ArrayList<>();
        StockAdjustmentService service = new StockAdjustmentService(() -> recordingConnection(executions, 0, BigDecimal.ZERO));

        service.addStock(new StockAdjustmentService.AddRequest(7, amount("5"), amount("180"), amount("250"),
                amount("280"), LocalDate.of(2027, 1, 1), "Free stock", 19));

        assertEquals(3, executions.size());
        Execution batch = executions.get(0), product = executions.get(1), movement = executions.get(2);
        assertTrue(batch.sql().startsWith("INSERT INTO product_batches"));
        assertEquals(7, batch.values().get(1)); assertEquals(amount("5"), batch.values().get(6)); assertEquals(amount("5"), batch.values().get(7));
        assertEquals("UPDATE products SET stock_quantity=stock_quantity+? WHERE product_id=?", product.sql());
        assertEquals(amount("5"), product.values().get(1)); assertEquals(7, product.values().get(2));
        assertEquals(StockAdjustmentService.ADJUSTMENT_IN, movement.values().get(2)); assertEquals(amount("5"), movement.values().get(3));
        assertEquals(19, movement.values().get(4)); assertEquals("Free stock", movement.values().get(5));
        assertFalse(executions.stream().anyMatch(execution -> execution.sql().startsWith("UPDATE product_batches")));
    }

    @Test
    void removeLocksAndReducesOnlyTheSelectedBatchAndMasterStock() throws Exception {
        List<Execution> executions = new ArrayList<>();
        StockAdjustmentService service = new StockAdjustmentService(() -> recordingConnection(executions, 7, amount("10")));

        service.removeStock(new StockAdjustmentService.RemoveRequest(7, 46, amount("3"), "Damaged", 19));

        assertEquals(3, executions.size());
        Execution batch = executions.get(0), product = executions.get(1), movement = executions.get(2);
        assertTrue(batch.sql().startsWith("UPDATE product_batches SET quantity_available=quantity_available-?"));
        assertEquals(amount("3"), batch.values().get(1)); assertEquals(46, batch.values().get(2)); assertEquals(7, batch.values().get(3)); assertEquals(amount("3"), batch.values().get(4));
        assertEquals("UPDATE products SET stock_quantity=stock_quantity-? WHERE product_id=? AND stock_quantity>=?", product.sql());
        assertEquals(amount("3"), product.values().get(1)); assertEquals(7, product.values().get(2)); assertEquals(amount("3"), product.values().get(3));
        assertEquals(StockAdjustmentService.ADJUSTMENT_OUT, movement.values().get(2)); assertEquals(amount("-3"), movement.values().get(3));
        assertEquals("Damaged", movement.values().get(5));
    }

    @Test
    void rejectsRemovalAboveTheLockedBatchAvailability() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                StockAdjustmentService.validateLockedBatch(new StockAdjustmentService.LockedBatch(7, amount("2")), 7, amount("3")));

        assertEquals("Selected batch no longer has enough stock. Refresh and try again.", exception.getMessage());
    }

    @Test
    void permitsRemovingAPositiveStockBatchWithoutAnyExpiryRestriction() {
        StockAdjustmentService.validateLockedBatch(new StockAdjustmentService.LockedBatch(7, amount("3")), 7, amount("3"));
    }

    @Test
    void validatesPositiveAddPricesAndRequiresAReasonForEveryAdjustment() {
        assertThrows(IllegalArgumentException.class, () -> StockAdjustmentService.validateAdd(
                new StockAdjustmentService.AddRequest(7, amount("1"), BigDecimal.ZERO, amount("250"), null, null, "", 19)));
        assertThrows(IllegalArgumentException.class, () -> StockAdjustmentService.validateAdd(
                new StockAdjustmentService.AddRequest(7, amount("1"), amount("200"), amount("250"), null, null, " ", 19)));
        assertThrows(IllegalArgumentException.class, () -> StockAdjustmentService.validateRemove(
                new StockAdjustmentService.RemoveRequest(7, 46, amount("1"), " ", 19)));
    }

    @Test
    void adjustmentHistoryUsesTheDeployedMovementDateColumn() throws Exception {
        final String[] sql = new String[1];
        Connection connection = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
            if (!"prepareStatement".equals(method.getName())) return defaultValue(method.getReturnType());
            sql[0] = (String) args[0];
            return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{PreparedStatement.class}, (statement, call, callArgs) -> {
                if ("executeQuery".equals(call.getName())) return emptyResultSet();
                return defaultValue(call.getReturnType());
            });
        });

        new StockAdjustmentService(() -> connection).history();

        assertTrue(sql[0].contains("sm.movement_date"));
        assertFalse(sql[0].contains("sm.moved_at"));
    }

    private Connection recordingConnection(List<Execution> executions, int lockedProductId, BigDecimal lockedAvailable) {
        return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
            if (!"prepareStatement".equals(method.getName())) return defaultValue(method.getReturnType());
            String sql = (String) args[0]; Map<Integer, Object> values = new LinkedHashMap<>();
            return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{PreparedStatement.class}, (statement, call, callArgs) -> {
                if (call.getName().startsWith("set")) values.put((Integer) callArgs[0], "setNull".equals(call.getName()) ? null : callArgs[1]);
                if ("executeQuery".equals(call.getName())) return lockedBatchResult(lockedProductId, lockedAvailable);
                if ("executeUpdate".equals(call.getName())) { executions.add(new Execution(sql, new LinkedHashMap<>(values))); return 1; }
                return defaultValue(call.getReturnType());
            });
        });
    }

    private ResultSet lockedBatchResult(int productId, BigDecimal available) {
        final boolean[] first = {true};
        return (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ResultSet.class}, (proxy, method, args) -> {
            if ("next".equals(method.getName())) { boolean result = first[0]; first[0] = false; return result; }
            if ("getInt".equals(method.getName())) return productId;
            if ("getBigDecimal".equals(method.getName())) return available;
            return defaultValue(method.getReturnType());
        });
    }

    private ResultSet emptyResultSet() {
        return (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ResultSet.class}, (proxy, method, args) ->
                "next".equals(method.getName()) ? false : defaultValue(method.getReturnType()));
    }

    private record Execution(String sql, Map<Integer, Object> values) { }
    private BigDecimal amount(String value) { return new BigDecimal(value); }
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
