package com.mycompany.easymartpos.ui;

import com.mycompany.easymartpos.model.ReportResult;
import com.mycompany.easymartpos.model.ReportMetric;
import com.mycompany.easymartpos.model.ReportType;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportExcelExporterTest {
    @Test void writesAReadableWorkbookWithNumericCountsAndAmounts() throws Exception {
        ReportResult report = new ReportResult("Inventory Overview", List.of("Metric", "Value"),
                List.of(List.of("Total Products", 5L), List.of("Total Stock Units", new BigDecimal("238.500")), List.of("Stock Cost Value", new BigDecimal("65320.00"))),
                List.of("Inventory metrics generated from active products."));
        Path file = Files.createTempFile("easymart-report-", ".xlsx");
        try {
            ReportExcelExporter.export(file, report, ReportType.INVENTORY_OVERVIEW, "As at 2026-09-08");
            try (XSSFWorkbook workbook = new XSSFWorkbook(Files.newInputStream(file))) {
                var sheet = workbook.getSheetAt(0);
                assertEquals("USHANTHA SUPER MART", sheet.getRow(0).getCell(0).getStringCellValue());
                assertEquals(CellType.NUMERIC, sheet.getRow(5).getCell(1).getCellType());
                assertEquals(5d, sheet.getRow(5).getCell(1).getNumericCellValue());
                assertTrue(sheet.getRow(7).getCell(1).getCellStyle().getDataFormatString().contains("0.00"));
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test void writesTypedSummaryAndBusinessSheetFeaturesForDetailedReports() throws Exception {
        ReportResult report = new ReportResult("Daily Sales", List.of("Invoice", "Transactions", "Net Sales", "Status"),
                List.of(List.of("INV-00042", 5L, new BigDecimal("3150.00"), "COMPLETED")),
                List.of("Rows: 1"), List.of(new ReportMetric("Transactions", "5"), new ReportMetric("Net Sales", "LKR 3,150.00")),
                List.of(), false, "");
        Path file = Files.createTempFile("easymart-daily-sales-", ".xlsx");
        try {
            ReportExcelExporter.export(file, report, ReportType.DAILY_SALES, "2026-09-08");
            try (XSSFWorkbook workbook = new XSSFWorkbook(Files.newInputStream(file))) {
                var sheet = workbook.getSheetAt(0);
                assertTrue(sheet.getCTWorksheet().isSetAutoFilter());
                assertEquals(CellType.NUMERIC, sheet.getRow(8).getCell(1).getCellType());
                assertEquals(5d, sheet.getRow(8).getCell(1).getNumericCellValue());
                assertTrue(sheet.getRow(9).getCell(1).getCellStyle().getDataFormatString().contains("0.00"));
                assertEquals((short) 1, sheet.getPrintSetup().getFitWidth());
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test void preservesBatchLevelExpiryRowsWithTypedPricesQuantitiesAndDates() throws Exception {
        ReportResult report = new ReportResult("Near Expiry Items Report",
                List.of("Batch ID", "Barcode", "Product", "Our Price", "Retail Price", "Expiry Date", "Days Left", "Remaining Qty", "Source", "Status"),
                List.of(List.of(46L, "PEPSI-001", "Pepsi", new BigDecimal("250.00"), new BigDecimal("280.00"), LocalDate.of(2026, 10, 1), 8L, new BigDecimal("3.000"), "PURCHASE", "NEAR EXPIRY")),
                List.of("Near Expiry Batches: 1"));
        Path file = Files.createTempFile("easymart-batch-expiry-", ".xlsx");
        try {
            ReportExcelExporter.export(file, report, ReportType.NEAR_EXPIRY_ITEMS, "Within 30 days");
            try (XSSFWorkbook workbook = new XSSFWorkbook(Files.newInputStream(file))) {
                var row = workbook.getSheetAt(0).getRow(5);
                assertEquals(CellType.NUMERIC, row.getCell(0).getCellType());
                assertEquals(46d, row.getCell(0).getNumericCellValue());
                assertEquals(CellType.NUMERIC, row.getCell(3).getCellType());
                assertEquals(CellType.NUMERIC, row.getCell(5).getCellType());
                assertEquals(CellType.NUMERIC, row.getCell(7).getCellType());
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
