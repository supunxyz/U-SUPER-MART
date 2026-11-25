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


public class SupplierForm extends javax.swing.JFrame {
    
    private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(SupplierForm.class.getName());
    private final javax.swing.JTextField searchSuppliers = new javax.swing.JTextField(22);

    
    public SupplierForm() {
        Authorization.requireAdmin();
        initComponents();
        buildModernSupplierLayout();
        setTitle("USHANTHA SUPER MART | Suppliers"); setDefaultCloseOperation(DISPOSE_ON_CLOSE); setSize(1080, 650); setMinimumSize(new java.awt.Dimension(900, 560)); setLocationByPlatform(true);
        UITheme.applyWindow(this);
        jButton5ActionPerformed(null);
    }

    /** Reuses existing supplier persistence/event logic in a wide card-based layout. */
    private void buildModernSupplierLayout() {
        javax.swing.JPanel root = new javax.swing.JPanel(new java.awt.BorderLayout(14, 14)); root.setBackground(UITheme.BACKGROUND); root.setBorder(javax.swing.BorderFactory.createEmptyBorder(18, 20, 18, 20));
        javax.swing.JPanel upper = new javax.swing.JPanel(new java.awt.BorderLayout(0, 12)); upper.setOpaque(false); upper.add(UITheme.pageHeader("SUPPLIERS", "Manage supplier contacts and purchasing partners"), java.awt.BorderLayout.NORTH);
        javax.swing.JPanel details = UITheme.card(new java.awt.GridLayout(0, 4, 12, 9));
        addSupplierField(details, "Supplier Name", jTextField1); addSupplierField(details, "Phone", jTextField2); addSupplierField(details, "Address", jTextField3); addSupplierField(details, "Email", jTextField4);
        javax.swing.JPanel actions = UITheme.card(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 10, 2)); actions.add(jButton1); actions.add(jButton2); actions.add(jButton3); actions.add(jButton4);
        upper.add(details, java.awt.BorderLayout.CENTER); upper.add(actions, java.awt.BorderLayout.SOUTH); root.add(upper, java.awt.BorderLayout.NORTH);
        javax.swing.JPanel records = UITheme.card(new java.awt.BorderLayout(0, 8));
        javax.swing.JPanel recordBar = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0)); recordBar.setOpaque(false);
        javax.swing.JLabel title = new javax.swing.JLabel("SUPPLIER RECORDS"); title.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 13)); title.setForeground(UITheme.GREY); recordBar.add(title); recordBar.add(new javax.swing.JLabel("Search Suppliers")); recordBar.add(searchSuppliers); recordBar.add(jButton5);
        records.add(recordBar, java.awt.BorderLayout.NORTH); records.add(jScrollPane1, java.awt.BorderLayout.CENTER); root.add(records, java.awt.BorderLayout.CENTER);
        getContentPane().removeAll(); getContentPane().setLayout(new java.awt.BorderLayout()); getContentPane().add(root, java.awt.BorderLayout.CENTER);
        installSupplierSearch();
    }

    private void addSupplierField(javax.swing.JPanel panel, String text, java.awt.Component field) {
        javax.swing.JLabel label = new javax.swing.JLabel(text); label.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 12)); label.setForeground(UITheme.GREY); panel.add(label); panel.add(field);
    }

    private void installSupplierSearch() {
        javax.swing.table.TableRowSorter<javax.swing.table.TableModel> sorter = new javax.swing.table.TableRowSorter<>(jTable1.getModel()); jTable1.setRowSorter(sorter);
        searchSuppliers.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void filter() { String text = searchSuppliers.getText().trim(); sorter.setRowFilter(text.isEmpty() ? null : javax.swing.RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(text))); }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent event) { filter(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent event) { filter(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent event) { filter(); }
        });
    }

    
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        jTextField1 = new javax.swing.JTextField();
        jTextField2 = new javax.swing.JTextField();
        jTextField3 = new javax.swing.JTextField();
        jTextField4 = new javax.swing.JTextField();
        jButton1 = new javax.swing.JButton();
        jButton2 = new javax.swing.JButton();
        jButton3 = new javax.swing.JButton();
        jButton4 = new javax.swing.JButton();
        jButton5 = new javax.swing.JButton();
        jScrollPane1 = new javax.swing.JScrollPane();
        jTable1 = new javax.swing.JTable();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        jLabel1.setText("Supplier Name ");

        jLabel2.setText("Phone ");

        jLabel3.setText("Address ");

        jLabel4.setText("Email");

        jButton1.setText("Add Supplier ");
        jButton1.addActionListener(this::jButton1ActionPerformed);

        jButton2.setText("Update");
        jButton2.addActionListener(this::jButton2ActionPerformed);

        jButton3.setText("Delete");
        jButton3.addActionListener(this::jButton3ActionPerformed);

        jButton4.setText("Clear");
        jButton4.addActionListener(this::jButton4ActionPerformed);

        jButton5.setText("View Suppliers");
        jButton5.addActionListener(this::jButton5ActionPerformed);

        jTable1.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null, null},
                {null, null, null, null, null},
                {null, null, null, null, null},
                {null, null, null, null, null}
            },
            new String [] {
                "Supplier ID ", "Supplier Name ", "Phone", "Address", "Email"
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
                        .addComponent(jTextField3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jLabel4)
                        .addGap(18, 18, 18)
                        .addComponent(jTextField4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(jButton5)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 523, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jButton1)
                            .addComponent(jButton3))
                        .addGap(62, 62, 62)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jButton4)
                            .addComponent(jButton2))))
                .addContainerGap(192, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(jTextField1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel2)
                    .addComponent(jTextField2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel3)
                    .addComponent(jTextField3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel4)
                    .addComponent(jTextField4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(36, 36, 36)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButton1)
                    .addComponent(jButton2))
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButton3)
                    .addComponent(jButton4))
                .addGap(18, 18, 18)
                .addComponent(jButton5)
                .addGap(55, 55, 55)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 119, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(142, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton1ActionPerformed
        String supplierName = jTextField1.getText();
String phone = jTextField2.getText();
String address = jTextField3.getText();
String email = jTextField4.getText();

if (supplierName.isEmpty()) {
    JOptionPane.showMessageDialog(this, "Please enter supplier name.");
    return;
}

String sql = "INSERT INTO suppliers " +
             "(supplier_name, phone, address, email) " +
             "VALUES (?, ?, ?, ?)";

try {
    Connection con = DBConnection.getConnection();
    PreparedStatement pst = con.prepareStatement(sql);

    pst.setString(1, supplierName);
    pst.setString(2, phone);
    pst.setString(3, address);
    pst.setString(4, email);

    pst.executeUpdate();

    JOptionPane.showMessageDialog(this,
            "Supplier Added Successfully!");

    pst.close();
    con.close();

    jTextField1.setText("");
    jTextField2.setText("");
    jTextField3.setText("");
    jTextField4.setText("");

} catch (Exception e) {
    JOptionPane.showMessageDialog(this,
            "Error: " + e.getMessage());
}
    }//GEN-LAST:event_jButton1ActionPerformed

    private void jButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton2ActionPerformed
        String supplierName = jTextField1.getText();
String phone = jTextField2.getText();
String address = jTextField3.getText();
String email = jTextField4.getText();

if (supplierName.isEmpty()) {
    JOptionPane.showMessageDialog(this, "Enter supplier name.");
    return;
}

String sql = "UPDATE suppliers SET phone = ?, address = ?, email = ? " +
             "WHERE supplier_name = ?";

try {
    Connection con = DBConnection.getConnection();
    PreparedStatement pst = con.prepareStatement(sql);

    pst.setString(1, phone);
    pst.setString(2, address);
    pst.setString(3, email);
    pst.setString(4, supplierName);

    int rows = pst.executeUpdate();

    if (rows > 0) {
        JOptionPane.showMessageDialog(this,
                "Supplier Updated Successfully!");
    } else {
        JOptionPane.showMessageDialog(this,
                "Supplier not found.");
    }

    pst.close();
    con.close();

} catch (Exception e) {
    JOptionPane.showMessageDialog(this,
            "Error: " + e.getMessage());
}
    }//GEN-LAST:event_jButton2ActionPerformed

    private void jButton5ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton5ActionPerformed
        String sql = "SELECT supplier_id, supplier_name, phone, address, email FROM suppliers";

try {
    Connection con = DBConnection.getConnection();
    PreparedStatement pst = con.prepareStatement(sql);
    ResultSet rs = pst.executeQuery();

    javax.swing.table.DefaultTableModel model =
            (javax.swing.table.DefaultTableModel) jTable1.getModel();

    model.setRowCount(0);

    while (rs.next()) {
        model.addRow(new Object[]{
            rs.getInt("supplier_id"),
            rs.getString("supplier_name"),
            rs.getString("phone"),
            rs.getString("address"),
            rs.getString("email")
        });
    }

    rs.close();
    pst.close();
    con.close();

} catch (Exception e) {
    JOptionPane.showMessageDialog(this,
            "Error loading suppliers: " + e.getMessage());
}
    }//GEN-LAST:event_jButton5ActionPerformed

    private void jButton3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton3ActionPerformed
        String supplierName = jTextField1.getText();

if (supplierName.isEmpty()) {
    JOptionPane.showMessageDialog(this, "Enter supplier name to delete.");
    return;
}

int confirm = JOptionPane.showConfirmDialog(
        this,
        "Are you sure you want to delete this supplier?",
        "Confirm Delete",
        JOptionPane.YES_NO_OPTION
);

if (confirm != JOptionPane.YES_OPTION) {
    return;
}

String sql = "DELETE FROM suppliers WHERE supplier_name = ?";

try {
    Connection con = DBConnection.getConnection();
    PreparedStatement pst = con.prepareStatement(sql);

    pst.setString(1, supplierName);

    int rows = pst.executeUpdate();

    if (rows > 0) {
        JOptionPane.showMessageDialog(this,
                "Supplier Deleted Successfully!");

        jTextField1.setText("");
        jTextField2.setText("");
        jTextField3.setText("");
        jTextField4.setText("");

    } else {
        JOptionPane.showMessageDialog(this,
                "Supplier not found.");
    }

    pst.close();
    con.close();

} catch (Exception e) {
    JOptionPane.showMessageDialog(this,
            "Error: " + e.getMessage());
}
    }//GEN-LAST:event_jButton3ActionPerformed

    private void jButton4ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton4ActionPerformed
        jTextField1.setText("");
        jTextField2.setText("");
        jTextField3.setText("");
        jTextField4.setText("");
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
        java.awt.EventQueue.invokeLater(() -> new SupplierForm().setVisible(true));
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton jButton1;
    private javax.swing.JButton jButton2;
    private javax.swing.JButton jButton3;
    private javax.swing.JButton jButton4;
    private javax.swing.JButton jButton5;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTable jTable1;
    private javax.swing.JTextField jTextField1;
    private javax.swing.JTextField jTextField2;
    private javax.swing.JTextField jTextField3;
    private javax.swing.JTextField jTextField4;
    // End of variables declaration//GEN-END:variables
}
