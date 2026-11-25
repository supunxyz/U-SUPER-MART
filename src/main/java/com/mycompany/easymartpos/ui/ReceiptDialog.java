package com.mycompany.easymartpos.ui;

import com.mycompany.easymartpos.Dialogs;
import com.mycompany.easymartpos.model.ReceiptData;
import com.mycompany.easymartpos.model.ReceiptData.ReceiptItem;
import com.mycompany.easymartpos.model.ReceiptData.ReceiptPayment;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.net.URL;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.JTextArea;
import javax.imageio.ImageIO;

/** Visual 80 mm receipt preview and component-based printing. */
public final class ReceiptDialog {
    private static final Logger LOGGER = Logger.getLogger(ReceiptDialog.class.getName());
    /** Shared preview/print layout expressed directly in thermal-printer points. */
    private static final int PAPER_WIDTH = 193;
    private static final int CONTENT_WIDTH = PAPER_WIDTH;
    /** Java printing measures paper in points (1/72 inch), not Swing pixels. */
    private static final double RECEIPT_PAPER_WIDTH_POINTS = millimetresToPoints(72d);
    private static final double RECEIPT_HORIZONTAL_MARGIN_POINTS = millimetresToPoints(2d);
    private static final double RECEIPT_TOP_MARGIN_POINTS = millimetresToPoints(1d);
    private static final double RECEIPT_BOTTOM_MARGIN_POINTS = millimetresToPoints(2d);
    private static final double RECEIPT_LAYOUT_WIDTH_POINTS = RECEIPT_PAPER_WIDTH_POINTS - (2d * RECEIPT_HORIZONTAL_MARGIN_POINTS);
    /** Fixed conversion from the shared preview layout to the physical printable receipt width. */
    private static final double RECEIPT_PRINT_SCALE = RECEIPT_LAYOUT_WIDTH_POINTS / PAPER_WIDTH;
    private static final String RULE = "------------------------------------------------";
    private static final String SAVING_TITLE = "ඔබට ලැබුණු ලාභය";
    private static final Pattern ITEM_VALUES = Pattern.compile("^\\s*(.*?)\\s+\\|\\s+(\\S+)\\s+\\|\\s+(.+?)\\s+\\|\\s+(.+?)\\s+\\|\\s+(.+?)\\s*$");
    /** Logical "Dialog" cannot render Sinhala on Windows, so pick the first installed family that can. */
    private static final String RECEIPT_FONT_FAMILY = resolveReceiptFontFamily();

    private static String resolveReceiptFontFamily() {
        String sample = "ප්‍රමාණය ලාතය";
        java.util.Set<String> installed = new java.util.HashSet<>(Arrays.asList(
                java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String family : new String[] {"Iskoola Pota", "Nirmala UI", "Nirmala Text", "Noto Sans Sinhala",
                "Noto Serif Sinhala", "Sinhala MN", "Sinhala Sangam MN", "FM Malithi", "Arial Unicode MS"}) {
            if (installed.contains(family) && new Font(family, Font.PLAIN, 12).canDisplayUpTo(sample) == -1) return family;
        }
        return Font.DIALOG;
    }

    private static Font receiptFont(int style, int size) { return new Font(RECEIPT_FONT_FAMILY, style, size); }

    private static final Font TEXT = receiptFont(Font.PLAIN, 9);
    private static final Font BOLD = receiptFont(Font.BOLD, 9);
    private static final Font ITEM_TEXT = receiptFont(Font.PLAIN, 9);
    private static final Font ITEM_BOLD = receiptFont(Font.BOLD, 9);
    /** Header-only compact face keeps every Sinhala column label on one thermal-print line. */
    private static final Font ITEM_HEADER_FONT = receiptFont(Font.BOLD, 11)
            .deriveFont(java.awt.geom.AffineTransform.getScaleInstance(.50d, 1d));
    private static final Font STORE_NAME_FONT = receiptFont(Font.BOLD, 15);
    /** Retain a dense bitmap for crisp thermal thresholding; this is not its printed size. */
    private static final int THERMAL_LOGO_MAX_WIDTH = 300;
    private static final int THERMAL_LOGO_MAX_HEIGHT = 300;
    /** Physical print width in Java printer points. */
    private static final double LOGO_PRINT_WIDTH_POINTS = 95d;
    private static final int LOGO_INK_THRESHOLD = 248;
    private static final int THERMAL_BLACK_THRESHOLD = 175;
    private static final PreparedThermalLogo PREPARED_THERMAL_LOGO = loadThermalLogo();
    private static final BufferedImage THERMAL_LOGO = PREPARED_THERMAL_LOGO.image();
    /** Numeric item columns share fixed x-positions below each full-width product name. */
    private static final int[] ITEM_COLUMN_WIDTHS = {35, 53, 53, 52};
    /** Header-only visual adjustment; numeric item cells remain at their established positions. */
    private static final int[] ITEM_HEADER_RIGHT_INSETS = {0, 20, 20, 20};
    private static final int METADATA_LEFT_LABEL_WIDTH = 35;
    private static final int METADATA_LEFT_VALUE_WIDTH = 51;
    private static final int METADATA_COLUMN_GAP = 5;
    private static final int METADATA_RIGHT_LABEL_WIDTH = 43;
    private static final int METADATA_RIGHT_VALUE_WIDTH = 54;
    private static final int METADATA_RIGHT_MARGIN = 5;
    private static final int METADATA_RIGHT_GROUP_SHIFT = 15;
    private static final int METADATA_VALUE_GAP = 4;
    private static final int TIME_PERIOD_GAP = 3;
    private static final Pattern TIME_PARTS = Pattern.compile("^(\\d{2}:\\d{2})\\s*(AM|PM)$", Pattern.CASE_INSENSITIVE);

    private ReceiptDialog() { }

    public static void show(Window owner, ReceiptData receipt) {
        receipt.validateForPrint();
        JDialog dialog = new JDialog(owner, "80 mm Receipt Preview", JDialog.ModalityType.APPLICATION_MODAL);
        JPanel paper = createPaper(receipt);
        JScrollPane scroll = new JScrollPane(paper);
        scroll.setPreferredSize(new Dimension(430, 680));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        JButton print = new JButton("Print"); UITheme.stylePrimary(print);
        print.addActionListener(event -> print(dialog, paper));
        JButton close = new JButton("Close"); UITheme.styleSecondary(close);
        close.addActionListener(event -> dialog.dispose());
        JPanel buttons = new JPanel(); buttons.setBackground(UITheme.BACKGROUND);
        buttons.add(print); buttons.add(close);
        dialog.add(scroll, BorderLayout.CENTER); dialog.add(buttons, BorderLayout.SOUTH);
        dialog.pack(); dialog.setLocationRelativeTo(owner); dialog.setVisible(true);
    }

    static JPanel createPaper(ReceiptData receipt) {
        receipt.validateForPrint();
        return createPaper(receiptText(receipt));
    }

    private static JPanel createPaper(String receipt) {
        ReceiptParts parts = ReceiptParts.parse(receipt);
        JPanel paper = new JPanel();
        paper.setBackground(Color.WHITE);
        paper.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
        paper.setLayout(new BoxLayout(paper, BoxLayout.Y_AXIS));
        paper.setPreferredSize(new Dimension(PAPER_WIDTH, 1));
        paper.add(new ReceiptHeader(parts.header));
        paper.add(rule());
        paper.add(metadata(parts.details));
        paper.add(rule());
        paper.add(itemHeader());
        paper.add(rule());
        paper.add(items(parts.items));
        paper.add(rule());
        paper.add(summary(parts.totals, false));
        paper.add(rule());
        paper.add(summary(parts.grandTotal, true));
        paper.add(rule());
        paper.add(summary(parts.payment, false));
        paper.add(rule());
        paper.add(savingBox(parts.savingAmount));
        if (!parts.afterSaving.isEmpty()) {
            paper.add(Box.createVerticalStrut(8));
            paper.add(loyaltyBox(parts.afterSaving));
        }
        if (!parts.footer.isBlank()) {
            paper.add(Box.createVerticalStrut(10));
            paper.add(centered(parts.footer, BOLD));
        }
        int receiptHeight = layoutPaper(paper);
        paper.setPreferredSize(new Dimension(PAPER_WIDTH, receiptHeight));
        return paper;
    }

    private static PreparedThermalLogo loadThermalLogo() {
        URL resource = ReceiptDialog.class.getResource("/images/receipt-logo.png");
        if (resource == null) {
            LOGGER.warning("Receipt logo resource is missing: /images/receipt-logo.png");
            return new PreparedThermalLogo(null, null, 0, 0);
        }
        try {
            BufferedImage source = ImageIO.read(resource);
            if (source == null) {
                LOGGER.warning("Receipt logo resource cannot be decoded: " + resource);
                return new PreparedThermalLogo(null, resource, 0, 0);
            }
            BufferedImage cropped = cropWhiteMargins(source);
            double scale = Math.min((double) THERMAL_LOGO_MAX_WIDTH / cropped.getWidth(), (double) THERMAL_LOGO_MAX_HEIGHT / cropped.getHeight());
            int width = Math.max(1, (int) Math.round(cropped.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(cropped.getHeight() * scale));
            BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = scaled.createGraphics();
            graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, width, height);
            graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(java.awt.RenderingHints.KEY_ALPHA_INTERPOLATION, java.awt.RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
            graphics.drawImage(cropped, 0, 0, width, height, Color.WHITE, null); graphics.dispose();
            BufferedImage grayscale = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
            Graphics2D grayscaleGraphics = grayscale.createGraphics();
            grayscaleGraphics.drawImage(scaled, 0, 0, null); grayscaleGraphics.dispose();
            BufferedImage thermal = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                int grey = grayscale.getRaster().getSample(x, y, 0);
                thermal.setRGB(x, y, grey < THERMAL_BLACK_THRESHOLD ? Color.BLACK.getRGB() : Color.WHITE.getRGB());
            }
            return new PreparedThermalLogo(thermal, resource, source.getWidth(), source.getHeight());
        }
        catch (IOException exception) {
            LOGGER.warning("Receipt logo could not be loaded from " + resource + ": " + exception.getMessage());
            return new PreparedThermalLogo(null, resource, 0, 0);
        }
    }

    private record PreparedThermalLogo(BufferedImage image, URL resource, int originalWidth, int originalHeight) { }

    /** Enabled only with -Deasymart.receipt.logo.diagnostics=true; avoids production log noise. */
    private static void logThermalLogoDiagnostics(String renderer) {
        if (!Boolean.getBoolean("easymart.receipt.logo.diagnostics")) return;
        BufferedImage image = PREPARED_THERMAL_LOGO.image();
        LOGGER.info(() -> String.format(Locale.ROOT,
                "[RECEIPT LOGO] Renderer = %s; Resource = %s; Original = %dx%d; Final = %s; threshold = %d",
                renderer, PREPARED_THERMAL_LOGO.resource(), PREPARED_THERMAL_LOGO.originalWidth(),
                PREPARED_THERMAL_LOGO.originalHeight(), image == null ? "unavailable" : image.getWidth() + "x" + image.getHeight(),
                THERMAL_BLACK_THRESHOLD));
    }

    /** Removes only near-white source padding, keeping a small white safety border around the artwork. */
    private static BufferedImage cropWhiteMargins(BufferedImage source) {
        int left = source.getWidth(), right = -1, top = source.getHeight(), bottom = -1;
        for (int y = 0; y < source.getHeight(); y++) for (int x = 0; x < source.getWidth(); x++) {
            if (!isInk(source.getRGB(x, y))) continue;
            left = Math.min(left, x); right = Math.max(right, x); top = Math.min(top, y); bottom = Math.max(bottom, y);
        }
        if (right < left || bottom < top) return source;
        int padding = 3;
        left = Math.max(0, left - padding); right = Math.min(source.getWidth() - 1, right + padding);
        top = Math.max(0, top - padding); bottom = Math.min(source.getHeight() - 1, bottom + padding);
        int width = right - left + 1, height = bottom - top + 1;
        BufferedImage cropped = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = cropped.createGraphics(); graphics.drawImage(source, 0, 0, width, height, left, top, right + 1, bottom + 1, null); graphics.dispose();
        return cropped;
    }

    private static boolean isInk(int rgb) {
        int alpha = (rgb >>> 24) & 0xff;
        int red = ((rgb >>> 16) & 0xff) * alpha / 255 + (255 - alpha);
        int green = ((rgb >>> 8) & 0xff) * alpha / 255 + (255 - alpha);
        int blue = (rgb & 0xff) * alpha / 255 + (255 - alpha);
        int grey = (int) Math.round(.299d * red + .587d * green + .114d * blue);
        return grey < LOGO_INK_THRESHOLD;
    }

    /** Draws the logo and store details directly, avoiding JLabel truncation on printer graphics. */
    private static final class ReceiptHeader extends JPanel {
        private final List<String> lines;

        private ReceiptHeader(List<String> headerLines) {
            lines = headerLines.stream().map(String::trim).filter(line -> !line.isBlank()).toList();
            setBackground(Color.WHITE);
            setAlignmentX(0.5f);
            setMaximumSize(new Dimension(CONTENT_WIDTH, getPreferredSize().height));
        }

        @Override public Dimension getPreferredSize() {
            int height = 1;
            if (THERMAL_LOGO != null) height += logoHeight() + 5;
            for (int index = 0; index < lines.size(); index++) {
                Font font = index == 0 ? STORE_NAME_FONT : TEXT;
                height += getFontMetrics(font).getHeight() + 1;
            }
            return new Dimension(CONTENT_WIDTH, height + 3);
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setColor(Color.BLACK);
            int y = 0;
            if (THERMAL_LOGO != null) {
                int width = logoWidth(), height = logoHeight();
                // The processed bitmap is intentionally about 300 pixels wide, but Java printer
                // coordinates are points. Draw it at 95 points, not at native pixels.
                g.drawImage(THERMAL_LOGO, (getWidth() - width) / 2, y, width, height, Color.WHITE, null);
                y += height + 4;
            }
            for (int index = 0; index < lines.size(); index++) {
                Font font = fittingFont(g, lines.get(index), index == 0 ? STORE_NAME_FONT : TEXT, index == 0 ? 11f : 8f);
                FontMetrics metrics = g.getFontMetrics(font);
                int textWidth = metrics.stringWidth(lines.get(index));
                int x = Math.max(0, (getWidth() - textWidth) / 2);
                g.setFont(font);
                g.drawString(lines.get(index), x, y + metrics.getAscent());
                y += metrics.getHeight() + 1;
            }
            g.dispose();
        }

        private void logMetrics(Graphics2D graphics) {
            if (lines.isEmpty()) return;
            Font font = fittingFont(graphics, lines.get(0), STORE_NAME_FONT, 11f);
            int width = graphics.getFontMetrics(font).stringWidth(lines.get(0));
            LOGGER.info(() -> String.format(Locale.ROOT,
                    "Receipt header: storeNameFont=%.2f pt, storeNameWidth=%d pt, contentWidth=%d pt, logo=%dx%d layout units (%.1f pt wide)",
                    font.getSize2D(), width, CONTENT_WIDTH, logoWidth(), logoHeight(), LOGO_PRINT_WIDTH_POINTS));
        }

        private static Font fittingFont(Graphics2D graphics, String text, Font initial, float minimumSize) {
            for (float size = initial.getSize2D(); size >= minimumSize; size -= .25f) {
                Font candidate = initial.deriveFont(size);
                if (graphics.getFontMetrics(candidate).stringWidth(text) <= CONTENT_WIDTH - 4) return candidate;
            }
            return initial.deriveFont(minimumSize);
        }

        private static int logoWidth() {
            if (THERMAL_LOGO == null) return 0;
            // Header coordinates are scaled to printer points by RECEIPT_PRINT_SCALE.
            return (int) Math.round(LOGO_PRINT_WIDTH_POINTS / RECEIPT_PRINT_SCALE);
        }

        private static int logoHeight() {
            if (THERMAL_LOGO == null) return 0;
            return Math.max(1, (int) Math.round((double) logoWidth() * THERMAL_LOGO.getHeight() / THERMAL_LOGO.getWidth()));
        }
    }

    private static JPanel itemHeader() {
        return itemRow(new String[] {"භාණ්ඩය", "ප්‍රමාණය", "සිල්ලර මිල", "අපේ මිල", "වටිනාකම"}, true);
    }

    private static JPanel items(List<String> itemLines) {
        JPanel panel = vertical();
        boolean firstItem = true;
        for (String line : itemLines) {
            Matcher match = ITEM_VALUES.matcher(line);
            if (!match.matches()) continue;
            if (!firstItem) panel.add(Box.createVerticalStrut(4));
            panel.add(itemBlock(match.group(1), new String[] {match.group(2), match.group(3), match.group(4), match.group(5)}));
            firstItem = false;
        }
        panel.setMaximumSize(new Dimension(CONTENT_WIDTH, panel.getPreferredSize().height));
        return panel;
    }

    /** Compatibility adapter: the former five-column header omits its product-name heading. */
    private static JPanel itemRow(String[] values, boolean header) {
        if (header) return numericRow(Arrays.copyOfRange(values, 1, values.length), true);
        return itemBlock(values[0], Arrays.copyOfRange(values, 1, values.length));
    }

    /** Each product has a full-width bold name followed by its fixed numeric row. */
    private static JPanel itemBlock(String productName, String[] values) {
        JPanel block = vertical();
        block.add(productNameCell(productName));
        block.add(numericRow(values, false));
        block.setMaximumSize(new Dimension(CONTENT_WIDTH, block.getPreferredSize().height));
        return block;
    }

    /** Four numeric columns keep identical x-positions for the header and every product. */
    private static JPanel numericRow(String[] values, boolean header) {
        JPanel row = new JPanel(new GridBagLayout());
        row.setBackground(Color.WHITE); row.setAlignmentX(0.5f);
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridy = 0; constraints.fill = GridBagConstraints.BOTH; constraints.anchor = GridBagConstraints.NORTHWEST;
        for (int index = 0; index < values.length; index++) {
            constraints.gridx = index; constraints.weightx = 0;
            int alignment = index == 0 ? SwingConstants.CENTER : SwingConstants.RIGHT;
            if (header) row.add(itemHeaderCell(values[index], index, ITEM_COLUMN_WIDTHS[index]), constraints);
            else row.add(valueCell(values[index], alignment, index == values.length - 1, ITEM_TEXT, ITEM_COLUMN_WIDTHS[index]), constraints);
        }
        Dimension rowSize = new Dimension(CONTENT_WIDTH, header ? 20 : 18);
        row.setPreferredSize(rowSize); row.setMaximumSize(rowSize);
        return row;
    }

    /** Product names use the entire receipt width and retain their supplied spaces. */
    private static JTextArea productNameCell(String value) {
        String name = value == null ? "" : value;
        JTextArea product = new JTextArea();
        product.setText(name);
        product.setFont(ITEM_BOLD); product.setForeground(Color.BLACK); product.setBackground(Color.WHITE);
        product.setEditable(false); product.setLineWrap(true); product.setWrapStyleWord(true);
        product.setBorder(BorderFactory.createEmptyBorder()); product.setColumns(1);
        product.setSize(CONTENT_WIDTH, Short.MAX_VALUE);
        int height = Math.max(product.getFontMetrics(ITEM_BOLD).getHeight(), product.getPreferredSize().height);
        Dimension size = new Dimension(CONTENT_WIDTH, height);
        product.setMinimumSize(size); product.setPreferredSize(size); product.setMaximumSize(size);
        return product;
    }

    private static JPanel itemHeaderCell(String value, int column, int width) {
        JPanel cell = new JPanel(new BorderLayout()); cell.setBackground(Color.WHITE);
        JLabel label = new JLabel(value, SwingConstants.CENTER);
        label.setFont(ITEM_HEADER_FONT); label.setForeground(Color.BLACK); label.setVerticalAlignment(SwingConstants.CENTER);
        label.setBorder(BorderFactory.createEmptyBorder(0, ITEM_HEADER_RIGHT_INSETS[column], 0, 0));
        cell.add(label, BorderLayout.CENTER);
        Dimension size = new Dimension(width, 20); cell.setMinimumSize(size); cell.setPreferredSize(size); cell.setMaximumSize(size);
        return cell;
    }

    private static JPanel valueCell(String value, int alignment, boolean bold, Font font, int width) {
        JPanel cell = new JPanel(new BorderLayout()); cell.setBackground(Color.WHITE);
        JLabel amount = new JLabel(value); amount.setFont(bold ? font.deriveFont(Font.BOLD) : font); amount.setForeground(Color.BLACK); amount.setHorizontalAlignment(alignment); amount.setVerticalAlignment(SwingConstants.TOP);
        cell.add(amount, BorderLayout.CENTER);
        Dimension size = new Dimension(width, 18); cell.setMinimumSize(size); cell.setPreferredSize(size); cell.setMaximumSize(size);
        return cell;
    }

    private static JPanel metadata(List<String> lines) {
        JPanel panel = new JPanel(new GridBagLayout()); panel.setBackground(Color.WHITE); panel.setAlignmentX(0.5f);
        GridBagConstraints constraints = new GridBagConstraints(); constraints.insets = new Insets(1, 0, 1, 0); constraints.fill = GridBagConstraints.HORIZONTAL;
        int row = 0;
        for (String line : lines) {
            if (line.isBlank()) continue;
            String[] twoColumns = line.split(" \\| ", 2);
            if (twoColumns.length == 2) {
                constraints.gridx = 0; constraints.gridy = row++; constraints.weightx = 1; constraints.gridwidth = 4;
                panel.add(detailRow(twoColumns[0], twoColumns[1]), constraints);
                constraints.gridwidth = 1;
            }
        }
        panel.setMaximumSize(new Dimension(CONTENT_WIDTH, panel.getPreferredSize().height));
        return panel;
    }

    /** Fixed label/value fields place the right metadata column at x=182 with a 10 px safe right margin. */
    private static JPanel detailRow(String left, String right) {
        JPanel row = new JPanel(new GridBagLayout()) {
            @Override public void doLayout() {
                super.doLayout();
                Component rightGroup = getComponent(2);
                rightGroup.setLocation(rightGroup.getX() + METADATA_RIGHT_GROUP_SHIFT, rightGroup.getY());
            }
        };
        row.setBackground(Color.WHITE);
        GridBagConstraints constraints = new GridBagConstraints(); constraints.gridy = 0; constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.gridx = 0; constraints.weightx = 0;
        row.add(detailCell(left, METADATA_LEFT_LABEL_WIDTH, METADATA_LEFT_VALUE_WIDTH), constraints);
        constraints.gridx = 1;
        row.add(Box.createHorizontalStrut(METADATA_COLUMN_GAP), constraints);
        constraints.gridx = 2;
        row.add(detailCell(right, METADATA_RIGHT_LABEL_WIDTH, METADATA_RIGHT_VALUE_WIDTH), constraints);
        constraints.gridx = 3;
        row.add(Box.createHorizontalStrut(METADATA_RIGHT_MARGIN), constraints);
        Dimension size = new Dimension(CONTENT_WIDTH, 18);
        row.setMinimumSize(size); row.setPreferredSize(size); row.setMaximumSize(size);
        return row;
    }

    private static JPanel detailCell(String text, int labelWidth, int valueWidth) {
        String[] pair = text.split(" : ", 2);
        Font compact = receiptFont(Font.PLAIN, 8);
        JPanel cell = new JPanel(new GridBagLayout()); cell.setBackground(Color.WHITE);
        JLabel label = new JLabel(pair[0] + " :"); label.setFont(compact); label.setForeground(Color.BLACK);
        String valueText = pair.length == 2 ? pair[1] : "";
        JLabel value = "Time".equals(pair[0]) ? new ThermalTimeValueLabel(valueText) : new JLabel(valueText);
        value.setFont(compact); value.setForeground(Color.BLACK); value.setHorizontalAlignment(SwingConstants.LEFT);
        int valueStart = compactValueLabel(pair[0])
                ? label.getFontMetrics(compact).stringWidth(label.getText()) + METADATA_VALUE_GAP
                : labelWidth;
        Dimension labelSize = new Dimension(valueStart, 18);
        label.setMinimumSize(labelSize); label.setPreferredSize(labelSize); label.setMaximumSize(labelSize);
        Dimension valueSize = new Dimension(valueWidth, 18);
        value.setMinimumSize(valueSize); value.setPreferredSize(valueSize); value.setMaximumSize(valueSize);
        GridBagConstraints constraints = new GridBagConstraints(); constraints.gridy = 0; constraints.fill = GridBagConstraints.NONE; constraints.anchor = GridBagConstraints.WEST;
        constraints.gridx = 0; cell.add(label, constraints);
        constraints.gridx = 1;
        if ("Date".equals(pair[0])) {
            constraints.weightx = 1;
            constraints.fill = GridBagConstraints.HORIZONTAL;
        }
        cell.add(value, constraints);
        Dimension size = new Dimension(labelWidth + valueWidth, 18);
        cell.setMinimumSize(size); cell.setPreferredSize(size); cell.setMaximumSize(size);
        return cell;
    }

    /** Prints the time and AM/PM separately so thermal output has a fixed visible period gap. */
    private static final class ThermalTimeValueLabel extends JLabel {
        private final String timePart;
        private final String periodPart;

        private ThermalTimeValueLabel(String value) {
            super(value);
            Matcher parts = TIME_PARTS.matcher(value);
            timePart = parts.matches() ? parts.group(1) : value;
            periodPart = parts.matches() ? parts.group(2).toUpperCase(Locale.ROOT) : "";
        }

        @Override protected void paintComponent(Graphics graphics) {
            if (periodPart.isEmpty()) {
                super.paintComponent(graphics);
                return;
            }
            Graphics2D graphics2d = (Graphics2D) graphics.create();
            graphics2d.setColor(getForeground());
            graphics2d.setFont(getFont());
            FontMetrics metrics = graphics2d.getFontMetrics();
            int baseline = getBaseline(getWidth(), getHeight());
            graphics2d.drawString(timePart, 0, baseline);
            int periodX = metrics.stringWidth(timePart) + TIME_PERIOD_GAP;
            graphics2d.drawString(periodPart, periodX, baseline);
            graphics2d.dispose();
        }
    }

    private static boolean compactValueLabel(String label) {
        return "Date".equals(label) || "Invoice".equals(label) || "Time".equals(label);
    }

    private static JPanel summary(List<String> lines, boolean grandTotal) {
        JPanel panel = vertical();
        for (String line : lines) {
            if (!line.isBlank()) panel.add(summaryRow(line, grandTotal));
        }
        panel.setMaximumSize(new Dimension(CONTENT_WIDTH, panel.getPreferredSize().height));
        return panel;
    }

    /** All amounts occupy one fixed right-hand field, keeping decimal values perfectly vertical. */
    private static JPanel summaryRow(String line, boolean grandTotal) {
        String[] pair = line.split(" : ", 2);
        Font font = grandTotal ? receiptFont(Font.BOLD, 11) : TEXT;
        JPanel row = new JPanel(new BorderLayout()); row.setBackground(Color.WHITE); row.setAlignmentX(0.5f);
        JLabel label = grandTotal ? new ThermalGrandTotalLabel(pair[0]) : new JLabel(pair[0]);
        label.setFont(font); label.setForeground(Color.BLACK);
        JLabel value = new JLabel(pair.length == 2 ? pair[1] : "", SwingConstants.RIGHT); value.setFont(font); value.setForeground(Color.BLACK);
        value.setPreferredSize(new Dimension(76, grandTotal ? 22 : 18));
        row.add(label, BorderLayout.CENTER); row.add(value, BorderLayout.EAST);
        row.setMaximumSize(new Dimension(CONTENT_WIDTH, grandTotal ? 24 : 19));
        return row;
    }

    /**
     * Printer drivers can visually collapse a JLabel's word space. Draw the two words separately
     * with exactly one measured normal-space gap while retaining the existing label bounds/font.
     */
    private static final class ThermalGrandTotalLabel extends JLabel {
        private ThermalGrandTotalLabel(String text) { super(text); setOpaque(false); }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setColor(getForeground()); g.setFont(getFont());
            FontMetrics metrics = g.getFontMetrics();
            int baseline = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
            g.drawString("GRAND", 0, baseline);
            int totalX = metrics.stringWidth("GRAND") + Math.max(metrics.charWidth(' '), 1);
            g.drawString("TOTAL", totalX, baseline);
            g.dispose();
        }
    }

    private static JPanel savingBox(String amount) {
        FlowLayout layout = new FlowLayout(FlowLayout.CENTER, 5, 0);
        layout.setAlignOnBaseline(true);
        JPanel box = new JPanel(layout);
        box.setBackground(Color.WHITE); box.setAlignmentX(0.5f);
        box.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.BLACK, 2), BorderFactory.createEmptyBorder(11, 10, 11, 10)));
        box.setMaximumSize(new Dimension(CONTENT_WIDTH - 4, 52));
        JLabel title = new JLabel(SAVING_TITLE + " රුපියල්"); title.setFont(receiptFont(Font.BOLD, 16)
                .deriveFont(java.awt.geom.AffineTransform.getScaleInstance(.50d, 1d)));
        JLabel value = new JLabel(amount.isBlank() ? "0.00" : amount); value.setFont(receiptFont(Font.BOLD, 12));
        title.setForeground(Color.BLACK); value.setForeground(Color.BLACK);
        box.add(title); box.add(value);
        return box;
    }

    private static JPanel loyaltyBox(List<String> lines) {
        JPanel box = vertical();
        box.setBackground(Color.WHITE); box.setAlignmentX(0.5f);
        box.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.BLACK, 1), BorderFactory.createEmptyBorder(9, 10, 9, 10)));
        box.setMaximumSize(new Dimension(CONTENT_WIDTH - 4, 180));
        for (String line : lines) {
            if (line.isBlank()) continue;
            if (line.startsWith("*** LOYALTY DETAILS")) box.add(centered("LOYALTY DETAILS", BOLD));
            else box.add(loyaltyLine(line));
        }
        return box;
    }

    private static JPanel loyaltyLine(String line) {
        String[] pair = line.split(" : ", 2);
        JPanel row = new JPanel(new BorderLayout(8, 0)); row.setBackground(Color.WHITE); row.setAlignmentX(0.5f);
        JLabel label = new JLabel(pair[0]); label.setFont(TEXT); label.setForeground(Color.BLACK);
        label.setPreferredSize(new Dimension(82, 18));
        JLabel value = new JLabel(pair.length == 2 ? pair[1] : ""); value.setFont(TEXT); value.setForeground(Color.BLACK);
        row.add(label, BorderLayout.WEST); row.add(value, BorderLayout.CENTER);
        row.setMaximumSize(new Dimension(CONTENT_WIDTH - 8, 19));
        return row;
    }

    private static JLabel centered(String text, Font font) {
        JLabel label = new JLabel(text, SwingConstants.CENTER); label.setFont(font); label.setForeground(Color.BLACK);
        label.setAlignmentX(0.5f); label.setMaximumSize(new Dimension(CONTENT_WIDTH, label.getPreferredSize().height));
        return label;
    }
    private static JPanel vertical() { JPanel panel = new JPanel(); panel.setBackground(Color.WHITE); panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS)); panel.setAlignmentX(0.5f); return panel; }
    private static JPanel rule() { JPanel rule = new JPanel(); rule.setBackground(Color.WHITE); rule.setAlignmentX(0.5f); rule.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Color.DARK_GRAY)); rule.setPreferredSize(new Dimension(CONTENT_WIDTH, 6)); rule.setMaximumSize(new Dimension(CONTENT_WIDTH, 6)); return rule; }

    private static void print(JDialog dialog, JPanel paper) {
        PrinterJob job = PrinterJob.getPrinterJob(); job.setJobName("EasyMart 80mm Receipt");
        try {
            // The printer must be chosen first: a page validated for the default printer can be wrong for the one selected here.
            if (!job.printDialog()) return;
            ReceiptPrintPlan plan = prepareReceiptPrintPlan(job, paper);
            job.setPrintable((graphics, format, page) -> printPaper(paper, plan, graphics, format, page), plan.validatedFormat());
            job.print();
        }
        catch (PrinterException exception) { Dialogs.error(dialog, "Print", "Print failed: " + exception.getMessage()); }
    }

    /** Sends the same finalized 80 mm layout to the configured default printer without opening a preview. */
    public static void printSilently(ReceiptData receipt) throws PrinterException {
        receipt.validateForPrint();
        JPanel paper = createPaper(receipt);
        PrinterJob job = PrinterJob.getPrinterJob(); job.setJobName("EasyMart 80mm Receipt");
        ReceiptPrintPlan plan = prepareReceiptPrintPlan(job, paper);
        job.setPrintable((graphics, format, page) -> printPaper(paper, plan, graphics, format, page), plan.validatedFormat());
        job.print();
    }

    private record ReceiptPrintPlan(int layoutHeight, PageFormat validatedFormat) { }

    private static ReceiptPrintPlan prepareReceiptPrintPlan(PrinterJob job, JPanel paper) throws PrinterException {
        int layoutHeight = layoutPaper(paper);
        PageFormat requestedFormat = receiptPageFormat(layoutHeight);
        PageFormat validatedFormat = job.validatePage(requestedFormat);
        if (validatedFormat.getHeight() > requestedFormat.getHeight() + 1d) {
            // The receipt is only as long as its content when the driver accepts the requested custom roll length.
            String printer = job.getPrintService() == null ? "default printer" : job.getPrintService().getName();
            LOGGER.warning(() -> String.format(Locale.ROOT,
                    "Printer %s replaced the %.0f pt receipt length with %.0f pt; set the printer's paper size to 80 mm custom/roll length.",
                    printer, requestedFormat.getHeight(), validatedFormat.getHeight()));
        }
        return new ReceiptPrintPlan(layoutHeight, validatedFormat);
    }

    private static int printPaper(JPanel paper, ReceiptPrintPlan plan, java.awt.Graphics graphics, PageFormat format, int page)
            throws PrinterException {
        double printableLayoutHeight = format.getImageableHeight() / RECEIPT_PRINT_SCALE;
        if (printableLayoutHeight <= 0d) throw new PrinterException("The printer reported no printable receipt height.");
        int pageCount = Math.max(1, (int) Math.ceil(plan.layoutHeight() / printableLayoutHeight));
        if (page >= pageCount) return Printable.NO_SUCH_PAGE;
        logThermalLogoDiagnostics("com.mycompany.easymartpos.ui.ReceiptDialog.printPaper -> ReceiptHeader.paintComponent");
        layoutPaper(paper);
        Graphics2D graphics2d = (Graphics2D) graphics.create();
        graphics2d.setColor(Color.BLACK);
        double horizontalInset = Math.max(0d, (format.getImageableWidth() - RECEIPT_LAYOUT_WIDTH_POINTS) / 2d);
        graphics2d.translate(format.getImageableX() + horizontalInset, format.getImageableY());
        // This is a fixed layout-pixels-to-printer-points conversion, never a fit/shrink-to-page calculation.
        graphics2d.scale(RECEIPT_PRINT_SCALE, RECEIPT_PRINT_SCALE);
        graphics2d.translate(0d, -page * printableLayoutHeight);
        logPrintGeometry(format);
        for (Component component : paper.getComponents()) if (component instanceof ReceiptHeader header) header.logMetrics(graphics2d);
        paper.printAll(graphics2d); graphics2d.dispose(); return Printable.PAGE_EXISTS;
    }

    /** Test adapter for rendering an already constructed PageFormat without submitting a printer job. */
    private static int printPaper(JPanel paper, java.awt.Graphics graphics, PageFormat format, int page) throws PrinterException {
        int layoutHeight = layoutPaper(paper);
        ReceiptPrintPlan plan = new ReceiptPrintPlan(layoutHeight, format);
        return printPaper(paper, plan, graphics, format, page);
    }

    static int layoutPaper(JPanel paper) {
        Insets insets = paper.getInsets();
        int height = insets.top + insets.bottom;
        for (Component component : paper.getComponents()) height += component.getPreferredSize().height;
        height = Math.max(1, height);
        paper.setSize(PAPER_WIDTH, height);
        layoutComponentTree(paper);
        return height;
    }

    /** Printer graphics do not realize nested Swing components; lay them all out before printAll. */
    private static void layoutComponentTree(java.awt.Container container) {
        container.doLayout();
        for (Component component : container.getComponents()) {
            if (component instanceof java.awt.Container child) layoutComponentTree(child);
        }
    }

    /** Builds a receipt-sized PageFormat instead of allowing the job default (often A4/Letter) to reach the driver. */
    static PageFormat receiptPageFormat(int receiptLayoutHeight) {
        double paperHeight = calculateReceiptPaperHeight(receiptLayoutHeight);
        Paper paper = new Paper();
        paper.setSize(RECEIPT_PAPER_WIDTH_POINTS, paperHeight);
        paper.setImageableArea(RECEIPT_HORIZONTAL_MARGIN_POINTS, RECEIPT_TOP_MARGIN_POINTS,
                RECEIPT_LAYOUT_WIDTH_POINTS, paperHeight - RECEIPT_TOP_MARGIN_POINTS - RECEIPT_BOTTOM_MARGIN_POINTS);
        PageFormat format = new PageFormat();
        format.setOrientation(PageFormat.PORTRAIT);
        format.setPaper(paper);
        return format;
    }

    private static double calculateReceiptPaperHeight(int receiptLayoutHeight) {
        return Math.max(millimetresToPoints(40d), receiptLayoutHeight * RECEIPT_PRINT_SCALE
                + RECEIPT_TOP_MARGIN_POINTS + RECEIPT_BOTTOM_MARGIN_POINTS);
    }

    private static void logPrintGeometry(PageFormat format) {
        LOGGER.info(() -> String.format(Locale.ROOT,
                "Receipt print geometry: paperWidth=%.2f pt, imageableWidth=%.2f pt, receiptLayoutWidth=%.2f pt, graphicsScale=%.4f",
                format.getWidth(), format.getImageableWidth(), RECEIPT_LAYOUT_WIDTH_POINTS, RECEIPT_PRINT_SCALE));
    }

    private static double millimetresToPoints(double millimetres) { return millimetres * 72d / 25.4d; }

    /** Converts one saved-sale snapshot into the existing preview/thermal layout sections. */
    private static String receiptText(ReceiptData receipt) {
        StringBuilder text = new StringBuilder();
        centeredLine(text, "USHANTHA SUPER MART");
        centeredLine(text, "No. 236/2/A, Padukka Road, Handapangoda.");
        centeredLine(text, "Tel. 071 489 8169");
        text.append(RULE).append('\n');
        text.append("Cashier : ").append(receipt.cashier()).append(" | Invoice : ").append(receipt.invoiceNumber()).append('\n');
        text.append("Date : ").append(receipt.saleDateTime().toLocalDate()).append(" | Time : ")
                .append(receiptTime(receipt.saleDateTime())).append('\n');
        text.append(RULE).append('\n');
        text.append("Item | Qty | Retail | Our Price | Amount\n").append(RULE).append('\n');
        for (ReceiptItem item : receipt.items()) {
            text.append(itemName(item.productName())).append(" | ").append(quantity(item.quantity())).append(" | ")
                    .append(amount(item.retailPrice())).append(" | ").append(amount(normalUnitPrice(item))).append(" | ")
                    .append(amount(normalItemValue(item))).append('\n');
        }
        text.append(RULE).append('\n');
        appendTotals(text, receipt);
        text.append(RULE).append('\n'); amountLine(text, "GRAND TOTAL", receipt.grandTotal()); text.append(RULE).append('\n');
        appendPayments(text, receipt.payments());
        text.append("Items : ").append(quantity(receipt.totalQuantity())).append('\n').append(RULE).append('\n');
        text.append(SAVING_TITLE).append('\n').append(amount(receipt.saving())).append('\n');
        if (receipt.hasLoyaltyDetails()) text.append("\n*** LOYALTY DETAILS ***\nCUSTOMER NAME : ").append(receipt.customerName())
                .append('\n').append("CURRENT POINTS : ").append(receipt.loyaltyPoints()).append('\n');
        text.append('\n'); centeredLine(text, "Thank You. Please Come Again...");
        return text.toString();
    }

    /** Keeps a single ordinary space between the minutes and the saved sale's AM/PM marker. */
    private static String receiptTime(java.time.LocalDateTime saleDateTime) {
        String formattedTime = saleDateTime.format(java.time.format.DateTimeFormatter.ofPattern("hh:mm a"));
        return formattedTime.replaceAll("(?i)\\s*(AM|PM)$", " $1");
    }

    /** Renders the sale snapshot as normal-price subtotal, then each actual discount once. */
    private static void appendTotals(StringBuilder text, ReceiptData receipt) {
        ReceiptTotals totals = ReceiptTotals.from(receipt.items());
        amountLine(text, "Subtotal", totals.normalSubtotal());
        if (totals.promotionDiscount().signum() > 0)
            amountLine(text, "Promotion Saving", totals.promotionDiscount().negate());
        if (receipt.discount().signum() > 0)
            amountLine(text, "Manual Discount", receipt.discount().negate());
    }

    /** Uses only saved item values, including the normal-price snapshot where it exists. */
    private record ReceiptTotals(BigDecimal normalSubtotal, BigDecimal promotionDiscount) {
        private static ReceiptTotals from(List<ReceiptItem> items) {
            BigDecimal normal = BigDecimal.ZERO;
            BigDecimal finalTotal = BigDecimal.ZERO;
            for (ReceiptItem item : items) {
                BigDecimal itemFinal = value(item.itemTotal());
                // Pre-snapshot sales cannot be reconstructed from the current product price. Treat
                // their saved charged price as the only reliable normal-price value.
                BigDecimal itemNormal = normalItemValue(item);
                normal = normal.add(itemNormal);
                finalTotal = finalTotal.add(itemFinal);
            }
            BigDecimal promotion = normal.subtract(finalTotal).max(BigDecimal.ZERO);
            return new ReceiptTotals(normal.setScale(2, RoundingMode.HALF_UP), promotion.setScale(2, RoundingMode.HALF_UP));
        }

        private static BigDecimal value(BigDecimal amount) { return amount == null ? BigDecimal.ZERO : amount; }
    }

    /** Normal price/value shown in the locked receipt item columns; promotions never replace them. */
    private static BigDecimal normalUnitPrice(ReceiptItem item) {
        return item.regularUnitPrice() == null ? item.finalUnitPrice() : item.regularUnitPrice();
    }

    private static BigDecimal normalItemValue(ReceiptItem item) {
        BigDecimal unitPrice = normalUnitPrice(item);
        BigDecimal quantity = item.quantity();
        if (unitPrice == null || quantity == null) return BigDecimal.ZERO;
        return unitPrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
    }

    private static void appendPayments(StringBuilder text, List<ReceiptPayment> payments) {
        long methodCount = payments.stream().map(ReceiptPayment::method).distinct().count();
        if (methodCount == 1 && "CASH".equalsIgnoreCase(payments.get(0).method())) {
            ReceiptPayment payment = payments.get(0); text.append("Payment : CASH\n"); amountLine(text, "Tendered Cash", payment.tenderedAmount()); amountLine(text, "Change", payment.changeAmount()); return;
        }
        if (methodCount == 1 && "CARD".equalsIgnoreCase(payments.get(0).method())) { text.append("Payment : CARD\n"); return; }
        if (methodCount == 1 && "TRANSFER".equalsIgnoreCase(payments.get(0).method())) { text.append("Payment : TRANSFER\n"); return; }
        text.append("Payment : SPLIT\n");
        for (ReceiptPayment payment : payments) if (payment.amount().compareTo(BigDecimal.ZERO) != 0) amountLine(text, paymentLabel(payment.method()), payment.amount());
    }

    private static void amountLine(StringBuilder text, String label, BigDecimal value) { text.append(label).append(" : ").append(amount(value)).append('\n'); }
    private static String amount(BigDecimal value) { return value == null ? "--" : value.setScale(2, RoundingMode.HALF_UP).toPlainString(); }
    private static String quantity(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }
    private static String itemName(String value) { return value == null ? "" : value.replace('|', '/').replace('\n', ' ').replace('\r', ' '); }
    private static String dash(String value) { return blank(value) ? "--" : value; }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String paymentLabel(String method) { return switch (method == null ? "" : method.toUpperCase(Locale.ROOT)) { case "CASH" -> "Cash"; case "CARD" -> "Card"; case "TRANSFER" -> "Transfer"; default -> method; }; }
    private static void centeredLine(StringBuilder text, String value) { text.append("                ").append(value).append('\n'); }

    private record ReceiptParts(List<String> header, List<String> details, List<String> items, List<String> totals, List<String> grandTotal, List<String> payment, String savingAmount, List<String> afterSaving, String footer) {
        private static ReceiptParts parse(String receipt) {
            List<String> lines = new ArrayList<>(Arrays.asList(receipt.replace("\r", "").split("\n", -1)));
            List<Integer> rules = new ArrayList<>();
            for (int index = 0; index < lines.size(); index++) if (RULE.equals(lines.get(index))) rules.add(index);
            if (rules.size() < 7) return new ReceiptParts(lines, List.of(), List.of(), List.of(), List.of(), List.of(), "", List.of(), "");
            int saving = indexOfTrimmed(lines, SAVING_TITLE);
            String savingAmount = saving >= 0 && saving + 1 < lines.size() ? lines.get(saving + 1).trim() : "";
            int loyalty = indexOfPrefix(lines, "*** LOYALTY DETAILS", saving + 1);
            int footer = lastNonBlank(lines);
            List<String> afterSaving = loyalty >= 0 ? lines.subList(loyalty, footer) : List.of();
            return new ReceiptParts(
                    lines.subList(0, rules.get(0)),
                    lines.subList(rules.get(0) + 1, rules.get(1)),
                    lines.subList(rules.get(2) + 1, rules.get(3)),
                    lines.subList(rules.get(3) + 1, rules.get(4)),
                    lines.subList(rules.get(4) + 1, rules.get(5)),
                    lines.subList(rules.get(5) + 1, rules.get(6)),
                    savingAmount, afterSaving, footer >= 0 ? lines.get(footer).trim() : "");
        }
        private static int indexOfTrimmed(List<String> lines, String wanted) { for (int index = 0; index < lines.size(); index++) if (wanted.equals(lines.get(index).trim())) return index; return -1; }
        private static int indexOfPrefix(List<String> lines, String wanted, int start) { for (int index = Math.max(0, start); index < lines.size(); index++) if (lines.get(index).trim().startsWith(wanted)) return index; return -1; }
        private static int lastNonBlank(List<String> lines) { for (int index = lines.size() - 1; index >= 0; index--) if (!lines.get(index).isBlank()) return index; return -1; }
    }
}
