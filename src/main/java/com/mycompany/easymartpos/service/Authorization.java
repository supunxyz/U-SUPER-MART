package com.mycompany.easymartpos.service;

/** Centralized role checks for dashboard visibility and protected screens. */
public final class Authorization {
    public enum Permission { CHECKOUT, SALES_HISTORY, ADMINISTRATION }

    private Authorization() { }

    public static boolean isAllowed(Permission permission) {
        if (!Session.isAuthenticated()) return false;
        String role = Session.currentRole();
        if ("ADMIN".equals(role)) return true;
        return "CASHIER".equals(role) && (permission == Permission.CHECKOUT || permission == Permission.SALES_HISTORY);
    }

    public static void require(Permission permission) {
        if (!Session.isAuthenticated()) throw new SecurityException("Please log in before opening this screen.");
        if (!isAllowed(permission)) throw new SecurityException("Your " + Session.currentRole() + " role is not authorized to open this screen.");
    }

    public static void requireAdmin() { require(Permission.ADMINISTRATION); }
    public static void requireCheckout() { require(Permission.CHECKOUT); }
    public static void requireSalesHistory() { require(Permission.SALES_HISTORY); }
}
