package com.mycompany.easymartpos.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Product(int id, String barcode, String name, int categoryId, String categoryName,
        BigDecimal costPrice, BigDecimal sellingPrice, BigDecimal retailPrice, BigDecimal stockQuantity,
        BigDecimal minimumStock, LocalDate expiryDate, boolean active) {

    @Override
    public String toString() {
        return name;
    }
}
