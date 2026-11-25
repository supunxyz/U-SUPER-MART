package com.mycompany.easymartpos.ui;

import com.mycompany.easymartpos.model.ReceiptData;
import com.mycompany.easymartpos.model.ReceiptData.ReceiptItem;
import com.mycompany.easymartpos.model.ReceiptData.ReceiptPayment;
import java.awt.Component;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.swing.JLabel;
import javax.swing.Box;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReceiptDialogPrintFormatTest {
    @Test void preparesALargerCrispThermalLogo() throws Exception {
        var field = ReceiptDialog.class.getDeclaredField("THERMAL_LOGO");
        field.setAccessible(true);
        BufferedImage logo = (BufferedImage) field.get(null);
        var thresholdField = ReceiptDialog.class.getDeclaredField("THERMAL_BLACK_THRESHOLD");
        thresholdField.setAccessible(true);

        assertEquals(300, logo.getWidth());
        assertTrue(logo.getHeight() >= 290 && logo.getHeight() <= 300, "logo must preserve its cropped aspect ratio");
        assertEquals(BufferedImage.TYPE_BYTE_BINARY, logo.getType());
        assertEquals(175, thresholdField.getInt(null));
        int black = 0;
        for (int y = 0; y < logo.getHeight(); y++) for (int x = 0; x < logo.getWidth(); x++) {
            int rgb = logo.getRGB(x, y) & 0x00ffffff;
            assertTrue(rgb == 0x000000 || rgb == 0xffffff, "thermal logo must not contain grey pixels");
            if (rgb == 0) black++;
        }
        assertTrue(black > 0, "thermal logo must retain its black artwork");
    }

    @Test void createsAPortrait72mmReceiptPageWithSafeMarginsAndDynamicHeight() {
        PageFormat format = ReceiptDialog.receiptPageFormat(600);

        assertEquals(PageFormat.PORTRAIT, format.getOrientation());
        assertEquals(204.09d, format.getWidth(), 0.02d);
        assertEquals(5.67d, format.getImageableX(), 0.02d);
        assertEquals(192.76d, format.getImageableWidth(), 0.03d);
        assertTrue(format.getHeight() > 600d && format.getHeight() < 620d,
                "receipt height should follow content instead of a full A4 page");
    }

    @Test void shiftsOnlyTheInvoiceAndTimeMetadataGroupFifteenPrintPointsRight() {
        JPanel paper = ReceiptDialog.createPaper(receipt(money("1020.00"), money("0.00"), money("1020.00"),
                List.of(item("1030.00", "1020.00", "1020.00"))));
        Container metadata = (Container) paper.getComponent(2);

        for (Component component : metadata.getComponents()) {
            Container row = (Container) component;
            assertEquals(0, row.getComponent(0).getX(), "Cashier/Date metadata must not move");
            assertEquals(106, row.getComponent(2).getX(), "Invoice/Time metadata must move right by 15 points");
        }
    }

    @Test void placesDateInvoiceAndTimeValuesFourPrintPointsAfterTheirLabelsOnly() {
        JPanel paper = ReceiptDialog.createPaper(receipt(money("1020.00"), money("0.00"), money("1020.00"),
                List.of(item("1030.00", "1020.00", "1020.00"))));
        Container metadata = (Container) paper.getComponent(2);

        for (Component component : metadata.getComponents()) {
            Container row = (Container) component;
            assertMetadataValueGap((Container) row.getComponent(0));
            assertMetadataValueGap((Container) row.getComponent(2));
        }
    }

    @Test void alignsTheDateLabelWithTheCashierLabelWithoutChangingItsValueGap() {
        JPanel paper = ReceiptDialog.createPaper(receipt(money("1020.00"), money("0.00"), money("1020.00"),
                List.of(item("1030.00", "1020.00", "1020.00"))));
        Container metadata = (Container) paper.getComponent(2);
        Container cashierCell = (Container) ((Container) metadata.getComponent(0)).getComponent(0);
        Container dateCell = (Container) ((Container) metadata.getComponent(1)).getComponent(0);
        JLabel cashierLabel = (JLabel) cashierCell.getComponent(0);
        JLabel dateLabel = (JLabel) dateCell.getComponent(0);
        JLabel dateValue = (JLabel) dateCell.getComponent(1);

        assertEquals(cashierLabel.getX(), dateLabel.getX());
        assertEquals(dateLabel.getX() + dateLabel.getFontMetrics(dateLabel.getFont()).stringWidth(dateLabel.getText()) + 4,
                dateValue.getX());
    }

    @Test void formatsThePrintedTimeWithExactlyOneSpaceBeforeAmOrPm() {
        ReceiptData receipt = new ReceiptData(86, "INV-00086", "Cashier A", LocalDateTime.of(2026, 9, 20, 3, 29),
                "Walk-in Customer", null, money("1020.00"), money("0.00"), money("1020.00"),
                List.of(item("1030.00", "1020.00", "1020.00")),
                List.of(new ReceiptPayment("CASH", money("1020.00"), money("1020.00"), money("0.00"), null, null, null)),
                money("10.00"), money("1"));

        List<String> text = labelsIn(ReceiptDialog.createPaper(receipt)).stream().map(JLabel::getText).toList();

        assertTrue(text.contains("03:29 AM"));
        assertFalse(text.contains("03:29AM"));
        JPanel paper = ReceiptDialog.createPaper(receipt);
        Container metadata = (Container) paper.getComponent(2);
        Container timeCell = (Container) ((Container) metadata.getComponent(1)).getComponent(2);
        assertEquals("ThermalTimeValueLabel", timeCell.getComponent(1).getClass().getSimpleName());
    }

    @Test void enlargesTheSinhalaBenefitLabelWithoutChangingTheAmountOrBoxLayout() {
        JPanel paper = ReceiptDialog.createPaper(receipt(money("1020.00"), money("0.00"), money("1020.00"),
                List.of(item("1030.00", "1020.00", "1020.00")), money("10.00"), money("1")));
        Container box = (Container) paper.getComponent(14);
        JLabel title = (JLabel) box.getComponent(0);
        JLabel amount = (JLabel) box.getComponent(1);

        assertEquals(16f, title.getFont().getSize2D());
        assertEquals(12f, amount.getFont().getSize2D(), "the benefit amount font remains unchanged");
        assertTrue(amount.getFont().isBold());
        assertEquals(189, box.getWidth());
        assertEquals(52, box.getMaximumSize().height, "the benefit box size constraint remains unchanged");
        assertTrue(((FlowLayout) box.getLayout()).getAlignOnBaseline());
        assertTrue(title.getX() + title.getWidth() + 3 <= amount.getX(), "title and amount must retain a clear gap");
        assertEquals(title.getY() + title.getBaseline(title.getWidth(), title.getHeight()),
                amount.getY() + amount.getBaseline(amount.getWidth(), amount.getHeight()),
                "title and amount must share one visual baseline");
    }

    @Test void laysOutAndRendersEverySavedDynamicReceiptSection() throws Exception {
        ReceiptData receipt = new ReceiptData(81, "INV-00081", "Cashier A", LocalDateTime.of(2026, 9, 18, 10, 30),
                "Registered Customer", 120, money("800.00"), money("0.00"), money("750.00"),
                List.of(new ReceiptItem("Promoted Vanilla Ice Cream 2L", money("1"), money("500.00"), money("500.00"), money("450.00"), money("450.00")),
                        new ReceiptItem("Normal Milk Powder", money("2"), money("175.00"), money("150.00"), money("150.00"), money("300.00"))),
                List.of(new ReceiptPayment("CASH", money("300.00"), money("300.00"), money("0.00"), null, null, null),
                        new ReceiptPayment("CARD", money("450.00"), null, money("0.00"), "CARD-22", null, null)),
                money("50.00"), money("3"));

        JPanel paper = ReceiptDialog.createPaper(receipt);
        int height = ReceiptDialog.layoutPaper(paper);
        List<String> text = textIn(paper);

        assertTrue(height > 300, "dynamic sections must contribute to physical receipt height");
        assertTrue(text.contains("Cashier A"));
        assertTrue(text.contains("INV-00081"));
        String allText = String.join("\n", text);
        assertTrue(allText.contains("Promoted") && allText.contains("Ice Cream"));
        assertTrue(allText.contains("Normal") && allText.contains("Milk") && allText.contains("Powder"));
        assertTrue(text.contains("500.00"));
        assertFalse(text.contains("CARD-22"), "split receipts must not print saved card references");
        List<JTextArea> productNames = textAreasIn(paper);
        assertEquals(List.of("Promoted Vanilla Ice Cream 2L", "Normal Milk Powder"),
                productNames.stream().map(JTextArea::getText).collect(Collectors.toList()));
        assertTrue(productNames.stream().allMatch(name -> name.getWidth() == 193 && name.getFont().isBold()),
                "each product name must be a full-width bold line");
        assertAlignedNumericRows(productNames);
        assertReadableSingleLineItemHeaders(paper);
        assertPhysicalGrandTotalLabel(paper, height);
        assertLaidOut(paper);
    }

    @Test void rendersRetailPriceOrOnlyTheMissingValueMarker() {
        ReceiptData receipt = new ReceiptData(82, "INV-00082", "Cashier A", LocalDateTime.of(2026, 9, 18, 10, 30),
                "Walk-in Customer", null, money("2468.00"), money("0.00"), money("2468.00"),
                List.of(new ReceiptItem("Savoury Nuts", money("3"), money("550.00"), money("410.00"), money("410.00"), money("1230.00")),
                        new ReceiptItem("Magic Vanilla Ice Cream 2L", money("1"), money("1030.00"), money("1020.00"), money("918.00"), money("918.00")),
                        new ReceiptItem("No Retail Product", money("1"), null, money("320.00"), money("320.00"), money("320.00"))),
                List.of(new ReceiptPayment("CASH", money("2468.00"), money("2468.00"), money("0.00"), null, null, null)),
                money("102.00"), money("5"));

        JPanel paper = ReceiptDialog.createPaper(receipt);
        ReceiptDialog.layoutPaper(paper);
        List<String> text = textIn(paper);

        assertTrue(text.contains("550.00"));
        assertEquals("1030.00", numericValue(textAreasIn(paper).get(1), 1),
                "a non-null retail price must reach the physical receipt layout");
        assertTrue(text.contains("1020.00"));
        assertTrue(text.contains("--"));
    }

    @Test void printsOnlyTheCardPaymentMethodNotSavedCardDetails() {
        List<String> text = textIn(ReceiptDialog.createPaper(paymentReceipt(List.of(
                new ReceiptPayment("CARD", money("1000.00"), null, money("0.00"), "CARD-PRIVATE", null, "CARD-BANK")))));

        assertTrue(text.contains("Payment") && text.contains("CARD"));
        assertFalse(text.contains("CARD-PRIVATE"));
        assertFalse(text.contains("CARD-BANK"));
    }

    @Test void printsOnlyTheTransferPaymentMethodNotSavedTransferDetails() {
        List<String> text = textIn(ReceiptDialog.createPaper(paymentReceipt(List.of(
                new ReceiptPayment("TRANSFER", money("1000.00"), null, money("0.00"), null, "TRANSFER-PRIVATE", "TRANSFER-BANK")))));

        assertTrue(text.contains("Payment") && text.contains("TRANSFER"));
        assertFalse(text.contains("TRANSFER-PRIVATE"));
        assertFalse(text.contains("TRANSFER-BANK"));
    }

    @Test void printsOnlySplitAmountsNotSavedCardOrTransferDetails() {
        List<String> text = textIn(ReceiptDialog.createPaper(paymentReceipt(List.of(
                new ReceiptPayment("CASH", money("500.00"), money("500.00"), money("0.00"), null, null, null),
                new ReceiptPayment("CARD", money("300.00"), null, money("0.00"), "CARD-PRIVATE", null, "CARD-BANK"),
                new ReceiptPayment("TRANSFER", money("200.00"), null, money("0.00"), null, "TRANSFER-PRIVATE", "TRANSFER-BANK")))));

        assertTrue(text.contains("SPLIT"));
        assertTrue(text.contains("500.00") && text.contains("300.00") && text.contains("200.00"));
        assertFalse(text.contains("CARD-PRIVATE") || text.contains("TRANSFER-PRIVATE") || text.contains("CARD-BANK") || text.contains("TRANSFER-BANK"));
    }

    @Test void showsOnlyTheSavedNormalSubtotalWhenThereIsNoPromotion() {
        Map<String, String> totals = summaryPairs(receipt(money("1020.00"), money("0.00"), money("1020.00"),
                List.of(item("1020.00", "1020.00", "1020.00"))));

        assertEquals(Map.of("Subtotal", "1020.00", "GRAND TOTAL", "1020.00"), totals);
    }

    @Test void showsOnePromotionAsOneSeparateDiscount() {
        Map<String, String> totals = summaryPairs(receipt(money("816.00"), money("0.00"), money("816.00"),
                List.of(item("1030.00", "1020.00", "816.00"))));

        assertEquals("1020.00", totals.get("Subtotal"));
        assertEquals("-204.00", totals.get("Promotion Saving"));
        assertEquals("816.00", totals.get("GRAND TOTAL"));
        assertEquals(3, totals.size());
    }

    @Test void aggregatesOnePromotionDiscountAcrossAllProducts() {
        Map<String, String> totals = summaryPairs(receipt(money("1316.00"), money("0.00"), money("1316.00"), List.of(
                item("500.00", "500.00", "400.00"), item("1020.00", "1020.00", "816.00"), item("150.00", "150.00", "100.00"))));

        assertEquals("1670.00", totals.get("Subtotal"));
        assertEquals("-354.00", totals.get("Promotion Saving"));
        assertEquals("1316.00", totals.get("GRAND TOTAL"));
        assertEquals(3, totals.size(), "the summary must contain one aggregate promotion line");
    }

    @Test void showsPromotionAndManualDiscountSeparatelyWithoutDoubleSubtracting() {
        Map<String, String> totals = summaryPairs(receipt(money("4550.00"), money("100.00"), money("4450.00"),
                List.of(item("5200.00", "5200.00", "4550.00"))));

        assertEquals("5200.00", totals.get("Subtotal"));
        assertEquals("-650.00", totals.get("Promotion Saving"));
        assertEquals("-100.00", totals.get("Manual Discount"));
        assertEquals("4450.00", totals.get("GRAND TOTAL"));
    }

    @Test void printsNormalOurPriceAndValueWhileShowingPromotionSeparately() {
        ReceiptData receipt = receipt(money("816.00"), money("0.00"), money("816.00"),
                List.of(item("1030.00", "1020.00", "816.00")), money("214.00"), money("1"));
        JPanel paper = ReceiptDialog.createPaper(receipt);
        List<JTextArea> items = textAreasIn(paper);
        Map<String, String> totals = summaryPairs(receipt);

        assertEquals("1", numericValue(items.get(0), 0));
        assertEquals("1030.00", numericValue(items.get(0), 1));
        assertEquals("1020.00", numericValue(items.get(0), 2));
        assertEquals("1020.00", numericValue(items.get(0), 3));
        assertEquals("1020.00", totals.get("Subtotal"));
        assertEquals("-204.00", totals.get("Promotion Saving"));
        assertEquals("816.00", totals.get("GRAND TOTAL"));
        assertEquals("214.00", savingValue(paper));
    }

    @Test void calculatesNormalItemValuesAndBenefitAcrossMixedQuantities() {
        ReceiptData receipt = receipt(money("2396.00"), money("0.00"), money("2396.00"), List.of(
                item("550.00", "410.00", "410.00", "3"),
                item("400.00", "350.00", "350.00", "1"),
                item("1030.00", "1020.00", "816.00", "1")), money("684.00"), money("5"));
        JPanel paper = ReceiptDialog.createPaper(receipt);
        List<JTextArea> items = textAreasIn(paper);
        Map<String, String> totals = summaryPairs(receipt);

        assertEquals("1230.00", numericValue(items.get(0), 3));
        assertEquals("350.00", numericValue(items.get(1), 3));
        assertEquals("1020.00", numericValue(items.get(2), 3));
        assertEquals("2600.00", totals.get("Subtotal"));
        assertEquals("-204.00", totals.get("Promotion Saving"));
        assertEquals("2396.00", totals.get("GRAND TOTAL"));
        assertEquals("684.00", savingValue(paper));
    }

    @Test void continuesALongReceiptWhenTheDriverLimitsThePhysicalPageHeight() throws Exception {
        List<ReceiptItem> items = new ArrayList<>();
        for (int index = 1; index <= 10; index++) {
            items.add(new ReceiptItem("Long wrapped product " + index + " with enough descriptive wording to exercise every line of a constrained thermal receipt page",
                    money("1"), money("120.00"), money("100.00"), index == 1 ? money("80.00") : money("100.00"),
                    index == 1 ? money("80.00") : money("100.00")));
        }
        ReceiptData receipt = new ReceiptData(83, "INV-00083", "Cashier A", LocalDateTime.of(2026, 9, 18, 10, 30),
                "Registered Customer", 120, money("980.00"), money("20.00"), money("960.00"), items,
                List.of(new ReceiptPayment("CASH", money("960.00"), money("1050.00"), money("90.00"), null, null, null)),
                money("240.00"), money("10"));

        JPanel paper = ReceiptDialog.createPaper(receipt);
        int receiptHeight = ReceiptDialog.layoutPaper(paper);
        List<JTextArea> productNames = textAreasIn(paper);
        Map<String, String> totals = summaryPairs(receipt);
        assertEquals(10, productNames.size());
        for (JTextArea product : productNames) {
            assertEquals("120.00", numericValue(product, 1));
            assertEquals("100.00", numericValue(product, 2));
            assertEquals("100.00", numericValue(product, 3));
        }
        assertEquals("1000.00", totals.get("Subtotal"));
        assertEquals("-20.00", totals.get("Promotion Saving"));
        assertEquals("-20.00", totals.get("Manual Discount"));
        assertEquals("960.00", totals.get("GRAND TOTAL"));
        assertEquals("240.00", savingValue(paper));
        PageFormat requested = ReceiptDialog.receiptPageFormat(receiptHeight);
        double constrainedHeight = 180d * 72d / 25.4d;
        Paper constrainedPaper = new Paper();
        constrainedPaper.setSize(requested.getWidth(), constrainedHeight);
        constrainedPaper.setImageableArea(requested.getImageableX(), requested.getImageableY(), requested.getImageableWidth(),
                constrainedHeight - requested.getImageableY() - (2d * 72d / 25.4d));
        PageFormat constrained = new PageFormat();
        constrained.setOrientation(PageFormat.PORTRAIT); constrained.setPaper(constrainedPaper);

        var printPaper = ReceiptDialog.class.getDeclaredMethod("printPaper", JPanel.class, java.awt.Graphics.class, PageFormat.class, int.class);
        printPaper.setAccessible(true);
        int pages = 0;
        while ((int) printPaper.invoke(null, paper, new BufferedImage(240, 600, BufferedImage.TYPE_INT_RGB).createGraphics(), constrained, pages)
                == Printable.PAGE_EXISTS) pages++;

        assertTrue(pages > 1, "a driver-limited thermal form must continue long receipts instead of clipping them");
    }

    private static BigDecimal money(String value) { return new BigDecimal(value); }

    private static ReceiptData receipt(BigDecimal subtotal, BigDecimal discount, BigDecimal grandTotal, List<ReceiptItem> items) {
        return receipt(subtotal, discount, grandTotal, items, money("0.00"), money("1"));
    }

    private static ReceiptData paymentReceipt(List<ReceiptPayment> payments) {
        return new ReceiptData(85, "INV-00085", "Cashier A", LocalDateTime.of(2026, 9, 18, 10, 30),
                "Walk-in Customer", null, money("1000.00"), money("0.00"), money("1000.00"),
                List.of(item("1000.00", "1000.00", "1000.00")), payments, money("0.00"), money("1"));
    }

    private static ReceiptData receipt(BigDecimal subtotal, BigDecimal discount, BigDecimal grandTotal, List<ReceiptItem> items,
            BigDecimal saving, BigDecimal totalQuantity) {
        return new ReceiptData(84, "INV-00084", "Cashier A", LocalDateTime.of(2026, 9, 18, 10, 30),
                "Walk-in Customer", null, subtotal, discount, grandTotal, items,
                List.of(new ReceiptPayment("CASH", grandTotal, grandTotal, money("0.00"), null, null, null)), saving, totalQuantity);
    }

    private static ReceiptItem item(String retail, String regular, String finalPrice) {
        return item(retail, regular, finalPrice, "1");
    }

    private static ReceiptItem item(String retail, String regular, String finalPrice, String quantity) {
        BigDecimal qty = money(quantity);
        BigDecimal finalAmount = money(finalPrice);
        return new ReceiptItem("Item", qty, money(retail), money(regular), finalAmount, finalAmount.multiply(qty));
    }

    private static Map<String, String> summaryPairs(ReceiptData receipt) {
        JPanel paper = ReceiptDialog.createPaper(receipt);
        Map<String, String> pairs = new LinkedHashMap<>();
        for (int section : List.of(8, 10)) {
            Container panel = (Container) paper.getComponent(section);
            for (Component row : panel.getComponents()) {
                Container summaryRow = (Container) row;
                pairs.put(((JLabel) summaryRow.getComponent(0)).getText(), ((JLabel) summaryRow.getComponent(1)).getText());
            }
        }
        return pairs;
    }

    private static List<String> textIn(Container container) {
        List<String> text = new ArrayList<>();
        for (Component component : container.getComponents()) {
            if (component instanceof JLabel label) text.add(label.getText());
            if (component instanceof JTextArea area) text.add(area.getText());
            if (component instanceof Container child) text.addAll(textIn(child));
        }
        return text;
    }

    private static List<JTextArea> textAreasIn(Container container) {
        List<JTextArea> areas = new ArrayList<>();
        for (Component component : container.getComponents()) {
            if (component instanceof JTextArea area) areas.add(area);
            if (component instanceof Container child) areas.addAll(textAreasIn(child));
        }
        return areas;
    }

    private static void assertAlignedNumericRows(List<JTextArea> productNames) {
        List<Integer> first = numericXPositions(productNames.get(0));
        assertEquals(4, first.size(), "one four-column header/row format is required");
        for (JTextArea name : productNames) assertEquals(first, numericXPositions(name));
    }

    private static List<Integer> numericXPositions(JTextArea productName) {
        Container block = productName.getParent();
        Container numericRow = (Container) block.getComponent(1);
        List<Integer> positions = new ArrayList<>();
        for (Component cell : numericRow.getComponents()) positions.add(cell.getX());
        return positions;
    }

    private static String numericValue(JTextArea productName, int column) {
        Container block = productName.getParent();
        Container numericRow = (Container) block.getComponent(1);
        Container cell = (Container) numericRow.getComponent(column);
        return ((JLabel) cell.getComponent(0)).getText();
    }

    private static void assertMetadataValueGap(Container cell) {
        JLabel label = (JLabel) cell.getComponent(0);
        JLabel value = (JLabel) cell.getComponent(1);
        if (List.of("Date :", "Invoice :", "Time :").contains(label.getText())) {
            int expected = label.getX() + label.getFontMetrics(label.getFont()).stringWidth(label.getText()) + 4;
            assertEquals(expected, value.getX(), label.getText() + " value gap must be 4 print points");
        } else if ("Cashier :".equals(label.getText())) {
            assertEquals(35, value.getX(), "Cashier spacing must remain unchanged");
        }
    }

    private static String savingValue(JPanel paper) {
        Container box = (Container) paper.getComponent(14);
        return ((JLabel) box.getComponent(1)).getText();
    }

    private static void assertReadableSingleLineItemHeaders(JPanel paper) {
        Container headerRow = (Container) paper.getComponent(4);
        assertEquals(4, headerRow.getComponentCount());
        int column = 0;
        for (Component cell : headerRow.getComponents()) {
            JLabel header = (JLabel) ((Container) cell).getComponent(0);
            assertTrue(header.getFont().isBold());
            assertEquals(11f, header.getFont().getSize2D());
            assertTrue(header.getPreferredSize().width <= cell.getWidth(), "header must fit its assigned column");
            assertEquals(column++ == 0 ? 0 : 20, header.getInsets().left);
        }
    }

    private static void assertPhysicalGrandTotalLabel(JPanel paper, int height) throws Exception {
        JLabel label = labelsIn(paper).stream().filter(candidate -> "GRAND TOTAL".equals(candidate.getText())).findFirst().orElseThrow();
        assertEquals("ThermalGrandTotalLabel", label.getClass().getSimpleName());
        var printPaper = ReceiptDialog.class.getDeclaredMethod("printPaper", JPanel.class, java.awt.Graphics.class, PageFormat.class, int.class);
        printPaper.setAccessible(true);
        BufferedImage rendered = new BufferedImage(240, height + 30, BufferedImage.TYPE_INT_RGB);
        assertEquals(Printable.PAGE_EXISTS, printPaper.invoke(null, paper, rendered.createGraphics(), ReceiptDialog.receiptPageFormat(height), 0));
    }

    private static List<JLabel> labelsIn(Container container) {
        List<JLabel> labels = new ArrayList<>();
        for (Component component : container.getComponents()) {
            if (component instanceof JLabel label) labels.add(label);
            if (component instanceof Container child) labels.addAll(labelsIn(child));
        }
        return labels;
    }

    private static void assertLaidOut(Container container) {
        for (Component component : container.getComponents()) {
            if (component instanceof Box.Filler) continue;
            assertTrue(component.getWidth() > 0 && component.getHeight() > 0,
                    () -> component.getClass().getSimpleName() + " must have physical print bounds");
            if (component instanceof Container child) assertLaidOut(child);
        }
    }
}
