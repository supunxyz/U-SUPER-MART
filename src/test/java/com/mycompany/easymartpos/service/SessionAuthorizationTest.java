package com.mycompany.easymartpos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SessionAuthorizationTest {
    @AfterEach void clearSession() { Session.clear(); }

    @Test void adminCanUseAdministrativeAndCashierFunctions() {
        Session.start(1, "admin", "admin");
        assertEquals("admin", Session.currentCashierName());
        assertEquals("ADMIN", Session.currentRole());
        assertTrue(Authorization.isAllowed(Authorization.Permission.ADMINISTRATION));
        assertTrue(Authorization.isAllowed(Authorization.Permission.CHECKOUT));
    }

    @Test void cashierCannotUseAdministrativeFunctions() {
        Session.start(2, "cashier1", "CASHIER");
        assertTrue(Authorization.isAllowed(Authorization.Permission.CHECKOUT));
        assertTrue(Authorization.isAllowed(Authorization.Permission.SALES_HISTORY));
        assertFalse(Authorization.isAllowed(Authorization.Permission.ADMINISTRATION));
        assertThrows(SecurityException.class, Authorization::requireAdmin);
    }

    @Test void clearedSessionCannotAccessProtectedScreens() {
        Session.clear();
        assertFalse(Session.isAuthenticated());
        assertThrows(SecurityException.class, Authorization::requireCheckout);
    }
}
