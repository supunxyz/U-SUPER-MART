package com.mycompany.easymartpos.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;
import javax.swing.table.DefaultTableCellRenderer;

/** Shared, lightweight visual language for EasyMartPOS Swing screens. */
public final class UITheme {
    public static final Color NAVY = new Color(15, 38, 64);
    public static final Color NAVY_LIGHT = new Color(28, 59, 91);
    public static final Color BLUE = new Color(33, 111, 180);
    public static final Color BLUE_DARK = new Color(23, 84, 142);
    public static final Color GREEN = new Color(32, 139, 92);
    public static final Color RED = new Color(201, 68, 68);
    public static final Color GREY = new Color(100, 112, 125);
    public static final Color BACKGROUND = new Color(244, 247, 250);
    public static final Color CARD = Color.WHITE;
    public static final Color BORDER = new Color(218, 226, 234);
    public static final Font BODY = new Font("Dialog", Font.PLAIN, 13);
    public static final Font HEADING = new Font("Dialog", Font.BOLD, 22);

    private UITheme() { }

    /** Installs application-wide defaults before the first frame is created. */
    public static void install() {
        UIManager.put("Panel.background", BACKGROUND);
        UIManager.put("Label.font", BODY);
        UIManager.put("Button.font", new Font("Dialog", Font.BOLD, 13));
        UIManager.put("Button.focus", NAVY);
        UIManager.put("Button.background", BLUE);
        UIManager.put("Button.foreground", Color.WHITE);
        UIManager.put("TextField.font", BODY);
        UIManager.put("PasswordField.font", BODY);
        UIManager.put("ComboBox.font", BODY);
        UIManager.put("Table.font", BODY);
        UIManager.put("Table.rowHeight", 26);
        UIManager.put("Table.gridColor", new Color(232, 237, 242));
        UIManager.put("Table.selectionBackground", new Color(218, 234, 248));
        UIManager.put("Table.selectionForeground", new Color(20, 45, 70));
        UIManager.put("TableHeader.background", NAVY);
        UIManager.put("TableHeader.foreground", Color.WHITE);
        UIManager.put("TableHeader.font", new Font("Dialog", Font.BOLD, 12));
        UIManager.put("OptionPane.background", CARD);
        UIManager.put("OptionPane.foreground", NAVY);
        UIManager.put("OptionPane.messageForeground", NAVY);
        UIManager.put("OptionPane.messageFont", BODY);
        UIManager.put("OptionPane.buttonFont", new Font("Dialog", Font.BOLD, 13));
        UIManager.put("OptionPane.border", BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER), BorderFactory.createEmptyBorder(8, 8, 8, 8)));
        UIManager.put("OptionPane.buttonAreaBorder", BorderFactory.createEmptyBorder(8, 12, 10, 12));
        UIManager.put("OptionPane.minimumSize", new Dimension(390, 150));
    }

    public static JPanel pageHeader(String title, String subtitle) {
        JPanel panel = new JPanel(new java.awt.GridLayout(0, 1, 0, 3));
        panel.setOpaque(false);
        JLabel heading = new JLabel(title); heading.setFont(HEADING); heading.setForeground(NAVY);
        JLabel detail = new JLabel(subtitle); detail.setFont(new Font("Dialog", Font.PLAIN, 12)); detail.setForeground(GREY);
        panel.add(heading); panel.add(detail);
        return panel;
    }

    public static JPanel card() { return new RoundedPanel(); }

    public static JPanel card(java.awt.LayoutManager layout) { JPanel panel = new RoundedPanel(layout); return panel; }

    public static void stylePrimary(JButton button) { styleButton(button, BLUE); }
    public static void styleSuccess(JButton button) { styleButton(button, GREEN); }
    public static void styleDanger(JButton button) { styleButton(button, RED); }
    public static void styleSecondary(JButton button) { styleButton(button, GREY); }

    public static void styleButton(JButton button, Color color) {
        button.setFont(new Font("Dialog", Font.BOLD, 13));
        button.setForeground(Color.WHITE); button.setBackground(color); button.setFocusPainted(false); button.setOpaque(true);
        button.setBorder(BorderFactory.createCompoundBorder(new RoundedBorder(color.darker(), 9), BorderFactory.createEmptyBorder(8, 15, 8, 15)));
    }

    public static void styleTable(JTable table) {
        table.setRowHeight(Math.max(26, table.getRowHeight()));
        table.setShowVerticalLines(false); table.setShowHorizontalLines(true); table.setGridColor(new Color(232, 237, 242));
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setBackground(NAVY); table.getTableHeader().setForeground(Color.WHITE);
        table.getTableHeader().setFont(new Font("Dialog", Font.BOLD, 12));
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable source, Object value, boolean selected, boolean focused, int row, int column) {
                Component component = super.getTableCellRendererComponent(source, value, selected, focused, row, column);
                if (!selected) component.setBackground(row % 2 == 0 ? Color.WHITE : new Color(249, 251, 253));
                setBorder(BorderFactory.createEmptyBorder(0, 7, 0, 7));
                return component;
            }
        });
    }

    public static String money(BigDecimal value) {
        java.text.DecimalFormat format = new java.text.DecimalFormat("#,##0.00", java.text.DecimalFormatSymbols.getInstance(java.util.Locale.US));
        format.setRoundingMode(RoundingMode.HALF_UP);
        return "LKR " + format.format(value == null ? BigDecimal.ZERO : value);
    }

    /** Applies LKR formatting without changing the underlying table-model values. */
    public static void installMoneyRenderer(JTable table, int... columns) {
        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable source, Object value, boolean selected, boolean focused, int row, int column) {
                String text = "";
                if (value instanceof Number number) text = money(new BigDecimal(number.toString()));
                else if (value != null) try { text = money(new BigDecimal(value.toString())); } catch (NumberFormatException ignored) { text = value.toString(); }
                return super.getTableCellRendererComponent(source, text, selected, focused, row, column);
            }
        };
        for (int column : columns) if (column >= 0 && column < table.getColumnCount()) table.getColumnModel().getColumn(column).setCellRenderer(renderer);
    }

    /** Applies a compact yyyy-MM-dd date (or yyyy-MM-dd HH:mm for LocalDateTime) display renderer. */
    public static void installDateRenderer(JTable table, int... columns) {
        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable source, Object value, boolean selected, boolean focused, int row, int column) {
                String text;
                if (value instanceof java.sql.Timestamp timestamp) text = timestamp.toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                else if (value instanceof java.sql.Date date) text = date.toLocalDate().toString();
                else if (value instanceof LocalDateTime time) text = time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                else if (value instanceof Date date) text = new java.text.SimpleDateFormat("yyyy-MM-dd").format(date);
                else text = value == null ? "" : value.toString();
                return super.getTableCellRendererComponent(source, text, selected, focused, row, column);
            }
        };
        for (int column : columns) if (column >= 0 && column < table.getColumnCount()) table.getColumnModel().getColumn(column).setCellRenderer(renderer);
    }

    /** Applies common control styles to generated legacy forms without changing their event handlers. */
    public static void applyWindow(JFrame frame) {
        frame.getContentPane().setBackground(BACKGROUND);
        apply(frame.getContentPane());
    }

    private static void apply(Container parent) {
        for (Component component : parent.getComponents()) {
            if (component instanceof JButton button) styleByMeaning(button);
            else if (component instanceof JTable table) styleTable(table);
            else if (component instanceof JTextField field) styleField(field);
            else if (component instanceof JScrollPane scroll) scroll.setBorder(new RoundedBorder(BORDER, 10));
            if (component instanceof Container nested) apply(nested);
        }
    }

    private static void styleByMeaning(JButton button) {
        String text = button.getText() == null ? "" : button.getText().toLowerCase();
        if (text.contains("delete") || text.contains("remove") || text.contains("void")) styleDanger(button);
        else if (text.contains("add") || text.contains("save") || text.contains("update") || text.contains("complete") || text.contains("start")) styleSuccess(button);
        else if (text.contains("clear") || text.contains("cancel") || text.contains("close") || text.contains("back") || text.contains("logout")) styleSecondary(button);
        else stylePrimary(button);
    }

    private static void styleField(JTextField field) {
        field.setFont(BODY);
        Dimension preferred = field.getPreferredSize();
        field.setPreferredSize(new Dimension(preferred.width, Math.max(30, preferred.height)));
        field.setBorder(BorderFactory.createCompoundBorder(new RoundedBorder(BORDER, 8), BorderFactory.createEmptyBorder(5, 8, 5, 8)));
    }

    private static final class RoundedPanel extends JPanel {
        private RoundedPanel() { super(); initialise(); }
        private RoundedPanel(java.awt.LayoutManager layout) { super(layout); initialise(); }
        private void initialise() { setOpaque(false); setBackground(CARD); setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16)); }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(getBackground()); g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
            g.setColor(BORDER); g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16); g.dispose();
            super.paintComponent(graphics);
        }
    }

    private static final class RoundedBorder extends AbstractBorder {
        private final Color color; private final int radius;
        private RoundedBorder(Color color, int radius) { this.color = color; this.radius = radius; }
        @Override public void paintBorder(Component component, Graphics graphics, int x, int y, int width, int height) {
            Graphics2D g = (Graphics2D) graphics.create(); g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(color); g.drawRoundRect(x, y, width - 1, height - 1, radius, radius); g.dispose();
        }
        @Override public java.awt.Insets getBorderInsets(Component component) { return new java.awt.Insets(1, 1, 1, 1); }
    }
}
