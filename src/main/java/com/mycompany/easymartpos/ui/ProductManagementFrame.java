package com.mycompany.easymartpos.ui;

import com.mycompany.easymartpos.Dialogs;
import com.mycompany.easymartpos.DBConnection;
import com.mycompany.easymartpos.model.Product;
import com.mycompany.easymartpos.model.ProductBatchInventory;
import com.mycompany.easymartpos.model.ProductInventory;
import com.mycompany.easymartpos.model.ProductInput;
import com.mycompany.easymartpos.repository.ProductRepository;
import com.mycompany.easymartpos.service.Session;
import com.mycompany.easymartpos.service.StockAdjustmentService;
import com.mycompany.easymartpos.service.Authorization;
import com.mycompany.easymartpos.util.Money;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

public class ProductManagementFrame extends JFrame {
    private record CategoryChoice(int id, String name) { @Override public String toString() { return name; } }
    private final ProductRepository repository = new ProductRepository();
    private final JTextField search = new JTextField(16);
    private final JTextField barcode = new JTextField(12);
    private final JTextField name = new JTextField(16);
    private final JComboBox<CategoryChoice> category = new JComboBox<>();
    private final JTextField cost = new JTextField(8);
    private final JTextField price = new JTextField(8);
    private final JTextField retailPrice = new JTextField(8);
    private final JTextField openingStock = new JTextField("0", 8);
    private final JLabel stockLabel = new JLabel("Opening stock");
    private final JTextField minimumStock = new JTextField("0", 8);
    private final JTextField expiry = new JTextField(10);
    private final JButton viewBatches = new JButton("View Batches");
    private final DefaultTableModel model = new DefaultTableModel(new String[]{"Product ID", "Barcode", "Product Name", "Category", "Default Our Price", "Default Retail Price", "Total Stock", "Minimum Stock", "Nearest Expiry", "Status"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = new JTable(model);
    private List<ProductInventory> displayed = new ArrayList<>();
    private Integer editingId;
    private BigDecimal editingCurrentStock = BigDecimal.ZERO;
    private BigDecimal editingSellingPrice = BigDecimal.ZERO;
    private final StockAdjustmentService stockAdjustments = new StockAdjustmentService();

    public ProductManagementFrame() {
        super("Item Management");
        Authorization.requireAdmin();
        setDefaultCloseOperation(DISPOSE_ON_CLOSE); setSize(1180, 700); setMinimumSize(new java.awt.Dimension(980, 600)); setLocationByPlatform(true);
        openingStock.setToolTipText("New item: opening quantity. Existing item: type the new stock quantity and press Save to add or remove the difference.");
        JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT)); tools.setOpaque(false);
        JButton find = new JButton("Search"); find.addActionListener(event -> loadProducts());
        JButton clear = new JButton("New item"); clear.addActionListener(event -> clearForm());
        JButton reset = new JButton("Clear"); reset.addActionListener(event -> clearForm());
        JButton newCategory = new JButton("New category"); newCategory.addActionListener(event -> addCategory());
        tools.add(new JLabel("Search")); tools.add(search); tools.add(find); tools.add(clear); tools.add(newCategory);
        JPanel form = new JPanel(new GridLayout(0, 4, 12, 9)); form.setOpaque(false);
        addRow(form, "Barcode", barcode, "Name", name); addRow(form, "Category", category, "Cost price", cost);
        addRow(form, "Selling price", price, "Retail / MRP (optional)", retailPrice); addRow(form, stockLabel, openingStock, "Minimum stock", minimumStock); addRow(form, "Default expiry (yyyy-mm-dd)", expiry, "", new JLabel());
        JButton save = new JButton("Save item"); save.addActionListener(event -> save());
        JButton archive = new JButton("Archive selected"); archive.addActionListener(event -> archive());
        JButton delete = new JButton("Delete item"); UITheme.styleDanger(delete); delete.addActionListener(event -> deleteSelected());
        viewBatches.setEnabled(false); viewBatches.addActionListener(event -> viewBatches());
        JButton stockAdjustment = new JButton("Stock Adjustment"); stockAdjustment.addActionListener(event -> openStockAdjustment());
        JPanel actions = UITheme.card(new FlowLayout(FlowLayout.LEFT, 10, 2)); actions.add(save); actions.add(viewBatches); actions.add(stockAdjustment); actions.add(archive); actions.add(delete); actions.add(reset);
        JPanel upper = new JPanel(new BorderLayout(0, 12)); upper.setOpaque(false); upper.add(UITheme.pageHeader("PRODUCTS", "Manage products, pricing and stock"), BorderLayout.NORTH);
        JPanel details = UITheme.card(new BorderLayout(0, 10)); JLabel detailsTitle = new JLabel("PRODUCT DETAILS"); detailsTitle.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 13)); detailsTitle.setForeground(UITheme.GREY); details.add(detailsTitle, BorderLayout.NORTH); details.add(form, BorderLayout.CENTER);
        JPanel top = new JPanel(new BorderLayout(0, 10)); top.setOpaque(false); top.add(tools, BorderLayout.NORTH); top.add(details, BorderLayout.CENTER); top.add(actions, BorderLayout.SOUTH); upper.add(top, BorderLayout.CENTER);
        table.getSelectionModel().addListSelectionListener(event -> { if (!event.getValueIsAdjusting()) selectProduct(); });
        JPanel records = UITheme.card(new BorderLayout(0, 8)); JLabel recordsTitle = new JLabel("PRODUCT RECORDS"); recordsTitle.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 13)); recordsTitle.setForeground(UITheme.GREY); records.add(recordsTitle, BorderLayout.NORTH); records.add(new JScrollPane(table), BorderLayout.CENTER);
        JPanel root = new JPanel(new BorderLayout(14, 14)); root.setBackground(UITheme.BACKGROUND); root.setBorder(javax.swing.BorderFactory.createEmptyBorder(18, 20, 18, 20)); root.add(upper, BorderLayout.NORTH); root.add(records, BorderLayout.CENTER);
        add(root); UITheme.applyWindow(this); UITheme.installMoneyRenderer(table, 4, 5); installProductStatusRenderer();
        loadCategories(); loadProducts();
    }

    private void addRow(JPanel panel, String firstLabel, java.awt.Component first, String secondLabel, java.awt.Component second) {
        addRow(panel, new JLabel(firstLabel), first, secondLabel, second);
    }

    private void addRow(JPanel panel, JLabel firstLabel, java.awt.Component first, String secondLabel, java.awt.Component second) {
        panel.add(firstLabel); panel.add(first); panel.add(new JLabel(secondLabel)); panel.add(second);
    }

    private void loadCategories() {
        try { repository.ensureDefaultCategories(); } catch (Exception exception) { showError(exception); }
        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT category_id, category_name FROM categories ORDER BY category_name"); ResultSet results = statement.executeQuery()) {
            category.removeAllItems(); while (results.next()) category.addItem(new CategoryChoice(results.getInt(1), results.getString(2)));
        } catch (Exception exception) { showError(exception); }
    }

    private void loadProducts() {
        try {
            displayed = repository.searchWithBatchInventory(search.getText()); model.setRowCount(0);
            for (ProductInventory item : displayed) {
                Product product = item.product();
                model.addRow(new Object[]{product.id(), product.barcode(), product.name(), product.categoryName(), product.sellingPrice(),
                    product.retailPrice(), item.totalBatchStock(), product.minimumStock(), item.nearestExpiry(), productStatus(item)});
            }
        } catch (Exception exception) { showError(exception); }
    }

    private void selectProduct() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= displayed.size()) { editingId = null; viewBatches.setEnabled(false); return; }
        ProductInventory inventory = displayed.get(row);
        Product item = inventory.product(); editingId = item.id(); barcode.setText(item.barcode()); name.setText(item.name());
        cost.setText(item.costPrice().toPlainString()); price.setText(item.sellingPrice().toPlainString()); retailPrice.setText(item.retailPrice() == null ? "" : item.retailPrice().toPlainString());
        minimumStock.setText(item.minimumStock().toPlainString()); expiry.setText(item.expiryDate() == null ? "" : item.expiryDate().toString());
        editingCurrentStock = inventory.totalBatchStock();
        editingSellingPrice = item.sellingPrice();
        openingStock.setText(editingCurrentStock.toPlainString()); openingStock.setEditable(true); stockLabel.setText("Stock quantity"); viewBatches.setEnabled(true);
        for (int index = 0; index < category.getItemCount(); index++) if (category.getItemAt(index).id() == item.categoryId()) category.setSelectedIndex(index);
    }

    private void clearForm() {
        editingId = null; editingCurrentStock = BigDecimal.ZERO; table.clearSelection(); barcode.setText(""); name.setText(""); cost.setText(""); price.setText(""); retailPrice.setText("");
        openingStock.setText("0"); openingStock.setEditable(true); stockLabel.setText("Opening stock"); minimumStock.setText("0"); expiry.setText(""); viewBatches.setEnabled(false); if (category.getItemCount() > 0) category.setSelectedIndex(0);
    }

    private void save() {
        try {
            CategoryChoice selectedCategory = (CategoryChoice) category.getSelectedItem();
            if (selectedCategory == null) throw new IllegalArgumentException("Create a category before adding items.");
            BigDecimal initial = editingId == null ? Money.parse(openingStock.getText(), "opening stock") : BigDecimal.ZERO;
            LocalDate date = expiry.getText().isBlank() ? null : LocalDate.parse(expiry.getText().trim());
            ProductInput input = new ProductInput(editingId, barcode.getText(), name.getText(), selectedCategory.id(),
                    Money.parse(cost.getText(), "cost price"), Money.parse(price.getText(), "selling price"), retailPrice.getText().isBlank() ? null : Money.parse(retailPrice.getText(), "retail / MRP price"), initial,
                    Money.parse(minimumStock.getText(), "minimum stock"), date);
            boolean priceChanged = editingId != null && input.sellingPrice().compareTo(editingSellingPrice) != 0;
            int savedId = repository.save(input, Session.currentUserId());
            String stockNote = editingId == null ? "" : applyStockChange(savedId, input);
            String priceNote = priceChanged ? " The new selling price now applies to unsold stock that used the old price." : "";
            Dialogs.success(this, "Item Management", "Item saved." + stockNote + priceNote); clearForm(); loadProducts();
        } catch (Exception exception) { showError(exception); }
    }

    /** Turns an edited stock quantity into a recorded stock adjustment (add a batch, or remove from oldest batches first). */
    private String applyStockChange(int productId, ProductInput input) throws Exception {
        BigDecimal desired = Money.parse(openingStock.getText(), "stock quantity");
        BigDecimal difference = desired.subtract(editingCurrentStock);
        if (difference.signum() == 0) return "";
        int userId = Session.currentUserId();
        String reason = "Stock changed from Item Management";
        if (difference.signum() > 0) {
            stockAdjustments.addStock(new StockAdjustmentService.AddRequest(productId, difference, input.costPrice(), input.sellingPrice(),
                    input.retailPrice(), input.expiryDate(), reason, userId));
            return " Stock increased by " + difference.stripTrailingZeros().toPlainString() + ".";
        }
        BigDecimal remaining = difference.negate();
        for (var batch : stockAdjustments.findRemovableBatches(productId)) {
            if (remaining.signum() <= 0) break;
            BigDecimal take = remaining.min(batch.quantityAvailable());
            stockAdjustments.removeStock(new StockAdjustmentService.RemoveRequest(productId, batch.batchId(), take, reason, userId));
            remaining = remaining.subtract(take);
        }
        if (remaining.signum() > 0) throw new IllegalStateException("Item saved, but only part of the stock could be removed.");
        return " Stock decreased by " + difference.negate().stripTrailingZeros().toPlainString() + ".";
    }

    private void archive() {
        if (editingId == null) { showError(new IllegalArgumentException("Select an item to archive.")); return; }
        if (!Dialogs.confirmYesNo(this, "Archive item", "Archive/deactivate this item? Historical batches and transactions will be preserved.")) return;
        try { repository.archive(editingId); clearForm(); loadProducts(); } catch (Exception exception) { showError(exception); }
    }

    /** Deletes an item outright when nothing sold or purchased refers to it; otherwise offers the archive path that keeps history. */
    private void deleteSelected() {
        if (editingId == null) { showError(new IllegalArgumentException("Select an item to delete.")); return; }
        String itemName = name.getText().trim();
        if (!Dialogs.confirmYesNo(this, "Delete item", "Permanently delete " + itemName + "? Its batches, stock movements and promotions will be removed. This cannot be undone.")) return;
        try {
            if (repository.deleteIfUnused(editingId)) {
                clearForm(); loadProducts();
                Dialogs.success(this, "Item Management", "Item deleted.");
                return;
            }
            if (!Dialogs.confirmYesNo(this, "Item has history", itemName + " has sales or purchase history, so it cannot be deleted without losing records. Archive it instead? Archived items are hidden from checkout.")) return;
            repository.archive(editingId);
            clearForm(); loadProducts();
            Dialogs.success(this, "Item Management", "Item archived.");
        } catch (Exception exception) { showError(exception); }
    }

    /** Adds a shop category and selects it so the new item can be saved straight away. */
    private void addCategory() {
        String entered = Dialogs.input(this, "New Category", "Category name (for example: Diary)");
        if (entered == null) return;
        try {
            int id = repository.addCategory(entered);
            loadCategories();
            for (int index = 0; index < category.getItemCount(); index++) if (category.getItemAt(index).id() == id) category.setSelectedIndex(index);
            Dialogs.success(this, "Item Management", "Category added: " + entered.trim());
        } catch (Exception exception) { showError(exception); }
    }

    private void viewBatches() {
        if (editingId == null) return;
        try {
            List<ProductBatchInventory> batches = repository.findBatchInventory(editingId);
            showBatchDialog(name.getText().trim(), batches);
        } catch (Exception exception) { showError(exception); }
    }

    private void openStockAdjustment() {
        StockAdjustmentFrame adjustment = editingId == null ? new StockAdjustmentFrame() : new StockAdjustmentFrame(editingId);
        adjustment.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent event) { clearForm(); loadProducts(); }
        });
        adjustment.setVisible(true);
    }

    private void showBatchDialog(String productName, List<ProductBatchInventory> batches) {
        DefaultTableModel batchModel = new DefaultTableModel(new String[]{"Batch ID", "Source", "Purchase ID", "Cost Price", "Our Price", "Retail Price", "Expiry Date", "Received Qty", "Remaining Qty", "Status", "Created Date"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        LocalDate today = LocalDate.now();
        for (ProductBatchInventory batch : batches) batchModel.addRow(new Object[]{batch.batchId(), batch.sourceType(), batch.purchaseId(),
            batch.costPrice(), batch.sellingPrice(), batch.retailPrice(), batch.expiryDate(), batch.quantityReceived(),
            batch.quantityAvailable(), batchStatus(batch, today), batch.createdAt()});
        JTable batchTable = new JTable(batchModel);
        UITheme.styleTable(batchTable); UITheme.installMoneyRenderer(batchTable, 3, 4, 5); UITheme.installDateRenderer(batchTable, 6, 10);
        installBatchStatusRenderer(batchTable);
        configureBatchTableColumns(batchTable);
        JDialog dialog = new JDialog(this, "BATCH STOCK - " + productName, true);
        JPanel content = UITheme.card(new BorderLayout(0, 10));
        content.add(new JLabel("Batch history is read-only. Stock and pricing are changed through controlled purchase and sale flows."), BorderLayout.NORTH);
        content.add(new JScrollPane(batchTable), BorderLayout.CENTER);
        JButton close = new JButton("Close"); close.addActionListener(event -> dialog.dispose());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT)); actions.setOpaque(false); actions.add(close); content.add(actions, BorderLayout.SOUTH);
        dialog.setContentPane(content); dialog.setSize(1120, 400); dialog.setLocationRelativeTo(this); dialog.setVisible(true);
    }

    /** Keeps batch values readable and lets the existing scroll pane handle the extra table width. */
    static void configureBatchTableColumns(JTable batchTable) {
        int[] widths = {75, 100, 90, 105, 105, 105, 95, 105, 110, 130, 155};
        batchTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        for (int index = 0; index < widths.length && index < batchTable.getColumnCount(); index++) {
            batchTable.getColumnModel().getColumn(index).setPreferredWidth(widths[index]);
            batchTable.getColumnModel().getColumn(index).setMinWidth(Math.min(widths[index], 55));
        }
    }

    static String batchStatus(ProductBatchInventory batch, LocalDate today) {
        if (!batch.active()) return "INACTIVE";
        if (batch.quantityAvailable().signum() <= 0) return "OUT OF STOCK";
        if (batch.expiryDate() != null && batch.expiryDate().isBefore(today)) return "EXPIRED";
        if (batch.expiryDate() != null && !batch.expiryDate().isAfter(today.plusDays(30))) return "NEAR EXPIRY";
        return "AVAILABLE";
    }

    static String productStatus(ProductInventory inventory) {
        Product product = inventory.product();
        if (!product.active()) return "ARCHIVED";
        // Expired units are still physical stock, but checkout cannot sell them, so the item needs attention first.
        if (inventory.expiredStock().signum() > 0) return "EXPIRED";
        if (inventory.totalBatchStock().signum() <= 0) return "OUT OF STOCK";
        return inventory.totalBatchStock().compareTo(product.minimumStock()) <= 0 ? "LOW STOCK" : "ACTIVE";
    }

    private void installProductStatusRenderer() { table.getColumnModel().getColumn(9).setCellRenderer(statusRenderer()); }
    private void installBatchStatusRenderer(JTable batchTable) { batchTable.getColumnModel().getColumn(9).setCellRenderer(statusRenderer()); }
    private DefaultTableCellRenderer statusRenderer() {
        return new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable source, Object value, boolean selected, boolean focused, int row, int column) {
                Component component = super.getTableCellRendererComponent(source, value, selected, focused, row, column);
                if (!selected) {
                    String status = value == null ? "" : value.toString();
                    component.setForeground(status.equals("EXPIRED") || status.equals("OUT OF STOCK") ? UITheme.RED
                            : status.equals("NEAR EXPIRY") || status.equals("LOW STOCK") ? new Color(187, 126, 18)
                            : status.equals("AVAILABLE") || status.equals("ACTIVE") ? UITheme.GREEN : UITheme.GREY);
                }
                return component;
            }
        };
    }

    private void showError(Exception exception) { Dialogs.error(this, "Item Management", exception.getMessage()); }
}
