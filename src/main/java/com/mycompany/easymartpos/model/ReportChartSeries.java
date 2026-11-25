package com.mycompany.easymartpos.model;

import java.util.List;

/** A named series supplied by the reporting service without coupling it to the Swing view. */
public record ReportChartSeries(String label, List<ReportChartPoint> points) {
    public ReportChartSeries {
        points = List.copyOf(points);
    }
}
