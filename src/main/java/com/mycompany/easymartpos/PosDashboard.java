package com.mycompany.easymartpos;

import com.mycompany.easymartpos.model.ReportType;
import com.mycompany.easymartpos.service.ReportsService;
import com.mycompany.easymartpos.service.Session;
import com.mycompany.easymartpos.service.Authorization;
import com.mycompany.easymartpos.service.ShiftService;
import com.mycompany.easymartpos.ui.CheckoutFrame;
import com.mycompany.easymartpos.ui.CashierShiftDialog;
import com.mycompany.easymartpos.ui.CashierShiftHistoryFrame;
import com.mycompany.easymartpos.ui.BackupRestoreFrame;
import com.mycompany.easymartpos.ui.AccountDialogs;
import com.mycompany.easymartpos.ui.ProductManagementFrame;
import com.mycompany.easymartpos.ui.PromotionForm;
import com.mycompany.easymartpos.ui.ReportsFrame;
import com.mycompany.easymartpos.ui.SalesHistoryFrame;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import com.mycompany.easymartpos.model.CashierShift;
import com.mycompany.easymartpos.model.ShiftTotals;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;

/** Main application launcher; module permissions are centralised here for future role rules. */
public class PosDashboard extends JFrame {
    private static final Color SIDEBAR = new Color(15, 23, 42);
    private static final Color SIDEBAR_SOFT = new Color(30, 41, 59);
    private static final Color SIDEBAR_TEXT = new Color(226, 232, 240);
    private static final Color SIDEBAR_MUTED = new Color(148, 163, 184);
    private static final Color BACKGROUND = new Color(244, 246, 248);
    private static final Color CARD = Color.WHITE;
    private static final Color BORDER = new Color(217, 225, 234);
    private static final Color PRIMARY = new Color(37, 99, 235);
    private static final Color SUCCESS = new Color(22, 163, 74);
    private static final Color WARNING = new Color(245, 158, 11);
    private static final Color DANGER = new Color(220, 38, 38);
    private static final Color TEXT = new Color(17, 24, 39);
    private static final Color MUTED = new Color(107, 114, 128);
    private final ReportsService reports = new ReportsService();
    private final ShiftService shifts = new ShiftService();
    private final JLabel todaySales = summaryValue("LKR 0.00");
    private final JLabel transactions = summaryValue("0");
    private final JLabel lowStock = summaryValue("0");
    private final JLabel expenses = summaryValue("LKR 0.00");
    private final JLabel shiftStatus = new JLabel();
    private long summaryRequestVersion;

    public PosDashboard() {
        super("USHANTHA SUPER MART | Dashboard");
        if (!Session.isAuthenticated()) throw new SecurityException("Please log in before opening the dashboard.");
        setDefaultCloseOperation(EXIT_ON_CLOSE); setSize(1366, 768); setMinimumSize(new java.awt.Dimension(1120, 650)); setLocationRelativeTo(null);
        JPanel root = new JPanel(new BorderLayout()); root.setBackground(BACKGROUND);
        root.add(sidebar(), BorderLayout.WEST);
        JPanel content = new JPanel(new BorderLayout(0, 24)); content.setBackground(BACKGROUND); content.setBorder(BorderFactory.createEmptyBorder(30, 32, 30, 32));
        content.add(header(), BorderLayout.NORTH); content.add(mainArea(), BorderLayout.CENTER);
        root.add(content, BorderLayout.CENTER);
        add(root);
        addComponentListener(new ComponentAdapter() {
            @Override public void componentShown(ComponentEvent event) { loadSummary(); }
        });
        addWindowListener(new WindowAdapter() {
            @Override public void windowActivated(WindowEvent event) { loadSummary(); }
        });
        loadSummary();
    }

    private JPanel header() {
        JPanel panel = new JPanel(new BorderLayout()); panel.setOpaque(false);
        JPanel heading = new JPanel(new GridLayout(0, 1, 0, 4)); heading.setOpaque(false);
        JLabel title = new JLabel("Dashboard"); title.setFont(new Font("Dialog", Font.BOLD, 28)); title.setForeground(TEXT);
        JLabel welcome = new JLabel(isCashier() ? "Welcome back. Your shift and checkout tools are ready." : "Welcome back. Here is today’s store activity.");
        welcome.setText(isCashier() ? "Welcome back. Your shift and checkout tools are ready." : "Welcome back. Here is today's store activity.");
        welcome.setFont(new Font("Dialog", Font.PLAIN, 13)); welcome.setForeground(MUTED); heading.add(title); heading.add(welcome);
        JPanel identity = new JPanel(); identity.setLayout(new BoxLayout(identity, BoxLayout.Y_AXIS)); identity.setOpaque(false); identity.add(profileChip());
        if (isCashier()) { identity.add(Box.createVerticalStrut(5)); shiftStatus.setFont(new Font("Dialog", Font.PLAIN, 12)); shiftStatus.setForeground(MUTED); shiftStatus.setHorizontalAlignment(SwingConstants.RIGHT); shiftStatus.setAlignmentX(RIGHT_ALIGNMENT); identity.add(shiftStatus); }
        panel.add(heading, BorderLayout.WEST); panel.add(identity, BorderLayout.EAST); return panel;
    }

    private JPanel profileChip() {
        JPanel chip = new DashboardCard(new BorderLayout(9, 0), CARD, BORDER);
        chip.setAlignmentX(RIGHT_ALIGNMENT); chip.setBorder(BorderFactory.createEmptyBorder(8, 11, 8, 12));
        JLabel avatar = new DashboardBadge(Session.currentCashierName().substring(0, 1).toUpperCase(), PRIMARY, 28);
        JPanel details = new JPanel(new GridLayout(0, 1, 0, 2)); details.setOpaque(false);
        JLabel name = new JLabel(Session.currentCashierName()); name.setFont(new Font("Dialog", Font.BOLD, 12)); name.setForeground(TEXT);
        JLabel role = new JLabel(Session.currentRole()); role.setFont(new Font("Dialog", Font.PLAIN, 10)); role.setForeground(MUTED);
        details.add(name); details.add(role); chip.add(avatar, BorderLayout.WEST); chip.add(details, BorderLayout.CENTER);
        return chip;
    }

    private JPanel mainArea() {
        JPanel panel = new JPanel(new BorderLayout(0, 24)); panel.setOpaque(false);
        panel.add(summaryPanel(), BorderLayout.NORTH); panel.add(cards(), BorderLayout.CENTER);
        return panel;
    }

    private JPanel cards() {
        JPanel outer = new JPanel(new BorderLayout(0, 12)); outer.setOpaque(false);
        JLabel title = new JLabel("Quick Actions"); title.setFont(new Font("Dialog", Font.BOLD, 15)); title.setForeground(TEXT); outer.add(title, BorderLayout.NORTH);
        JPanel panel = new JPanel(new GridLayout(0, isCashier() ? 2 : 3, 14, 14)); panel.setOpaque(false);
        panel.add(card("New Sale", "Create a customer sale", this::openCheckout));
        panel.add(card("Sales History", "Review completed sales", () -> new SalesHistoryFrame().setVisible(true)));
        if (isCashier()) {
            panel.add(card("Start Shift", "Enter opening cash", this::startShift));
            panel.add(card("Close Shift", "Count and reconcile cash", this::closeShift));
        } else {
            addCard(panel, Authorization.Permission.ADMINISTRATION, "Products", "Items and stock", () -> new ProductManagementFrame().setVisible(true));
            addCard(panel, Authorization.Permission.ADMINISTRATION, "Customers", "Customer records", () -> new CustomerForm().setVisible(true));
            addCard(panel, Authorization.Permission.ADMINISTRATION, "Promotions", "Product offers and discounts", this::openPromotions);
            addCard(panel, Authorization.Permission.ADMINISTRATION, "Suppliers", "Supplier records", () -> new SupplierForm().setVisible(true));
            addCard(panel, Authorization.Permission.ADMINISTRATION, "Purchases", "Receive stock", () -> new PurchaseForm().setVisible(true));
            addCard(panel, Authorization.Permission.ADMINISTRATION, "Expenses", "Business expenses", () -> new ExpenseForm().setVisible(true));
            addCard(panel, Authorization.Permission.ADMINISTRATION, "Reports", "Owner reporting", () -> new ReportsFrame().setVisible(true));
            addCard(panel, Authorization.Permission.ADMINISTRATION, "Day Sales", "Sales report for one day", this::openDaySales);
            addCard(panel, Authorization.Permission.ADMINISTRATION, "Daily Profit", "Profit for each day", this::openDailyProfit);
            addCard(panel, Authorization.Permission.ADMINISTRATION, "Cashier Shifts", "Shift history and reconciliation", () -> new CashierShiftHistoryFrame().setVisible(true));
            addCard(panel, Authorization.Permission.ADMINISTRATION, "Stock Movements", "Stock audit trail", () -> new ReportsFrame(ReportType.STOCK_MOVEMENTS).setVisible(true));
            addCard(panel, Authorization.Permission.ADMINISTRATION, "Backup / Restore", "Database safety", this::openBackupRestore);
            addCard(panel, Authorization.Permission.ADMINISTRATION, "User Accounts", "Add staff and reset passwords", () -> AccountDialogs.manageUsers(this));
        }
        panel.add(card("Change Password", "Update your sign-in password", () -> AccountDialogs.changeOwnPassword(this)));
        outer.add(panel, BorderLayout.CENTER); return outer;
    }

    private void addCard(JPanel panel, Authorization.Permission permission, String title, String detail, Runnable action) {
        if (Authorization.isAllowed(permission)) panel.add(card(title, detail, action));
    }

    private JButton card(String title, String detail, Runnable action) {
        JButton button = new DashboardActionButton(title, detail);
        button.addActionListener(event -> action.run()); return button;
    }

    private JPanel sidebar() {
        JPanel panel = new JPanel(); panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS)); panel.setBackground(SIDEBAR); panel.setPreferredSize(new java.awt.Dimension(240, 1)); panel.setBorder(BorderFactory.createEmptyBorder(26, 16, 22, 16));
        JLabel store = new JLabel("USHANTHA SUPER MART"); store.setAlignmentX(LEFT_ALIGNMENT); store.setFont(new Font("Dialog", Font.BOLD, 17)); store.setForeground(Color.WHITE);
        JLabel system = new JLabel("SUPERMARKET POS"); system.setAlignmentX(LEFT_ALIGNMENT); system.setFont(new Font("Dialog", Font.BOLD, 10)); system.setForeground(SIDEBAR_MUTED);
        JPanel identity = new DashboardCard(new GridLayout(0, 1, 0, 3), SIDEBAR_SOFT, SIDEBAR_SOFT); identity.setAlignmentX(LEFT_ALIGNMENT); identity.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 54)); identity.setBorder(BorderFactory.createEmptyBorder(9, 11, 9, 11));
        JLabel identityName = new JLabel(Session.currentCashierName()); identityName.setFont(new Font("Dialog", Font.BOLD, 12)); identityName.setForeground(Color.WHITE);
        JLabel identityRole = new JLabel(Session.currentRole()); identityRole.setFont(new Font("Dialog", Font.PLAIN, 10)); identityRole.setForeground(SIDEBAR_MUTED); identity.add(identityName); identity.add(identityRole);
        panel.add(store); panel.add(Box.createVerticalStrut(5)); panel.add(system); panel.add(Box.createVerticalStrut(18)); panel.add(identity); panel.add(Box.createVerticalStrut(14));
        panel.add(navLabel("DASHBOARD")); panel.add(navButton("Dashboard", true, this::loadSummary));
        panel.add(navLabel("SALES")); panel.add(navButton("New Sale", this::openCheckout)); panel.add(navButton("Sales History", () -> new SalesHistoryFrame().setVisible(true)));
        if (isCashier()) {
            panel.add(navLabel("SHIFT")); panel.add(navButton("Start Shift", this::startShift)); panel.add(navButton("Close Shift", this::closeShift));
        } else {
            panel.add(navLabel("INVENTORY")); panel.add(navButton("Products", () -> new ProductManagementFrame().setVisible(true))); panel.add(navButton("Suppliers", () -> new SupplierForm().setVisible(true))); panel.add(navButton("Purchases", () -> new PurchaseForm().setVisible(true))); panel.add(navButton("Stock Movements", () -> new ReportsFrame(ReportType.STOCK_MOVEMENTS).setVisible(true)));
            panel.add(navLabel("BUSINESS")); panel.add(navButton("Customers", () -> new CustomerForm().setVisible(true))); panel.add(navButton("Promotions", this::openPromotions)); panel.add(navButton("Expenses", () -> new ExpenseForm().setVisible(true))); panel.add(navButton("Reports", () -> new ReportsFrame().setVisible(true)));
            panel.add(navButton("Day Sales", this::openDaySales)); panel.add(navButton("Daily Profit", this::openDailyProfit));
            panel.add(navLabel("MANAGEMENT")); panel.add(navButton("Cashier Shifts", () -> new CashierShiftHistoryFrame().setVisible(true))); panel.add(navButton("Backup / Restore", this::openBackupRestore));
            panel.add(navButton("User Accounts", () -> AccountDialogs.manageUsers(this)));
        }
        panel.add(Box.createVerticalGlue());
        JLabel role = new JLabel("Signed in as " + Session.currentCashierName() + " | " + Session.currentRole()); role.setAlignmentX(LEFT_ALIGNMENT); role.setFont(new Font("Dialog", Font.PLAIN, 11)); role.setForeground(new Color(186, 210, 233)); panel.add(role); panel.add(Box.createVerticalStrut(10));
        panel.add(navButton("Change Password", () -> AccountDialogs.changeOwnPassword(this)));
        panel.add(navButton("Logout", this::logout)); return panel;
    }

    private JButton navButton(String title, Runnable action) {
        return navButton(title, false, action);
    }

    private JButton navButton(String title, boolean active, Runnable action) {
        JButton button = new DashboardNavButton(title, active); button.setAlignmentX(LEFT_ALIGNMENT);
        button.addActionListener(event -> action.run()); return button;
    }

    private JLabel navLabel(String text) {
        JLabel label = new JLabel(text); label.setAlignmentX(LEFT_ALIGNMENT); label.setFont(new Font("Dialog", Font.BOLD, 9)); label.setForeground(SIDEBAR_MUTED); label.setBorder(BorderFactory.createEmptyBorder(8, 6, 2, 0)); return label;
    }

    private JPanel summaryPanel() {
        boolean admin = Authorization.isAllowed(Authorization.Permission.ADMINISTRATION);
        JPanel panel = new JPanel(new GridLayout(1, admin ? 4 : 2, 12, 0)); panel.setOpaque(false);
        if (admin) {
            panel.add(summaryCard("TODAY SALES", todaySales)); panel.add(summaryCard("TODAY TRANSACTIONS", transactions));
            JPanel lowStockCard = summaryCard("LOW STOCK ITEMS", lowStock);
            makeClickable(lowStockCard, () -> new ReportsFrame(ReportType.LOW_STOCK).setVisible(true));
            panel.add(lowStockCard); panel.add(summaryCard("TODAY EXPENSES", expenses));
        } else {
            panel.add(summaryCard("SHIFT SALES", todaySales)); panel.add(summaryCard("SHIFT TRANSACTIONS", transactions));
            panel.add(summaryCard("SHIFT CASH", lowStock)); panel.add(summaryCard("SHIFT CARD", expenses));
        }
        return panel;
    }

    /** Makes a whole summary card open its detail screen on click. */
    private static void makeClickable(JPanel card, Runnable action) {
        card.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        card.setToolTipText("Click to view the list");
        card.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent event) { action.run(); }
        });
        for (java.awt.Component child : card.getComponents()) {
            child.setCursor(card.getCursor());
            child.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseClicked(java.awt.event.MouseEvent event) { action.run(); }
            });
        }
    }

    private JPanel summaryCard(String label, JLabel value) {
        Color accent = switch (label) {
            case "TODAY SALES", "SHIFT SALES" -> PRIMARY;
            case "TODAY TRANSACTIONS", "SHIFT TRANSACTIONS" -> SUCCESS;
            case "LOW STOCK ITEMS", "SHIFT CASH" -> WARNING;
            default -> DANGER;
        };
        JPanel card = new DashboardCard(new BorderLayout(7, 7), CARD, BORDER); card.setBorder(BorderFactory.createEmptyBorder(17, 18, 17, 18));
        JPanel top = new JPanel(new BorderLayout()); top.setOpaque(false);
        JLabel heading = new JLabel(label); heading.setFont(new Font("Dialog", Font.BOLD, 10)); heading.setForeground(MUTED);
        top.add(heading, BorderLayout.WEST); top.add(new DashboardVectorIcon(label, accent, 26), BorderLayout.EAST);
        card.add(top, BorderLayout.NORTH); card.add(value, BorderLayout.CENTER); return card;
    }

    private JLabel summaryValue(String value) { JLabel label = new JLabel(value); label.setFont(new Font("Dialog", Font.BOLD, 25)); label.setForeground(TEXT); return label; }

    /** Dashboard-local colored badge used for profile, summary, and action affordances. */
    private static final class DashboardBadge extends JLabel {
        private final Color color;
        private final int size;

        private DashboardBadge(String text, Color color, int size) {
            super(text, SwingConstants.CENTER); this.color = color; this.size = size;
            setFont(new Font("Dialog", Font.BOLD, Math.max(10, size / 2))); setForeground(Color.WHITE); setOpaque(false);
            setPreferredSize(new java.awt.Dimension(size, size)); setMinimumSize(new java.awt.Dimension(size, size)); setMaximumSize(new java.awt.Dimension(size, size));
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(color); g.fillRoundRect(0, 0, size - 1, size - 1, Math.max(8, size / 2), Math.max(8, size / 2));
            g.setFont(getFont()); g.setColor(getForeground());
            java.awt.FontMetrics metrics = g.getFontMetrics();
            g.drawString(getText(), (size - metrics.stringWidth(getText())) / 2, (size - metrics.getHeight()) / 2 + metrics.getAscent());
            g.dispose();
        }
    }

    /** Dashboard-local vector icon: avoids a third-party dependency and placeholder text glyphs. */
    private static final class DashboardVectorIcon extends JLabel {
        private final String key;
        private final Color color;
        private final int size;

        private DashboardVectorIcon(String key, Color color, int size) {
            this.key = key; this.color = color; this.size = size; setOpaque(false);
            setPreferredSize(new java.awt.Dimension(size, size)); setMinimumSize(new java.awt.Dimension(size, size)); setMaximumSize(new java.awt.Dimension(size, size));
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            paintIcon(g, key, 0, 0, size, color); g.dispose();
        }
    }

    private static void paintIcon(Graphics2D graphics, String key, int x, int y, int size, Color color) {
        Graphics2D g = (Graphics2D) graphics.create();
        float stroke = size <= 16 ? 1.35f : 1.75f;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setStroke(new java.awt.BasicStroke(stroke, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
        g.setColor(color);
        int unit = Math.max(1, size / 8);
        int left = x + unit, top = y + unit, right = x + size - unit, bottom = y + size - unit;
        int midX = (left + right) / 2, midY = (top + bottom) / 2;
        switch (key) {
            case "Dashboard" -> {
                int box = Math.max(3, (size - 3 * unit) / 2);
                g.drawRoundRect(left, top, box, box, unit, unit); g.drawRoundRect(right - box, top, box, box, unit, unit);
                g.drawRoundRect(left, bottom - box, box, box, unit, unit); g.drawRoundRect(right - box, bottom - box, box, box, unit, unit);
            }
            case "New Sale" -> {
                g.drawLine(left, top + unit, left + 2 * unit, top + unit); g.drawLine(left + 2 * unit, top + unit, left + 3 * unit, bottom - 2 * unit);
                g.drawLine(left + 3 * unit, bottom - 2 * unit, right - unit, bottom - 2 * unit); g.drawLine(left + 4 * unit, bottom - unit, right - unit, bottom - unit);
                g.fillOval(left + 3 * unit, bottom - unit, unit + 1, unit + 1); g.fillOval(right - 2 * unit, bottom - unit, unit + 1, unit + 1);
                g.drawLine(right - 2 * unit, top + 2 * unit, right, top + 2 * unit); g.drawLine(right - unit, top + unit, right - unit, top + 3 * unit);
            }
            case "Sales History" -> {
                g.drawOval(left, top, right - left, bottom - top); g.drawLine(midX, top + 2 * unit, midX, midY); g.drawLine(midX, midY, right - 2 * unit, midY + unit);
            }
            case "Products" -> {
                g.drawRect(left + unit, top + 2 * unit, right - left - 2 * unit, bottom - top - 3 * unit);
                g.drawLine(left + unit, top + 2 * unit, midX, top); g.drawLine(midX, top, right - unit, top + 2 * unit); g.drawLine(midX, top, midX, bottom - unit);
            }
            case "Customers" -> {
                g.drawOval(midX - 2 * unit, top, 4 * unit, 4 * unit); g.drawArc(left, top + 3 * unit, right - left, 5 * unit, 200, 140);
            }
            case "Promotions" -> {
                int[] xs = {left, right - 2 * unit, right, right - 2 * unit, left}; int[] ys = {top + unit, top + unit, midY, bottom - unit, bottom - unit};
                g.drawPolygon(xs, ys, xs.length); g.drawOval(left + 2 * unit, midY - unit, unit + 1, unit + 1);
            }
            case "Suppliers" -> {
                g.drawRect(left, top + 3 * unit, 5 * unit, 3 * unit); g.drawRect(left + 5 * unit, top + 4 * unit, 2 * unit, 2 * unit);
                g.fillOval(left + unit, bottom - 2 * unit, 2 * unit, 2 * unit); g.fillOval(right - 3 * unit, bottom - 2 * unit, 2 * unit, 2 * unit);
            }
            case "Purchases" -> {
                g.drawRoundRect(left + 2 * unit, top + unit, 4 * unit, bottom - top - 2 * unit, unit, unit);
                g.drawArc(left + 3 * unit, top - unit, 2 * unit, 3 * unit, 180, 180); g.drawLine(left + 3 * unit, midY, right - 2 * unit, midY);
            }
            case "Expenses" -> {
                g.drawRoundRect(left, top + 2 * unit, right - left, 5 * unit, 2 * unit, 2 * unit); g.drawLine(left, top + 4 * unit, right, top + 4 * unit);
                g.fillOval(right - 3 * unit, top + 5 * unit, unit + 1, unit + 1);
            }
            case "Reports" -> {
                g.drawLine(left, bottom, left, top); g.drawLine(left, bottom, right, bottom);
                g.fillRect(left + unit, midY, unit + 1, bottom - midY - unit); g.fillRect(midX - unit / 2, top + 3 * unit, unit + 1, bottom - top - 4 * unit); g.fillRect(right - 2 * unit, top + unit, unit + 1, bottom - top - 2 * unit);
            }
            case "Cashier Shifts", "Start Shift", "Close Shift" -> {
                g.drawOval(left, top, right - left, bottom - top); g.drawLine(midX, top + 2 * unit, midX, midY); g.drawLine(midX, midY, midX + 2 * unit, midY + unit);
            }
            case "Stock Movements" -> {
                g.drawLine(left, top + 2 * unit, right - 2 * unit, top + 2 * unit); g.drawLine(right - 2 * unit, top + 2 * unit, right - 3 * unit, top + unit); g.drawLine(right - 2 * unit, top + 2 * unit, right - 3 * unit, top + 3 * unit);
                g.drawLine(right, bottom - 2 * unit, left + 2 * unit, bottom - 2 * unit); g.drawLine(left + 2 * unit, bottom - 2 * unit, left + 3 * unit, bottom - 3 * unit); g.drawLine(left + 2 * unit, bottom - 2 * unit, left + 3 * unit, bottom - unit);
            }
            case "Backup / Restore" -> {
                g.drawOval(left, top, right - left, 3 * unit); g.drawLine(left, top + unit, left, bottom - 2 * unit); g.drawLine(right, top + unit, right, bottom - 2 * unit); g.drawArc(left, bottom - 3 * unit, right - left, 3 * unit, 0, 180);
                g.drawLine(midX, bottom, midX, top + 4 * unit); g.drawLine(midX, top + 4 * unit, midX - unit, top + 5 * unit); g.drawLine(midX, top + 4 * unit, midX + unit, top + 5 * unit);
            }
            case "Logout" -> {
                g.drawRect(left, top, 4 * unit, bottom - top); g.drawLine(midX, midY, right, midY); g.drawLine(right, midY, right - 2 * unit, midY - 2 * unit); g.drawLine(right, midY, right - 2 * unit, midY + 2 * unit);
            }
            case "TODAY SALES", "SHIFT SALES" -> {
                g.drawRoundRect(left, top + unit, right - left, bottom - top - 2 * unit, 2 * unit, 2 * unit); g.drawLine(left, midY, right, midY);
            }
            case "TODAY TRANSACTIONS", "SHIFT TRANSACTIONS" -> {
                g.drawOval(left, top, right - left, bottom - top); g.drawLine(midX, top + 2 * unit, midX, midY); g.drawLine(midX, midY, right - 2 * unit, midY);
            }
            case "LOW STOCK ITEMS", "SHIFT CASH" -> {
                g.drawPolygon(new int[] {midX, right, left}, new int[] {top, bottom, bottom}, 3); g.drawLine(midX, top + 3 * unit, midX, midY + unit); g.fillOval(midX, bottom - 3 * unit, unit + 1, unit + 1);
            }
            default -> {
                g.drawRoundRect(left, top, right - left, bottom - top, 2 * unit, 2 * unit); g.drawLine(left + 2 * unit, midY, right - 2 * unit, midY);
            }
        }
        g.dispose();
    }

    /** Dashboard-only rounded surface; it deliberately does not alter shared application styling. */
    private static class DashboardCard extends JPanel {
        private final Color fill;
        private final Color outline;

        private DashboardCard(java.awt.LayoutManager layout, Color fill, Color outline) {
            super(layout); this.fill = fill; this.outline = outline; setOpaque(false);
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(fill); g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
            g.setColor(outline); g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
            g.dispose(); super.paintComponent(graphics);
        }
    }

    /** Dashboard quick-action card that retains the existing JButton action listener model. */
    private static final class DashboardActionButton extends JButton {
        private final String iconKey;

        private DashboardActionButton(String title, String detail) {
            super("<html><div style='text-align:left'><b style='font-size:16px'>" + title
                    + "</b><br/><span style='font-size:11px;color:#6B7280'>" + detail + "</span></div></html>");
            iconKey = title;
            setFont(new Font("Dialog", Font.PLAIN, 15)); setForeground(TEXT); setFocusPainted(false); setFocusable(false);
            setContentAreaFilled(false); setOpaque(false); setHorizontalAlignment(SwingConstants.LEFT);
            setBorder(BorderFactory.createEmptyBorder(14, 52, 14, 36)); setRolloverEnabled(true);
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean hover = getModel().isRollover() || getModel().isPressed();
            g.setColor(hover ? new Color(239, 246, 255) : CARD); g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
            g.setColor(hover ? PRIMARY : BORDER); g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
            int iconY = Math.max(8, (getHeight() - 24) / 2);
            g.setColor(hover ? new Color(219, 234, 254) : new Color(241, 245, 249)); g.fillRoundRect(16, iconY, 24, 24, 8, 8);
            paintIcon(g, iconKey, 20, iconY + 4, 16, hover ? PRIMARY : MUTED);
            int arrowX = getWidth() - 22; int arrowY = getHeight() / 2;
            g.setColor(hover ? PRIMARY : MUTED); g.drawLine(arrowX - 3, arrowY - 4, arrowX + 1, arrowY); g.drawLine(arrowX + 1, arrowY, arrowX - 3, arrowY + 4);
            g.dispose(); super.paintComponent(graphics);
        }
    }

    /** Dashboard-only navigation painter with a permanent Dashboard active state. */
    private static final class DashboardNavButton extends JButton {
        private final boolean active;
        private final String iconKey;

        private DashboardNavButton(String title, boolean active) {
            super(title); this.active = active; this.iconKey = title;
            setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 30)); setPreferredSize(new java.awt.Dimension(1, 30));
            setHorizontalAlignment(SwingConstants.LEFT); setForeground(SIDEBAR_TEXT); setFont(new Font("Dialog", Font.BOLD, 12));
            setFocusPainted(false); setFocusable(false); setContentAreaFilled(false); setOpaque(false); setRolloverEnabled(true);
            setBorder(BorderFactory.createEmptyBorder(5, 31, 5, 12));
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (active || getModel().isRollover() || getModel().isPressed()) {
                g.setColor(active ? PRIMARY : SIDEBAR_SOFT);
                g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 9, 9);
            }
            if (active) { g.setColor(Color.WHITE); g.fillRoundRect(0, 8, 3, Math.max(1, getHeight() - 16), 3, 3); }
            paintIcon(g, iconKey, 9, Math.max(7, (getHeight() - 14) / 2), 14, active ? Color.WHITE : SIDEBAR_MUTED);
            g.dispose(); super.paintComponent(graphics);
        }
    }

    private void loadSummary() {
        final long requestVersion = ++summaryRequestVersion;
        if (isCashier()) { loadCashierShiftSummary(); return; }
        new SwingWorker<ReportsService.DashboardSummary, Void>() {
            @Override protected ReportsService.DashboardSummary doInBackground() throws Exception { return reports.storeWideDashboardSummary(LocalDate.now()); }
            @Override protected void done() {
                if (requestVersion != summaryRequestVersion) return;
                try { ReportsService.DashboardSummary data = get(); todaySales.setText(currency(data.todaySales())); transactions.setText(Long.toString(data.todayTransactions())); lowStock.setText(Long.toString(data.lowStockCount())); expenses.setText(currency(data.todayExpenses())); }
                catch (Exception ignored) { todaySales.setText("Unavailable"); transactions.setText("-"); lowStock.setText("-"); expenses.setText("Unavailable"); }
            }
        }.execute();
    }

    private void loadCashierShiftSummary() {
        shiftStatus.setText("Shift: Loading...");
        final long requestVersion = summaryRequestVersion;
        new SwingWorker<CashierShiftState, Void>() {
            @Override protected CashierShiftState doInBackground() throws Exception {
                if (!shifts.isSchemaReady()) return new CashierShiftState(null, null, false);
                CashierShift shift = shifts.currentOpenShiftForCurrentUser().orElse(null);
                return new CashierShiftState(shift, shift == null ? null : shifts.totalsForShift(shift.shiftId()), true);
            }
            @Override protected void done() {
                if (requestVersion != summaryRequestVersion) return;
                try {
                    CashierShiftState state = get();
                    if (!state.ready()) { shiftStatus.setText("Shift: Setup required"); setShiftAmounts(null); return; }
                    if (state.shift() == null) { shiftStatus.setText("Shift: NOT STARTED"); setShiftAmounts(null); return; }
                    shiftStatus.setText("Shift: OPEN  |  Opened: " + state.shift().openedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + "  |  Opening Cash: " + currency(state.shift().openingCash()));
                    setShiftAmounts(state.totals());
                } catch (Exception exception) { shiftStatus.setText("Shift: Unavailable"); setShiftAmounts(null); }
            }
        }.execute();
    }

    private void setShiftAmounts(ShiftTotals totals) {
        todaySales.setText(currency(totals == null ? BigDecimal.ZERO : totals.sales()));
        transactions.setText(totals == null ? "0" : Long.toString(totals.transactions()));
        lowStock.setText(currency(totals == null ? BigDecimal.ZERO : totals.cashSales()));
        expenses.setText(currency(totals == null ? BigDecimal.ZERO : totals.cardSales()));
    }

    private void startShift() { if (CashierShiftDialog.start(this, shifts)) loadSummary(); }
    private void closeShift() { if (CashierShiftDialog.close(this, shifts)) loadSummary(); }
    private void openCheckout() { new CheckoutFrame(this::loadSummary).setVisible(true); }
    private boolean isCashier() { return "CASHIER".equals(Session.currentRole()); }
    private record CashierShiftState(CashierShift shift, ShiftTotals totals, boolean ready) { }

    private String currency(BigDecimal amount) { return "LKR " + amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(); }
    private void openPromotions() { new PromotionForm().setVisible(true); }
    private void openDaySales() { new ReportsFrame(ReportType.DAILY_SALES).setVisible(true); }
    private void openDailyProfit() { new ReportsFrame(ReportType.DAILY_PROFIT).setVisible(true); }
    private void openBackupRestore() { new BackupRestoreFrame().setVisible(true); }
    private void logout() { Session.clear(); dispose(); LoginForm login = new LoginForm(); login.setLocationRelativeTo(null); login.setVisible(true); }
}
