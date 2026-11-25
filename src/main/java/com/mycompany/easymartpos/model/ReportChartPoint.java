package com.mycompany.easymartpos.model;

import java.math.BigDecimal;

/** A single labelled value for the lightweight Swing report charts. */
public record ReportChartPoint(String label, BigDecimal value) { }
