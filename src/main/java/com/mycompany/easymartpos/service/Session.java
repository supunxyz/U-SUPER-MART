package com.mycompany.easymartpos.service;

import java.util.Locale;

/** In-memory session populated only after a successful users-table login. */
public final class Session {
    private static volatile Integer userId;
    private static volatile String username;
    private static volatile String role;

    private Session() { }

    public static void start(int id, String name, String userRole) {
        if (id <= 0) throw new IllegalArgumentException("User ID must be positive.");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Username is required.");
        if (userRole == null || userRole.isBlank()) throw new IllegalArgumentException("This user has no assigned role.");
        userId = id;
        username = name.trim();
        role = userRole.trim().toUpperCase(Locale.ROOT);
    }

    public static void clear() { userId = null; username = null; role = null; }
    public static boolean isAuthenticated() { return userId != null && username != null && role != null; }
    public static int currentUserId() { requireAuthenticated(); return userId; }
    public static String currentCashierName() { requireAuthenticated(); return username; }
    public static String currentRole() { requireAuthenticated(); return role; }

    private static void requireAuthenticated() {
        if (!isAuthenticated()) throw new SecurityException("Please log in before using EasyMart POS.");
    }
}
