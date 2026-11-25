package com.mycompany.easymartpos.ui;

import com.mycompany.easymartpos.model.ReportMetric;
import com.mycompany.easymartpos.model.ReportResult;
import com.mycompany.easymartpos.model.ReportType;
import java.awt.Color;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Reusable presentation-only writer for already-loaded Reports data. */
final class ReportExcelExporter {
    private static final Color NAVY = new Color(15, 38, 64);
    private static final Color ALTERNATE = new Color(248, 251, 253);
    private static final String MONEY = "#,##0.00", QUANTITY = "#,##0.###", PERCENT = "0.00%";
    private static final DateTimeFormatter GENERATED = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm a");

    private ReportExcelExporter() { }

    static void export(Path file, ReportResult report, ReportType type, String period) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(sheetName(report.title()));
            Styles styles = new Styles(workbook);
            int lastColumn = Math.max(1, report.columns().size()) - 1;
            int headerRow = 4;
            boolean metricsTable = isMetricsTable(report);
            writeTitle(sheet, report, period, lastColumn, styles);
            int tableLastRow = writeTable(sheet, report, type, headerRow, styles, metricsTable);
            int summaryLastRow = writeSummary(sheet, report, tableLastRow + 2, lastColumn, styles);
            configurePrint(sheet, report, headerRow, tableLastRow, summaryLastRow, lastColumn, metricsTable);
            workbook.setPrintArea(workbook.getSheetIndex(sheet), 0, lastColumn, 0, Math.max(tableLastRow, summaryLastRow));
            sizeColumns(sheet, report.columns().size());
            sheet.createFreezePane(0, headerRow + 1);
            try (var output = Files.newOutputStream(file)) { workbook.write(output); }
        }
    }

    private static void writeTitle(Sheet sheet, ReportResult report, String period, int lastColumn, Styles styles) {
        merge(sheet, 0, lastColumn, 0); merge(sheet, 0, lastColumn, 1); merge(sheet, 0, lastColumn, 2);
        cell(sheet, 0, 0).setCellValue("USHANTHA SUPER MART"); cell(sheet, 0, 0).setCellStyle(styles.storeTitle);
        cell(sheet, 1, 0).setCellValue(report.title().toUpperCase()); cell(sheet, 1, 0).setCellStyle(styles.reportTitle);
        cell(sheet, 2, 0).setCellValue("Period: " + nonBlank(period, "Current report") + "    |    Generated: " + LocalDateTime.now().format(GENERATED));
        cell(sheet, 2, 0).setCellStyle(styles.detail);
        sheet.getRow(0).setHeightInPoints(24); sheet.getRow(1).setHeightInPoints(20); sheet.getRow(2).setHeightInPoints(18);
    }

    private static int writeTable(Sheet sheet, ReportResult report, ReportType type, int headerRow, Styles styles, boolean metricsTable) {
        Row header = sheet.createRow(headerRow); header.setHeightInPoints(21);
        for (int column = 0; column < report.columns().size(); column++) {
            Cell cell = header.createCell(column); cell.setCellValue(report.columns().get(column)); cell.setCellStyle(styles.tableHeader);
        }
        for (int rowIndex = 0; rowIndex < report.rows().size(); rowIndex++) {
            List<Object> values = report.rows().get(rowIndex); Row row = sheet.createRow(headerRow + 1 + rowIndex);
            row.setHeightInPoints(containsLongText(values) ? 30 : 20); StatusPalette palette = palette(type, values);
            for (int column = 0; column < report.columns().size(); column++) {
                Object value = column < values.size() ? values.get(column) : null;
                String semanticName = metricsTable && !values.isEmpty() ? String.valueOf(values.get(0)) : report.columns().get(column);
                writeValue(row.createCell(column), value, semanticName, styles, palette, rowIndex);
            }
        }
        return headerRow + report.rows().size();
    }

    private static int writeSummary(Sheet sheet, ReportResult report, int startRow, int lastColumn, Styles styles) {
        if (report.metrics().isEmpty() && report.summaryLines().isEmpty()) return startRow - 1;
        merge(sheet, 0, lastColumn, startRow); cell(sheet, startRow, 0).setCellValue("SUMMARY"); cell(sheet, startRow, 0).setCellStyle(styles.summaryHeader);
        int rowIndex = startRow + 1; Set<String> labels = new HashSet<>();
        for (ReportMetric metric : report.metrics()) {
            if (metric.label() == null || metric.label().isBlank()) continue;
            writeSummaryPair(sheet, rowIndex++, metric.label(), metric.value(), styles); labels.add(normalize(metric.label()));
        }
        for (String summary : report.summaryLines()) {
            String[] pair = summary.split(":", 2); String label = pair[0].trim();
            if (label.isBlank() || labels.contains(normalize(label))) continue;
            writeSummaryPair(sheet, rowIndex++, label, pair.length == 2 ? pair[1].trim() : "", styles);
        }
        return rowIndex - 1;
    }

    private static void writeSummaryPair(Sheet sheet, int rowIndex, String labelText, String rawValue, Styles styles) {
        Row row = sheet.createRow(rowIndex); row.setHeightInPoints(20);
        Cell label = row.createCell(0); label.setCellValue(labelText); label.setCellStyle(styles.summaryLabel);
        Cell value = row.createCell(1); BigDecimal number = number(rawValue);
        if (number == null) { value.setCellValue(rawValue == null ? "" : rawValue); value.setCellStyle(styles.summaryText); }
        else if (isPercent(labelText, rawValue)) { value.setCellValue(number.divide(BigDecimal.valueOf(100)).doubleValue()); value.setCellStyle(styles.summaryPercent); }
        else if (isMoney(labelText)) { value.setCellValue(number.doubleValue()); value.setCellStyle(styles.summaryMoney(number)); }
        else { value.setCellValue(number.doubleValue()); value.setCellStyle(styles.summaryQuantity); }
    }

    private static void writeValue(Cell cell, Object value, String semanticName, Styles styles, StatusPalette palette, int rowIndex) {
        CellStyle style = styleFor(styles, semanticName, value, palette, rowIndex); cell.setCellStyle(style);
        if (value == null) cell.setCellValue("");
        else if (value instanceof java.sql.Timestamp timestamp) { cell.setCellValue(timestamp.toLocalDateTime()); cell.setCellStyle(styles.dateTime(style)); }
        else if (value instanceof java.sql.Date date) { cell.setCellValue(date.toLocalDate()); cell.setCellStyle(styles.date(style)); }
        else if (value instanceof LocalDate date) { cell.setCellValue(date); cell.setCellStyle(styles.date(style)); }
        else if (value instanceof LocalDateTime dateTime) { cell.setCellValue(dateTime); cell.setCellStyle(styles.dateTime(style)); }
        else if (value instanceof Number numeric) cell.setCellValue(numeric.doubleValue());
        else {
            String text = String.valueOf(value); BigDecimal numeric = number(text);
            if (numeric != null && isPercent(semanticName, text)) { cell.setCellValue(numeric.divide(BigDecimal.valueOf(100)).doubleValue()); cell.setCellStyle(styles.percent(style)); }
            else cell.setCellValue(text);
        }
    }

    private static CellStyle styleFor(Styles styles, String name, Object value, StatusPalette palette, int rowIndex) {
        boolean money = isMoney(name); CellStyle style;
        if (isStatus(name)) style = styles.status;
        else if (value instanceof Number) style = money ? styles.money : styles.quantity;
        else if (isLongText(name)) style = styles.wrappedText;
        else style = styles.text;
        if (money && value instanceof Number numeric && name.toLowerCase().contains("profit")) style = styles.profit(style, new BigDecimal(numeric.toString()));
        if (palette != null) return styles.status(style, palette);
        return rowIndex % 2 == 1 ? styles.alternate(style) : style;
    }

    private static void configurePrint(Sheet sheet, ReportResult report, int headerRow, int tableLastRow, int summaryLastRow, int lastColumn, boolean metricsTable) {
        sheet.setFitToPage(true); sheet.setAutobreaks(true); sheet.setHorizontallyCenter(true);
        PrintSetup setup = sheet.getPrintSetup(); setup.setLandscape(!metricsTable && lastColumn >= 4); setup.setFitWidth((short) 1); setup.setFitHeight((short) 0);
        sheet.setMargin(Sheet.LeftMargin, .25d); sheet.setMargin(Sheet.RightMargin, .25d); sheet.setMargin(Sheet.TopMargin, .5d); sheet.setMargin(Sheet.BottomMargin, .5d);
        sheet.getHeader().setCenter("USHANTHA SUPER MART");
        sheet.getFooter().setCenter(report.title() + " | Generated " + LocalDateTime.now().format(GENERATED) + " | Page &P of &N");
        sheet.setRepeatingRows(new CellRangeAddress(headerRow, headerRow, 0, 0));
        if (!metricsTable && !report.rows().isEmpty()) sheet.setAutoFilter(new CellRangeAddress(headerRow, tableLastRow, 0, lastColumn));
    }

    private static boolean isMetricsTable(ReportResult report) { return report.columns().size() == 2 && "Metric".equalsIgnoreCase(report.columns().get(0)); }
    private static StatusPalette palette(ReportType type, List<Object> row) {
        return switch (type) {
            case LOW_STOCK -> numericAt(row, 2).signum() <= 0 ? StatusPalette.OUT_OF_STOCK : StatusPalette.LOW_STOCK;
            case OUT_OF_STOCK -> StatusPalette.OUT_OF_STOCK;
            case EXPIRED_ITEMS -> StatusPalette.EXPIRED;
            case NEAR_EXPIRY_ITEMS -> StatusPalette.NEAR_EXPIRY;
            case VOID_SALES -> StatusPalette.VOID;
            default -> null;
        };
    }

    private static boolean isMoney(String name) {
        String value = name == null ? "" : name.toLowerCase();
        if (value.contains("count") || value.contains("transaction") || value.contains("product") || value.contains("customer") || value.contains("supplier") || value.contains("unit") || value.contains("quantity") || value.contains("qty") || value.contains("shortage") || value.contains("days") || value.contains("point") || value.contains("margin")) return false;
        return value.contains("sales") || value.contains("subtotal") || value.contains("price") || value.contains("amount") || value.contains("value") || value.contains("cost") || value.contains("contribution") || value.contains("profit") || value.contains("bill") || value.contains("expense") || value.contains("discount") || value.contains("cash") || value.contains("card") || value.contains("purchase") || value.contains("saving") || value.contains("spend");
    }
    private static boolean isPercent(String name, String value) { return name != null && name.toLowerCase().contains("margin") || value != null && value.trim().endsWith("%"); }
    private static boolean isStatus(String name) { String value = name == null ? "" : name.toLowerCase(); return value.contains("status") || value.contains("payment type"); }
    private static boolean isLongText(String name) { String value = name == null ? "" : name.toLowerCase(); return value.contains("product") || value.contains("description") || value.contains("reason") || value.contains("notes") || value.contains("customer") || value.contains("supplier"); }
    private static boolean containsLongText(List<Object> values) { return values.stream().filter(String.class::isInstance).map(String.class::cast).anyMatch(value -> value.length() > 38); }
    private static BigDecimal number(String value) { if (value == null || value.isBlank()) return null; try { return new BigDecimal(value.trim().replace("LKR", "").replace(",", "").replace("%", "").trim()); } catch (NumberFormatException ignored) { return null; } }
    private static BigDecimal numericAt(List<Object> row, int index) { if (index >= row.size() || row.get(index) == null) return BigDecimal.ZERO; Object value = row.get(index); if (value instanceof BigDecimal decimal) return decimal; if (value instanceof Number numeric) return new BigDecimal(numeric.toString()); BigDecimal parsed = number(value.toString()); return parsed == null ? BigDecimal.ZERO : parsed; }
    private static void sizeColumns(Sheet sheet, int columns) { for (int column = 0; column < columns; column++) { sheet.autoSizeColumn(column); sheet.setColumnWidth(column, Math.max(12 * 256, Math.min(34 * 256, sheet.getColumnWidth(column) + 2 * 256))); } }
    private static String sheetName(String title) { String safe = title.replaceAll("[\\\\/:?*\\[\\]]", " ").trim(); return safe.isBlank() ? "Report" : safe.substring(0, Math.min(31, safe.length())); }
    private static String normalize(String label) { return label == null ? "" : label.replaceAll("[^A-Za-z0-9]", "").toLowerCase(); }
    private static String nonBlank(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private static void merge(Sheet sheet, int firstColumn, int lastColumn, int row) { if (lastColumn > firstColumn) sheet.addMergedRegion(new CellRangeAddress(row, row, firstColumn, lastColumn)); }
    private static Cell cell(Sheet sheet, int row, int column) {
        Row target = sheet.getRow(row);
        if (target == null) target = sheet.createRow(row);
        Cell existing = target.getCell(column);
        return existing == null ? target.createCell(column) : existing;
    }

    private enum StatusPalette { LOW_STOCK(new Color(255, 239, 239)), OUT_OF_STOCK(new Color(255, 217, 217)), NEAR_EXPIRY(new Color(255, 246, 212)), EXPIRED(new Color(255, 222, 228)), VOID(new Color(255, 235, 235)); private final Color color; StatusPalette(Color color) { this.color = color; } }

    private static final class Styles {
        private final XSSFWorkbook workbook; private final CellStyle storeTitle, reportTitle, detail, tableHeader, text, wrappedText, status, money, quantity, summaryHeader, summaryLabel, summaryText, summaryQuantity, summaryPercent, summaryMoneyBase;
        private Styles(XSSFWorkbook workbook) {
            this.workbook = workbook; storeTitle = headerStyle(16); reportTitle = headerStyle(13); detail = detailStyle(); tableHeader = headerStyle(11);
            text = body(HorizontalAlignment.LEFT); wrappedText = wrapped(HorizontalAlignment.LEFT); status = body(HorizontalAlignment.CENTER); money = numeric(MONEY); quantity = numeric(QUANTITY);
            summaryHeader = headerStyle(11); summaryLabel = body(HorizontalAlignment.LEFT); summaryText = bold(body(HorizontalAlignment.RIGHT)); summaryQuantity = bold(numeric(QUANTITY)); summaryPercent = bold(numeric(PERCENT)); summaryMoneyBase = bold(numeric(MONEY));
        }
        private XSSFCellStyle headerStyle(int size) { XSSFCellStyle style = workbook.createCellStyle(); style.setFillForegroundColor(new XSSFColor(NAVY, null)); style.setFillPattern(FillPatternType.SOLID_FOREGROUND); style.setAlignment(HorizontalAlignment.LEFT); style.setVerticalAlignment(VerticalAlignment.CENTER); borders(style); Font font = workbook.createFont(); font.setBold(true); font.setFontHeightInPoints((short) size); font.setColor(IndexedColors.WHITE.getIndex()); style.setFont(font); return style; }
        private XSSFCellStyle detailStyle() { XSSFCellStyle style = body(HorizontalAlignment.LEFT); Font font = workbook.createFont(); font.setColor(IndexedColors.GREY_50_PERCENT.getIndex()); font.setItalic(true); style.setFont(font); return style; }
        private XSSFCellStyle body(HorizontalAlignment alignment) { XSSFCellStyle style = workbook.createCellStyle(); style.setAlignment(alignment); style.setVerticalAlignment(VerticalAlignment.CENTER); borders(style); return style; }
        private XSSFCellStyle wrapped(HorizontalAlignment alignment) { XSSFCellStyle style = body(alignment); style.setWrapText(true); return style; }
        private XSSFCellStyle numeric(String format) { XSSFCellStyle style = body(HorizontalAlignment.RIGHT); style.setDataFormat(workbook.createDataFormat().getFormat(format)); return style; }
        private XSSFCellStyle bold(CellStyle source) { XSSFCellStyle style = copy(source); style.setFont(boldFont(null)); return style; }
        private XSSFCellStyle alternate(CellStyle source) { return recolor(source, ALTERNATE); }
        private XSSFCellStyle status(CellStyle source, StatusPalette palette) { return recolor(source, palette.color); }
        private XSSFCellStyle profit(CellStyle source, BigDecimal value) { XSSFCellStyle style = copy(source); style.setFont(boldFont(value.signum() < 0 ? IndexedColors.RED : IndexedColors.DARK_GREEN)); return style; }
        private XSSFCellStyle date(CellStyle source) { XSSFCellStyle style = copy(source); style.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd")); return style; }
        private XSSFCellStyle dateTime(CellStyle source) { XSSFCellStyle style = copy(source); style.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd hh:mm")); return style; }
        private XSSFCellStyle percent(CellStyle source) { XSSFCellStyle style = copy(source); style.setDataFormat(workbook.createDataFormat().getFormat(PERCENT)); return style; }
        private XSSFCellStyle summaryMoney(BigDecimal value) { XSSFCellStyle style = copy(summaryMoneyBase); if (value.signum() < 0) style.setFont(boldFont(IndexedColors.RED)); return style; }
        private XSSFCellStyle recolor(CellStyle source, Color color) { XSSFCellStyle style = copy(source); style.setFillForegroundColor(new XSSFColor(color, null)); style.setFillPattern(FillPatternType.SOLID_FOREGROUND); return style; }
        private XSSFCellStyle copy(CellStyle source) { XSSFCellStyle style = workbook.createCellStyle(); style.cloneStyleFrom(source); return style; }
        private Font boldFont(IndexedColors color) { Font font = workbook.createFont(); font.setBold(true); if (color != null) font.setColor(color.getIndex()); return font; }
        private static void borders(XSSFCellStyle style) { style.setBorderTop(BorderStyle.THIN); style.setBorderBottom(BorderStyle.THIN); style.setBorderLeft(BorderStyle.THIN); style.setBorderRight(BorderStyle.THIN); }
    }
}
