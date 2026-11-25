package com.mycompany.easymartpos.model;

/** A cashier filter option for the admin shift-history screen. */
public record CashierOption(Integer userId, String username) {
    @Override public String toString() { return username; }
}
