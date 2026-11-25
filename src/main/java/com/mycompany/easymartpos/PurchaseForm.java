/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JFrame.java to edit this template
 */
package com.mycompany.easymartpos;

import com.mycompany.easymartpos.ui.UITheme;

import com.mycompany.easymartpos.service.Authorization;
import com.mycompany.easymartpos.service.Session;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import javax.swing.JOptionPane;
import javax.swing.table.DefaultTableModel;



public class PurchaseForm extends javax.swing.JFrame {
    
    private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(PurchaseForm.class.getName());
    private final javax.swing.JLabel totalPurchaseValue = new javax.swing.JLabel("LKR 0.00");
    private final javax.swing.JLabel purchaseSummary = new javax.swing.JLabel();
    private final javax.swing.JTextField searchPurchases = new javax.swing.JTextField(20);
    private final javax.swing.JTextField batchSellingPriceField = new javax.swing.JTextField();
    private final javax.swing.JTextField batchRetailPriceField = new javax.swing.JTextField();
    private final javax.swing.JTextField batchExpiryDateField = new javax.swing.JTextField();
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ofPattern("uuuu-MM-dd")
            .withResolverStyle(ResolverStyle.STRICT);

    /**
     * Creates new form PurchaseForm
     */
    public PurchaseForm() {
        Authorization.requireAdmin();
        initComponents();
        buildModernPurchaseLayout();
        setTitle("USHANTHA SUPER MART | Purchases"); setDefaultCloseOperation(DISPOSE_ON_CLOSE); setSize(1180, 700); setMinimumSize(new java.awt.Dimension(980, 600)); setLocationByPlatform(true);
        UITheme.applyWindow(this);
        UITheme.installDateRenderer(jTable1, 4); UITheme.installDateRenderer(jTable1, 10);
        UITheme.installMoneyRenderer(jTable1, 7, 8, 9, 12);
        
        jTextField3.setText(java.time.LocalDate.now().toString());
         
        loadSuppliers();
        loadProducts();
        
        
        jButton2ActionPerformed(null);
    }

    /** Reuses the existing fields/actions while presenting purchases as a modern operations workspace. */
    private void buildModernPurchaseLayout() {
        batchExpiryDateField.setToolTipText("Optional; use yyyy-MM-dd (Example: 2027-08-27)");
        javax.swing.JPanel root = new javax.swing.JPanel(new java.awt.BorderLayout(14, 14));
        root.setBackground(UITheme.BACKGROUND); root.setBorder(javax.swing.BorderFactory.createEmptyBorder(18, 20, 18, 20));
        javax.swing.JPanel upper = new javax.swing.JPanel(new java.awt.BorderLayout(0, 12)); upper.setOpaque(false);
        upper.add(UITheme.pageHeader("PURCHASES", "Record supplier deliveries and review purchase records"), java.awt.BorderLayout.NORTH);

        javax.swing.JPanel details = UITheme.card(new java.awt.GridLayout(0, 4, 10, 8));
        addPurchaseField(details, "Supplier", jComboBox1); addPurchaseField(details, "Product", jComboBox2);
        addPurchaseField(details, "Quantity", jTextField1); addPurchaseField(details, "Cost Price", jTextField2);
        addPurchaseField(details, "Our Selling Price", batchSellingPriceField); addPurchaseField(details, "Retail Price", batchRetailPriceField);
        addPurchaseField(details, "Expiry Date", batchExpiryDateField);
        addPurchaseField(details, "Purchase Date", jTextField3); addPurchaseField(details, "Invoice Number", jTextField4);
        addPurchaseField(details, "Payment Status", jComboBox3);

        javax.swing.JPanel summary = UITheme.card(new java.awt.BorderLayout(0, 8)); javax.swing.JLabel summaryTitle = new javax.swing.JLabel("PURCHASE SUMMARY"); summaryTitle.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 13)); summaryTitle.setForeground(UITheme.GREY); summary.add(summaryTitle, java.awt.BorderLayout.NORTH); purchaseSummary.setVerticalAlignment(javax.swing.SwingConstants.TOP); summary.add(purchaseSummary, java.awt.BorderLayout.CENTER);
        javax.swing.JPanel overview = new javax.swing.JPanel(new java.awt.GridLayout(1, 2, 14, 0)); overview.setOpaque(false); overview.add(details); overview.add(summary);
        javax.swing.JPanel actions = UITheme.card(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 10, 2));
        javax.swing.JLabel actionTitle = new javax.swing.JLabel("QUICK ACTIONS"); actionTitle.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 12)); actionTitle.setForeground(UITheme.GREY);
        actions.add(actionTitle); actions.add(jButton1); actions.add(jButton2); actions.add(jButton3); actions.add(jButton4);
        javax.swing.JPanel top = new javax.swing.JPanel(new java.awt.BorderLayout(0, 10)); top.setOpaque(false); top.add(overview, java.awt.BorderLayout.CENTER); top.add(actions, java.awt.BorderLayout.SOUTH);
        upper.add(top, java.awt.BorderLayout.CENTER); root.add(upper, java.awt.BorderLayout.NORTH);

        javax.swing.JPanel records = UITheme.card(new java.awt.BorderLayout(0, 8));
        javax.swing.JPanel recordsBar = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0)); recordsBar.setOpaque(false);
        javax.swing.JLabel recordsTitle = new javax.swing.JLabel("PURCHASE RECORDS"); recordsTitle.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 13)); recordsTitle.setForeground(UITheme.GREY); recordsBar.add(recordsTitle); recordsBar.add(new javax.swing.JLabel("Search Purchases")); recordsBar.add(searchPurchases);
        javax.swing.JButton search = new javax.swing.JButton("Search"); search.addActionListener(event -> jButton2ActionPerformed(null)); recordsBar.add(search);
        records.add(recordsBar, java.awt.BorderLayout.NORTH); records.add(jScrollPane1, java.awt.BorderLayout.CENTER);
        javax.swing.JPanel total = new javax.swing.JPanel(new java.awt.BorderLayout()); total.setOpaque(false);
        javax.swing.JLabel totalLabel = new javax.swing.JLabel("TOTAL PURCHASE VALUE"); totalLabel.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 12)); totalLabel.setForeground(UITheme.GREY);
        totalPurchaseValue.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 21)); totalPurchaseValue.setForeground(UITheme.NAVY); total.add(totalLabel, java.awt.BorderLayout.WEST); total.add(totalPurchaseValue, java.awt.BorderLayout.EAST);
        records.add(total, java.awt.BorderLayout.SOUTH); root.add(records, java.awt.BorderLayout.CENTER);

        getContentPane().removeAll(); getContentPane().setLayout(new java.awt.BorderLayout()); getContentPane().add(root, java.awt.BorderLayout.CENTER);
        configurePurchaseRecordsTable();
        jTable1.getModel().addTableModelListener(event -> refreshPurchaseSummary()); installPurchaseSearch();
        jTextField1.getDocument().addDocumentListener(purchaseSummaryListener()); jTextField2.getDocument().addDocumentListener(purchaseSummaryListener());
        batchSellingPriceField.getDocument().addDocumentListener(purchaseSummaryListener()); batchRetailPriceField.getDocument().addDocumentListener(purchaseSummaryListener()); batchExpiryDateField.getDocument().addDocumentListener(purchaseSummaryListener());
        jComboBox1.addActionListener(event -> refreshPurchasePreview());
        jComboBox2.addActionListener(event -> { loadProductBatchDefaults(); refreshPurchasePreview(); });
        refreshPurchaseSummary();
        refreshPurchasePreview();
    }

    /** Keeps the generated table declaration intact while providing the batch-aware records view. */
    private void configurePurchaseRecordsTable() {
        jTable1.setModel(new DefaultTableModel(new Object[0][], new String[]{
            "Purchase ID", "Batch ID", "Supplier", "Invoice Number", "Date", "Product",
            "Quantity", "Cost Price", "Our Price", "Retail Price", "Expiry Date",
            "Remaining Qty", "Subtotal", "Payment Status"
        }) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        });
        jTable1.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_OFF);
        int[] widths = {90, 80, 150, 120, 105, 180, 85, 95, 95, 95, 105, 105, 105, 105};
        for (int i = 0; i < widths.length; i++) jTable1.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
    }

    private void addPurchaseField(javax.swing.JPanel panel, String label, java.awt.Component field) {
        javax.swing.JLabel name = new javax.swing.JLabel(label); name.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 12)); name.setForeground(UITheme.GREY);
        panel.add(name); panel.add(field);
    }

    private void refreshPurchaseSummary() {
        java.math.BigDecimal sum = java.math.BigDecimal.ZERO;
        javax.swing.table.TableModel model = jTable1.getModel();
        for (int row = 0; row < model.getRowCount(); row++) {
            Object value = model.getValueAt(row, 12);
            if (value != null) try { sum = sum.add(new java.math.BigDecimal(value.toString())); } catch (NumberFormatException ignored) { }
        }
        totalPurchaseValue.setText(UITheme.money(sum));
    }

    private void installPurchaseSearch() {
        javax.swing.table.TableRowSorter<javax.swing.table.TableModel> sorter = new javax.swing.table.TableRowSorter<>(jTable1.getModel()); jTable1.setRowSorter(sorter);
        searchPurchases.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void filter() { String text = searchPurchases.getText().trim(); sorter.setRowFilter(text.isEmpty() ? null : javax.swing.RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(text))); }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent event) { filter(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent event) { filter(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent event) { filter(); }
        });
    }

    private javax.swing.event.DocumentListener purchaseSummaryListener() {
        return new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent event) { refreshPurchasePreview(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent event) { refreshPurchasePreview(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent event) { refreshPurchasePreview(); }
        };
    }

    private void refreshPurchasePreview() {
        java.math.BigDecimal quantity = previewNumber(jTextField1.getText()); java.math.BigDecimal cost = previewNumber(jTextField2.getText());
        String supplier = jComboBox1.getSelectedItem() == null ? "—" : jComboBox1.getSelectedItem().toString(); String product = jComboBox2.getSelectedItem() == null ? "—" : jComboBox2.getSelectedItem().toString();
        java.math.BigDecimal selling = previewNumber(batchSellingPriceField.getText());
        String retail = batchRetailPriceField.getText().trim().isEmpty() ? "-" : UITheme.money(previewNumber(batchRetailPriceField.getText()));
        String expiry = batchExpiryDateField.getText().trim().isEmpty() ? "-" : batchExpiryDateField.getText().trim();
        purchaseSummary.setText("<html>Supplier: <b>" + html(supplier) + "</b><br><br>Product: <b>" + html(product) + "</b><br><br>Quantity: <b>" + quantity.stripTrailingZeros().toPlainString() + "</b><br>Cost Price: <b>" + UITheme.money(cost) + "</b><br>Our Selling Price: <b>" + UITheme.money(selling) + "</b><br>Retail Price: <b>" + retail + "</b><br>Expiry Date: <b>" + html(expiry) + "</b><br><br>Subtotal: <span style='font-size:16px'><b>" + UITheme.money(quantity.multiply(cost)) + "</b></span></html>");
    }
    private java.math.BigDecimal previewNumber(String value) { try { return new java.math.BigDecimal(value.trim()); } catch (Exception ignored) { return java.math.BigDecimal.ZERO; } }
    private String html(String value) { return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }
    
    
    private void loadSuppliers() {

    try {
        Connection con = DBConnection.getConnection();

        String sql = "SELECT supplier_id, supplier_name FROM suppliers";

        PreparedStatement pst = con.prepareStatement(sql);
        ResultSet rs = pst.executeQuery();

        jComboBox1.removeAllItems();

        while (rs.next()) {
            jComboBox1.addItem(
                rs.getInt("supplier_id") + " - "
                + rs.getString("supplier_name")
            );
        }

        rs.close();
        pst.close();
        con.close();

    } catch (Exception e) {
        JOptionPane.showMessageDialog(this,
                "Error loading suppliers: " + e.getMessage());
    }
}


private void loadProducts() {

    try {
        Connection con = DBConnection.getConnection();

        String sql = "SELECT product_id, product_name FROM products "
                   + "WHERE active = 1";

        PreparedStatement pst = con.prepareStatement(sql);
        ResultSet rs = pst.executeQuery();

        jComboBox2.removeAllItems();

        while (rs.next()) {
            jComboBox2.addItem(
                rs.getInt("product_id") + " - "
                + rs.getString("product_name")
            );
        }

        rs.close();
        pst.close();
        con.close();

        loadProductBatchDefaults();

    } catch (Exception e) {
        JOptionPane.showMessageDialog(this,
                "Error loading products: " + e.getMessage());
    }
}

    /** Loads product-master values only as editable defaults for a newly received batch. */
    private void loadProductBatchDefaults() {
        if (jComboBox2.getSelectedItem() == null) return;
        try (Connection con = DBConnection.getConnection();
             PreparedStatement pst = con.prepareStatement(
                     "SELECT selling_price, retail_price, expiry_date FROM products WHERE product_id = ?")) {
            pst.setInt(1, selectedId(jComboBox2));
            try (ResultSet rs = pst.executeQuery()) {
                if (rs.next()) {
                    BigDecimal selling = rs.getBigDecimal("selling_price");
                    BigDecimal retail = rs.getBigDecimal("retail_price");
                    java.sql.Date expiry = rs.getDate("expiry_date");
                    batchSellingPriceField.setText(selling == null ? "" : selling.stripTrailingZeros().toPlainString());
                    batchRetailPriceField.setText(retail == null ? "" : retail.stripTrailingZeros().toPlainString());
                    batchExpiryDateField.setText(expiry == null ? "" : expiry.toLocalDate().toString());
                }
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error loading product batch defaults: " + e.getMessage());
        }
    }

    private int selectedId(javax.swing.JComboBox<String> comboBox) {
        return Integer.parseInt(comboBox.getSelectedItem().toString().split(" - ", 2)[0]);
    }

    private LocalDate requiredIsoDate(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(fieldName + " is required.");
        try { return LocalDate.parse(value.trim(), ISO_DATE); }
        catch (DateTimeParseException e) { throw new IllegalArgumentException(fieldName + " must use yyyy-MM-dd."); }
    }

    private BigDecimal requiredPositive(String value, String fieldName) {
        try {
            BigDecimal number = new BigDecimal(value.trim());
            if (number.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException(fieldName + " must be greater than 0.");
            return number;
        } catch (NumberFormatException e) { throw new IllegalArgumentException("Please enter a valid " + fieldName + "."); }
    }

    private BigDecimal optionalNonNegative(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            BigDecimal number = new BigDecimal(value.trim());
            if (number.compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException(fieldName + " must be zero or greater.");
            return number;
        } catch (NumberFormatException e) { throw new IllegalArgumentException("Please enter a valid " + fieldName + "."); }
    }

    private void clearPurchaseEntry(boolean clearSelections) {
        if (clearSelections) { jComboBox1.setSelectedIndex(-1); jComboBox2.setSelectedIndex(-1); }
        jTextField1.setText(""); jTextField2.setText("");
        batchSellingPriceField.setText(""); batchRetailPriceField.setText(""); batchExpiryDateField.setText("");
        jTextField3.setText(LocalDate.now().toString()); jTextField4.setText(""); jComboBox3.setSelectedIndex(0);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        jLabel5 = new javax.swing.JLabel();
        jComboBox1 = new javax.swing.JComboBox<>();
        jComboBox2 = new javax.swing.JComboBox<>();
        jTextField1 = new javax.swing.JTextField();
        jTextField2 = new javax.swing.JTextField();
        jTextField3 = new javax.swing.JTextField();
        jButton1 = new javax.swing.JButton();
        jButton2 = new javax.swing.JButton();
        jButton3 = new javax.swing.JButton();
        jButton4 = new javax.swing.JButton();
        jScrollPane1 = new javax.swing.JScrollPane();
        jTable1 = new javax.swing.JTable();
        jLabel6 = new javax.swing.JLabel();
        jLabel7 = new javax.swing.JLabel();
        jTextField4 = new javax.swing.JTextField();
        jComboBox3 = new javax.swing.JComboBox<>();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        jLabel1.setText("Supplier");

        jLabel2.setText("Product");

        jLabel3.setText("Quantity");

        jLabel4.setText("Cost Price");

        jLabel5.setText("Purchase Date");

        jComboBox1.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));

        jComboBox2.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));

        jTextField2.addActionListener(this::jTextField2ActionPerformed);

        jTextField3.setText("2026-09-01");
        jTextField3.setToolTipText("Enter date as yyyy-MM-dd (Example: 2026-09-01)");
        jTextField3.addActionListener(this::jTextField3ActionPerformed);

        jButton1.setText("Add Purchase");
        jButton1.addActionListener(this::jButton1ActionPerformed);

        jButton2.setText("View Purchases");
        jButton2.addActionListener(this::jButton2ActionPerformed);

        jButton3.setText("Delete");
        jButton3.addActionListener(this::jButton3ActionPerformed);

        jButton4.setText("Clear");
        jButton4.addActionListener(this::jButton4ActionPerformed);

        jTable1.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null, null, null, null, null, null},
                {null, null, null, null, null, null, null, null, null},
                {null, null, null, null, null, null, null, null, null},
                {null, null, null, null, null, null, null, null, null}
            },
            new String [] {
                "Purchase ID", "Supplier", "Invoice Number", "Date", "Product", "Quantity", "Cost Price", "Subtotal", "Payment Status"
            }
        ));
        jScrollPane1.setViewportView(jTable1);

        jLabel6.setText("Invoice Number");

        jLabel7.setText("Payment Status");

        jComboBox3.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Paid", "Pending", "Partial" }));

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jLabel1)
                        .addGap(18, 18, 18)
                        .addComponent(jComboBox1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jLabel2)
                        .addGap(18, 18, 18)
                        .addComponent(jComboBox2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jLabel3)
                        .addGap(18, 18, 18)
                        .addComponent(jTextField1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jLabel4)
                        .addGap(18, 18, 18)
                        .addComponent(jTextField2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jLabel5)
                        .addGap(18, 18, 18)
                        .addComponent(jTextField3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jLabel6)
                        .addGap(18, 18, 18)
                        .addComponent(jTextField4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jButton3)
                            .addComponent(jButton1)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(jLabel7)
                                .addGap(18, 18, 18)
                                .addComponent(jComboBox3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addGap(60, 60, 60)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jButton2)
                            .addComponent(jButton4)))
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 654, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(16, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel1)
                            .addComponent(jComboBox1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(18, 18, 18)
                        .addComponent(jLabel2))
                    .addComponent(jComboBox2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel3)
                    .addComponent(jTextField1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel4)
                    .addComponent(jTextField2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel5)
                    .addComponent(jTextField3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel6)
                    .addComponent(jTextField4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel7)
                    .addComponent(jComboBox3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(31, 31, 31)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButton1)
                    .addComponent(jButton2))
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButton3)
                    .addComponent(jButton4))
                .addGap(48, 48, 48)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 139, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(103, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void jTextField2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jTextField2ActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_jTextField2ActionPerformed

    private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton1ActionPerformed
        try {
            if (jComboBox1.getSelectedItem() == null || jComboBox2.getSelectedItem() == null) {
                throw new IllegalArgumentException("Please select a supplier and product.");
            }
            String invoiceNumber = jTextField4.getText().trim();
            if (invoiceNumber.isEmpty()) throw new IllegalArgumentException("Invoice Number is required.");

            LocalDate purchaseDate = requiredIsoDate(jTextField3.getText(), "Purchase Date");
            BigDecimal quantity = requiredPositive(jTextField1.getText(), "Quantity");
            BigDecimal costPrice = requiredPositive(jTextField2.getText(), "Cost Price");
            BigDecimal sellingPrice = requiredPositive(batchSellingPriceField.getText(), "Our Selling Price");
            BigDecimal retailPrice = optionalNonNegative(batchRetailPriceField.getText(), "Retail Price");
            String expiryText = batchExpiryDateField.getText().trim();
            LocalDate expiryDate = expiryText.isEmpty() ? null : requiredIsoDate(expiryText, "Expiry Date");
            if (expiryDate != null && expiryDate.isBefore(purchaseDate)) {
                throw new IllegalArgumentException("Expiry Date cannot be before Purchase Date.");
            }

            int supplierId = selectedId(jComboBox1);
            int productId = selectedId(jComboBox2);
            BigDecimal subtotal = quantity.multiply(costPrice);
            String paymentStatus = jComboBox3.getSelectedItem().toString();

            try (Connection con = DBConnection.getConnection()) {
                con.setAutoCommit(false);
                try {
                    int purchaseId;
                    String purchaseSQL = "INSERT INTO suppliers_purchases "
                            + "(supplier_id, invoice_number, purchase_date, total_amount, payment_status) VALUES (?, ?, ?, ?, ?)";
                    try (PreparedStatement pstPurchase = con.prepareStatement(purchaseSQL, Statement.RETURN_GENERATED_KEYS)) {
                        pstPurchase.setInt(1, supplierId);
                        pstPurchase.setString(2, invoiceNumber);
                        pstPurchase.setTimestamp(3, java.sql.Timestamp.valueOf(purchaseDate.atStartOfDay()));
                        pstPurchase.setBigDecimal(4, subtotal);
                        pstPurchase.setString(5, paymentStatus);
                        pstPurchase.executeUpdate();
                        try (ResultSet keys = pstPurchase.getGeneratedKeys()) {
                            if (!keys.next()) throw new IllegalStateException("Purchase ID was not generated.");
                            purchaseId = keys.getInt(1);
                        }
                    }

                    int purchaseItemId;
                    String itemSQL = "INSERT INTO purchase_items "
                            + "(purchase_id, product_id, quantity, cost_price, subtotal) VALUES (?, ?, ?, ?, ?)";
                    try (PreparedStatement pstItem = con.prepareStatement(itemSQL, Statement.RETURN_GENERATED_KEYS)) {
                        pstItem.setInt(1, purchaseId); pstItem.setInt(2, productId);
                        pstItem.setBigDecimal(3, quantity); pstItem.setBigDecimal(4, costPrice); pstItem.setBigDecimal(5, subtotal);
                        pstItem.executeUpdate();
                        try (ResultSet keys = pstItem.getGeneratedKeys()) {
                            if (!keys.next()) throw new IllegalStateException("Purchase item ID was not generated; purchase was rolled back.");
                            purchaseItemId = keys.getInt(1);
                        }
                    }

                    String batchSQL = "INSERT INTO product_batches (product_id, purchase_id, purchase_item_id, source_type, "
                            + "cost_price, selling_price, retail_price, expiry_date, quantity_received, quantity_available, active) "
                            + "VALUES (?, ?, ?, 'PURCHASE', ?, ?, ?, ?, ?, ?, 1)";
                    try (PreparedStatement pstBatch = con.prepareStatement(batchSQL)) {
                        pstBatch.setInt(1, productId); pstBatch.setInt(2, purchaseId); pstBatch.setInt(3, purchaseItemId);
                        pstBatch.setBigDecimal(4, costPrice); pstBatch.setBigDecimal(5, sellingPrice);
                        if (retailPrice == null) pstBatch.setNull(6, java.sql.Types.DECIMAL); else pstBatch.setBigDecimal(6, retailPrice);
                        if (expiryDate == null) pstBatch.setNull(7, java.sql.Types.DATE); else pstBatch.setDate(7, java.sql.Date.valueOf(expiryDate));
                        pstBatch.setBigDecimal(8, quantity); pstBatch.setBigDecimal(9, quantity);
                        if (pstBatch.executeUpdate() != 1) throw new IllegalStateException("Purchase batch was not created.");
                    }

                    try (PreparedStatement pstStock = con.prepareStatement("UPDATE products SET stock_quantity = stock_quantity + ? WHERE product_id = ?")) {
                        pstStock.setBigDecimal(1, quantity); pstStock.setInt(2, productId);
                        if (pstStock.executeUpdate() != 1) throw new IllegalStateException("Product stock could not be updated.");
                    }
                    try (PreparedStatement pstMovement = con.prepareStatement("INSERT INTO stock_movements (product_id, movement_type, quantity, reference_id, user_id) VALUES (?, ?, ?, ?, ?)")) {
                        pstMovement.setInt(1, productId); pstMovement.setString(2, "PURCHASE"); pstMovement.setBigDecimal(3, quantity);
                        pstMovement.setInt(4, purchaseId); pstMovement.setInt(5, Session.currentUserId()); pstMovement.executeUpdate();
                    }
                    con.commit();
                } catch (Exception e) {
                    con.rollback();
                    throw e;
                } finally {
                    con.setAutoCommit(true);
                }
            }
            JOptionPane.showMessageDialog(this, "Purchase Added Successfully!\nStock Updated Successfully!");
            clearPurchaseEntry(false);
            jButton2ActionPerformed(null);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Purchase Failed: " + e.getMessage());
        }

    }//GEN-LAST:event_jButton1ActionPerformed

    private void jButton3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton3ActionPerformed
        int row = jTable1.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Please select a purchase to delete.");
            return;
        }
        int purchaseId = Integer.parseInt(jTable1.getValueAt(row, 0).toString());
        if (JOptionPane.showConfirmDialog(this, "Are you sure you want to delete this purchase?", "Confirm Delete", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;

        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                java.util.List<Integer> batchIds = new java.util.ArrayList<>();
                java.util.List<Integer> productIds = new java.util.ArrayList<>();
                java.util.List<BigDecimal> receivedQuantities = new java.util.ArrayList<>();
                String batchesSQL = "SELECT batch_id, product_id, quantity_received, quantity_available FROM product_batches "
                        + "WHERE source_type = 'PURCHASE' AND purchase_id = ? FOR UPDATE";
                try (PreparedStatement pstBatches = con.prepareStatement(batchesSQL)) {
                    pstBatches.setInt(1, purchaseId);
                    try (ResultSet rs = pstBatches.executeQuery()) {
                        while (rs.next()) {
                            BigDecimal received = rs.getBigDecimal("quantity_received");
                            BigDecimal available = rs.getBigDecimal("quantity_available");
                            if (available == null || received == null || available.compareTo(received) != 0) {
                                throw new IllegalStateException("This purchase cannot be deleted because stock from this batch has already been sold.");
                            }
                            batchIds.add(rs.getInt("batch_id"));
                            productIds.add(rs.getInt("product_id"));
                            receivedQuantities.add(received);
                        }
                    }
                }
                if (batchIds.isEmpty()) {
                    throw new IllegalStateException("This is a legacy purchase created before batch tracking and cannot be safely deleted.");
                }

                for (int i = 0; i < batchIds.size(); i++) {
                    try (PreparedStatement pstDeleteBatch = con.prepareStatement("DELETE FROM product_batches WHERE batch_id = ? AND purchase_id = ? AND source_type = 'PURCHASE'")) {
                        pstDeleteBatch.setInt(1, batchIds.get(i)); pstDeleteBatch.setInt(2, purchaseId);
                        if (pstDeleteBatch.executeUpdate() != 1) throw new IllegalStateException("Purchase batch could not be deleted.");
                    }
                    try (PreparedStatement pstStock = con.prepareStatement("UPDATE products SET stock_quantity = stock_quantity - ? WHERE product_id = ? AND stock_quantity >= ?")) {
                        BigDecimal quantity = receivedQuantities.get(i);
                        pstStock.setBigDecimal(1, quantity); pstStock.setInt(2, productIds.get(i)); pstStock.setBigDecimal(3, quantity);
                        if (pstStock.executeUpdate() != 1) throw new IllegalStateException("Purchase cannot be deleted because it would make product stock negative.");
                    }
                    try (PreparedStatement pstMovement = con.prepareStatement("INSERT INTO stock_movements (product_id, movement_type, quantity, reference_id, user_id) VALUES (?, ?, ?, ?, ?)")) {
                        pstMovement.setInt(1, productIds.get(i)); pstMovement.setString(2, "PURCHASE_DELETE");
                        pstMovement.setBigDecimal(3, receivedQuantities.get(i).negate()); pstMovement.setInt(4, purchaseId); pstMovement.setInt(5, Session.currentUserId());
                        pstMovement.executeUpdate();
                    }
                }
                try (PreparedStatement pstItems = con.prepareStatement("DELETE FROM purchase_items WHERE purchase_id = ?")) {
                    pstItems.setInt(1, purchaseId); pstItems.executeUpdate();
                }
                try (PreparedStatement pstPurchase = con.prepareStatement("DELETE FROM suppliers_purchases WHERE purchase_id = ?")) {
                    pstPurchase.setInt(1, purchaseId);
                    if (pstPurchase.executeUpdate() != 1) throw new IllegalStateException("Purchase could not be deleted.");
                }
                con.commit();
                JOptionPane.showMessageDialog(this, "Purchase deleted successfully!");
                jButton2ActionPerformed(null);
            } catch (Exception e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        } catch (Exception e) {
            String message = e.getMessage();
            if ("This is a legacy purchase created before batch tracking and cannot be safely deleted.".equals(message)
                    || "This purchase cannot be deleted because stock from this batch has already been sold.".equals(message)) {
                JOptionPane.showMessageDialog(this, message);
            } else {
                JOptionPane.showMessageDialog(this, "Delete failed: " + message);
            }
        }

    }//GEN-LAST:event_jButton3ActionPerformed

    private void jButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton2ActionPerformed
                                               

    try {
        Connection con = DBConnection.getConnection();

        String sql = "SELECT sp.purchase_id, pb.batch_id, s.supplier_name, "
                + "sp.invoice_number, sp.purchase_date, p.product_name, pi.quantity, pi.cost_price, "
                + "pb.selling_price, pb.retail_price, pb.expiry_date, pb.quantity_available, "
                + "pi.subtotal, sp.payment_status "
                + "FROM suppliers_purchases sp "
                + "JOIN suppliers s ON sp.supplier_id = s.supplier_id "
                + "JOIN purchase_items pi ON sp.purchase_id = pi.purchase_id "
                + "JOIN products p ON pi.product_id = p.product_id "
                + "LEFT JOIN product_batches pb ON pb.purchase_item_id = pi.purchase_item_id "
                + "AND pb.source_type = 'PURCHASE' "
                + "ORDER BY sp.purchase_id DESC";

        PreparedStatement pst = con.prepareStatement(sql);
        ResultSet rs = pst.executeQuery();

        DefaultTableModel model =
                (DefaultTableModel) jTable1.getModel();

        model.setRowCount(0);

        while (rs.next()) {

            model.addRow(new Object[]{
                rs.getInt("purchase_id"),
                rs.getObject("batch_id") == null ? "LEGACY" : rs.getInt("batch_id"),
                rs.getString("supplier_name"),
                rs.getString("invoice_number"),
                rs.getTimestamp("purchase_date"),
                rs.getString("product_name"),
                rs.getBigDecimal("quantity"),
                rs.getBigDecimal("cost_price"),
                rs.getBigDecimal("selling_price"),
                rs.getBigDecimal("retail_price"),
                rs.getDate("expiry_date"),
                rs.getBigDecimal("quantity_available"),
                rs.getBigDecimal("subtotal"),
                rs.getString("payment_status")
            });
        }

        rs.close();
        pst.close();
        con.close();

    } catch (Exception e) {

        JOptionPane.showMessageDialog(this,
                "Error loading purchases: " + e.getMessage());
    }

    }//GEN-LAST:event_jButton2ActionPerformed

    private void jTextField3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jTextField3ActionPerformed
        try { requiredIsoDate(jTextField3.getText(), "Purchase Date"); }
        catch (IllegalArgumentException e) { JOptionPane.showMessageDialog(this, e.getMessage()); }
    }//GEN-LAST:event_jTextField3ActionPerformed

    private void jButton4ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton4ActionPerformed
                                          

    clearPurchaseEntry(true);

    JOptionPane.showMessageDialog(this,
            "Fields cleared.");


    }//GEN-LAST:event_jButton4ActionPerformed

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException ex) {
            logger.log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(() -> new PurchaseForm().setVisible(true));
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton jButton1;
    private javax.swing.JButton jButton2;
    private javax.swing.JButton jButton3;
    private javax.swing.JButton jButton4;
    private javax.swing.JComboBox<String> jComboBox1;
    private javax.swing.JComboBox<String> jComboBox2;
    private javax.swing.JComboBox<String> jComboBox3;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTable jTable1;
    private javax.swing.JTextField jTextField1;
    private javax.swing.JTextField jTextField2;
    private javax.swing.JTextField jTextField3;
    private javax.swing.JTextField jTextField4;
    // End of variables declaration//GEN-END:variables
}
