package com.mycompany.easymartpos;

import com.mycompany.easymartpos.service.Authorization;
import com.mycompany.easymartpos.service.LoyaltyCardService;
import com.mycompany.easymartpos.ui.UITheme;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/** Screen-local Customer Management and Loyalty Customer workspace. */
public class CustomerForm extends JFrame {
    private static final Color ORANGE = new Color(203, 123, 35);
    private static final Color MUTED = new Color(109, 120, 132);
    private final LoyaltyCardService loyaltyCards = new LoyaltyCardService();
    private final DefaultTableModel customerModel = new DefaultTableModel(
            new String[]{"Customer ID", "Customer Name", "Phone", "Card Number", "Card Status", "Loyalty Points", "Assigned Date"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable customerTable = new JTable(customerModel);
    private final JTextField searchField = new JTextField(30);
    private final JTextField registrationCardField = new JTextField(20);
    private final JTextField nameField = new JTextField(20);
    private final JTextField phoneField = new JTextField(20);
    private final JButton addCustomerButton = new JButton("Add Customer");
    private final JButton updateCustomerButton = new JButton("Update");
    private final JButton clearCustomerButton = new JButton("Clear");
    private final JButton blockCardButton = new JButton("Block Card");
    private final JButton unblockCardButton = new JButton("Unblock");
    private final JButton replaceCardButton = new JButton("Replace Card");
    private final JButton cardHistoryButton = new JButton("Card History");
    private final JButton deleteCustomerButton = new JButton("Delete Customer");
    private final JLabel registrationCardStatus = badge("NO CARD ASSIGNED", MUTED);
    private final Timer searchTimer;
    private final Timer cardLookupTimer;
    private Integer selectedCustomerId;
    private boolean selectedCustomerHasCardHistory;

    public CustomerForm() {
        Authorization.requireAdmin();
        setTitle("USHANTHA SUPER MART | Customer Management");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(1040, 700));
        setSize(1280, 790);
        setLocationByPlatform(true);
        buildScreen();
        searchTimer = new Timer(280, event -> loadCustomers()); searchTimer.setRepeats(false);
        cardLookupTimer = new Timer(350, event -> refreshRegistrationCardStatus()); cardLookupTimer.setRepeats(false);
        installInteractions();
        loadCustomers();
    }

    private void buildScreen() {
        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBackground(UITheme.BACKGROUND);
        root.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));
        root.add(pageHeader(), BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(10, 10)); body.setOpaque(false);
        body.add(buildRegistrationCard(), BorderLayout.NORTH);
        body.add(buildCustomerTableCard(), BorderLayout.CENTER);
        root.add(body, BorderLayout.CENTER);
        setContentPane(root);
    }

    private JPanel pageHeader() {
        JPanel header = new JPanel(new BorderLayout()); header.setOpaque(false);
        JPanel title = UITheme.pageHeader("CUSTOMER MANAGEMENT", "Register customers, review loyalty cards, and manage customer details.");
        header.add(title, BorderLayout.WEST);
        JLabel policy = new JLabel("LOYALTY CARD CONTROL"); policy.setForeground(UITheme.NAVY_LIGHT); policy.setFont(new Font("Dialog", Font.BOLD, 12));
        header.add(policy, BorderLayout.EAST);
        return header;
    }

    private JPanel buildRegistrationCard() {
        JPanel card = UITheme.card(new BorderLayout(0, 8));
        JPanel heading = new JPanel(new BorderLayout()); heading.setOpaque(false);
        JLabel title = sectionTitle("CUSTOMER DETAILS"); heading.add(title, BorderLayout.WEST);
        heading.add(registrationCardStatus, BorderLayout.EAST); card.add(heading, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout()); form.setOpaque(false);
        GridBagConstraints c = baseConstraints();
        addField(form, c, 0, "Loyalty Card / Scan", registrationCardField);
        addField(form, c, 1, "Customer Name", nameField);
        addField(form, c, 2, "Phone Number", phoneField);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0)); actions.setOpaque(false);
        UITheme.styleSuccess(addCustomerButton);
        UITheme.stylePrimary(updateCustomerButton);
        UITheme.styleSecondary(clearCustomerButton);
        actions.add(addCustomerButton); actions.add(updateCustomerButton); actions.add(clearCustomerButton);
        c.gridx = 0; c.gridy = 3; c.gridwidth = 2; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.insets = new Insets(8, 0, 0, 0); form.add(actions, c);
        addCustomerButton.addActionListener(event -> addLoyaltyCustomer());
        updateCustomerButton.addActionListener(event -> updateCustomer());
        clearCustomerButton.addActionListener(event -> clearForm());

        JPanel lifecycleActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 7, 0)); lifecycleActions.setOpaque(false);
        UITheme.styleDanger(blockCardButton);
        UITheme.styleSuccess(unblockCardButton);
        UITheme.stylePrimary(replaceCardButton);
        UITheme.styleButton(cardHistoryButton, UITheme.NAVY_LIGHT);
        UITheme.styleDanger(deleteCustomerButton);
        lifecycleActions.add(blockCardButton); lifecycleActions.add(unblockCardButton); lifecycleActions.add(replaceCardButton);
        lifecycleActions.add(cardHistoryButton); lifecycleActions.add(deleteCustomerButton);
        c.gridx = 0; c.gridy = 4; c.gridwidth = 2; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.insets = new Insets(6, 0, 0, 0); form.add(lifecycleActions, c);
        blockCardButton.addActionListener(event -> blockSelectedCard());
        unblockCardButton.addActionListener(event -> unblockSelectedCard());
        replaceCardButton.addActionListener(event -> showReplaceCardDialog());
        cardHistoryButton.addActionListener(event -> showCardHistoryDialog());
        deleteCustomerButton.addActionListener(event -> deleteSelectedCustomer());
        refreshCustomerFormActions();
        card.add(form, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildCustomerTableCard() {
        JPanel card = UITheme.card(new BorderLayout(0, 10));
        JPanel top = new JPanel(new BorderLayout(10, 0)); top.setOpaque(false);
        top.add(sectionTitle("CUSTOMERS & LOYALTY CARDS"), BorderLayout.WEST);
        JPanel search = new JPanel(new FlowLayout(FlowLayout.RIGHT, 7, 0)); search.setOpaque(false);
        JLabel label = new JLabel("Search"); label.setFont(new Font("Dialog", Font.BOLD, 12)); label.setForeground(UITheme.GREY);
        search.add(label); search.add(searchField); top.add(search, BorderLayout.EAST); card.add(top, BorderLayout.NORTH);

        UITheme.styleTable(customerTable);
        customerTable.setRowHeight(34);
        customerTable.setFillsViewportHeight(true);
        customerTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        customerTable.getColumnModel().getColumn(0).setPreferredWidth(78);
        customerTable.getColumnModel().getColumn(1).setPreferredWidth(190);
        customerTable.getColumnModel().getColumn(2).setPreferredWidth(130);
        customerTable.getColumnModel().getColumn(3).setPreferredWidth(140);
        customerTable.getColumnModel().getColumn(4).setPreferredWidth(112);
        customerTable.getColumnModel().getColumn(5).setPreferredWidth(100);
        customerTable.getColumnModel().getColumn(6).setPreferredWidth(145);
        customerTable.getColumnModel().getColumn(4).setCellRenderer(new CardStatusRenderer());
        customerTable.getColumnModel().getColumn(5).setCellRenderer(centeredRenderer());
        customerTable.getColumnModel().getColumn(6).setCellRenderer(new DateRenderer());
        JScrollPane scroll = new JScrollPane(customerTable); scroll.setBorder(BorderFactory.createLineBorder(UITheme.BORDER));
        card.add(scroll, BorderLayout.CENTER);
        return card;
    }

    private void installInteractions() {
        styleField(registrationCardField); styleField(nameField); styleField(phoneField); styleField(searchField);
        registrationCardField.addActionListener(event -> refreshRegistrationCardStatus());
        registrationCardField.addFocusListener(new FocusAdapter() { @Override public void focusLost(FocusEvent event) { refreshRegistrationCardStatus(); } });
        registrationCardField.getDocument().addDocumentListener(new RestartTimerListener(cardLookupTimer));
        searchField.getDocument().addDocumentListener(new RestartTimerListener(searchTimer));
        searchField.addActionListener(event -> loadCustomers());
        customerTable.getSelectionModel().addListSelectionListener(this::selectCustomer);
    }

    private void refreshRegistrationCardStatus() {
        if (!registrationCardField.isEditable() && selectedCustomerHasCard()) {
            setBadge(registrationCardStatus, "BLOCKED".equalsIgnoreCase(selectedCardStatus()) ? "CARD BLOCKED" : "CARD ASSIGNED",
                    "BLOCKED".equalsIgnoreCase(selectedCardStatus()) ? UITheme.RED : ORANGE);
            return;
        }
        String entered = registrationCardField.getText().trim();
        if (entered.isEmpty()) { setBadge(registrationCardStatus, "NO CARD ASSIGNED", MUTED); return; }
        try {
            Optional<LoyaltyCardService.CardLookup> result = loyaltyCards.lookupCard(entered);
            if (result.isEmpty()) { setBadge(registrationCardStatus, "NEW CARD - WILL BE REGISTERED", UITheme.GREEN); return; }
            LoyaltyCardService.CardLookup card = result.get();
            String status = card.status().toUpperCase();
            if ("AVAILABLE".equals(status)) setBadge(registrationCardStatus, "CARD AVAILABLE", UITheme.GREEN);
            else if ("ASSIGNED".equals(status)) setBadge(registrationCardStatus, "CARD ASSIGNED", ORANGE);
            else if ("BLOCKED".equals(status)) setBadge(registrationCardStatus, "CARD BLOCKED", UITheme.RED);
            else setBadge(registrationCardStatus, status, MUTED);
        } catch (Exception failure) { setBadge(registrationCardStatus, "CARD LOOKUP ERROR", UITheme.RED); }
    }

    private void addLoyaltyCustomer() {
        try {
            int customerId = loyaltyCards.createLoyaltyCustomer(registrationCardField.getText(), nameField.getText(), phoneField.getText());
            boolean withCard = !registrationCardField.getText().isBlank();
            JOptionPane.showMessageDialog(this, "Customer #" + customerId + (withCard ? " was created and the card was assigned." : " was created."), "Customer", JOptionPane.INFORMATION_MESSAGE);
            clearForm(); loadCustomers();
        } catch (LoyaltyCardService.LoyaltyCardAssignmentException failure) {
            String detail = failure.getCause() == null ? "" : "\n" + failure.getCause().getMessage();
            JOptionPane.showMessageDialog(this, failure.getMessage() + detail, "Customer", JOptionPane.WARNING_MESSAGE);
        }
    }

    /** Updates selected details and, only for a no-card customer, assigns the scanned available card. */
    private void updateCustomer() {
        if (selectedCustomerId == null) { JOptionPane.showMessageDialog(this, "Select a customer row to update.", "Customer", JOptionPane.WARNING_MESSAGE); return; }
        String name = nameField.getText().trim(); String phone = phoneField.getText().trim();
        if (name.isEmpty() || phone.isEmpty()) { JOptionPane.showMessageDialog(this, "Customer name and phone number are required.", "Customer", JOptionPane.WARNING_MESSAGE); return; }
        if (isWalkInCustomer()) {
            JOptionPane.showMessageDialog(this, "Walk-in Customer is the checkout default and cannot be renamed.", "Customer", JOptionPane.WARNING_MESSAGE);
            return;
        }
        // An assigned customer's displayed card is read-only information, never a second assignment request.
        boolean assigningCard = registrationCardField.isEditable() && !registrationCardField.getText().trim().isEmpty();
        if (assigningCard && isWalkInCustomer()) {
            JOptionPane.showMessageDialog(this, "Walk-in Customer cannot receive a loyalty card.", "Loyalty Card", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (assigningCard && selectedCustomerHasCard()) {
            JOptionPane.showMessageDialog(this, "This customer already has a loyalty card. A second card cannot be assigned.", "Loyalty Card", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            if (assigningCard) {
                loyaltyCards.updateCustomerAndAssignCard(selectedCustomerId, name, phone, registrationCardField.getText());
            } else {
                updateCustomerDetails(name, phone);
            }
            String message = assigningCard ? "Customer details updated and loyalty card assigned successfully." : "Customer details updated. Loyalty points were not changed.";
            clearForm(); loadCustomers(); JOptionPane.showMessageDialog(this, message, "Customer", JOptionPane.INFORMATION_MESSAGE);
        } catch (LoyaltyCardService.LoyaltyCardAssignmentException failure) {
            JOptionPane.showMessageDialog(this, failure.getMessage(), "Loyalty Card", JOptionPane.WARNING_MESSAGE);
        } catch (Exception failure) { JOptionPane.showMessageDialog(this, "Unable to update customer: " + failure.getMessage(), "Customer", JOptionPane.ERROR_MESSAGE); }
    }

    private void updateCustomerDetails(String name, String phone) throws Exception {
        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement("UPDATE customers SET customer_name=?, phone=? WHERE customer_id=?")) {
            statement.setString(1, name); statement.setString(2, phone); statement.setInt(3, selectedCustomerId);
            if (statement.executeUpdate() != 1) throw new IllegalStateException("Customer was not found.");
        }
    }

    private void blockSelectedCard() {
        if (!requireLifecycleCustomer()) return;
        String card = selectedCardCode(); String name = selectedCustomerName();
        if (JOptionPane.showConfirmDialog(this, "Block loyalty card " + card + " for " + name + "?", "Block Loyalty Card",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;
        try {
            loyaltyCards.blockCurrentCard(selectedCustomerId);
            loadCustomers();
            JOptionPane.showMessageDialog(this, "Loyalty card blocked.", "Loyalty Card", JOptionPane.INFORMATION_MESSAGE);
        } catch (LoyaltyCardService.LoyaltyCardLifecycleException failure) {
            JOptionPane.showMessageDialog(this, failure.getMessage(), "Loyalty Card", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void unblockSelectedCard() {
        if (!requireLifecycleCustomer()) return;
        try {
            loyaltyCards.unblockCurrentCard(selectedCustomerId);
            loadCustomers();
            JOptionPane.showMessageDialog(this, "Loyalty card unblocked.", "Loyalty Card", JOptionPane.INFORMATION_MESSAGE);
        } catch (LoyaltyCardService.LoyaltyCardLifecycleException failure) {
            JOptionPane.showMessageDialog(this, failure.getMessage(), "Loyalty Card", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void showReplaceCardDialog() {
        if (!requireLifecycleCustomer()) return;
        javax.swing.JDialog dialog = new javax.swing.JDialog(this, "Replace Loyalty Card", true);
        JPanel content = UITheme.card(new BorderLayout(0, 10));
        content.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        JPanel form = new JPanel(new GridBagLayout()); form.setOpaque(false);
        GridBagConstraints c = baseConstraints();
        JTextField replacementCardField = new JTextField(22); styleField(replacementCardField);
        JTextField currentCardField = new JTextField(selectedCardCode(), 22); currentCardField.setEditable(false); styleField(currentCardField);
        addField(form, c, 0, "Current Card", currentCardField);
        addField(form, c, 1, "New Card / Scan", replacementCardField);
        content.add(form, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0)); actions.setOpaque(false);
        JButton confirm = new JButton("Confirm Replacement"); UITheme.stylePrimary(confirm);
        JButton cancel = new JButton("Cancel"); UITheme.styleSecondary(cancel);
        actions.add(confirm); actions.add(cancel); content.add(actions, BorderLayout.SOUTH);
        confirm.addActionListener(event -> {
            try {
                loyaltyCards.replaceCurrentCard(selectedCustomerId, replacementCardField.getText());
                dialog.dispose(); loadCustomers();
                JOptionPane.showMessageDialog(this, "Loyalty card replaced successfully.", "Loyalty Card", JOptionPane.INFORMATION_MESSAGE);
            } catch (LoyaltyCardService.LoyaltyCardLifecycleException failure) {
                JOptionPane.showMessageDialog(dialog, failure.getMessage(), "Loyalty Card", JOptionPane.WARNING_MESSAGE);
            }
        });
        replacementCardField.addActionListener(event -> confirm.doClick());
        cancel.addActionListener(event -> dialog.dispose());
        dialog.setContentPane(content); dialog.pack(); dialog.setResizable(false); dialog.setLocationRelativeTo(this);
        javax.swing.SwingUtilities.invokeLater(() -> replacementCardField.requestFocusInWindow());
        dialog.setVisible(true);
    }

    private void showCardHistoryDialog() {
        if (!requireLifecycleCustomer()) return;
        try {
            DefaultTableModel model = new DefaultTableModel(new String[]{"Card Number", "Status", "Assigned Date", "Blocked Date"}, 0) {
                @Override public boolean isCellEditable(int row, int column) { return false; }
            };
            for (LoyaltyCardService.CardHistoryRow row : loyaltyCards.cardHistory(selectedCustomerId)) {
                model.addRow(new Object[]{row.cardCode(), row.status(), formatTimestamp(row.assignedAt()), formatTimestamp(row.blockedAt())});
            }
            JTable historyTable = new JTable(model); UITheme.styleTable(historyTable); historyTable.setRowHeight(30);
            historyTable.getColumnModel().getColumn(1).setCellRenderer(new CardStatusRenderer());
            javax.swing.JDialog dialog = new javax.swing.JDialog(this, "LOYALTY CARD HISTORY", true);
            JPanel content = UITheme.card(new BorderLayout(0, 8)); content.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
            content.add(sectionTitle("LOYALTY CARD HISTORY — " + selectedCustomerName()), BorderLayout.NORTH);
            JScrollPane scroll = new JScrollPane(historyTable); scroll.setPreferredSize(new Dimension(620, 220)); scroll.setBorder(BorderFactory.createLineBorder(UITheme.BORDER));
            content.add(scroll, BorderLayout.CENTER);
            dialog.setContentPane(content); dialog.pack(); dialog.setLocationRelativeTo(this); dialog.setVisible(true);
        } catch (Exception failure) {
            JOptionPane.showMessageDialog(this, "Unable to load card history: " + failure.getMessage(), "Loyalty Card", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteSelectedCustomer() {
        if (!requireLifecycleCustomer()) return;
        try {
            LoyaltyCardService.DeleteEligibility eligibility = loyaltyCards.canDeleteCustomer(selectedCustomerId);
            if (!eligibility.allowed()) {
                if (!eligibility.archivable()) {
                    JOptionPane.showMessageDialog(this, eligibility.reason(), "Delete Customer", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                // Sales must keep pointing at a real customer, so history-bearing customers are archived, not erased.
                if (JOptionPane.showConfirmDialog(this, eligibility.reason() + "\n\nArchive " + selectedCustomerName()
                                + " instead? Their sales and loyalty records are kept, but they are removed from the customer list and checkout, and their loyalty card is released.",
                        "Archive Customer", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;
                loyaltyCards.archiveCustomer(selectedCustomerId);
                clearForm(); loadCustomers();
                JOptionPane.showMessageDialog(this, "Customer archived. Their history is kept.", "Archive Customer", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            if (JOptionPane.showConfirmDialog(this, "Delete customer " + selectedCustomerName() + "? This action cannot be undone.", "Delete Customer",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;
            loyaltyCards.deleteCustomerIfSafe(selectedCustomerId);
            clearForm(); loadCustomers();
            JOptionPane.showMessageDialog(this, "Customer deleted and unused loyalty cards released.", "Delete Customer", JOptionPane.INFORMATION_MESSAGE);
        } catch (LoyaltyCardService.LoyaltyCardLifecycleException failure) {
            JOptionPane.showMessageDialog(this, failure.getMessage(), "Delete Customer", JOptionPane.WARNING_MESSAGE);
        } catch (Exception failure) {
            JOptionPane.showMessageDialog(this, "Unable to delete customer: " + failure.getMessage(), "Delete Customer", JOptionPane.ERROR_MESSAGE);
        }
    }

    private boolean requireLifecycleCustomer() {
        if (selectedCustomerId == null || isWalkInCustomer()) {
            JOptionPane.showMessageDialog(this, "Select a non Walk-in Customer first.", "Customer", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        return true;
    }

    private String selectedCustomerName() {
        int row = customerTable.getSelectedRow();
        return row < 0 ? "customer" : String.valueOf(customerModel.getValueAt(row, 1));
    }

    private String selectedCardCode() {
        int row = customerTable.getSelectedRow();
        return row < 0 ? "" : String.valueOf(customerModel.getValueAt(row, 3));
    }

    private static String formatTimestamp(java.sql.Timestamp timestamp) {
        return timestamp == null ? "-" : timestamp.toLocalDateTime().toString().replace('T', ' ');
    }

    private void loadCustomers() {
        Integer retain = selectedCustomerId;
        try {
            List<LoyaltyCardService.CustomerCardRow> rows = loyaltyCards.listCustomers(searchField.getText());
            customerModel.setRowCount(0);
            for (LoyaltyCardService.CustomerCardRow row : rows) customerModel.addRow(new Object[]{row.customerId(), row.customerName(), row.phone(), row.cardCode(), row.cardStatus(), row.loyaltyPoints(), row.assignedAt()});
            selectedCustomerId = null; selectedCustomerHasCardHistory = false;
            if (retain != null) selectCustomerRow(retain);
            refreshCustomerFormActions();
        } catch (Exception failure) {
            JOptionPane.showMessageDialog(this, "Unable to load customers: " + failure.getMessage(), "Customer Management", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void selectCustomerRow(int customerId) {
        for (int row = 0; row < customerModel.getRowCount(); row++) if (((Number) customerModel.getValueAt(row, 0)).intValue() == customerId) { customerTable.setRowSelectionInterval(row, row); return; }
    }

    private void selectCustomer(ListSelectionEvent event) {
        if (event.getValueIsAdjusting()) return;
        int row = customerTable.getSelectedRow();
        if (row < 0) { selectedCustomerId = null; selectedCustomerHasCardHistory = false; refreshCustomerFormActions(); return; }
        selectedCustomerId = ((Number) customerModel.getValueAt(row, 0)).intValue();
        try { selectedCustomerHasCardHistory = loyaltyCards.hasCardHistory(selectedCustomerId); }
        catch (Exception ignored) { selectedCustomerHasCardHistory = selectedCustomerHasCard(); }
        nameField.setText(String.valueOf(customerModel.getValueAt(row, 1)));
        Object phone = customerModel.getValueAt(row, 2); phoneField.setText(phone == null ? "" : phone.toString());
        if (selectedCustomerHasCard()) {
            registrationCardField.setText(String.valueOf(customerModel.getValueAt(row, 3)));
            registrationCardField.setEditable(false);
            setBadge(registrationCardStatus, "BLOCKED".equalsIgnoreCase(selectedCardStatus()) ? "CARD BLOCKED" : "CARD ASSIGNED",
                    "BLOCKED".equalsIgnoreCase(selectedCardStatus()) ? UITheme.RED : ORANGE);
        } else {
            registrationCardField.setEditable(true);
            registrationCardField.setText("");
            if (isWalkInCustomer()) setBadge(registrationCardStatus, "WALK-IN CUSTOMER", MUTED);
            else setBadge(registrationCardStatus, "NO CARD ASSIGNED", MUTED);
        }
        refreshCustomerFormActions();
    }

    private boolean selectedCustomerHasCard() {
        int row = customerTable.getSelectedRow();
        return row >= 0 && !"UNASSIGNED".equalsIgnoreCase(String.valueOf(customerModel.getValueAt(row, 4)));
    }

    private String selectedCardStatus() {
        int row = customerTable.getSelectedRow();
        return row < 0 ? "UNASSIGNED" : String.valueOf(customerModel.getValueAt(row, 4)).toUpperCase();
    }

    private boolean isWalkInCustomer() {
        int row = customerTable.getSelectedRow();
        return row >= 0 && "walk-in customer".equalsIgnoreCase(String.valueOf(customerModel.getValueAt(row, 1)).trim());
    }

    private void clearForm() {
        selectedCustomerId = null; selectedCustomerHasCardHistory = false; customerTable.clearSelection();
        registrationCardField.setEditable(true);
        registrationCardField.setText(""); nameField.setText(""); phoneField.setText("");
        setBadge(registrationCardStatus, "NO CARD ASSIGNED", MUTED);
        refreshCustomerFormActions();
    }

    /** Add is only available for a fresh form; Update is only available for a selected table row. */
    private void refreshCustomerFormActions() {
        boolean existingCustomerSelected = selectedCustomerId != null;
        boolean walkIn = existingCustomerSelected && isWalkInCustomer();
        String cardStatus = selectedCardStatus();
        boolean assigned = existingCustomerSelected && "ASSIGNED".equalsIgnoreCase(cardStatus);
        boolean blocked = existingCustomerSelected && "BLOCKED".equalsIgnoreCase(cardStatus);
        boolean hasCardHistory = existingCustomerSelected && selectedCustomerHasCardHistory;
        addCustomerButton.setEnabled(!existingCustomerSelected);
        updateCustomerButton.setEnabled(existingCustomerSelected && !walkIn);
        clearCustomerButton.setEnabled(true);
        blockCardButton.setEnabled(assigned && !walkIn);
        unblockCardButton.setEnabled(blocked && !walkIn);
        replaceCardButton.setEnabled((assigned || blocked) && !walkIn);
        cardHistoryButton.setEnabled(hasCardHistory && !walkIn);
        deleteCustomerButton.setEnabled(existingCustomerSelected && !walkIn);
    }

    private static JLabel sectionTitle(String text) { JLabel title = new JLabel(text); title.setFont(new Font("Dialog", Font.BOLD, 13)); title.setForeground(UITheme.NAVY); return title; }
    private static JLabel badge(String text, Color color) { JLabel label = new JLabel(); label.setHorizontalAlignment(SwingConstants.CENTER); label.setFont(new Font("Dialog", Font.BOLD, 11)); setBadge(label, text, color); return label; }
    private static void setBadge(JLabel label, String text, Color color) { label.setText("  " + text + "  "); label.setForeground(Color.WHITE); label.setBackground(color); label.setOpaque(true); label.setBorder(BorderFactory.createEmptyBorder(5, 7, 5, 7)); }
    private static GridBagConstraints baseConstraints() { GridBagConstraints c = new GridBagConstraints(); c.anchor = GridBagConstraints.WEST; c.fill = GridBagConstraints.HORIZONTAL; c.insets = new Insets(4, 0, 4, 10); return c; }
    private static void addField(JPanel panel, GridBagConstraints c, int row, String label, Component field) { c.gridx = 0; c.gridy = row; c.weightx = 0; c.gridwidth = 1; panel.add(fieldLabel(label), c); c.gridx = 1; c.weightx = 1; c.insets = new Insets(4, 0, 4, 0); panel.add(field, c); c.insets = new Insets(4, 0, 4, 10); }
    private static JLabel fieldLabel(String text) { JLabel label = new JLabel(text); label.setFont(new Font("Dialog", Font.BOLD, 12)); label.setForeground(UITheme.GREY); return label; }
    private static void styleField(JTextField field) { field.setFont(UITheme.BODY); field.setPreferredSize(new Dimension(field.getPreferredSize().width, 32)); field.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(UITheme.BORDER), BorderFactory.createEmptyBorder(5, 8, 5, 8))); }

    private static final class RestartTimerListener implements DocumentListener {
        private final Timer timer; private RestartTimerListener(Timer timer) { this.timer = timer; }
        @Override public void insertUpdate(DocumentEvent event) { timer.restart(); }
        @Override public void removeUpdate(DocumentEvent event) { timer.restart(); }
        @Override public void changedUpdate(DocumentEvent event) { timer.restart(); }
    }
    private static DefaultTableCellRenderer centeredRenderer() { DefaultTableCellRenderer renderer = new DefaultTableCellRenderer(); renderer.setHorizontalAlignment(SwingConstants.CENTER); return renderer; }
    private static final class DateRenderer extends DefaultTableCellRenderer {
        @Override public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focused, int row, int column) {
            return super.getTableCellRendererComponent(table, value instanceof java.sql.Timestamp timestamp ? timestamp.toLocalDateTime().toString().replace('T', ' ') : "", selected, focused, row, column);
        }
    }
    private static final class CardStatusRenderer extends DefaultTableCellRenderer {
        @Override public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focused, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, selected, focused, row, column);
            String status = value == null ? "UNASSIGNED" : value.toString().toUpperCase();
            label.setHorizontalAlignment(SwingConstants.CENTER); label.setFont(new Font("Dialog", Font.BOLD, 11));
            label.setForeground(Color.WHITE); label.setBackground("AVAILABLE".equals(status) ? UITheme.GREEN : "ASSIGNED".equals(status) ? ORANGE : "BLOCKED".equals(status) ? UITheme.RED : MUTED);
            label.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8)); label.setOpaque(true); return label;
        }
    }
}
