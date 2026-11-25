package com.mycompany.easymartpos.model;

import java.math.BigDecimal;

public record ShiftTotals(BigDecimal sales, long transactions, BigDecimal cashSales, BigDecimal cardSales, BigDecimal transferSales) { }
