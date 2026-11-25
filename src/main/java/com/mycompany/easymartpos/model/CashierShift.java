package com.mycompany.easymartpos.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CashierShift(int shiftId, int userId, LocalDateTime openedAt, BigDecimal openingCash) { }
