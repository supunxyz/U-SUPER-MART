package com.mycompany.easymartpos.ui;

import com.mycompany.easymartpos.DBConnection;
import com.mycompany.easymartpos.Dialogs;
import com.mycompany.easymartpos.model.Product;
import com.mycompany.easymartpos.repository.ProductRepository;
import com.mycompany.easymartpos.service.Authorization;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.KeyEvent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.DefaultListCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;

/** Admin-only promotion maintenance; checkout promotion application remains unchanged. */
public class PromotionForm extends JFrame {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd (HH:mm)");
    private static final DateTimeFormatter LEGACY_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Color ACTIVE_ROW = new Color(231, 247, 235);
    private static final Color ACTIVE_ROW_SELECTED = new Color(202, 231, 209);
    private static final Color DISABLED_ROW = new Color(253, 234, 236);
    private static final Color DISABLED_ROW_SELECTED = new Color(246, 207, 212);
    private record ProductChoice(int id, String name, String barcode) { @Override public String toString() { return name; } }
    private record PromotionRow(int id, String name, int productId, String productName, String discountType,
            BigDecimal discountValue, String customerScope, LocalDateTime start, LocalDateTime end, boolean active) { }

    private final ProductRepository products = new ProductRepository();
    private final JTextField promotionName = new JTextField(18);
    private final JComboBox<ProductChoice> product = new JComboBox<>();
    private final JTextField barcode = new JTextField(16);
    private final JComboBox<String> discountType = new JComboBox<>(new String[]{"Percentage", "Fixed Amount"});
    private final JTextField discountValue = new JTextField(10);
    private final JComboBox<String> customerScope = new JComboBox<>(new String[]{"All Customers", "Loyalty Customers Only"});
    private final JTextField startDateTime = new JTextField(16);
    private final JTextField endDateTime = new JTextField(16);
    private final JCheckBox active = new JCheckBox("Active", true);
    private final DefaultTableModel model = new DefaultTableModel(new String[]{"ID", "Promotion Name", "Product", "Discount Type", "Discount Value", "Customer Scope", "Start Date", "End Date", "Status"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = new JTable(model);
    private final List<Product> activeProducts = new ArrayList<>();
    private List<PromotionRow> displayed = new ArrayList<>();
    private Integer editingId;
    private JTextField productEditor;
    private boolean updatingProductSearch;
    private boolean committingProductSelection;
    private boolean productFilterUpdateQueued;
    private boolean productPopupMouseHandlerInstalled;
    private int productPopupHighlightedIndex = -1;

    public PromotionForm() {
        super("Promotions / Discounts");
        Authorization.requireAdmin();
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(1160, 720);
        setMinimumSize(new java.awt.Dimension(980, 620));
        setLocationByPlatform(true);

        JPanel form = new JPanel(new GridLayout(0, 4, 12, 9));
        form.setOpaque(false);
        addRow(form, "Promotion Name", promotionName, "Product", product);
        addRow(form, "Barcode", barcode, "Discount Type", discountType);
        addRow(form, "Discount Value", discountValue, "Customer Scope", customerScope);
        addRow(form, "Start (yyyy-MM-dd (HH:mm))", startDateTime, "End (yyyy-MM-dd (HH:mm))", endDateTime);
        addRow(form, "Status", active, "", new JLabel());

        JButton add = new JButton("Add Promotion");
        add.addActionListener(event -> addPromotion());
        JButton update = new JButton("Update");
        update.addActionListener(event -> updatePromotion());
        JButton toggle = new JButton("Enable / Disable");
        toggle.addActionListener(event -> toggleActive());
        JButton delete = new JButton("Delete Promotion");
        UITheme.styleDanger(delete);
        delete.addActionListener(event -> deletePromotion());
        JButton clear = new JButton("Clear");
        clear.addActionListener(event -> clearForm());
        JPanel actions = UITheme.card(new FlowLayout(FlowLayout.LEFT, 10, 2));
        actions.add(add); actions.add(update); actions.add(toggle); actions.add(delete); actions.add(clear);

        JPanel details = UITheme.card(new BorderLayout(0, 10));
        JLabel detailTitle = new JLabel("PROMOTION DETAILS");
        detailTitle.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 13));
        detailTitle.setForeground(UITheme.GREY);
        details.add(detailTitle, BorderLayout.NORTH);
        details.add(form, BorderLayout.CENTER);

        JPanel upper = new JPanel(new BorderLayout(0, 12));
        upper.setOpaque(false);
        upper.add(UITheme.pageHeader("PROMOTIONS / DISCOUNTS", "Create, schedule and manage product promotions"), BorderLayout.NORTH);
        upper.add(details, BorderLayout.CENTER);
        upper.add(actions, BorderLayout.SOUTH);

        table.getSelectionModel().addListSelectionListener(event -> { if (!event.getValueIsAdjusting()) selectPromotion(); });
        JPanel records = UITheme.card(new BorderLayout(0, 8));
        JLabel recordTitle = new JLabel("PROMOTION RECORDS");
        recordTitle.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 13));
        recordTitle.setForeground(UITheme.GREY);
        records.add(recordTitle, BorderLayout.NORTH);
        records.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel root = new JPanel(new BorderLayout(14, 14));
        root.setBackground(UITheme.BACKGROUND);
        root.setBorder(javax.swing.BorderFactory.createEmptyBorder(18, 20, 18, 20));
        root.add(upper, BorderLayout.NORTH);
        root.add(records, BorderLayout.CENTER);
        add(root);
        UITheme.applyWindow(this);
        UITheme.installMoneyRenderer(table, 4);
        UITheme.installDateRenderer(table, 6, 7);
        stylePromotionRecordsTable();
        configureProductSelection();
        stylePromotionInputFonts();
        active.setFont(active.getFont().deriveFont(Font.BOLD));
        loadProducts();
        clearForm();
        loadPromotions();
    }

    private void addRow(JPanel panel, String firstLabel, java.awt.Component first, String secondLabel, java.awt.Component second) {
        panel.add(new JLabel(firstLabel)); panel.add(first); panel.add(new JLabel(secondLabel)); panel.add(second);
    }

    /** Applies bold only to form values; labels, table, and control geometry remain untouched. */
    private void stylePromotionInputFonts() {
        for (JTextField field : List.of(promotionName, barcode, discountValue, startDateTime, endDateTime, productEditor)) {
            field.setFont(field.getFont().deriveFont(Font.BOLD));
        }
        for (JComboBox<?> combo : List.of(product, discountType, customerScope)) {
            combo.setFont(combo.getFont().deriveFont(Font.BOLD));
        }
    }

    /** Keeps Promotion Records readable while preserving the existing header and data values. */
    private void stylePromotionRecordsTable() {
        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable source, Object value, boolean selected,
                    boolean focused, int row, int column) {
                String text = value instanceof LocalDateTime dateTime ? dateTime.format(DATE_TIME) : value == null ? "" : value.toString();
                javax.swing.JLabel cell = (javax.swing.JLabel) super.getTableCellRendererComponent(source, text, selected, focused, row, column);
                String status = source.getModel().getValueAt(source.convertRowIndexToModel(row), 8).toString();
                boolean isActive = "active".equalsIgnoreCase(status);
                boolean isDisabled = "disabled".equalsIgnoreCase(status);
                if (isActive) cell.setBackground(selected ? ACTIVE_ROW_SELECTED : ACTIVE_ROW);
                else if (isDisabled) cell.setBackground(selected ? DISABLED_ROW_SELECTED : DISABLED_ROW);
                else cell.setBackground(selected ? new Color(218, 234, 248) : Color.WHITE);
                cell.setForeground(UITheme.NAVY);
                cell.setFont(UITheme.BODY.deriveFont(Font.BOLD));
                cell.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 7, 0, 7));
                return cell;
            }
        };
        for (int column = 0; column < table.getColumnCount(); column++) table.getColumnModel().getColumn(column).setCellRenderer(renderer);
        table.setSelectionBackground(ACTIVE_ROW_SELECTED);
        table.setSelectionForeground(UITheme.NAVY);
    }

    private void loadProducts() {
        try {
            activeProducts.clear();
            for (Product item : products.search("")) if (item.active()) activeProducts.add(item);
            showProductMatches("");
        } catch (Exception exception) { showError(exception); }
    }

    private void configureProductSelection() {
        product.setEditable(true);
        product.setMaximumRowCount(8);
        productEditor = (JTextField) product.getEditor().getEditorComponent();
        product.addActionListener(event -> {
            if (updatingProductSearch || !committingProductSelection) return;
            if (product.getSelectedItem() instanceof ProductChoice choice) applyCommittedProductSearchSelection(choice);
        });
        productEditor.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { queueProductFilter(); }
            @Override public void removeUpdate(DocumentEvent event) { queueProductFilter(); }
            @Override public void changedUpdate(DocumentEvent event) { queueProductFilter(); }
        });
        barcode.addActionListener(event -> selectProductByBarcode());
        installProductPopupKeyboardNavigation();
    }

    private void queueProductFilter() {
        if (updatingProductSearch || committingProductSelection || productFilterUpdateQueued) return;
        productFilterUpdateQueued = true;
        SwingUtilities.invokeLater(() -> {
            productFilterUpdateQueued = false;
            if (!updatingProductSearch && !committingProductSelection) showProductMatches(productEditor.getText());
        });
    }

    private void showProductMatches(String text) {
        String query = text == null ? "" : text.trim();
        String normalized = query.toLowerCase(java.util.Locale.ROOT);
        List<Product> matches = activeProducts.stream()
                .filter(item -> item.name().toLowerCase(java.util.Locale.ROOT).contains(normalized))
                .toList();
        updatingProductSearch = true;
        try {
            product.removeAllItems();
            for (Product item : matches) product.addItem(productChoice(item));
            product.setSelectedIndex(-1);
            productEditor.setText(query);
            productEditor.setCaretPosition(query.length());
        } finally {
            updatingProductSearch = false;
        }
        if (!query.isEmpty() && product.isShowing()) {
            SwingUtilities.invokeLater(() -> {
                if (query.equals(productEditor.getText())) product.setPopupVisible(true);
            });
        }
    }

    /** Arrow navigation previews a popup item.  Enter or a mouse click is the only commit path. */
    private void installProductPopupKeyboardNavigation() {
        bindProductPopupKey(KeyEvent.VK_DOWN, "promotion-product-popup-next", () -> moveProductPopupHighlight(1));
        bindProductPopupKey(KeyEvent.VK_UP, "promotion-product-popup-previous", () -> moveProductPopupHighlight(-1));
        javax.swing.Action commit = new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) { commitProductPopupSelection(); }
        };
        bindProductPopupEnterKey(product, javax.swing.JComponent.WHEN_FOCUSED, commit);
        bindProductPopupEnterKey(product, javax.swing.JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, commit);
        bindProductPopupEnterKey(productEditor, javax.swing.JComponent.WHEN_FOCUSED, commit);
        bindProductPopupEditorNavigationKey(productEditor, KeyEvent.VK_DOWN, "promotion-product-popup-next");
        bindProductPopupEditorNavigationKey(productEditor, KeyEvent.VK_UP, "promotion-product-popup-previous");
        product.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            @Override public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent event) {
                productPopupHighlightedIndex = -1;
                javax.swing.JList<?> list = productPopupList();
                if (list != null) list.clearSelection();
                sizeProductPopup();
            }
            @Override public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent event) { productPopupHighlightedIndex = -1; }
            @Override public void popupMenuCanceled(javax.swing.event.PopupMenuEvent event) { productPopupHighlightedIndex = -1; }
        });
        javax.swing.JList<?> list = productPopupList();
        if (list != null) {
            styleProductPopup(list);
            bindProductPopupEnterKey(list, javax.swing.JComponent.WHEN_FOCUSED, commit);
            installProductPopupMouseCommit(list);
        }
    }

    /** Styles only the Promotion Product Search popup; filtering and selection remain unchanged. */
    private void styleProductPopup(javax.swing.JList<?> list) {
        list.setFont(UITheme.BODY);
        list.setFixedCellHeight(30);
        list.setVisibleRowCount(8);
        list.setBackground(Color.WHITE);
        list.setSelectionBackground(UITheme.BLUE);
        list.setSelectionForeground(Color.WHITE);
        list.setBorder(javax.swing.BorderFactory.createEmptyBorder());
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(javax.swing.JList<?> source, Object value, int index,
                    boolean selected, boolean focused) {
                javax.swing.JLabel row = (javax.swing.JLabel) super.getListCellRendererComponent(source, value, index, selected, focused);
                row.setFont(UITheme.BODY);
                row.setOpaque(true);
                row.setBackground(selected ? UITheme.BLUE : Color.WHITE);
                row.setForeground(selected ? Color.WHITE : UITheme.NAVY);
                row.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 10, 0, 10));
                row.setToolTipText(value == null ? null : value.toString());
                return row;
            }
        });

        javax.swing.JScrollPane scrollPane = (javax.swing.JScrollPane) SwingUtilities.getAncestorOfClass(javax.swing.JScrollPane.class, list);
        if (scrollPane == null) return;
        scrollPane.setBorder(javax.swing.BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(Color.WHITE);
        scrollPane.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        styleProductPopupScrollBar(scrollPane.getVerticalScrollBar());

        if (product.getUI().getAccessibleChild(product, 0) instanceof javax.swing.plaf.basic.BasicComboPopup popup) {
            popup.setBorder(javax.swing.BorderFactory.createLineBorder(UITheme.BORDER));
            popup.setBackground(Color.WHITE);
        }
    }

    private void styleProductPopupScrollBar(javax.swing.JScrollBar scrollBar) {
        scrollBar.setUnitIncrement(20);
        scrollBar.setBackground(Color.WHITE);
        scrollBar.setPreferredSize(new Dimension(10, 30));
        scrollBar.setUI(new BasicScrollBarUI() {
            @Override protected void configureScrollBarColors() {
                thumbColor = new Color(180, 194, 207);
                trackColor = Color.WHITE;
            }

            @Override protected javax.swing.JButton createDecreaseButton(int orientation) { return hiddenScrollButton(); }
            @Override protected javax.swing.JButton createIncreaseButton(int orientation) { return hiddenScrollButton(); }
        });
    }

    private javax.swing.JButton hiddenScrollButton() {
        javax.swing.JButton button = new javax.swing.JButton();
        button.setPreferredSize(new Dimension(0, 0));
        button.setMinimumSize(new Dimension(0, 0));
        button.setMaximumSize(new Dimension(0, 0));
        return button;
    }

    /** Gives the native combo popup a stable, row-based size without affecting form layout. */
    private void sizeProductPopup() {
        javax.swing.JList<?> list = productPopupList();
        if (list == null || !(product.getUI().getAccessibleChild(product, 0) instanceof javax.swing.plaf.basic.BasicComboPopup popup)) return;
        int rowHeight = list.getFixedCellHeight() > 0 ? list.getFixedCellHeight() : 30;
        int visibleRows = Math.max(1, Math.min(product.getMaximumRowCount(), product.getItemCount()));
        int popupWidth = Math.max(product.getWidth(), product.getPreferredSize().width);
        int popupHeight = visibleRows * rowHeight + 2;
        Dimension popupSize = new Dimension(popupWidth, popupHeight);
        popup.setPreferredSize(popupSize);
        popup.setPopupSize(popupSize);

        javax.swing.JScrollPane scrollPane = (javax.swing.JScrollPane) SwingUtilities.getAncestorOfClass(javax.swing.JScrollPane.class, list);
        if (scrollPane != null) {
            scrollPane.setPreferredSize(new Dimension(popupWidth - 2, visibleRows * rowHeight));
            scrollPane.setVerticalScrollBarPolicy(product.getItemCount() > product.getMaximumRowCount()
                    ? javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS
                    : javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        }
    }

    private void bindProductPopupKey(int keyCode, String actionName, Runnable action) {
        javax.swing.KeyStroke keyStroke = javax.swing.KeyStroke.getKeyStroke(keyCode, 0);
        product.getInputMap(javax.swing.JComponent.WHEN_FOCUSED).put(keyStroke, actionName);
        product.getInputMap(javax.swing.JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(keyStroke, actionName);
        product.getActionMap().put(actionName, new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) { action.run(); }
        });
    }

    private void bindProductPopupEnterKey(javax.swing.JComponent component, int condition, javax.swing.Action action) {
        component.getInputMap(condition).put(javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "promotion-product-popup-commit");
        component.getActionMap().put("promotion-product-popup-commit", action);
    }

    private void bindProductPopupEditorNavigationKey(JTextField editor, int keyCode, String actionName) {
        editor.getInputMap(javax.swing.JComponent.WHEN_FOCUSED).put(javax.swing.KeyStroke.getKeyStroke(keyCode, 0), actionName);
        editor.getActionMap().put(actionName, product.getActionMap().get(actionName));
    }

    private void installProductPopupMouseCommit(javax.swing.JList<?> list) {
        if (productPopupMouseHandlerInstalled) return;
        productPopupMouseHandlerInstalled = true;
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseReleased(java.awt.event.MouseEvent event) {
                if (!SwingUtilities.isLeftMouseButton(event)) return;
                int index = list.locationToIndex(event.getPoint());
                java.awt.Rectangle cell = list.getCellBounds(index, index);
                if (index < 0 || cell == null || !cell.contains(event.getPoint()) || index >= product.getItemCount()) return;
                ProductChoice clicked = product.getItemAt(index);
                SwingUtilities.invokeLater(() -> commitProductSearchSelection(clicked));
            }
        });
    }

    private void moveProductPopupHighlight(int delta) {
        if (!product.isPopupVisible()) return;
        javax.swing.JList<?> list = productPopupList();
        if (list == null || list.getModel().getSize() == 0) return;
        int lastIndex = list.getModel().getSize() - 1;
        productPopupHighlightedIndex = productPopupHighlightedIndex < 0
                ? (delta > 0 ? 0 : lastIndex)
                : Math.max(0, Math.min(productPopupHighlightedIndex + delta, lastIndex));
        list.setSelectedIndex(productPopupHighlightedIndex);
        list.ensureIndexIsVisible(productPopupHighlightedIndex);
    }

    private void commitProductPopupSelection() {
        if (!product.isPopupVisible()) return;
        if (productPopupHighlightedIndex >= 0 && productPopupHighlightedIndex < product.getItemCount()) {
            commitProductSearchSelection(product.getItemAt(productPopupHighlightedIndex));
        }
        productPopupHighlightedIndex = -1;
        product.hidePopup();
    }

    private void commitProductSearchSelection(ProductChoice choice) {
        committingProductSelection = true;
        try {
            product.setSelectedItem(choice);
        } finally {
            committingProductSelection = false;
        }
        product.hidePopup();
    }

    private javax.swing.JList<?> productPopupList() {
        javax.accessibility.Accessible child = product.getUI().getAccessibleChild(product, 0);
        return child instanceof javax.swing.plaf.basic.BasicComboPopup popup ? popup.getList() : null;
    }

    private void selectProductByBarcode() {
        String scannedBarcode = barcode.getText().trim();
        if (scannedBarcode.isEmpty()) return;
        try {
            Product matched = products.findActiveByBarcode(scannedBarcode).orElse(null);
            if (matched == null) {
                Dialogs.error(this, "Promotions", "Product not found");
                return;
            }
            if (activeProducts.stream().noneMatch(item -> item.id() == matched.id())) activeProducts.add(matched);
            selectProduct(productChoice(matched));
        } catch (Exception exception) { showError(exception); }
    }

    private ProductChoice productChoice(Product item) { return new ProductChoice(item.id(), item.name(), item.barcode()); }

    private void selectProduct(ProductChoice choice) {
        updatingProductSearch = true;
        try {
            product.setSelectedItem(choice);
            productEditor.setText(choice.name());
            barcode.setText(choice.barcode());
            product.hidePopup();
        } finally {
            updatingProductSearch = false;
        }
    }

    private void applyCommittedProductSearchSelection(ProductChoice choice) {
        updatingProductSearch = true;
        try {
            productEditor.setText(choice.name());
            productEditor.setCaretPosition(choice.name().length());
            barcode.setText(choice.barcode());
            product.hidePopup();
        } finally {
            updatingProductSearch = false;
        }
    }

    private void loadPromotions() {
        String sql = "SELECT pr.promotion_id, pr.promotion_name, pr.product_id, p.product_name, pr.discount_type, "
                + "pr.discount_value, pr.customer_scope, pr.start_datetime, pr.end_datetime, pr.active "
                + "FROM promotions pr JOIN products p ON p.product_id=pr.product_id ORDER BY pr.active DESC, pr.start_datetime DESC, pr.promotion_id DESC";
        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql); ResultSet rows = statement.executeQuery()) {
            displayed = new ArrayList<>(); model.setRowCount(0);
            while (rows.next()) {
                Timestamp start = rows.getTimestamp("start_datetime");
                Timestamp end = rows.getTimestamp("end_datetime");
                PromotionRow item = new PromotionRow(rows.getInt("promotion_id"), rows.getString("promotion_name"), rows.getInt("product_id"),
                        rows.getString("product_name"), rows.getString("discount_type"), rows.getBigDecimal("discount_value"),
                        rows.getString("customer_scope"), start.toLocalDateTime(), end.toLocalDateTime(), rows.getBoolean("active"));
                displayed.add(item);
                model.addRow(new Object[]{item.id(), item.name(), item.productName(), displayDiscountType(item.discountType()), displayDiscountValue(item),
                        displayCustomerScope(item.customerScope()), item.start(), item.end(), status(item)});
            }
        } catch (Exception exception) { showError(exception); }
    }

    private void selectPromotion() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= displayed.size()) return;
        PromotionRow item = displayed.get(row);
        editingId = item.id();
        promotionName.setText(item.name());
        discountType.setSelectedItem(displayDiscountType(item.discountType()));
        discountValue.setText(item.discountValue().stripTrailingZeros().toPlainString());
        customerScope.setSelectedItem(displayCustomerScope(item.customerScope()));
        startDateTime.setText(item.start().format(DATE_TIME));
        endDateTime.setText(item.end().format(DATE_TIME));
        active.setSelected(item.active());
        selectProduct(item.productId());
    }

    private void selectProduct(int id) {
        activeProducts.stream().filter(item -> item.id() == id).findFirst()
                .ifPresent(item -> selectProduct(productChoice(item)));
    }

    private void addPromotion() {
        if (editingId != null) { showError(new IllegalArgumentException("Clear the form before adding a new promotion.")); return; }
        try {
            PromotionRow input = formPromotion(null);
            String sql = "INSERT INTO promotions (promotion_name, product_id, discount_type, discount_value, customer_scope, start_datetime, end_datetime, active) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
                bind(statement, input, false); statement.executeUpdate();
            }
            Dialogs.success(this, "Promotions", "Promotion added.");
            clearForm(); loadPromotions();
        } catch (Exception exception) { showError(exception); }
    }

    private void updatePromotion() {
        if (editingId == null) { showError(new IllegalArgumentException("Select a promotion to update.")); return; }
        try {
            PromotionRow input = formPromotion(editingId);
            String sql = "UPDATE promotions SET promotion_name=?, product_id=?, discount_type=?, discount_value=?, customer_scope=?, start_datetime=?, end_datetime=?, active=? WHERE promotion_id=?";
            try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
                bind(statement, input, true); statement.executeUpdate();
            }
            Dialogs.success(this, "Promotions", "Promotion updated.");
            clearForm(); loadPromotions();
        } catch (Exception exception) { showError(exception); }
    }

    private void toggleActive() {
        if (editingId == null) { showError(new IllegalArgumentException("Select a promotion to enable or disable.")); return; }
        PromotionRow selected = displayed.stream().filter(item -> item.id() == editingId).findFirst().orElse(null);
        if (selected == null) { showError(new IllegalArgumentException("Promotion not found.")); return; }
        boolean next = !selected.active();
        if (!Dialogs.confirmYesNo(this, next ? "Enable promotion" : "Disable promotion",
                (next ? "Enable " : "Disable ") + selected.name() + "?")) return;
        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement("UPDATE promotions SET active=? WHERE promotion_id=?")) {
            statement.setBoolean(1, next); statement.setInt(2, editingId); statement.executeUpdate();
            clearForm(); loadPromotions();
        } catch (Exception exception) { showError(exception); }
    }

    /** Promotions are standalone rules with no sales references, so a delete removes only the offer itself. */
    private void deletePromotion() {
        if (editingId == null) { showError(new IllegalArgumentException("Select a promotion to delete.")); return; }
        PromotionRow selected = displayed.stream().filter(item -> item.id() == editingId).findFirst().orElse(null);
        if (selected == null) { showError(new IllegalArgumentException("Promotion not found.")); return; }
        if (!Dialogs.confirmYesNo(this, "Delete promotion", "Permanently delete the promotion " + selected.name() + "? This cannot be undone.")) return;
        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement("DELETE FROM promotions WHERE promotion_id=?")) {
            statement.setInt(1, editingId);
            if (statement.executeUpdate() != 1) throw new IllegalArgumentException("Promotion was already removed.");
            clearForm(); loadPromotions();
            Dialogs.success(this, "Promotions", "Promotion deleted.");
        } catch (Exception exception) { showError(exception); }
    }

    private PromotionRow formPromotion(Integer id) {
        String name = promotionName.getText().trim();
        if (name.isEmpty()) throw new IllegalArgumentException("Promotion name is required.");
        ProductChoice selectedProduct = (ProductChoice) product.getSelectedItem();
        if (selectedProduct == null) throw new IllegalArgumentException("Select a product.");
        BigDecimal value;
        try { value = new BigDecimal(discountValue.getText().trim()); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException("Discount value must be numeric."); }
        if (value.signum() <= 0) throw new IllegalArgumentException("Discount value must be greater than 0.");
        String type = "Percentage".equals(discountType.getSelectedItem()) ? "PERCENTAGE" : "FIXED";
        if ("PERCENTAGE".equals(type) && value.compareTo(BigDecimal.valueOf(100)) > 0)
            throw new IllegalArgumentException("Percentage discount cannot exceed 100.");
        LocalDateTime start = parseDateTime(startDateTime.getText(), "start");
        LocalDateTime end = parseDateTime(endDateTime.getText(), "end");
        if (!end.isAfter(start)) throw new IllegalArgumentException("End date/time must be after start date/time.");
        String scope = "All Customers".equals(customerScope.getSelectedItem()) ? "ALL" : "LOYALTY";
        return new PromotionRow(id == null ? 0 : id, name, selectedProduct.id(), selectedProduct.name(), type, value, scope, start, end, active.isSelected());
    }

    private void bind(PreparedStatement statement, PromotionRow item, boolean updating) throws Exception {
        statement.setString(1, item.name()); statement.setInt(2, item.productId()); statement.setString(3, item.discountType());
        statement.setBigDecimal(4, item.discountValue()); statement.setString(5, item.customerScope());
        statement.setTimestamp(6, Timestamp.valueOf(item.start())); statement.setTimestamp(7, Timestamp.valueOf(item.end())); statement.setBoolean(8, item.active());
        if (updating) statement.setInt(9, item.id());
    }

    private LocalDateTime parseDateTime(String text, String label) {
        try { return LocalDateTime.parse(text.trim(), DATE_TIME); }
        catch (DateTimeParseException ignored) {
            try { return LocalDateTime.parse(text.trim(), LEGACY_DATE_TIME); }
            catch (DateTimeParseException exception) { throw new IllegalArgumentException("Enter a valid " + label + " date/time as yyyy-MM-dd (HH:mm)."); }
        }
    }

    private void clearForm() {
        editingId = null; table.clearSelection(); promotionName.setText(""); discountValue.setText("");
        discountType.setSelectedIndex(0); customerScope.setSelectedIndex(0); active.setSelected(true);
        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
        startDateTime.setText(now.format(DATE_TIME)); endDateTime.setText(now.plusDays(1).format(DATE_TIME));
        showProductMatches("");
        updatingProductSearch = true;
        try {
            product.setSelectedIndex(-1);
            productEditor.setText("");
            barcode.setText("");
        } finally {
            updatingProductSearch = false;
        }
        promotionName.requestFocusInWindow();
    }

    private String status(PromotionRow item) {
        if (!item.active()) return "Disabled";
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(item.start())) return "Scheduled";
        if (now.isAfter(item.end())) return "Expired";
        return "Active";
    }

    private String displayDiscountType(String value) { return "PERCENTAGE".equals(value) ? "Percentage" : "Fixed Amount"; }
    private String displayDiscountValue(PromotionRow item) {
        if ("PERCENTAGE".equals(item.discountType())) return item.discountValue().stripTrailingZeros().toPlainString() + "%";
        return "LKR " + item.discountValue().setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
    private String displayCustomerScope(String value) { return "ALL".equals(value) ? "All Customers" : "Loyalty Customers Only"; }
    private void showError(Exception exception) { Dialogs.error(this, "Promotions", exception.getMessage()); }
}
