package com.mycompany.easymartpos.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SaleHistoryRow(int saleId, String invoiceNumber, String customerName,
        LocalDateTime saleDate, BigDecimal total, String paymentMethod, String status) { }
