package com.mycompany.easymartpos.model;

/** Checkout customer selector data. The display remains the customer name. */
public record CustomerChoice(int id, String name, String phone, String loyaltyCardCode, String loyaltyCardStatus,
        int loyaltyPoints, boolean loyaltyCustomer) {
    public CustomerChoice(int id, String name) { this(id, name, null, null, null, 0, false); }

    @Override public String toString() { return name; }

    public boolean matchesLookup(String text) {
        String normalized = text == null ? "" : text.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.isEmpty()) return true;
        return contains(name, normalized) || normalizePhone(phone).contains(normalizePhone(normalized))
                || normalizeCard(loyaltyCardCode).contains(normalizeCard(normalized));
    }

    public boolean isExactCardScan(String text) { return !normalizeCard(text).isEmpty() && normalizeCard(text).equals(normalizeCard(loyaltyCardCode)); }
    public boolean isExactPhoneLookup(String text) { return !normalizePhone(text).isEmpty() && normalizePhone(text).equals(normalizePhone(phone)); }
    public boolean hasBlockedCard() { return "BLOCKED".equalsIgnoreCase(loyaltyCardStatus); }

    private static boolean contains(String value, String query) { return value != null && value.toLowerCase(java.util.Locale.ROOT).contains(query); }
    private static String normalizePhone(String value) { return value == null ? "" : value.replaceAll("[\\s()\\-]+", ""); }
    private static String normalizeCard(String value) { return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(java.util.Locale.ROOT); }
}
