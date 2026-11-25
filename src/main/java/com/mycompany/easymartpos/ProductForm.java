/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JFrame.java to edit this template
 */
package com.mycompany.easymartpos;

import com.mycompany.easymartpos.ui.UITheme;

import com.mycompany.easymartpos.service.Authorization;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javax.swing.JOptionPane;

public class ProductForm extends javax.swing.JFrame {
    
    private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(ProductForm.class.getName());

    
    public ProductForm() {
        Authorization.requireAdmin();
        initComponents();
        setTitle("USHANTHA SUPER MART | Products"); setDefaultCloseOperation(DISPOSE_ON_CLOSE); setSize(1100, 680); setMinimumSize(new java.awt.Dimension(920, 580)); setLocationByPlatform(true);
        UITheme.applyWindow(this);
        loadCategories();
    }
    
    
    private void loadCategories() {

    String sql = "SELECT category_id, category_name FROM categories ORDER BY category_id ASC";

    try {
        Connection con = DBConnection.getConnection();
        PreparedStatement pst = con.prepareStatement(sql);
        ResultSet rs = pst.executeQuery();

        cmbCategory.removeAllItems();

        while (rs.next()) {
            cmbCategory.addItem(
                rs.getInt("category_id") + " - " +
                rs.getString("category_name")
            );
        }

        rs.close();
        pst.close();
        con.close();

    } catch (Exception e) {
        JOptionPane.showMessageDialog(this,
                "Error loading categories: " + e.getMessage());
    }
}

    
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        jLabel5 = new javax.swing.JLabel();
        jLabel6 = new javax.swing.JLabel();
        jLabel7 = new javax.swing.JLabel();
        jLabel8 = new javax.swing.JLabel();
        jTextField1 = new javax.swing.JTextField();
        jTextField2 = new javax.swing.JTextField();
        jButton1 = new javax.swing.JButton();
        jButton2 = new javax.swing.JButton();
        jButton3 = new javax.swing.JButton();
        jButton4 = new javax.swing.JButton();
        jButton5 = new javax.swing.JButton();
        cmbCategory = new javax.swing.JComboBox<>();
        jTextField3 = new javax.swing.JTextField();
        jTextField4 = new javax.swing.JTextField();
        jTextField5 = new javax.swing.JTextField();
        jTextField6 = new javax.swing.JTextField();
        jTextField7 = new javax.swing.JTextField();
        jScrollPane1 = new javax.swing.JScrollPane();
        jTable1 = new javax.swing.JTable();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        jLabel1.setText("Barcode");

        jLabel2.setText("Product Name ");

        jLabel3.setText("Category ");

        jLabel4.setText("Cost Price ");

        jLabel5.setText("Selling Price ");

        jLabel6.setText("Stock Quantity ");

        jLabel7.setText("Minimum Stock ");

        jLabel8.setText("Expiry Date");

        jTextField1.addActionListener(this::jTextField1ActionPerformed);

        jButton1.setText("Add Product ");
        jButton1.addActionListener(this::jButton1ActionPerformed);

        jButton2.setText("Update");
        jButton2.addActionListener(this::jButton2ActionPerformed);

        jButton3.setText("Delete");
        jButton3.addActionListener(this::jButton3ActionPerformed);

        jButton4.setText("Clear");
        jButton4.addActionListener(this::jButton4ActionPerformed);

        jButton5.setText("View Products");
        jButton5.addActionListener(this::jButton5ActionPerformed);

        cmbCategory.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        cmbCategory.addActionListener(this::cmbCategoryActionPerformed);

        jTextField5.addActionListener(this::jTextField5ActionPerformed);

        jTextField6.addActionListener(this::jTextField6ActionPerformed);

        jTextField7.addActionListener(this::jTextField7ActionPerformed);

        jTable1.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null, null, null, null, null, null},
                {null, null, null, null, null, null, null, null, null},
                {null, null, null, null, null, null, null, null, null},
                {null, null, null, null, null, null, null, null, null}
            },
            new String [] {
                "Product ID", "Barcode", "Product Name", "Categoryl", "Cost Price", "Selling Price", "Stock Quantity", "Minimum Stock", "Expiry Date"
            }
        ));
        jScrollPane1.setViewportView(jTable1);

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
                        .addComponent(jTextField1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jLabel2)
                        .addGap(18, 18, 18)
                        .addComponent(jTextField2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jLabel3)
                        .addGap(18, 18, 18)
                        .addComponent(cmbCategory, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jLabel4)
                        .addGap(18, 18, 18)
                        .addComponent(jTextField3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jLabel5)
                        .addGap(18, 18, 18)
                        .addComponent(jTextField4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jLabel6)
                        .addGap(18, 18, 18)
                        .addComponent(jTextField5, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jLabel7)
                        .addGap(18, 18, 18)
                        .addComponent(jTextField6, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jLabel8)
                        .addGap(18, 18, 18)
                        .addComponent(jTextField7, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(jButton5)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jButton1)
                            .addComponent(jButton3))
                        .addGap(18, 18, 18)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jButton4)
                            .addComponent(jButton2)))
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 865, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(jTextField1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel2)
                    .addComponent(jTextField2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel3)
                    .addComponent(cmbCategory, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel4)
                    .addComponent(jTextField3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel5)
                    .addComponent(jTextField4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel6)
                    .addComponent(jTextField5, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel7)
                    .addComponent(jTextField6, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel8)
                    .addComponent(jTextField7, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(27, 27, 27)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButton1)
                    .addComponent(jButton2))
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButton3)
                    .addComponent(jButton4))
                .addGap(18, 18, 18)
                .addComponent(jButton5)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 62, Short.MAX_VALUE)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 128, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(290, 290, 290))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton1ActionPerformed
        
        String barcode = jTextField1.getText();
        String productName = jTextField2.getText();
        String costPrice = jTextField3.getText();
        String sellingPrice = jTextField4.getText();
        String stockQuantity = jTextField5.getText();
        String minimumStock = jTextField6.getText();
        String expiryDate = jTextField7.getText();

    if (barcode.isEmpty() || productName.isEmpty() ||
        costPrice.isEmpty() || sellingPrice.isEmpty() ||
        stockQuantity.isEmpty() || minimumStock.isEmpty()) {

    JOptionPane.showMessageDialog(this, "Please fill all required fields.");
    return;
}

    String sql = "INSERT INTO products " +
             "(barcode, product_name, category_id, cost_price, selling_price, " +
             "stock_quantity, minimum_stock, expiry_date) " +
             "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    try {
        Connection con = DBConnection.getConnection();

        PreparedStatement pst = con.prepareStatement(sql);

        pst.setString(1, barcode);
        pst.setString(2, productName);

    // Category
    String selectedCategory = (String) cmbCategory.getSelectedItem();

    int categoryId = Integer.parseInt(
            selectedCategory.split(" - ")[0]
    );

    pst.setInt(3, categoryId);

        pst.setDouble(4, Double.parseDouble(costPrice));
        pst.setDouble(5, Double.parseDouble(sellingPrice));
        pst.setDouble(6, Double.parseDouble(stockQuantity));
        pst.setDouble(7, Double.parseDouble(minimumStock));

    if (expiryDate.isEmpty()) {
        pst.setObject(8, null);
    } else {
        pst.setDate(8, java.sql.Date.valueOf(expiryDate));
    }

    pst.executeUpdate();

    JOptionPane.showMessageDialog(this, "Product Added Successfully!");

    pst.close();
    con.close();

    jTextField1.setText("");
    jTextField2.setText("");
    jTextField3.setText("");
    jTextField4.setText("");
    jTextField5.setText("");
    jTextField6.setText("");
    jTextField7.setText("");

    } catch (Exception e) {
        JOptionPane.showMessageDialog(this, "Error: " + e.getMessage());
    }
    }//GEN-LAST:event_jButton1ActionPerformed

    private void cmbCategoryActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cmbCategoryActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_cmbCategoryActionPerformed

    private void jButton3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton3ActionPerformed
        String barcode = jTextField1.getText();

if (barcode.isEmpty()) {
    JOptionPane.showMessageDialog(this, "Enter the barcode to delete.");
    return;
}

int confirm = JOptionPane.showConfirmDialog(
        this,
        "Are you sure you want to delete this product?",
        "Confirm Delete",
        JOptionPane.YES_NO_OPTION
);

if (confirm != JOptionPane.YES_OPTION) {
    return;
}

// Preserve sale history: legacy deletion is now an archive operation.
String sql = "UPDATE products SET active = FALSE WHERE barcode = ?";

try {
    Connection con = DBConnection.getConnection();
    PreparedStatement pst = con.prepareStatement(sql);

    pst.setString(1, barcode);

    int rows = pst.executeUpdate();

    if (rows > 0) {
        JOptionPane.showMessageDialog(this, "Product Archived Successfully!");

        jTextField1.setText("");
        jTextField2.setText("");
        jTextField3.setText("");
        jTextField4.setText("");
        jTextField5.setText("");
        jTextField6.setText("");
        jTextField7.setText("");

    } else {
        JOptionPane.showMessageDialog(this, "Product not found.");
    }

    pst.close();
    con.close();

} catch (Exception e) {
    JOptionPane.showMessageDialog(this,
            "Error: " + e.getMessage());
}
    }//GEN-LAST:event_jButton3ActionPerformed

    private void jTextField1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jTextField1ActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_jTextField1ActionPerformed

    private void jTextField5ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jTextField5ActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_jTextField5ActionPerformed

    private void jTextField6ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jTextField6ActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_jTextField6ActionPerformed

    private void jTextField7ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jTextField7ActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_jTextField7ActionPerformed

    private void jButton5ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton5ActionPerformed
        String sql = "SELECT p.product_id, p.barcode, p.product_name, " +
             "c.category_name, p.cost_price, p.selling_price, " +
             "p.stock_quantity, p.minimum_stock, p.expiry_date " +
             "FROM products p " +
             "LEFT JOIN categories c ON p.category_id = c.category_id";

try {
    Connection con = DBConnection.getConnection();
    PreparedStatement pst = con.prepareStatement(sql);
    ResultSet rs = pst.executeQuery();

    javax.swing.table.DefaultTableModel model =
            (javax.swing.table.DefaultTableModel) jTable1.getModel();

    model.setRowCount(0);

    while (rs.next()) {
        model.addRow(new Object[]{
            rs.getInt("product_id"),
            rs.getString("barcode"),
            rs.getString("product_name"),
            rs.getString("category_name"),
            rs.getDouble("cost_price"),
            rs.getDouble("selling_price"),
            rs.getDouble("stock_quantity"),
            rs.getDouble("minimum_stock"),
            rs.getDate("expiry_date")
        });
    }

    rs.close();
    pst.close();
    con.close();

} catch (Exception e) {
    JOptionPane.showMessageDialog(this,
            "Error loading products: " + e.getMessage());
}
    }//GEN-LAST:event_jButton5ActionPerformed

    private void jButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton2ActionPerformed
        String barcode = jTextField1.getText();
String productName = jTextField2.getText();
String costPrice = jTextField3.getText();
String sellingPrice = jTextField4.getText();
String stockQuantity = jTextField5.getText();
String minimumStock = jTextField6.getText();
String expiryDate = jTextField7.getText();

if (barcode.isEmpty()) {
    JOptionPane.showMessageDialog(this, "Enter the barcode of the product to update.");
    return;
}

String sql = "UPDATE products SET product_name = ?, cost_price = ?, " +
             "selling_price = ?, stock_quantity = ?, minimum_stock = ?, " +
             "expiry_date = ? WHERE barcode = ?";

try {
    Connection con = DBConnection.getConnection();
    PreparedStatement pst = con.prepareStatement(sql);

    pst.setString(1, productName);
    pst.setDouble(2, Double.parseDouble(costPrice));
    pst.setDouble(3, Double.parseDouble(sellingPrice));
    pst.setDouble(4, Double.parseDouble(stockQuantity));
    pst.setDouble(5, Double.parseDouble(minimumStock));

    if (expiryDate.isEmpty()) {
        pst.setObject(6, null);
    } else {
        pst.setDate(6, java.sql.Date.valueOf(expiryDate));
    }

    pst.setString(7, barcode);

    int rows = pst.executeUpdate();

    if (rows > 0) {
        JOptionPane.showMessageDialog(this, "Product Updated Successfully!");
    } else {
        JOptionPane.showMessageDialog(this, "Product not found.");
    }

    pst.close();
    con.close();

} catch (Exception e) {
    JOptionPane.showMessageDialog(this, "Error: " + e.getMessage());
}
    }//GEN-LAST:event_jButton2ActionPerformed

    private void jButton4ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton4ActionPerformed
        jTextField1.setText("");
        jTextField2.setText("");
        jTextField3.setText("");
        jTextField4.setText("");
        jTextField5.setText("");
        jTextField6.setText("");
        jTextField7.setText("");

cmbCategory.setSelectedIndex(-1);
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
        java.awt.EventQueue.invokeLater(() -> new ProductForm().setVisible(true));
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JComboBox<String> cmbCategory;
    private javax.swing.JButton jButton1;
    private javax.swing.JButton jButton2;
    private javax.swing.JButton jButton3;
    private javax.swing.JButton jButton4;
    private javax.swing.JButton jButton5;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTable jTable1;
    private javax.swing.JTextField jTextField1;
    private javax.swing.JTextField jTextField2;
    private javax.swing.JTextField jTextField3;
    private javax.swing.JTextField jTextField4;
    private javax.swing.JTextField jTextField5;
    private javax.swing.JTextField jTextField6;
    private javax.swing.JTextField jTextField7;
    // End of variables declaration//GEN-END:variables
}
