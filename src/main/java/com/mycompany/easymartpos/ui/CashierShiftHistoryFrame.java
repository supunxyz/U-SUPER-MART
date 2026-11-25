package com.mycompany.easymartpos.ui;

import com.mycompany.easymartpos.Dialogs;
import com.mycompany.easymartpos.model.CashierOption;
import com.mycompany.easymartpos.model.ShiftDetails;
import com.mycompany.easymartpos.model.ShiftHistoryRow;
import com.mycompany.easymartpos.model.ShiftHistorySummary;
import com.mycompany.easymartpos.service.Authorization;
import com.mycompany.easymartpos.service.ShiftHistoryService;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;

/** Admin-only, read-only cashier shift history and cash reconciliation screen. */
public final class CashierShiftHistoryFrame extends JFrame {
    private static final String[] COLUMNS = {"Shift ID", "Cashier", "Opened At", "Closed At", "Opening Cash", "Transactions",
        "Cash Sales", "Card Sales", "Transfer Sales", "Total Sales", "Expected Cash", "Closing Cash", "Difference", "Status"};
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final ShiftHistoryService history = new ShiftHistoryService();
    private final JTextField from = new JTextField(LocalDate.now().toString(), 10);
    private final JTextField to = new JTextField(LocalDate.now().toString(), 10);
    private final JComboBox<CashierOption> cashier = new JComboBox<>();
    private final JComboBox<String> status = new JComboBox<>(new String[]{"All", "OPEN", "CLOSED"});
    private final JButton refresh = new JButton("Refresh");
    private final JLabel summary = new JLabel("Loading shift history...");
    private final JLabel totalShiftsValue = summaryValue();
    private final JLabel totalTransactionsValue = summaryValue();
    private final JLabel totalSalesValue = summaryValue();
    private final JLabel cashSalesValue = summaryValue();
    private final JLabel cardSalesValue = summaryValue();
    private final JLabel transferSalesValue = summaryValue();
    private final JLabel differenceValue = summaryValue();
    private DefaultTableModel model = newModel();
    private final JTable table = new JTable(model);
    private final Timer dateLoadTimer;
    private ShiftHistoryService.ShiftHistoryResult current;
    private SwingWorker<ShiftHistoryService.ShiftHistoryResult, Void> activeLoad;
    private long loadVersion;
    private boolean loadingFilters;

    public CashierShiftHistoryFrame() {
        super("Cashier Shifts | History and Reconciliation");
        Authorization.requireAdmin();
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(1420, 720);
        setMinimumSize(new Dimension(1100, 580));
        setLocationByPlatform(true);

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBackground(new Color(245, 247, 250));
        root.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        root.add(filters(), BorderLayout.NORTH);
        table.setAutoCreateRowSorter(true);
        table.setRowHeight(25);
        table.setFillsViewportHeight(true);
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) { if (event.getClickCount() == 2 && event.getButton() == MouseEvent.BUTTON1) showDetails(); }
        });
        root.add(new JScrollPane(table), BorderLayout.CENTER);
        summary.setFont(new Font("Dialog", Font.BOLD, 13));
        JPanel bottom = new JPanel(new BorderLayout(0, 7));
        bottom.setOpaque(false); bottom.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0)); bottom.add(summary, BorderLayout.NORTH);
        JPanel cards = new JPanel(new java.awt.GridLayout(1, 7, 8, 0)); cards.setOpaque(false);
        cards.add(summaryCard("TOTAL SHIFTS", totalShiftsValue)); cards.add(summaryCard("TRANSACTIONS", totalTransactionsValue)); cards.add(summaryCard("TOTAL SALES", totalSalesValue)); cards.add(summaryCard("CASH SALES", cashSalesValue)); cards.add(summaryCard("CARD SALES", cardSalesValue)); cards.add(summaryCard("TRANSFER SALES", transferSalesValue)); cards.add(summaryCard("CASH DIFFERENCE", differenceValue));
        bottom.add(cards, BorderLayout.CENTER);
        root.add(bottom, BorderLayout.SOUTH);
        add(root);
        UITheme.applyWindow(this);

        dateLoadTimer = new Timer(500, event -> loadHistory(false));
        dateLoadTimer.setRepeats(false);
        installListeners();
        loadCashiers();
        loadHistory(false);
    }

    private JPanel filters() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        panel.setOpaque(false);
        JLabel title = new JLabel("CASHIER SHIFT HISTORY"); title.setFont(new Font("Dialog", Font.BOLD, 18)); title.setForeground(new Color(27, 65, 100));
        panel.add(title); panel.add(new JLabel("    From (yyyy-MM-dd)")); panel.add(from); panel.add(new JLabel("To")); panel.add(to);
        panel.add(new JLabel("Cashier")); panel.add(cashier); panel.add(new JLabel("Status")); panel.add(status);
        refresh.addActionListener(event -> loadHistory(true)); panel.add(refresh);
        JButton export = new JButton("Export CSV"); export.addActionListener(event -> exportCsv()); panel.add(export);
        return panel;
    }

    private void installListeners() {
        DocumentListener datesChanged = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { scheduleDateLoad(); }
            @Override public void removeUpdate(DocumentEvent event) { scheduleDateLoad(); }
            @Override public void changedUpdate(DocumentEvent event) { scheduleDateLoad(); }
        };
        from.getDocument().addDocumentListener(datesChanged); to.getDocument().addDocumentListener(datesChanged);
        FocusAdapter dateFocus = new FocusAdapter() { @Override public void focusLost(FocusEvent event) { loadHistory(true); } };
        from.addFocusListener(dateFocus); to.addFocusListener(dateFocus);
        from.addActionListener(event -> loadHistory(true)); to.addActionListener(event -> loadHistory(true));
        cashier.addActionListener(event -> { if (!loadingFilters) loadHistory(false); });
        status.addActionListener(event -> { if (!loadingFilters) loadHistory(false); });
    }

    private void scheduleDateLoad() { if (!loadingFilters) dateLoadTimer.restart(); }

    private void loadCashiers() {
        loadingFilters = true;
        try {
            cashier.removeAllItems();
            for (CashierOption option : history.cashiers()) cashier.addItem(option);
        } catch (Exception exception) {
            showError(exception);
        } finally {
            loadingFilters = false;
        }
    }

    /** The one loading path used by every filter and the Refresh button. */
    private void loadHistory(boolean showValidationError) {
        if (loadingFilters) return;
        final LocalDate start;
        final LocalDate end;
        try {
            start = LocalDate.parse(from.getText().trim());
            end = LocalDate.parse(to.getText().trim());
            if (end.isBefore(start)) throw new IllegalArgumentException("To Date cannot be before From Date.");
        } catch (Exception exception) {
            current = null;
            resetModel();
            clearSummaryCards();
            summary.setText("Enter valid From and To dates in yyyy-MM-dd format.");
            if (showValidationError) showError(new IllegalArgumentException("Enter valid From and To dates in yyyy-MM-dd format."));
            return;
        }
        if (activeLoad != null && !activeLoad.isDone()) activeLoad.cancel(true);
        final CashierOption selectedCashier = (CashierOption) cashier.getSelectedItem();
        final String selectedStatus = "All".equals(status.getSelectedItem()) ? null : (String) status.getSelectedItem();
        final long request = ++loadVersion;
        current = null;
        resetModel();
        clearSummaryCards();
        summary.setText("Loading shift history...");
        refresh.setEnabled(false);
        activeLoad = new SwingWorker<>() {
            @Override protected ShiftHistoryService.ShiftHistoryResult doInBackground() throws Exception {
                return history.load(start, end, selectedCashier == null ? null : selectedCashier.userId(), selectedStatus);
            }
            @Override protected void done() {
                if (isCancelled() || request != loadVersion) return;
                refresh.setEnabled(true);
                try { showResult(get()); }
                catch (Exception exception) { summary.setText("Load failed: " + usefulMessage(exception)); showError(exception); }
            }
        };
        activeLoad.execute();
    }

    private void resetModel() { model = newModel(); table.setModel(model); configureWidths(); }

    private DefaultTableModel newModel() {
        return new DefaultTableModel(COLUMNS, 0) { @Override public boolean isCellEditable(int row, int column) { return false; } };
    }

    private void configureWidths() {
        int[] widths = {65, 110, 125, 125, 105, 85, 100, 100, 100, 105, 110, 105, 100, 75};
        for (int index = 0; index < widths.length; index++) table.getColumnModel().getColumn(index).setPreferredWidth(widths[index]);
    }

    private void showResult(ShiftHistoryService.ShiftHistoryResult result) {
        current = result;
        resetModel();
        for (ShiftHistoryRow row : result.rows()) {
            model.addRow(new Object[]{row.shiftId(), row.cashier(), date(row.openedAt()), date(row.closedAt()), money(row.openingCash()),
                row.transactions(), money(row.cashSales()), money(row.cardSales()), money(row.transferSales()), money(row.totalSales()), money(row.expectedCash()),
                row.closingCash() == null ? "" : money(row.closingCash()), row.difference() == null ? "" : differenceMoney(row.difference()), row.status()});
        }
        setSummaryCards(result.summary());
        summary.setText(result.rows().isEmpty() ? "No shifts found for the selected filters." : "Filtered shift reconciliation totals");
    }

    private String summaryText(ShiftHistorySummary value) {
        return "Total Shifts: " + value.totalShifts() + "   |   Total Transactions: " + value.totalTransactions()
                + "   |   Total Sales: " + money(value.totalSales()) + "   |   Cash Sales: " + money(value.cashSales())
                + "   |   Card Sales: " + money(value.cardSales()) + "   |   Transfer Sales: " + money(value.transferSales()) + "   |   Total Cash Difference: " + differenceMoney(value.totalCashDifference());
    }

    private void setSummaryCards(ShiftHistorySummary value) {
        totalShiftsValue.setText(Long.toString(value.totalShifts())); totalTransactionsValue.setText(Long.toString(value.totalTransactions()));
        totalSalesValue.setText(money(value.totalSales())); cashSalesValue.setText(money(value.cashSales())); cardSalesValue.setText(money(value.cardSales())); transferSalesValue.setText(money(value.transferSales())); differenceValue.setText(differenceMoney(value.totalCashDifference()));
    }
    private void clearSummaryCards() {
        totalShiftsValue.setText("0"); totalTransactionsValue.setText("0"); totalSalesValue.setText("LKR 0.00"); cashSalesValue.setText("LKR 0.00"); cardSalesValue.setText("LKR 0.00"); transferSalesValue.setText("LKR 0.00"); differenceValue.setText("LKR 0.00");
    }

    private JPanel summaryCard(String heading, JLabel value) {
        JPanel card = UITheme.card(new BorderLayout(2, 2)); card.setBorder(BorderFactory.createEmptyBorder(9, 10, 9, 10));
        JLabel title = new JLabel(heading); title.setFont(new Font("Dialog", Font.BOLD, 10)); title.setForeground(UITheme.GREY); card.add(title, BorderLayout.NORTH); card.add(value, BorderLayout.CENTER); return card;
    }
    private static JLabel summaryValue() { JLabel label = new JLabel("0"); label.setFont(new Font("Dialog", Font.BOLD, 15)); label.setForeground(UITheme.NAVY); return label; }

    private void showDetails() {
        int selected = table.getSelectedRow();
        if (selected < 0 || current == null) return;
        int row = table.convertRowIndexToModel(selected);
        try { ShiftDetailsDialog.show(this, history.details(current.rows().get(row).shiftId())); }
        catch (Exception exception) { showError(exception); }
    }

    private void exportCsv() {
        if (current == null) return;
        JFileChooser chooser = new JFileChooser(); chooser.setSelectedFile(new java.io.File("cashier_shift_history.csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try (var writer = Files.newBufferedWriter(Path.of(chooser.getSelectedFile().toURI()), StandardCharsets.UTF_8)) {
            writeCsv(writer, COLUMNS);
            for (int row = 0; row < model.getRowCount(); row++) {
                String[] values = new String[model.getColumnCount()];
                for (int column = 0; column < values.length; column++) values[column] = String.valueOf(model.getValueAt(row, column));
                writeCsv(writer, values);
            }
            writer.write(csv(summary.getText())); writer.newLine();
            Dialogs.success(this, "Cashier Shifts", "CSV exported successfully.");
        } catch (Exception exception) { showError(exception); }
    }

    private void writeCsv(java.io.BufferedWriter writer, String[] values) throws java.io.IOException {
        for (int index = 0; index < values.length; index++) { if (index > 0) writer.write(','); writer.write(csv(values[index])); }
        writer.newLine();
    }
    private String csv(String value) { return '"' + (value == null ? "" : value.replace("\"", "\"\"")) + '"'; }
    private String money(BigDecimal value) { return "LKR " + value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(); }
    private String differenceMoney(BigDecimal value) {
        int sign = value.signum();
        if (sign > 0) return "+LKR " + value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
        if (sign < 0) return "-LKR " + value.abs().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
        return money(value);
    }
    private String date(LocalDateTime value) { return value == null ? "" : DATE_TIME.format(value); }
    private String usefulMessage(Throwable value) { Throwable cause = value instanceof java.util.concurrent.ExecutionException && value.getCause() != null ? value.getCause() : value; return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage(); }
    private void showError(Throwable exception) { Dialogs.error(this, "Cashier Shifts", usefulMessage(exception)); }

    private static final class ShiftDetailsDialog {
        private static void show(JFrame owner, ShiftDetails details) {
            JDialog dialog = new JDialog(owner, "Shift #" + details.shift().shiftId() + " Details", true);
            JPanel fields = new JPanel(new GridBagLayout()); fields.setBorder(BorderFactory.createEmptyBorder(12, 14, 8, 14));
            ShiftHistoryRow shift = details.shift();
            String[] labels = {"Cashier", "Opened At", "Closed At", "Opening Cash", "Cash Sales", "Card Sales", "Total Sales", "Transactions", "Expected Cash", "Closing Cash", "Difference", "Status"};
            String[] values = {shift.cashier(), date(shift.openedAt()), date(shift.closedAt()), money(shift.openingCash()), money(shift.cashSales()), money(shift.cardSales()), money(shift.totalSales()), Long.toString(shift.transactions()), money(shift.expectedCash()), shift.closingCash() == null ? "" : money(shift.closingCash()), shift.difference() == null ? "" : money(shift.difference()), shift.status()};
            for (int index = 0; index < labels.length; index++) addField(fields, index, labels[index], values[index]);
            DefaultTableModel sales = new DefaultTableModel(new String[]{"Invoice", "Date / Time", "Payment", "Total"}, 0) { @Override public boolean isCellEditable(int row, int column) { return false; } };
            for (var sale : details.sales()) sales.addRow(new Object[]{sale.invoiceNumber(), date(sale.saleDate()), sale.paymentMethod(), money(sale.total())});
            JTable salesTable = new JTable(sales); salesTable.setRowHeight(23); salesTable.setFillsViewportHeight(true);
            JPanel content = new JPanel(new BorderLayout(8, 8)); content.add(fields, BorderLayout.NORTH); content.add(new JScrollPane(salesTable), BorderLayout.CENTER);
            JButton close = new JButton("Close"); close.addActionListener(event -> dialog.dispose()); JPanel buttons = new JPanel(); buttons.add(close); content.add(buttons, BorderLayout.SOUTH);
            dialog.setContentPane(content); dialog.setSize(700, 600); dialog.setLocationRelativeTo(owner); dialog.setVisible(true);
        }
        private static void addField(JPanel panel, int row, String label, String value) {
            GridBagConstraints c = new GridBagConstraints(); c.insets = new Insets(3, 2, 3, 12); c.anchor = GridBagConstraints.WEST; c.gridx = (row % 2) * 2; c.gridy = row / 2;
            JLabel name = new JLabel(label + ":"); name.setFont(new Font("Dialog", Font.BOLD, 12)); panel.add(name, c);
            c.gridx++; c.insets = new Insets(3, 2, 3, 18); JLabel amount = new JLabel(value); amount.setHorizontalAlignment(SwingConstants.RIGHT); panel.add(amount, c);
        }
        private static String money(BigDecimal value) { return "LKR " + value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(); }
        private static String date(LocalDateTime value) { return value == null ? "" : DATE_TIME.format(value); }
    }
}
