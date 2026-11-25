package com.mycompany.easymartpos.ui;

import com.mycompany.easymartpos.Dialogs;
import com.mycompany.easymartpos.model.ReportMetric;
import com.mycompany.easymartpos.model.ReportResult;
import com.mycompany.easymartpos.model.ReportType;
import com.mycompany.easymartpos.service.Authorization;
import com.mycompany.easymartpos.service.ReportsService;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.print.PrinterException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.RowFilter;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/** Professional, dependency-free reporting centre for sales, stock and profitability insight. */
public final class ReportsFrame extends JFrame {
    private final ReportsService reports = new ReportsService();
    private final JComboBox<ReportCategory> reportCategory = new JComboBox<>(ReportCategory.values());
    private final JComboBox<ReportType> reportType = new JComboBox<>(ReportType.values());
    private final JLabel fromLabel = new JLabel("From (yyyy-MM-dd)");
    private final JLabel toLabel = new JLabel("To (yyyy-MM-dd)");
    private final JTextField from = new JTextField(LocalDate.now().toString(), 10);
    private final JTextField to = new JTextField(LocalDate.now().toString(), 10);
    private final JLabel nearExpiryLabel = new JLabel("Within days");
    private final JSpinner nearExpiryDays = new JSpinner(new javax.swing.SpinnerNumberModel(30, 1, 365, 1));
    private final JButton load = new JButton("Load / Generate");
    private final JTextField search = new JTextField(18);
    private final JTable table = new JTable();
    private final SimpleChartPanel chart = new SimpleChartPanel();
    private final JLabel chartTitle = sectionTitle("TREND & INSIGHTS");
    private final JLabel resultTitle = sectionTitle("REPORT RESULTS");
    private final JLabel footerSummary = new JLabel("Choose a report and load results.");
    private final JLabel notice = new JLabel();
    private final JPanel tableState = new JPanel(new CardLayout());
    private final JPanel chartArea = UITheme.card(new BorderLayout(0, 7));
    private final MetricCard[] cards = {new MetricCard(), new MetricCard(), new MetricCard(), new MetricCard()};
    private DefaultTableModel model = modelFor(List.of());
    private ReportResult current;
    private long loadVersion;
    private SwingWorker<ReportResult, Void> activeLoad;
    private TableRowSorter<DefaultTableModel> sorter;
    private JPanel fromControl;
    private JPanel toControl;
    private JPanel nearExpiryControl;
    private JPanel quickRangeControl;
    private JPanel searchPanel;
    private final List<JButton> quickRangeButtons = new ArrayList<>();
    private JButton activeQuickRange;
    private boolean selectorUpdating;

    public ReportsFrame() { this(null); }

    public ReportsFrame(ReportType initial) {
        super("USHANTHA SUPER MART | Reports");
        Authorization.requireAdmin();
        setDefaultCloseOperation(DISPOSE_ON_CLOSE); setSize(1320, 820); setMinimumSize(new Dimension(1080, 680)); setLocationByPlatform(true);
        buildLayout();
        UITheme.applyWindow(this);
        configureReportSelectors();
        installOpenFade();
        selectInitialReport(initial == null ? ReportType.DAILY_SALES : initial);
        reportCategory.addActionListener(event -> categoryChanged());
        reportType.addActionListener(event -> reportTypeChanged());
        configureDateFields(selectedType());
        loadSelectedReport();
    }

    private void buildLayout() {
        JPanel root = new JPanel(new BorderLayout(0, 14)); root.setBackground(UITheme.BACKGROUND); root.setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));
        JPanel top = new JPanel(); top.setOpaque(false); top.setLayout(new javax.swing.BoxLayout(top, javax.swing.BoxLayout.Y_AXIS));
        top.add(brandHeader()); top.add(javax.swing.Box.createVerticalStrut(12)); top.add(filters());
        root.add(top, BorderLayout.NORTH);

        JPanel analytics = new JPanel(); analytics.setOpaque(false); analytics.setLayout(new javax.swing.BoxLayout(analytics, javax.swing.BoxLayout.Y_AXIS)); analytics.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        JPanel cardRow = cards(); cardRow.setAlignmentX(Component.LEFT_ALIGNMENT); analytics.add(cardRow); analytics.add(javax.swing.Box.createVerticalStrut(14));
        chartArea.add(chartTitle, BorderLayout.NORTH); chartArea.add(chart, BorderLayout.CENTER); chartArea.setAlignmentX(Component.LEFT_ALIGNMENT); chartArea.setMaximumSize(new Dimension(Integer.MAX_VALUE, 250)); chartArea.setPreferredSize(new Dimension(1, 250)); analytics.add(chartArea); analytics.add(javax.swing.Box.createVerticalStrut(14));
        JPanel resultsCard = UITheme.card(new BorderLayout(0, 8)); resultsCard.setAlignmentX(Component.LEFT_ALIGNMENT); resultsCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 470)); resultsCard.setPreferredSize(new Dimension(1, 470)); resultsCard.add(tableToolbar(), BorderLayout.NORTH); resultsCard.add(buildTableState(), BorderLayout.CENTER); analytics.add(resultsCard); analytics.add(javax.swing.Box.createVerticalStrut(14));
        JPanel footer = UITheme.card(new BorderLayout(10, 0)); footer.setAlignmentX(Component.LEFT_ALIGNMENT); footer.setBorder(BorderFactory.createEmptyBorder(9, 14, 9, 14)); footerSummary.setFont(new Font("Dialog", Font.BOLD, 12)); footerSummary.setForeground(UITheme.NAVY); notice.setFont(new Font("Dialog", Font.PLAIN, 12)); notice.setForeground(UITheme.GREY); footer.add(footerSummary, BorderLayout.WEST); footer.add(notice, BorderLayout.CENTER); analytics.add(footer);
        JScrollPane page = new JScrollPane(analytics); page.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER); page.getVerticalScrollBar().setUnitIncrement(18); page.setBorder(BorderFactory.createEmptyBorder()); page.getViewport().setBackground(UITheme.BACKGROUND); root.add(page, BorderLayout.CENTER);

        setContentPane(root);
    }

    private JPanel brandHeader() {
        JPanel header = new JPanel(new BorderLayout(12, 0)); header.setBackground(UITheme.NAVY); header.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18)); header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 78));
        JLabel icon = new JLabel("▦", SwingConstants.CENTER); icon.setFont(new Font("Dialog", Font.BOLD, 25)); icon.setForeground(Color.WHITE); icon.setPreferredSize(new Dimension(34, 36));
        JPanel heading = new JPanel(); heading.setOpaque(false); heading.setLayout(new javax.swing.BoxLayout(heading, javax.swing.BoxLayout.Y_AXIS));
        JLabel title = new JLabel("REPORTS"); title.setFont(new Font("Dialog", Font.BOLD, 22)); title.setForeground(Color.WHITE);
        JLabel subtitle = new JLabel("Sales, profit, stock and business insights"); subtitle.setFont(new Font("Dialog", Font.PLAIN, 12)); subtitle.setForeground(new Color(202, 218, 234)); heading.add(title); heading.add(javax.swing.Box.createVerticalStrut(2)); heading.add(subtitle);
        JPanel left = new JPanel(new BorderLayout(9, 0)); left.setOpaque(false); left.add(icon, BorderLayout.WEST); left.add(heading, BorderLayout.CENTER); header.add(left, BorderLayout.WEST);
        JPanel store = new JPanel(); store.setOpaque(false); store.setLayout(new javax.swing.BoxLayout(store, javax.swing.BoxLayout.Y_AXIS));
        JLabel storeName = new JLabel("USHANTHA SUPER MART", SwingConstants.RIGHT); storeName.setAlignmentX(Component.RIGHT_ALIGNMENT); storeName.setFont(new Font("Dialog", Font.BOLD, 13)); storeName.setForeground(Color.WHITE);
        JLabel storeDetail = new JLabel("BUSINESS ANALYTICS", SwingConstants.RIGHT); storeDetail.setAlignmentX(Component.RIGHT_ALIGNMENT); storeDetail.setFont(new Font("Dialog", Font.PLAIN, 10)); storeDetail.setForeground(new Color(202, 218, 234)); store.add(storeName); store.add(javax.swing.Box.createVerticalStrut(2)); store.add(storeDetail); header.add(store, BorderLayout.EAST);
        return header;
    }

    private JPanel filters() {
        JPanel filters = UITheme.card(); filters.setLayout(new javax.swing.BoxLayout(filters, javax.swing.BoxLayout.Y_AXIS));
        JPanel selectionRow = toolbarRow(); selectionRow.add(control("Report Category", reportCategory)); selectionRow.add(control("Report Type", reportType));
        fromControl = control(fromLabel, from); toControl = control(toLabel, to); nearExpiryControl = control(nearExpiryLabel, nearExpiryDays);
        selectionRow.add(fromControl); selectionRow.add(toControl); selectionRow.add(nearExpiryControl);
        quickRangeControl = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0)); quickRangeControl.setOpaque(false); quickRangeControl.add(filterLabel("Quick Range")); quickRangeControl.add(quickRange("Today", 0)); quickRangeControl.add(quickRange("7 Days", 6)); quickRangeControl.add(quickRange("30 Days", 29));
        JButton month = new JButton("This Month"); month.addActionListener(event -> { LocalDate now = LocalDate.now(); from.setText(now.withDayOfMonth(1).toString()); to.setText(now.toString()); selectQuickRange(month); loadSelectedReport(); }); styleQuickRange(month); quickRangeControl.add(month); quickRangeButtons.add(month);
        selectQuickRange(quickRangeButtons.get(0));
        filters.add(selectionRow); filters.add(javax.swing.Box.createVerticalStrut(6));
        JPanel quickRangeRow = toolbarRow(); quickRangeRow.add(quickRangeControl); filters.add(quickRangeRow); filters.add(javax.swing.Box.createVerticalStrut(6));
        JPanel actionRow = toolbarRow(); load.addActionListener(event -> loadSelectedReport()); reportButton(load, UITheme.BLUE); actionRow.add(load);
        JButton refresh = new JButton("Refresh"); refresh.addActionListener(event -> loadSelectedReport()); reportButton(refresh, UITheme.GREY); actionRow.add(refresh);
        JButton print = new JButton("Print Report"); print.addActionListener(event -> printReport()); reportButton(print, UITheme.BLUE); actionRow.add(print);
        JButton export = new JButton("Export CSV"); export.addActionListener(event -> exportCsv()); reportButton(export, UITheme.BLUE); actionRow.add(export);
        JButton excel = new JButton("Export Excel"); excel.addActionListener(event -> exportExcel()); reportButton(excel, UITheme.BLUE); actionRow.add(excel); filters.add(actionRow);
        return filters;
    }

    private static JPanel toolbarRow() { JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0)); row.setOpaque(false); row.setAlignmentX(Component.LEFT_ALIGNMENT); return row; }
    private static JPanel control(String label, Component field) { return control(filterLabel(label), field); }
    private static JPanel control(JLabel label, Component field) { JPanel group = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0)); group.setOpaque(false); group.add(label); group.add(field); return group; }

    /** Keeps both selector popups readable, spacious and high-contrast. */
    private void configureReportSelectors() {
        configureSelector(reportCategory, 165); configureSelector(reportType, 300);
        Dimension dateSize = new Dimension(from.getPreferredSize().width, 38); from.setPreferredSize(dateSize); to.setPreferredSize(dateSize);
        nearExpiryDays.setPreferredSize(new Dimension(80, 38));
    }

    private void configureSelector(JComboBox<?> combo, int width) {
        Dimension size = new Dimension(width, 38); combo.setMinimumSize(size); combo.setPreferredSize(size); combo.setMaximumSize(size); combo.setMaximumRowCount(12); combo.setBackground(Color.WHITE); combo.setForeground(UITheme.NAVY);
        combo.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(UITheme.BORDER), BorderFactory.createEmptyBorder(3, 9, 3, 9)));
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index, boolean selected, boolean focused) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focused);
                label.setFont(new Font("Dialog", Font.PLAIN, 13)); label.setVerticalAlignment(SwingConstants.CENTER); label.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
                if (selected) { label.setBackground(UITheme.NAVY); label.setForeground(Color.WHITE); }
                else { label.setBackground(Color.WHITE); label.setForeground(UITheme.NAVY); }
                return label;
            }
        });
    }

    private void selectInitialReport(ReportType type) {
        selectorUpdating = true; ReportCategory category = categoryFor(type); reportCategory.setSelectedItem(category); populateReportTypes(category); reportType.setSelectedItem(type); selectorUpdating = false;
    }

    private void categoryChanged() {
        if (selectorUpdating) return;
        selectorUpdating = true; populateReportTypes((ReportCategory) reportCategory.getSelectedItem()); selectorUpdating = false;
        configureDateFields(selectedType()); loadSelectedReport();
    }

    private void populateReportTypes(ReportCategory category) {
        reportType.removeAllItems(); for (ReportType type : reportsFor(category)) reportType.addItem(type);
    }

    private static ReportCategory categoryFor(ReportType type) {
        for (ReportCategory category : ReportCategory.values()) if (reportsFor(category).contains(type)) return category;
        return ReportCategory.OVERVIEW;
    }

    private static List<ReportType> reportsFor(ReportCategory category) {
        return switch (category) {
            case OVERVIEW -> List.of(ReportType.BUSINESS_OVERVIEW);
            case SALES -> List.of(ReportType.DAILY_SALES, ReportType.DATE_RANGE_SALES, ReportType.SALES_TREND, ReportType.PRODUCT_WISE_SALES, ReportType.CATEGORY_WISE_SALES, ReportType.TOP_SELLING_PRODUCTS, ReportType.DISCOUNT_REPORT, ReportType.PAYMENTS);
            case PROFIT -> List.of(ReportType.DAILY_PROFIT, ReportType.PROFIT_SUMMARY, ReportType.PRODUCT_PROFITABILITY);
            case INVENTORY -> List.of(ReportType.INVENTORY_OVERVIEW, ReportType.PRODUCT_STOCK, ReportType.LOW_STOCK, ReportType.OUT_OF_STOCK, ReportType.EXPIRED_ITEMS, ReportType.NEAR_EXPIRY_ITEMS, ReportType.STOCK_VALUATION, ReportType.SLOW_MOVING_PRODUCTS, ReportType.STOCK_MOVEMENTS);
            case PURCHASES -> List.of(ReportType.PURCHASES, ReportType.SUPPLIER_PURCHASES);
            case EXPENSES -> List.of(ReportType.EXPENSES);
            case CUSTOMERS -> List.of(ReportType.CUSTOMER_PURCHASES, ReportType.LOYALTY_POINTS, ReportType.CUSTOMER_SAVINGS);
            case CASHIERS -> List.of(ReportType.CASHIER_WISE_SALES, ReportType.VOID_SALES);
            case SUPPLIERS -> List.of(ReportType.SUPPLIER_PURCHASES);
        };
    }

    /** A small, one-time transition makes the reporting workspace feel responsive without affecting loading work. */
    private void installOpenFade() {
        if (GraphicsEnvironment.isHeadless()) return;
        GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        if (!device.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.TRANSLUCENT)) return;
        addWindowListener(new WindowAdapter() {
            @Override public void windowOpened(WindowEvent event) {
                setOpacity(0f);
                javax.swing.Timer fade = new javax.swing.Timer(18, null);
                fade.addActionListener(step -> { float opacity = Math.min(1f, getOpacity() + 0.12f); setOpacity(opacity); if (opacity >= 1f) fade.stop(); });
                fade.start();
            }
        });
    }

    private JButton quickRange(String label, int priorDays) { JButton button = new JButton(label); button.addActionListener(event -> { LocalDate now = LocalDate.now(); from.setText(now.minusDays(priorDays).toString()); to.setText(now.toString()); selectQuickRange(button); loadSelectedReport(); }); styleQuickRange(button); quickRangeButtons.add(button); return button; }

    private static void reportButton(JButton button, Color color) {
        UITheme.styleButton(button, color);
        button.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent event) { if (button.isEnabled()) button.setBackground(color.brighter()); }
            @Override public void mouseExited(MouseEvent event) { button.setBackground(color); }
        });
    }

    private void styleQuickRange(JButton button) {
        UITheme.styleButton(button, new Color(236, 242, 248)); button.setForeground(UITheme.NAVY); button.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        button.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent event) { if (button != activeQuickRange) button.setBackground(new Color(218, 234, 248)); }
            @Override public void mouseExited(MouseEvent event) { if (button != activeQuickRange) button.setBackground(new Color(236, 242, 248)); }
        });
    }

    private void selectQuickRange(JButton selected) {
        activeQuickRange = selected;
        for (JButton button : quickRangeButtons) { boolean active = button == selected; button.setBackground(active ? UITheme.BLUE : new Color(236, 242, 248)); button.setForeground(active ? Color.WHITE : UITheme.NAVY); }
    }

    private JPanel cards() {
        JPanel panel = new JPanel(new GridLayout(1, 4, 12, 0)); panel.setOpaque(false);
        for (MetricCard card : cards) panel.add(card); return panel;
    }

    private Component buildTableState() {
        table.setModel(model); table.setAutoCreateRowSorter(true); table.setFillsViewportHeight(true); table.setRowHeight(29); UITheme.styleTable(table);
        JScrollPane scroll = new JScrollPane(table); scroll.setBorder(BorderFactory.createLineBorder(UITheme.BORDER));
        JLabel empty = new JLabel("No records found for the selected report and period.", SwingConstants.CENTER); empty.setForeground(UITheme.GREY); empty.setFont(new Font("Dialog", Font.PLAIN, 14));
        tableState.setOpaque(false); tableState.add(scroll, "table"); tableState.add(empty, "empty"); ((CardLayout) tableState.getLayout()).show(tableState, "table");
        return tableState;
    }

    private JPanel tableToolbar() {
        JPanel toolbar = new JPanel(new BorderLayout()); toolbar.setOpaque(false); toolbar.add(resultTitle, BorderLayout.WEST);
        searchPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0)); searchPanel.setOpaque(false); search.setToolTipText("Search report results..."); searchPanel.add(filterLabel("Search")); searchPanel.add(search); toolbar.add(searchPanel, BorderLayout.EAST);
        search.getDocument().addDocumentListener(new DocumentListener() { @Override public void insertUpdate(DocumentEvent event) { filterTable(); } @Override public void removeUpdate(DocumentEvent event) { filterTable(); } @Override public void changedUpdate(DocumentEvent event) { filterTable(); } });
        return toolbar;
    }

    private void loadSelectedReport() {
        ReportType selected = selectedType();
        if (activeLoad != null && !activeLoad.isDone()) activeLoad.cancel(true);
        LocalDate start; LocalDate end;
        try {
            if (isInventoryReport(selected)) { start = LocalDate.now(); end = start; }
            else { start = LocalDate.parse(from.getText().trim()); end = selected == ReportType.DAILY_SALES ? start : LocalDate.parse(to.getText().trim()); }
        } catch (Exception exception) { footerSummary.setText("Use dates in yyyy-MM-dd format."); showError(new IllegalArgumentException("Use dates in yyyy-MM-dd format.")); return; }
        final long request = ++loadVersion; resetTableColumns(selected); resetAnalyticsState(selected); current = null; footerSummary.setText("Loading " + selected + "…"); notice.setText(""); load.setEnabled(false); table.setEnabled(false);
        activeLoad = new SwingWorker<>() {
            @Override protected ReportResult doInBackground() throws Exception { return reports.generate(selected, start, end, (Integer) nearExpiryDays.getValue()); }
            @Override protected void done() {
                if (isCancelled() || request != loadVersion) return;
                load.setEnabled(true); table.setEnabled(true);
                try { showResult(get(), selected); }
                catch (Exception exception) { Throwable cause = exception instanceof java.util.concurrent.ExecutionException && exception.getCause() != null ? exception.getCause() : exception; showFailedState(selected, cause); showError(cause); }
            }
        };
        activeLoad.execute();
    }

    private void reportTypeChanged() { if (!selectorUpdating) { configureDateFields(selectedType()); loadSelectedReport(); } }

    private void configureDateFields(ReportType selected) {
        boolean inventory = isInventoryReport(selected); boolean daily = selected == ReportType.DAILY_SALES; boolean nearExpiry = selected == ReportType.NEAR_EXPIRY_ITEMS;
        boolean calendarDates = !inventory; boolean range = calendarDates && !daily;
        fromLabel.setText(daily ? "Date" : "From Date"); toLabel.setText("To Date");
        fromControl.setVisible(calendarDates); toControl.setVisible(range); nearExpiryControl.setVisible(nearExpiry); quickRangeControl.setVisible(calendarDates);
        from.setEnabled(calendarDates); to.setEnabled(range); nearExpiryDays.setEnabled(nearExpiry);
        revalidate(); repaint();
    }

    private static boolean isInventoryReport(ReportType type) {
        return switch (type) {
            case PRODUCT_STOCK, LOW_STOCK, OUT_OF_STOCK, EXPIRED_ITEMS, NEAR_EXPIRY_ITEMS, SLOW_MOVING_PRODUCTS, STOCK_VALUATION, INVENTORY_OVERVIEW, LOYALTY_POINTS -> true;
            default -> false;
        };
    }
    private ReportType selectedType() { return (ReportType) reportType.getSelectedItem(); }

    private void resetTableColumns(ReportType selected) {
        model = modelFor(reports.columnsFor(selected)); table.setModel(model); configureTable(selected); ((CardLayout) tableState.getLayout()).show(tableState, "table");
    }

    /** Clears report-specific visuals before an asynchronous request can succeed or fail. */
    private void resetAnalyticsState(ReportType type) {
        updateCards(loadingMetrics(type), type); chart.setData(List.of(), false); chartTitle.setText(type == ReportType.INVENTORY_OVERVIEW ? "INVENTORY INSIGHTS" : "REPORT INSIGHTS"); resultTitle.setText(tableTitle(type));
        searchPanel.setVisible(supportsSearch(type)); search.setText("");
        showInsightArea("Loading " + type + "…");
    }

    private void showFailedState(ReportType type, Throwable cause) {
        updateCards(failedMetrics(type), type); chart.setData(List.of(), false); chartTitle.setText(type == ReportType.INVENTORY_OVERVIEW ? "INVENTORY INSIGHTS" : "REPORT INSIGHTS");
        resultTitle.setText(tableTitle(type)); footerSummary.setText("Load failed: " + usefulMessage(cause)); notice.setText("No report data was applied. You can correct the issue and load the report again.");
        showInsightArea("Unable to load " + type + ".");
    }

    private void showInsightArea(String message) {
        chartArea.removeAll(); chartArea.add(chartTitle, BorderLayout.NORTH);
        JLabel insight = new JLabel(message); insight.setFont(new Font("Dialog", Font.PLAIN, 13)); insight.setForeground(UITheme.GREY); insight.setBorder(BorderFactory.createEmptyBorder(8, 0, 4, 0)); chartArea.add(insight, BorderLayout.CENTER);
        chartArea.setMaximumSize(new Dimension(Integer.MAX_VALUE, 82)); chartArea.setPreferredSize(new Dimension(1, 82)); chartArea.revalidate(); chartArea.repaint();
    }

    private static List<ReportMetric> loadingMetrics(ReportType type) {
        if (type == ReportType.INVENTORY_OVERVIEW) return List.of(new ReportMetric("Total Products", "—"), new ReportMetric("Total Stock Units", "—"), new ReportMetric("Stock Cost Value", "—"), new ReportMetric("Potential Sales Value", "—"));
        return List.of(new ReportMetric("Loading", "—"), new ReportMetric("Loading", "—"), new ReportMetric("Loading", "—"), new ReportMetric("Loading", "—"));
    }

    private static List<ReportMetric> failedMetrics(ReportType type) {
        if (type == ReportType.INVENTORY_OVERVIEW) return List.of(new ReportMetric("Total Products", "Unavailable"), new ReportMetric("Total Stock Units", "Unavailable"), new ReportMetric("Stock Cost Value", "Unavailable"), new ReportMetric("Potential Sales Value", "Unavailable"));
        return List.of(new ReportMetric("Report", "Unavailable"), new ReportMetric("Status", "Load failed"), new ReportMetric("Action", "Try again"), new ReportMetric("Data", "Not applied"));
    }

    private DefaultTableModel modelFor(List<String> columns) { return new DefaultTableModel(columns.toArray(), 0) { @Override public boolean isCellEditable(int row, int column) { return false; } }; }

    private void showResult(ReportResult result, ReportType type) {
        current = result; model = modelFor(result.columns()); table.setModel(model);
        for (List<Object> row : result.rows()) model.addRow(row.toArray());
        configureTable(type); updateCards(result.metrics(), type); chart.setData(result.chartSeries(), result.barChart()); chartTitle.setText(result.barChart() ? "COMPARISON & INSIGHTS" : "TREND & INSIGHTS"); resultTitle.setText(tableTitle(type));
        footerSummary.setText(result.rows().isEmpty() ? emptyMessage(type) : "Rows: " + result.rows().size() + "   •   " + String.join("   •   ", result.summaryLines()) + "   •   Generated: " + java.time.LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm a"))); notice.setText(result.notice());
        ((CardLayout) tableState.getLayout()).show(tableState, result.rows().isEmpty() ? "empty" : "table");
        searchPanel.setVisible(supportsSearch(type) && result.rows().size() > 1); if (!searchPanel.isVisible()) search.setText("");
        updateChartArea(result, type);
    }

    private void updateCards(List<ReportMetric> metrics, ReportType type) {
        for (int index = 0; index < cards.length; index++) {
            ReportMetric metric = index < metrics.size() ? metrics.get(index) : new ReportMetric("—", "—");
            cards[index].setMetric(metric, cardColor(type, metric.label()));
        }
    }

    private static Color cardColor(ReportType type, String label) {
        String key = label.toLowerCase();
        if (type == ReportType.INVENTORY_OVERVIEW && (key.contains("potential sales") || key.contains("stock units"))) return UITheme.GREEN;
        if (type == ReportType.EXPIRED_ITEMS || key.contains("expired") || key.contains("low stock")) return UITheme.RED;
        if (type == ReportType.NEAR_EXPIRY_ITEMS || key.contains("near expiry")) return new Color(196, 132, 22);
        if (key.contains("profit") || key.contains("net sales")) return UITheme.GREEN;
        return UITheme.BLUE;
    }

    private void configureTable(ReportType type) {
        UITheme.styleTable(table); table.setDefaultRenderer(Object.class, new ReportTableRenderer(type)); sorter = new TableRowSorter<>(model); table.setRowSorter(sorter); filterTable();
        table.setAutoResizeMode(type == ReportType.PRODUCT_STOCK || type == ReportType.EXPIRED_ITEMS || type == ReportType.NEAR_EXPIRY_ITEMS ? JTable.AUTO_RESIZE_OFF : JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        for (int index = 0; index < table.getColumnCount(); index++) table.getColumnModel().getColumn(index).setPreferredWidth(preferredWidth(table.getColumnName(index)));
    }

    private static int preferredWidth(String column) {
        String name = column.toLowerCase();
        if (name.contains("product name") || name.contains("description") || name.contains("reason")) return 190;
        if (name.contains("date") || name.contains("invoice") || name.contains("barcode")) return 120;
        if (name.contains("status") || name.contains("category") || name.contains("payment")) return 115;
        return 105;
    }

    private static String tableTitle(ReportType type) {
        return switch (type) { case PRODUCT_STOCK -> "PRODUCT STOCK"; case LOW_STOCK -> "STOCK ALERTS"; case EXPIRED_ITEMS -> "EXPIRED STOCK"; case NEAR_EXPIRY_ITEMS -> "NEAR EXPIRY STOCK"; default -> "REPORT RESULTS"; };
    }

    private void filterTable() { if (sorter == null) return; String term = search.getText().trim(); sorter.setRowFilter(term.isEmpty() ? null : RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(term))); }

    private static boolean supportsSearch(ReportType type) {
        return switch (type) {
            case DAILY_SALES, PROFIT_SUMMARY, INVENTORY_OVERVIEW, BUSINESS_OVERVIEW -> false;
            default -> true;
        };
    }

    private void updateChartArea(ReportResult result, ReportType type) {
        chartArea.removeAll(); chartArea.add(chartTitle, BorderLayout.NORTH);
        if (result.chartSeries().isEmpty()) {
            JLabel insight = new JLabel(insightMessage(result, type)); insight.setFont(new Font("Dialog", Font.PLAIN, 13)); insight.setForeground(UITheme.GREY); insight.setBorder(BorderFactory.createEmptyBorder(8, 0, 4, 0)); chartArea.add(insight, BorderLayout.CENTER); chartArea.setMaximumSize(new Dimension(Integer.MAX_VALUE, 82)); chartArea.setPreferredSize(new Dimension(1, 82));
        } else {
            chartArea.add(chart, BorderLayout.CENTER); chartArea.setMaximumSize(new Dimension(Integer.MAX_VALUE, 250)); chartArea.setPreferredSize(new Dimension(1, 250));
        }
        chartArea.revalidate(); chartArea.repaint();
    }

    private static String insightMessage(ReportResult result, ReportType type) {
        if (!result.notice().isBlank()) return result.notice();
        return switch (type) {
            case LOW_STOCK -> result.rows().isEmpty() ? "All monitored stock levels are healthy." : result.rows().size() + " product(s) require restocking.";
            case OUT_OF_STOCK -> result.rows().isEmpty() ? "All active products currently have stock." : result.rows().size() + " product(s) are out of stock.";
            case EXPIRED_ITEMS -> result.rows().isEmpty() ? "No expiry risk detected." : result.rows().size() + " expired product(s) require action.";
            case NEAR_EXPIRY_ITEMS -> result.rows().isEmpty() ? "No expiry risk detected." : result.rows().size() + " product(s) require expiry review.";
            default -> result.rows().isEmpty() ? "No data is available for the selected report and period." : "Use the results table and KPI cards for this report.";
        };
    }

    private static String emptyMessage(ReportType type) {
        return switch (type) {
            case DAILY_SALES, DATE_RANGE_SALES, SALES_TREND -> "No sales recorded for the selected period.";
            case LOW_STOCK -> "All stock levels are healthy.";
            case OUT_OF_STOCK -> "No out-of-stock products found.";
            case EXPIRED_ITEMS, NEAR_EXPIRY_ITEMS -> "No expiry risk detected.";
            case EXPENSES -> "No expenses recorded for the selected period.";
            case VOID_SALES -> "No voided sales found.";
            default -> "No records found for the selected report and period.";
        };
    }

    private void printReport() {
        if (current == null) return;
        try { table.print(JTable.PrintMode.FIT_WIDTH, new java.text.MessageFormat(current.title()), new java.text.MessageFormat("Page {0}")); }
        catch (PrinterException exception) { showError(exception); }
    }

    private void exportCsv() {
        if (current == null) return;
        JFileChooser chooser = new JFileChooser(); chooser.setSelectedFile(new java.io.File(current.title().replaceAll("[^A-Za-z0-9]+", "_") + ".csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try (var writer = Files.newBufferedWriter(Path.of(chooser.getSelectedFile().toURI()), StandardCharsets.UTF_8)) {
            writeCsv(writer, current.columns()); for (List<Object> row : current.rows()) writeCsv(writer, row.stream().map(ReportsFrame::display).toList());
            writer.newLine(); for (String line : current.summaryLines()) writer.write(csv(line) + "\n"); if (!current.notice().isBlank()) writer.write(csv(current.notice()) + "\n");
            Dialogs.success(this, "Reports", "CSV exported successfully.");
        } catch (Exception exception) { showError(exception); }
    }

    private void exportExcel() {
        if (current == null) return;
        JFileChooser chooser = new JFileChooser(); chooser.setFileFilter(new FileNameExtensionFilter("Excel Workbook (*.xlsx)", "xlsx"));
        chooser.setSelectedFile(new java.io.File(excelFileName()));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        java.io.File selected = chooser.getSelectedFile();
        if (!selected.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".xlsx")) selected = new java.io.File(selected.getParentFile(), selected.getName() + ".xlsx");
        try {
            ReportExcelExporter.export(selected.toPath(), current, selectedType(), reportPeriod());
            Dialogs.success(this, "Reports", "Excel report exported successfully.\n" + selected.getAbsolutePath());
        } catch (Exception exception) { showError(exception); }
    }

    private String excelFileName() {
        String title = current.title().replaceAll("[^A-Za-z0-9]+", "_").replaceAll("^_|_$", ""); String suffix;
        if (isInventoryReport(selectedType())) suffix = LocalDate.now().toString();
        else if (selectedType() == ReportType.DAILY_SALES) suffix = from.getText().trim();
        else suffix = from.getText().trim() + "_to_" + to.getText().trim();
        return title + "_" + suffix + ".xlsx";
    }

    private String reportPeriod() {
        if (selectedType() == ReportType.NEAR_EXPIRY_ITEMS) return "Within " + nearExpiryDays.getValue() + " days as at " + LocalDate.now();
        if (isInventoryReport(selectedType())) return "As at " + LocalDate.now();
        return selectedType() == ReportType.DAILY_SALES ? from.getText().trim() : from.getText().trim() + " to " + to.getText().trim();
    }

    private static JLabel sectionTitle(String text) { JLabel label = new JLabel(text); label.setFont(new Font("Dialog", Font.BOLD, 13)); label.setForeground(UITheme.GREY); return label; }
    private static JLabel filterLabel(String text) { JLabel label = new JLabel(text); label.setFont(new Font("Dialog", Font.BOLD, 12)); label.setForeground(UITheme.GREY); return label; }
    private static void writeCsv(java.io.BufferedWriter writer, List<String> values) throws java.io.IOException { writer.write(String.join(",", values.stream().map(ReportsFrame::csv).toList())); writer.newLine(); }
    private static String csv(String value) { return '"' + (value == null ? "" : value.replace("\"", "\"\"")) + '"'; }
    private static String display(Object value) { return value instanceof BigDecimal money ? money.setScale(2, RoundingMode.HALF_UP).toPlainString() : value == null ? "" : value.toString(); }
    private static String usefulMessage(Throwable exception) { return exception.getMessage() == null || exception.getMessage().isBlank() ? exception.getClass().getSimpleName() : exception.getMessage(); }
    private void showError(Throwable exception) { Dialogs.error(this, "Reports", usefulMessage(exception)); }

    private enum ReportCategory {
        OVERVIEW("Overview"), SALES("Sales"), PROFIT("Profit"), INVENTORY("Inventory"), PURCHASES("Purchases"), EXPENSES("Expenses"), CUSTOMERS("Customers"), CASHIERS("Cashiers"), SUPPLIERS("Suppliers");
        private final String label;
        ReportCategory(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    private static final class MetricCard extends JPanel {
        private final JLabel title = new JLabel("—"); private final JLabel value = new JLabel("—");
        private boolean hovered;
        private MetricCard() {
            super(new BorderLayout(0, 5)); setOpaque(false); setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
            title.setFont(new Font("Dialog", Font.BOLD, 11)); title.setForeground(UITheme.GREY); value.setFont(new Font("Dialog", Font.BOLD, 20)); add(title, BorderLayout.NORTH); add(value, BorderLayout.CENTER);
            setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() { @Override public void mouseEntered(MouseEvent event) { hovered = true; repaint(); } @Override public void mouseExited(MouseEvent event) { hovered = false; repaint(); } });
        }
        private void setMetric(ReportMetric metric, Color color) { title.setText(metric.label().toUpperCase()); value.setText(metric.value()); value.setForeground(color); }
        @Override protected void paintComponent(java.awt.Graphics graphics) { java.awt.Graphics2D g = (java.awt.Graphics2D) graphics.create(); g.setColor(hovered ? new Color(248, 251, 254) : Color.WHITE); g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 15, 15); g.setColor(hovered ? new Color(181, 205, 226) : UITheme.BORDER); g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 15, 15); g.dispose(); super.paintComponent(graphics); }
    }

    private static final class ReportTableRenderer extends DefaultTableCellRenderer {
        private final ReportType type;
        private static final javax.swing.border.Border CELL_PADDING = BorderFactory.createEmptyBorder(0, 7, 0, 7);
        private ReportTableRenderer(ReportType type) { this.type = type; setBorder(CELL_PADDING); }
        @Override public Component getTableCellRendererComponent(JTable source, Object value, boolean selected, boolean focused, int row, int column) {
            int modelRow = source.convertRowIndexToModel(row); String name = source.getColumnName(column); boolean metricTable = source.getColumnCount() == 2 && "Metric".equalsIgnoreCase(source.getColumnName(0));
            String metricName = metricTable ? String.valueOf(source.getModel().getValueAt(modelRow, 0)) : name; String text = formatted(value, name, metricName, metricTable); super.getTableCellRendererComponent(source, text, selected, focused, row, column);
            if (metricTable) setHorizontalAlignment(column == 0 ? SwingConstants.LEFT : SwingConstants.RIGHT);
            else if (name.equalsIgnoreCase("status")) setHorizontalAlignment(SwingConstants.CENTER);
            else setHorizontalAlignment(isNumber(value) || String.valueOf(value).endsWith("%") ? SwingConstants.RIGHT : (name.toLowerCase().contains("date") ? SwingConstants.CENTER : SwingConstants.LEFT));
            Color foreground = Color.BLACK; Color background = row % 2 == 0 ? Color.WHITE : new Color(249, 251, 253);
            if (type == ReportType.LOW_STOCK) { boolean zero = numeric(source.getModel().getValueAt(modelRow, 2)).signum() <= 0; background = zero ? new Color(255, 222, 222) : new Color(255, 239, 239); foreground = UITheme.RED; }
            else if (type == ReportType.OUT_OF_STOCK) { background = new Color(255, 217, 217); foreground = new Color(158, 32, 32); }
            else if (type == ReportType.EXPIRED_ITEMS) { background = new Color(255, 222, 228); foreground = new Color(165, 38, 55); }
            else if (type == ReportType.NEAR_EXPIRY_ITEMS) { int days = numeric(source.getModel().getValueAt(modelRow, 6)).intValue(); background = days <= 7 ? new Color(255, 234, 185) : new Color(255, 246, 212); foreground = new Color(132, 87, 5); }
            else if (type == ReportType.VOID_SALES) { background = new Color(255, 235, 235); foreground = UITheme.RED; }
            else if (type == ReportType.SLOW_MOVING_PRODUCTS) { background = new Color(255, 248, 224); foreground = new Color(132, 87, 5); }
            else if (type == ReportType.PROFIT_SUMMARY && value instanceof Number && numeric(value).signum() < 0) foreground = UITheme.RED;
            StatusStyle statusStyle = statusStyle(source, modelRow, foreground, background);
            if (statusStyle != null) {
                setBackground(selected ? statusStyle.selectedBackground() : statusStyle.background()); setForeground(statusStyle.foreground());
                setBorder(selected ? BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(statusStyle.selectionBorder(), 1), BorderFactory.createEmptyBorder(0, 6, 0, 6)) : CELL_PADDING);
            } else {
                setBackground(selected ? new Color(218, 234, 248) : background); setForeground(selected ? UITheme.NAVY : foreground); setBorder(CELL_PADDING);
            }
            return this;
        }

        /** A single status-aware palette preserves warning meaning for mouse and keyboard selections. */
        private StatusStyle statusStyle(JTable source, int modelRow, Color foreground, Color background) {
            if (type == ReportType.LOW_STOCK) {
                boolean zero = numeric(source.getModel().getValueAt(modelRow, 2)).signum() <= 0;
                return zero ? new StatusStyle(new Color(255, 222, 222), new Color(248, 194, 194), new Color(145, 38, 38), new Color(122, 32, 32))
                        : new StatusStyle(new Color(255, 239, 239), new Color(255, 215, 215), UITheme.RED, new Color(159, 49, 49));
            }
            if (type == ReportType.OUT_OF_STOCK) return new StatusStyle(new Color(255, 217, 217), new Color(247, 185, 185), new Color(158, 32, 32), new Color(126, 28, 28));
            if (type == ReportType.EXPIRED_ITEMS) return new StatusStyle(new Color(255, 222, 228), new Color(247, 194, 205), new Color(165, 38, 55), new Color(135, 31, 45));
            if (type == ReportType.NEAR_EXPIRY_ITEMS) {
                int days = numeric(source.getModel().getValueAt(modelRow, 6)).intValue();
                return days <= 7 ? new StatusStyle(new Color(255, 234, 185), new Color(255, 213, 139), new Color(132, 87, 5), new Color(116, 75, 4))
                        : new StatusStyle(new Color(255, 246, 212), new Color(255, 232, 171), new Color(132, 87, 5), new Color(116, 75, 4));
            }
            if (type == ReportType.VOID_SALES) return new StatusStyle(new Color(255, 235, 235), new Color(249, 208, 208), UITheme.RED, new Color(159, 49, 49));
            if (type == ReportType.SLOW_MOVING_PRODUCTS) return new StatusStyle(new Color(255, 248, 224), new Color(255, 234, 181), new Color(132, 87, 5), new Color(116, 75, 4));
            if (type == ReportType.PROFIT_SUMMARY && foreground.equals(UITheme.RED)) return new StatusStyle(background, new Color(255, 231, 231), UITheme.RED, new Color(159, 49, 49));
            return null;
        }
        private static String formatted(Object value, String column, String metricName, boolean metricTable) {
            if (value == null) return ""; if (value instanceof java.sql.Timestamp stamp) return stamp.toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")); if (value instanceof java.sql.Date date) return date.toLocalDate().toString(); if (value instanceof LocalDateTime time) return time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            if (isNumber(value)) return isCurrencyColumn(metricTable ? metricName : column) ? UITheme.money(numeric(value)) : plainNumber(numeric(value));
            return value.toString();
        }
        /** Currency is applied by metric meaning, never merely because a value appears in the Value column. */
        private static boolean isCurrencyColumn(String column) {
            String value = column.toLowerCase();
            if (value.contains("count") || value.contains("transaction") || value.contains("product") || value.contains("customer") || value.contains("supplier") || value.contains("unit") || value.contains("quantity") || value.contains("qty") || value.contains("shortage") || value.contains("days")) return false;
            return value.contains("sales") || value.contains("subtotal") || value.contains("price") || value.contains("amount") || value.contains("value") || value.contains("cost") || value.contains("contribution") || value.contains("profit") || value.contains("bill") || value.contains("expense") || value.contains("discount") || value.contains("cash") || value.contains("card") || value.contains("purchase");
        }
        private static boolean isNumber(Object value) { return value instanceof Number || value instanceof BigDecimal; }
        private static BigDecimal numeric(Object value) { if (value instanceof BigDecimal decimal) return decimal; if (value instanceof Number number) return new BigDecimal(number.toString()); try { return new BigDecimal(String.valueOf(value)); } catch (NumberFormatException ignored) { return BigDecimal.ZERO; } }
        private static String plainNumber(BigDecimal value) { return value.signum() == 0 ? "0" : value.stripTrailingZeros().toPlainString(); }
        private record StatusStyle(Color background, Color selectedBackground, Color foreground, Color selectionBorder) { }
    }
}
