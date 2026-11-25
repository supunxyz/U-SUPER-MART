package com.mycompany.easymartpos.service;

import com.mycompany.easymartpos.model.PaymentPart;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SalesServicePaymentStorageTest {
    @Test void storesCardReferenceAndBankNameOnTheCardPaymentRow() throws Exception {
        List<Map<Integer, Object>> executedRows = new ArrayList<>();
        Connection connection = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if (!"prepareStatement".equals(method.getName())) return defaultValue(method.getReturnType());
                    Map<Integer, Object> values = new LinkedHashMap<>();
                    return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{PreparedStatement.class}, (statement, call, callArgs) -> {
                        if (call.getName().startsWith("set")) values.put((Integer) callArgs[0], "setNull".equals(call.getName()) ? null : callArgs[1]);
                        if ("executeUpdate".equals(call.getName())) { executedRows.add(new LinkedHashMap<>(values)); return 1; }
                        return defaultValue(call.getReturnType());
                    });
                });

        var insert = SalesService.class.getDeclaredMethod("insertPayments", Connection.class, int.class, List.class, BigDecimal.class);
        insert.setAccessible(true);
        insert.invoke(new SalesService(), connection, 42,
                List.of(PaymentPart.card(new BigDecimal("816.00"), "CARD-1234", "Commercial Bank")), BigDecimal.ZERO);

        Map<Integer, Object> row = executedRows.get(0);
        assertEquals("CARD", row.get(2));
        assertEquals(new BigDecimal("816.00"), row.get(3));
        assertEquals("CARD-1234", row.get(6));
        assertEquals(null, row.get(7));
        assertEquals("Commercial Bank", row.get(8));
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
