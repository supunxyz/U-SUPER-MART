/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JFrame.java to edit this template
 */
package com.mycompany.easymartpos;

import com.mycompany.easymartpos.ui.UITheme;

import com.mycompany.easymartpos.service.Authorization;
import com.mycompany.easymartpos.service.Session;
import com.mycompany.easymartpos.util.Money;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javax.swing.JOptionPane;


public class ExpenseForm extends javax.swing.JFrame {
    
    private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(ExpenseForm.class.getName());
    private final javax.swing.JTextField searchExpenses = new javax.swing.JTextField(22);
    private final javax.swing.JLabel totalExpenses = new javax.swing.JLabel("LKR 0.00");

    
    public ExpenseForm() {
        Authorization.requireAdmin();
        initComponents();
        buildModernExpenseLayout();
        setTitle("USHANTHA SUPER MART | Expenses"); setDefaultCloseOperation(DISPOSE_ON_CLOSE); setSize(1080, 650); setMinimumSize(new java.awt.Dimension(900, 560)); setLocationByPlatform(true);
        UITheme.applyWindow(this);
        UITheme.installMoneyRenderer(jTable1, 3); UITheme.installDateRenderer(jTable1, 4);
        jButton4ActionPerformed(null);
    }

    /** Keeps the existing expense fields/actions while removing the old vertical form layout. */
    private void buildModernExpenseLayout() {
        javax.swing.JPanel root = new javax.swing.JPanel(new java.awt.BorderLayout(14, 14)); root.setBackground(UITheme.BACKGROUND); root.setBorder(javax.swing.BorderFactory.createEmptyBorder(18, 20, 18, 20));
        javax.swing.JPanel upper = new javax.swing.JPanel(new java.awt.BorderLayout(0, 12)); upper.setOpaque(false); upper.add(UITheme.pageHeader("EXPENSES", "Record and review business expenses"), java.awt.BorderLayout.NORTH);
        javax.swing.JPanel details = UITheme.card(new java.awt.GridLayout(0, 4, 12, 9));
        addExpenseField(details, "Expense Type", jTextField1); addExpenseField(details, "Description", jTextField2); addExpenseField(details, "Amount", jTextField3);
        javax.swing.JPanel actions = UITheme.card(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 10, 2)); actions.add(jButton1); actions.add(jButton2); actions.add(jButton3);
        upper.add(details, java.awt.BorderLayout.CENTER); upper.add(actions, java.awt.BorderLayout.SOUTH); root.add(upper, java.awt.BorderLayout.NORTH);
        javax.swing.JPanel records = UITheme.card(new java.awt.BorderLayout(0, 8));
        javax.swing.JPanel recordBar = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0)); recordBar.setOpaque(false);
        javax.swing.JLabel title = new javax.swing.JLabel("EXPENSE RECORDS"); title.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 13)); title.setForeground(UITheme.GREY); recordBar.add(title); recordBar.add(new javax.swing.JLabel("Search Expenses")); recordBar.add(searchExpenses); recordBar.add(jButton4);
        records.add(recordBar, java.awt.BorderLayout.NORTH); records.add(jScrollPane1, java.awt.BorderLayout.CENTER);
        javax.swing.JPanel total = new javax.swing.JPanel(new java.awt.BorderLayout()); total.setOpaque(false); javax.swing.JLabel label = new javax.swing.JLabel("TOTAL EXPENSES"); label.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 12)); label.setForeground(UITheme.GREY); totalExpenses.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 21)); totalExpenses.setForeground(UITheme.RED); total.add(label, java.awt.BorderLayout.WEST); total.add(totalExpenses, java.awt.BorderLayout.EAST); records.add(total, java.awt.BorderLayout.SOUTH);
        root.add(records, java.awt.BorderLayout.CENTER); getContentPane().removeAll(); getContentPane().setLayout(new java.awt.BorderLayout()); getContentPane().add(root, java.awt.BorderLayout.CENTER);
        installExpenseSearch(); jTable1.getModel().addTableModelListener(event -> refreshExpenseSummary()); refreshExpenseSummary();
    }

    private void addExpenseField(javax.swing.JPanel panel, String text, java.awt.Component field) {
        javax.swing.JLabel label = new javax.swing.JLabel(text); label.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 12)); label.setForeground(UITheme.GREY); panel.add(label); panel.add(field);
    }

    private void installExpenseSearch() {
        javax.swing.table.TableRowSorter<javax.swing.table.TableModel> sorter = new javax.swing.table.TableRowSorter<>(jTable1.getModel()); jTable1.setRowSorter(sorter);
        searchExpenses.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void filter() { String text = searchExpenses.getText().trim(); sorter.setRowFilter(text.isEmpty() ? null : javax.swing.RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(text))); }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent event) { filter(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent event) { filter(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent event) { filter(); }
        });
    }

    private void refreshExpenseSummary() {
        java.math.BigDecimal sum = java.math.BigDecimal.ZERO; javax.swing.table.TableModel model = jTable1.getModel();
        for (int row = 0; row < model.getRowCount(); row++) { Object value = model.getValueAt(row, 3); if (value != null) try { sum = sum.add(new java.math.BigDecimal(value.toString())); } catch (NumberFormatException ignored) { } }
        totalExpenses.setText(UITheme.money(sum));
    }

    
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        jTextField1 = new javax.swing.JTextField();
        jTextField2 = new javax.swing.JTextField();
        jTextField3 = new javax.swing.JTextField();
        jButton1 = new javax.swing.JButton();
        jButton2 = new javax.swing.JButton();
        jButton3 = new javax.swing.JButton();
        jButton4 = new javax.swing.JButton();
        jScrollPane1 = new javax.swing.JScrollPane();
        jTable1 = new javax.swing.JTable();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        jLabel1.setText("Expense Type");

        jLabel2.setText("Description");

        jLabel3.setText("Amount");

        jTextField1.addActionListener(this::jTextField1ActionPerformed);

        jTextField2.addActionListener(this::jTextField2ActionPerformed);

        jButton1.setText("Add Expense ");
        jButton1.addActionListener(this::jButton1ActionPerformed);

        jButton2.setText("Delete ");
        jButton2.addActionListener(this::jButton2ActionPerformed);

        jButton3.setText("Clear ");
        jButton3.addActionListener(this::jButton3ActionPerformed);

        jButton4.setText("View Expenses");
        jButton4.addActionListener(this::jButton4ActionPerformed);

        jTable1.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null, null, null},
                {null, null, null, null, null, null},
                {null, null, null, null, null, null},
                {null, null, null, null, null, null}
            },
            new String [] {
                "Expense ID ", "Expense Type ", "Description", "Amount", "Date", "Time"
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
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jButton1)
                            .addComponent(jButton3))
                        .addGap(39, 39, 39)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jButton4)
                            .addComponent(jButton2)))
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
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 550, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(26, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(jTextField1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel2)
                    .addComponent(jTextField2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel3)
                    .addComponent(jTextField3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(37, 37, 37)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButton1)
                    .addComponent(jButton2))
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButton3)
                    .addComponent(jButton4))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 54, Short.MAX_VALUE)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 86, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(232, 232, 232))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void jTextField1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jTextField1ActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_jTextField1ActionPerformed

    private void jTextField2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jTextField2ActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_jTextField2ActionPerformed

    private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton1ActionPerformed
        String expenseType = jTextField1.getText();
        String description = jTextField2.getText();
        String amountText = jTextField3.getText();

if (expenseType.isEmpty() || amountText.isEmpty()) {
    JOptionPane.showMessageDialog(this,
            "Please enter expense type and amount.");
    return;
}

try {
    java.math.BigDecimal amount = Money.parse(amountText, "expense amount");
    if (amount.signum() == 0) throw new IllegalArgumentException("Expense amount must be greater than zero.");

    String sql = "INSERT INTO expenses " +
                 "(expense_type, description, amount, user_id) " +
                 "VALUES (?, ?, ?, ?)";

    Connection con = DBConnection.getConnection();
    PreparedStatement pst = con.prepareStatement(sql);

    pst.setString(1, expenseType);
    pst.setString(2, description);
    pst.setBigDecimal(3, amount);
    pst.setInt(4, Session.currentUserId());

    pst.executeUpdate();

    JOptionPane.showMessageDialog(this,
            "Expense Added Successfully!");

    pst.close();
    con.close();

    jTextField1.setText("");
    jTextField2.setText("");
    jTextField3.setText("");

} catch (Exception e) {
    JOptionPane.showMessageDialog(this,
            "Error: " + e.getMessage());
}
    }//GEN-LAST:event_jButton1ActionPerformed

    private void jButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton2ActionPerformed
        int selectedRow = jTable1.getSelectedRow();

if (selectedRow == -1) {
    JOptionPane.showMessageDialog(this,
            "Please select an expense to delete.");
    return;
}

int expenseId = Integer.parseInt(
        jTable1.getValueAt(selectedRow, 0).toString());

int confirm = JOptionPane.showConfirmDialog(
        this,
        "Are you sure you want to delete this expense?",
        "Confirm Delete",
        JOptionPane.YES_NO_OPTION);

if (confirm == JOptionPane.YES_OPTION) {

    try {
        String sql = "DELETE FROM expenses WHERE expense_id = ?";

        Connection con = DBConnection.getConnection();
        PreparedStatement pst = con.prepareStatement(sql);

        pst.setInt(1, expenseId);
        pst.executeUpdate();

        pst.close();
        con.close();

        JOptionPane.showMessageDialog(this,
                "Expense Deleted Successfully!");

        ((javax.swing.table.DefaultTableModel) jTable1.getModel())
                .removeRow(selectedRow);

    } catch (Exception e) {
        JOptionPane.showMessageDialog(this,
                "Error: " + e.getMessage());
    }
}
    }//GEN-LAST:event_jButton2ActionPerformed

    private void jButton3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton3ActionPerformed
        jTextField1.setText("");
        jTextField2.setText("");
        jTextField3.setText("");
    }//GEN-LAST:event_jButton3ActionPerformed

    private void jButton4ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton4ActionPerformed
        String sql = "SELECT expense_id, expense_type, description, amount, expense_date " +
             "FROM expenses";

try {
    Connection con = DBConnection.getConnection();
    PreparedStatement pst = con.prepareStatement(sql);
    ResultSet rs = pst.executeQuery();

    javax.swing.table.DefaultTableModel model =
            (javax.swing.table.DefaultTableModel) jTable1.getModel();

    model.setRowCount(0);

    while (rs.next()) {

        java.sql.Timestamp timestamp =
                rs.getTimestamp("expense_date");

        String date = new java.text.SimpleDateFormat(
                "yyyy-MM-dd").format(timestamp);

        String time = new java.text.SimpleDateFormat(
                "HH:mm:ss").format(timestamp);

        model.addRow(new Object[]{
            rs.getInt("expense_id"),
            rs.getString("expense_type"),
            rs.getString("description"),
            rs.getDouble("amount"),
            date,
            time
        });
    }

    rs.close();
    pst.close();
    con.close();

} catch (Exception e) {
    JOptionPane.showMessageDialog(this,
            "Error loading expenses: " + e.getMessage());
}
    }//GEN-LAST:event_jButton4ActionPerformed

    
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
        java.awt.EventQueue.invokeLater(() -> new ExpenseForm().setVisible(true));
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton jButton1;
    private javax.swing.JButton jButton2;
    private javax.swing.JButton jButton3;
    private javax.swing.JButton jButton4;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTable jTable1;
    private javax.swing.JTextField jTextField1;
    private javax.swing.JTextField jTextField2;
    private javax.swing.JTextField jTextField3;
    // End of variables declaration//GEN-END:variables
}
