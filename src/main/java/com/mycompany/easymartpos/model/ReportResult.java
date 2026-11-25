package com.mycompany.easymartpos.model;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

public record ReportResult(String title, List<String> columns, List<List<Object>> rows, List<String> summaryLines,
        List<ReportMetric> metrics, List<ReportChartSeries> chartSeries, boolean barChart, String notice) {
    public ReportResult(String title, List<String> columns, List<List<Object>> rows, List<String> summaryLines) {
        this(title, columns, rows, summaryLines, List.of(), List.of(), false, "");
    }

    public ReportResult {
        columns = List.copyOf(columns);
        rows = rows.stream().map(row -> Collections.unmodifiableList(new ArrayList<>(row))).toList();
        summaryLines = List.copyOf(summaryLines);
        metrics = List.copyOf(metrics);
        chartSeries = List.copyOf(chartSeries);
        notice = notice == null ? "" : notice;
    }
}
