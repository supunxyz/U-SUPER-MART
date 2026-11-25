package com.mycompany.easymartpos.ui;

import com.mycompany.easymartpos.Dialogs;
import com.mycompany.easymartpos.model.SaleHistoryRow;
import com.mycompany.easymartpos.service.ReceiptService;
import com.mycompany.easymartpos.service.SalesService;
import com.mycompany.easymartpos.service.Session;
import com.mycompany.easymartpos.service.Authorization;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

public class SalesHistoryFrame extends JFrame {
    private final SalesService sales = new SalesService();
    private final ReceiptService receipts = new ReceiptService();
    private final JTextField search = new JTextField(20);
    private final DefaultTableModel model = new DefaultTableModel(new String[]{"Sale ID", "Invoice", "Customer", "Date", "Total", "Payment", "Status"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = new JTable(model);
    private List<SaleHistoryRow> rows = new ArrayList<>();

    public SalesHistoryFrame() {
        super("Sales History");
        Authorization.requireSalesHistory();
        setDefaultCloseOperation(DISPOSE_ON_CLOSE); setSize(1120, 650); setMinimumSize(new java.awt.Dimension(920, 560)); setLocationByPlatform(true);
        JButton find = new JButton("Search"); find.addActionListener(event -> load()); search.addActionListener(event -> load());
        JButton receipt = new JButton("Preview / Print receipt"); receipt.addActionListener(event -> previewReceipt());
        JButton voidSale = new JButton("Void selected sale"); voidSale.setEnabled(Authorization.isAllowed(Authorization.Permission.ADMINISTRATION)); voidSale.addActionListener(event -> voidSelected());
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8)); header.setBackground(java.awt.Color.WHITE); header.setBorder(javax.swing.BorderFactory.createCompoundBorder(javax.swing.BorderFactory.createLineBorder(UITheme.BORDER), javax.swing.BorderFactory.createEmptyBorder(5, 8, 5, 8)));
        JLabel title = new JLabel("SALES HISTORY"); title.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 18)); title.setForeground(UITheme.NAVY); header.add(title); header.add(new JLabel("Invoice / customer")); header.add(search); header.add(find); header.add(receipt); header.add(voidSale);
        add(header, BorderLayout.NORTH); add(new JScrollPane(table), BorderLayout.CENTER); UITheme.applyWindow(this); load();
    }

    private void load() {
        try {
            rows = sales.searchSales(search.getText()); model.setRowCount(0);
            for (SaleHistoryRow row : rows) model.addRow(new Object[]{row.saleId(), row.invoiceNumber(), row.customerName(), row.saleDate(), row.total(), row.paymentMethod(), row.status()});
        } catch (Exception exception) { showError(exception); }
    }

    private SaleHistoryRow selected() {
        int index = table.getSelectedRow();
        if (index < 0) throw new IllegalArgumentException("Select a sale first.");
        return rows.get(index);
    }

    private void previewReceipt() {
        try { ReceiptDialog.show(this, receipts.receiptFor(selected().saleId())); } catch (Exception exception) { showError(exception); }
    }

    private void voidSelected() {
        try {
            Authorization.requireAdmin();
            SaleHistoryRow sale = selected();
            if (!"COMPLETED".equals(sale.status())) throw new IllegalArgumentException("Only completed sales can be voided.");
            if (!Dialogs.confirmYesNo(this, "Confirm void", "Void " + sale.invoiceNumber() + " and restore its stock?")) return;
            String reason = Dialogs.multilineInput(this, "Void sale", "Reason for void:");
            if (reason == null) return;
            sales.voidSale(sale.saleId(), reason, Session.currentUserId());
            Dialogs.success(this, "Sales History", "Sale voided and stock restored."); load();
        } catch (Exception exception) { showError(exception); }
    }

    private void showError(Exception exception) { Dialogs.error(this, "Sales History", exception.getMessage()); }
}
