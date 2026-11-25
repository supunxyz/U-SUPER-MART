package com.mycompany.easymartpos.ui;

import com.mycompany.easymartpos.model.ReportChartPoint;
import com.mycompany.easymartpos.model.ReportChartSeries;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.List;
import javax.swing.JPanel;

/** Dependency-free Java2D chart used by the reporting centre. */
final class SimpleChartPanel extends JPanel {
    private static final Color[] SERIES_COLORS = {UITheme.BLUE, UITheme.GREEN, UITheme.RED, new Color(218, 143, 33)};
    private List<ReportChartSeries> series = List.of();
    private boolean barChart;

    SimpleChartPanel() { setOpaque(false); setPreferredSize(new java.awt.Dimension(600, 205)); setMinimumSize(new java.awt.Dimension(300, 175)); }

    void setData(List<ReportChartSeries> series, boolean barChart) {
        this.series = series == null ? List.of() : List.copyOf(series); this.barChart = barChart; repaint();
    }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int width = getWidth(), height = getHeight();
        g.setColor(Color.WHITE); g.fillRect(0, 0, width, height);
        List<ReportChartPoint> labels = series.isEmpty() ? List.of() : series.get(0).points();
        if (labels.isEmpty()) { empty(g, width, height); g.dispose(); return; }
        int left = 54, right = 18, top = 20, bottom = 42;
        int chartWidth = Math.max(1, width - left - right), chartHeight = Math.max(1, height - top - bottom);
        BigDecimal maximum = maximum(); if (maximum.signum() <= 0) maximum = BigDecimal.ONE;
        drawGrid(g, left, top, chartWidth, chartHeight, maximum);
        if (barChart) drawBars(g, labels, left, top, chartWidth, chartHeight, maximum);
        else drawLines(g, left, top, chartWidth, chartHeight, maximum);
        drawLabels(g, labels, left, top + chartHeight, chartWidth);
        drawLegend(g, width - right, 12); g.dispose();
    }

    private BigDecimal maximum() {
        BigDecimal maximum = BigDecimal.ZERO;
        for (ReportChartSeries item : series) for (ReportChartPoint point : item.points()) if (point.value() != null) maximum = maximum.max(point.value().abs());
        return maximum.multiply(new BigDecimal("1.12"));
    }

    private void drawGrid(Graphics2D g, int left, int top, int width, int height, BigDecimal maximum) {
        DecimalFormat format = new DecimalFormat("#,##0");
        g.setFont(new Font("Dialog", Font.PLAIN, 10));
        for (int index = 0; index <= 4; index++) {
            int y = top + (height * index / 4);
            g.setColor(new Color(230, 236, 242)); g.drawLine(left, y, left + width, y);
            BigDecimal value = maximum.multiply(BigDecimal.valueOf(4 - index)).divide(BigDecimal.valueOf(4));
            g.setColor(UITheme.GREY); String label = format.format(value); int labelWidth = g.getFontMetrics().stringWidth(label); g.drawString(label, left - labelWidth - 7, y + 4);
        }
        g.setColor(new Color(176, 189, 202)); g.drawLine(left, top, left, top + height); g.drawLine(left, top + height, left + width, top + height);
    }

    private void drawLines(Graphics2D g, int left, int top, int width, int height, BigDecimal maximum) {
        for (int seriesIndex = 0; seriesIndex < series.size(); seriesIndex++) {
            List<ReportChartPoint> points = series.get(seriesIndex).points(); if (points.isEmpty()) continue;
            g.setColor(SERIES_COLORS[seriesIndex % SERIES_COLORS.length]); g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int previousX = 0, previousY = 0;
            for (int index = 0; index < points.size(); index++) {
                int x = pointX(index, points.size(), left, width); int y = pointY(points.get(index).value(), top, height, maximum);
                if (index > 0) g.drawLine(previousX, previousY, x, y);
                int markerRadius = points.size() == 1 ? 5 : 3;
                g.fillOval(x - markerRadius, y - markerRadius, markerRadius * 2, markerRadius * 2); previousX = x; previousY = y;
            }
            if (points.size() == 1) singlePointNote(g, points.get(0), previousX, previousY);
        }
    }

    private void drawBars(Graphics2D g, List<ReportChartPoint> points, int left, int top, int width, int height, BigDecimal maximum) {
        int count = points.size(); int slot = Math.max(1, width / Math.max(1, count)); int barWidth = Math.max(8, Math.min(44, slot - 10));
        for (int index = 0; index < count; index++) {
            int x = left + index * slot + Math.max(1, (slot - barWidth) / 2); int y = pointY(points.get(index).value(), top, height, maximum);
            g.setColor(SERIES_COLORS[index % SERIES_COLORS.length]); g.fillRoundRect(x, y, barWidth, top + height - y, 5, 5);
        }
    }

    private void drawLabels(Graphics2D g, List<ReportChartPoint> points, int left, int bottom, int width) {
        g.setColor(UITheme.GREY); g.setFont(new Font("Dialog", Font.PLAIN, 10)); int step = Math.max(1, (int) Math.ceil(points.size() / 6d));
        for (int index = 0; index < points.size(); index += step) {
            String text = abbreviate(points.get(index).label()); int x = pointX(index, points.size(), left, width); int textWidth = g.getFontMetrics().stringWidth(text);
            g.drawString(text, x - textWidth / 2, bottom + 17);
        }
    }

    private void drawLegend(Graphics2D g, int right, int y) {
        if (barChart || series.size() < 2) return;
        g.setFont(new Font("Dialog", Font.PLAIN, 10));
        for (int index = series.size() - 1; index >= 0; index--) {
            String label = series.get(index).label(); int textWidth = g.getFontMetrics().stringWidth(label); right -= textWidth + 16;
            g.setColor(SERIES_COLORS[index % SERIES_COLORS.length]); g.fillRoundRect(right, y - 8, 9, 9, 3, 3); g.setColor(UITheme.GREY); g.drawString(label, right + 12, y);
            right -= 10;
        }
    }

    private static void singlePointNote(Graphics2D g, ReportChartPoint point, int x, int y) {
        String note = "Single data point for selected period"; g.setFont(new Font("Dialog", Font.PLAIN, 11)); g.setColor(UITheme.GREY);
        int width = g.getFontMetrics().stringWidth(note); g.drawString(note, x - width / 2, Math.max(34, y - 13));
    }

    private static int pointX(int index, int count, int left, int width) { return count <= 1 ? left + width / 2 : left + (width * index / (count - 1)); }
    private static int pointY(BigDecimal value, int top, int height, BigDecimal maximum) { double ratio = value == null ? 0 : value.doubleValue() / maximum.doubleValue(); return top + height - (int) Math.round(Math.max(0, Math.min(1, ratio)) * height); }
    private static String abbreviate(String value) { if (value == null) return ""; return value.length() <= 10 ? value : value.substring(0, 9) + "…"; }
    private static void empty(Graphics2D g, int width, int height) { g.setColor(UITheme.GREY); g.setFont(new Font("Dialog", Font.PLAIN, 13)); String message = "No chart data for the selected period"; int x = (width - g.getFontMetrics().stringWidth(message)) / 2; g.drawString(message, Math.max(10, x), height / 2); }
}
