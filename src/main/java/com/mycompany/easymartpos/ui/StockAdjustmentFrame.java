package com.mycompany.easymartpos.ui;

import com.mycompany.easymartpos.Dialogs;
import com.mycompany.easymartpos.model.Product;
import com.mycompany.easymartpos.model.StockAdjustmentBatch;
import com.mycompany.easymartpos.model.StockAdjustmentHistory;
import com.mycompany.easymartpos.repository.ProductRepository;
import com.mycompany.easymartpos.service.Authorization;
import com.mycompany.easymartpos.service.Session;
import com.mycompany.easymartpos.service.StockAdjustmentService;
import com.mycompany.easymartpos.util.Money;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/** Admin-only, batch-aware manual inventory adjustments. */
public final class StockAdjustmentFrame extends JFrame {
    private enum AdjustmentType {
        ADD_STOCK("ADD STOCK"), REMOVE_STOCK("REMOVE STOCK");
        private final String label;
        AdjustmentType(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    private final ProductRepository products = new ProductRepository();
    private final StockAdjustmentService adjustments = new StockAdjustmentService();
    private final JButton addStockModeButton = new JButton("ADD STOCK");
    private final JButton removeStockModeButton = new JButton("REMOVE STOCK");
    private final JLabel activeModeLabel = new JLabel();
    private final JComboBox<Product> product = new JComboBox<>();
    private final JTextField totalStock = new JTextField("0", 10);
    private final JTextField quantity = new JTextField(10);
    private final JTextField reason = new JTextField(28);
    private final JTextField costPrice = new JTextField(10);
    private final JTextField sellingPrice = new JTextField(10);
    private final JTextField retailPrice = new JTextField(10);
    private final JTextField expiryDate = new JTextField(10);
    private final CardLayout modeLayout = new CardLayout();
    private final JPanel modePanel = new JPanel(modeLayout);
    private final JPanel modeCard = UITheme.card(new BorderLayout(0, 8));
    private final DefaultTableModel batchModel = readonlyModel("Batch ID", "Source", "Cost Price", "Our Price", "Retail Price", "Expiry Date", "Remaining Qty", "Status");
    private final JTable batchTable = new JTable(batchModel);
    private final DefaultTableModel historyModel = readonlyModel("Date", "Product", "Type", "Quantity", "Reason", "User");
    private final JTable historyTable = new JTable(historyModel);
    private List<StockAdjustmentBatch> removableBatches = List.of();
    private AdjustmentType activeMode = AdjustmentType.ADD_STOCK;
    private boolean loadingProducts;

    public StockAdjustmentFrame() { this(null); }

    /** Opens directly on a known product when launched from Product Management. */
    public StockAdjustmentFrame(int initialProductId) { this(Integer.valueOf(initialProductId)); }

    private StockAdjustmentFrame(Integer initialProductId) {
        super("Stock Adjustment");
        Authorization.requireAdmin();
        setDefaultCloseOperation(DISPOSE_ON_CLOSE); setSize(1120, 780); setMinimumSize(new java.awt.Dimension(900, 640)); setLocationByPlatform(true);
        totalStock.setEditable(false); totalStock.setToolTipText("Read-only total of remaining batch quantities.");
        compactField(quantity); compactField(reason); compactField(costPrice); compactField(sellingPrice); compactField(retailPrice); compactField(expiryDate);
        product.setMaximumRowCount(18); product.addActionListener(event -> { if (!loadingProducts) loadSelectedProduct(); });
        addStockModeButton.addActionListener(event -> switchAdjustmentMode(AdjustmentType.ADD_STOCK));
        removeStockModeButton.addActionListener(event -> switchAdjustmentMode(AdjustmentType.REMOVE_STOCK));

        JPanel details = UITheme.card(new BorderLayout(0, 10));
        JPanel detailsHeader = new JPanel(new BorderLayout(0, 6)); detailsHeader.setOpaque(false);
        detailsHeader.add(sectionTitle("ADJUSTMENT DETAILS"), BorderLayout.NORTH);
        JPanel modeButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0)); modeButtons.setOpaque(false);
        modeButtons.add(addStockModeButton); modeButtons.add(removeStockModeButton); modeButtons.add(activeModeLabel);
        detailsHeader.add(modeButtons, BorderLayout.SOUTH); details.add(detailsHeader, BorderLayout.NORTH);
        JPanel fields = new JPanel(new GridLayout(0, 4, 12, 9)); fields.setOpaque(false);
        addRow(fields, "Product", product, "Current Total Stock", totalStock);
        addRow(fields, "Quantity", quantity, "Reason", reason);
        details.add(fields, BorderLayout.CENTER);

        modePanel.setOpaque(false); modePanel.add(addMode(), AdjustmentType.ADD_STOCK.name()); modePanel.add(removeMode(), AdjustmentType.REMOVE_STOCK.name());
        modeCard.add(modePanel, BorderLayout.CENTER);

        JButton apply = new JButton("Apply Adjustment"); apply.addActionListener(event -> applyAdjustment());
        JButton clear = new JButton("Clear"); clear.addActionListener(event -> clear());
        JButton refresh = new JButton("Refresh"); refresh.addActionListener(event -> refresh());
        JPanel actions = UITheme.card(new FlowLayout(FlowLayout.LEFT, 10, 2)); actions.add(apply); actions.add(clear); actions.add(refresh);

        JPanel editor = new JPanel(new BorderLayout(0, 10)); editor.setOpaque(false); editor.add(details, BorderLayout.NORTH); editor.add(modeCard, BorderLayout.CENTER);

        JPanel history = UITheme.card(new BorderLayout(0, 8)); history.add(sectionTitle("ADJUSTMENT HISTORY"), BorderLayout.NORTH);
        configureHistoryTable(); history.add(new JScrollPane(historyTable), BorderLayout.CENTER);
        javax.swing.JSplitPane content = new javax.swing.JSplitPane(javax.swing.JSplitPane.VERTICAL_SPLIT, editor, history);
        content.setResizeWeight(.46d); content.setBorder(null); content.setDividerSize(8);
        JPanel root = new JPanel(new BorderLayout(14, 14)); root.setBackground(UITheme.BACKGROUND); root.setBorder(javax.swing.BorderFactory.createEmptyBorder(18, 20, 18, 20));
        root.add(UITheme.pageHeader("STOCK ADJUSTMENT", "Controlled batch-aware corrections outside supplier purchases"), BorderLayout.NORTH);
        root.add(content, BorderLayout.CENTER); root.add(actions, BorderLayout.SOUTH); add(root); UITheme.applyWindow(this);
        UITheme.styleSuccess(apply); UITheme.styleSecondary(clear); UITheme.stylePrimary(refresh);
        UITheme.styleSuccess(addStockModeButton); UITheme.styleDanger(removeStockModeButton);
        configureBatchTable(); loadProducts(initialProductId); loadHistory(); switchAdjustmentMode(AdjustmentType.ADD_STOCK);
    }

    private JPanel addMode() {
        JPanel panel = new JPanel(new BorderLayout(0, 8)); panel.setOpaque(false);
        panel.add(sectionTitle("ADD STOCK — NEW ADJUSTMENT BATCH"), BorderLayout.NORTH);
        JPanel fields = new JPanel(new GridBagLayout()); fields.setOpaque(false);
        addCompactField(fields, "Cost Price", costPrice, 0, 0); addCompactField(fields, "Our Selling Price", sellingPrice, 1, 0);
        addCompactField(fields, "Retail Price (optional)", retailPrice, 0, 2); addCompactField(fields, "Expiry Date (yyyy-MM-dd)", expiryDate, 1, 2);
        JPanel wrapper = new JPanel(new BorderLayout()); wrapper.setOpaque(false); wrapper.add(fields, BorderLayout.NORTH);
        panel.add(wrapper, BorderLayout.CENTER); return panel;
    }

    private JPanel removeMode() {
        JPanel panel = new JPanel(new BorderLayout(0, 8)); panel.setOpaque(false);
        panel.add(sectionTitle("REMOVE STOCK — SELECT THE EXACT BATCH"), BorderLayout.NORTH);
        panel.add(new JScrollPane(batchTable), BorderLayout.CENTER); return panel;
    }

    private void configureBatchTable() {
        UITheme.styleTable(batchTable); UITheme.installMoneyRenderer(batchTable, 2, 3, 4); UITheme.installDateRenderer(batchTable, 5);
        batchTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        int[] widths = {75, 105, 100, 100, 105, 105, 110, 125};
        for (int index = 0; index < widths.length; index++) batchTable.getColumnModel().getColumn(index).setPreferredWidth(widths[index]);
        batchTable.getColumnModel().getColumn(7).setCellRenderer(statusRenderer());
    }

    private void configureHistoryTable() {
        UITheme.styleTable(historyTable); UITheme.installDateRenderer(historyTable, 0);
        historyTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        int[] widths = {145, 190, 130, 100, 300, 140};
        for (int index = 0; index < widths.length; index++) historyTable.getColumnModel().getColumn(index).setPreferredWidth(widths[index]);
    }

    /** Switches the visible editor card while preserving the selected product and existing service workflow. */
    private void switchAdjustmentMode(AdjustmentType mode) {
        activeMode = mode;
        batchTable.clearSelection();
        modeLayout.show(modePanel, mode.name());
        activeModeLabel.setText("ACTIVE MODE: " + mode.label);
        activeModeLabel.setFont(activeModeLabel.getFont().deriveFont(java.awt.Font.BOLD, 12f));
        activeModeLabel.setForeground(mode == AdjustmentType.ADD_STOCK ? UITheme.GREEN : UITheme.RED);
        addStockModeButton.setFont(addStockModeButton.getFont().deriveFont(mode == AdjustmentType.ADD_STOCK ? java.awt.Font.BOLD : java.awt.Font.PLAIN));
        removeStockModeButton.setFont(removeStockModeButton.getFont().deriveFont(mode == AdjustmentType.REMOVE_STOCK ? java.awt.Font.BOLD : java.awt.Font.PLAIN));
        loadSelectedProduct();
        modePanel.revalidate(); modeCard.revalidate(); getContentPane().revalidate();
        modePanel.repaint(); modeCard.repaint(); repaint();
    }

    private void loadProducts() { loadProducts(null); }

    private void loadProducts(Integer preferredProductId) {
        try {
            Integer selectedId = preferredProductId != null ? preferredProductId : selectedProduct() == null ? null : selectedProduct().id();
            loadingProducts = true; product.removeAllItems();
            for (Product item : products.search("")) if (item.active()) product.addItem(item);
            if (selectedId != null) {
                product.setSelectedItem(null);
                for (int index = 0; index < product.getItemCount(); index++) if (product.getItemAt(index).id() == selectedId) { product.setSelectedIndex(index); break; }
            }
        } catch (Exception exception) { showError(exception); }
        finally { loadingProducts = false; }
        loadSelectedProduct();
    }

    private void loadSelectedProduct() {
        Product selected = selectedProduct();
        if (selected == null) { totalStock.setText("0"); batchModel.setRowCount(0); removableBatches = List.of(); return; }
        try {
            totalStock.setText(adjustments.currentBatchTotal(selected.id()).stripTrailingZeros().toPlainString());
            if (selectedType() == AdjustmentType.ADD_STOCK) {
                costPrice.setText(selected.costPrice().toPlainString()); sellingPrice.setText(selected.sellingPrice().toPlainString());
                retailPrice.setText(selected.retailPrice() == null ? "" : selected.retailPrice().toPlainString());
                expiryDate.setText(selected.expiryDate() == null ? "" : selected.expiryDate().toString());
            } else {
                loadRemovableBatches();
            }
        } catch (Exception exception) { showError(exception); }
    }

    private void loadRemovableBatches() {
        Product selected = selectedProduct(); batchModel.setRowCount(0); removableBatches = List.of();
        if (selected == null) return;
        try {
            removableBatches = adjustments.findRemovableBatches(selected.id());
            LocalDate today = LocalDate.now();
            for (StockAdjustmentBatch batch : removableBatches) batchModel.addRow(new Object[]{batch.batchId(), batch.sourceType(), batch.costPrice(),
                batch.sellingPrice(), batch.retailPrice(), batch.expiryDate(), batch.quantityAvailable(), batchStatus(batch, today)});
        } catch (Exception exception) { showError(exception); }
    }

    private void loadHistory() {
        try {
            historyModel.setRowCount(0);
            for (StockAdjustmentHistory item : adjustments.history()) historyModel.addRow(new Object[]{item.movedAt(), item.productName(), item.movementType(), item.quantity(), item.reason(), item.username()});
        } catch (Exception exception) { showError(exception); }
    }

    private void applyAdjustment() {
        try {
            Product selected = selectedProduct();
            if (selected == null) throw new IllegalArgumentException("Select a product.");
            BigDecimal adjustedQuantity = Money.parse(quantity.getText(), "quantity");
            if (selectedType() == AdjustmentType.ADD_STOCK) {
                LocalDate expiry = expiryDate.getText().isBlank() ? null : LocalDate.parse(expiryDate.getText().trim());
                BigDecimal retail = retailPrice.getText().isBlank() ? null : Money.parse(retailPrice.getText(), "retail price");
                adjustments.addStock(new StockAdjustmentService.AddRequest(selected.id(), adjustedQuantity,
                        Money.parse(costPrice.getText(), "cost price"), Money.parse(sellingPrice.getText(), "our selling price"),
                        retail, expiry, reason.getText(), Session.currentUserId()));
            } else {
                int row = batchTable.getSelectedRow();
                if (row < 0) throw new IllegalArgumentException("Select the exact batch to remove stock from.");
                StockAdjustmentBatch batch = removableBatches.get(batchTable.convertRowIndexToModel(row));
                adjustments.removeStock(new StockAdjustmentService.RemoveRequest(selected.id(), batch.batchId(), adjustedQuantity, reason.getText(), Session.currentUserId()));
            }
            Dialogs.success(this, "Stock Adjustment", "Stock adjustment applied successfully.");
            quantity.setText(""); reason.setText(""); loadSelectedProduct(); loadHistory();
        } catch (Exception exception) { showError(exception); }
    }

    private void clear() {
        product.setSelectedItem(null); totalStock.setText("0"); quantity.setText(""); reason.setText("");
        costPrice.setText(""); sellingPrice.setText(""); retailPrice.setText(""); expiryDate.setText(""); removableBatches = List.of(); batchModel.setRowCount(0);
        switchAdjustmentMode(AdjustmentType.ADD_STOCK);
    }

    private void refresh() { loadProducts(); loadHistory(); }
    private Product selectedProduct() { return (Product) product.getSelectedItem(); }
    private AdjustmentType selectedType() { return activeMode; }
    private static DefaultTableModel readonlyModel(String... columns) { return new DefaultTableModel(columns, 0) { @Override public boolean isCellEditable(int row, int column) { return false; } }; }
    private static JLabel sectionTitle(String text) { JLabel label = new JLabel(text); label.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 13)); label.setForeground(UITheme.GREY); return label; }
    private static void addRow(JPanel panel, String firstLabel, Component first, String secondLabel, Component second) { panel.add(new JLabel(firstLabel)); panel.add(first); panel.add(new JLabel(secondLabel)); panel.add(second); }
    private static void compactField(JTextField field) { field.setPreferredSize(new Dimension(field.getPreferredSize().width, 38)); }
    private static void addCompactField(JPanel panel, String label, JTextField field, int column, int row) {
        GridBagConstraints labelConstraints = new GridBagConstraints(); labelConstraints.gridx = column; labelConstraints.gridy = row;
        labelConstraints.weightx = 1; labelConstraints.anchor = GridBagConstraints.WEST; labelConstraints.insets = new Insets(0, column == 0 ? 0 : 12, 3, 0);
        panel.add(new JLabel(label), labelConstraints);
        GridBagConstraints fieldConstraints = new GridBagConstraints(); fieldConstraints.gridx = column; fieldConstraints.gridy = row + 1;
        fieldConstraints.weightx = 1; fieldConstraints.fill = GridBagConstraints.HORIZONTAL; fieldConstraints.anchor = GridBagConstraints.NORTHWEST;
        fieldConstraints.insets = new Insets(0, column == 0 ? 0 : 12, 10, 0); panel.add(field, fieldConstraints);
    }
    private static String batchStatus(StockAdjustmentBatch batch, LocalDate today) {
        if (!batch.active()) return "INACTIVE";
        if (batch.quantityAvailable().signum() <= 0) return "OUT OF STOCK";
        if (batch.expiryDate() != null && batch.expiryDate().isBefore(today)) return "EXPIRED";
        if (batch.expiryDate() != null && !batch.expiryDate().isAfter(today.plusDays(30))) return "NEAR EXPIRY";
        return "AVAILABLE";
    }
    private static DefaultTableCellRenderer statusRenderer() { return new DefaultTableCellRenderer() {
        @Override public Component getTableCellRendererComponent(JTable source, Object value, boolean selected, boolean focused, int row, int column) {
            Component component = super.getTableCellRendererComponent(source, value, selected, focused, row, column);
            if (!selected) {
                String status = value == null ? "" : value.toString();
                component.setForeground(status.equals("EXPIRED") || status.equals("OUT OF STOCK") ? UITheme.RED
                        : status.equals("NEAR EXPIRY") ? new Color(187, 126, 18) : status.equals("AVAILABLE") ? UITheme.GREEN : UITheme.GREY);
            }
            return component;
        }
    }; }
    private void showError(Exception exception) { Dialogs.error(this, "Stock Adjustment", exception.getMessage()); }
}
