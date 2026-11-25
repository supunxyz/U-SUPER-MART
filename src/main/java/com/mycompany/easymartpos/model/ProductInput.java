package com.mycompany.easymartpos.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProductInput(Integer id, String barcode, String name, int categoryId,
        BigDecimal costPrice, BigDecimal sellingPrice, BigDecimal retailPrice, BigDecimal openingStock,
        BigDecimal minimumStock, LocalDate expiryDate) { }
