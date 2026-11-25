package com.mycompany.easymartpos.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A completed sale linked to a shift, shown only in read-only shift details. */
public record ShiftSaleRow(String invoiceNumber, LocalDateTime saleDate, String paymentMethod, BigDecimal total) { }
