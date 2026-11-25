package com.mycompany.easymartpos.model;

import java.util.List;

/** Full read-only data for the shift-details dialog. */
public record ShiftDetails(ShiftHistoryRow shift, List<ShiftSaleRow> sales) { }
