package com.mycompany.easymartpos.model;

import java.math.BigDecimal;

public record SaleResult(int saleId, String invoiceNumber, BigDecimal total, BigDecimal changeAmount) { }
