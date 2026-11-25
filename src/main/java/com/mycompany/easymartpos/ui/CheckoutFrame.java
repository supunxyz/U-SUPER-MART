package com.mycompany.easymartpos.ui;

import com.mycompany.easymartpos.Dialogs;
import com.mycompany.easymartpos.model.CartItem;
import com.mycompany.easymartpos.model.BatchAllocation;
import com.mycompany.easymartpos.model.BatchResolution;
import com.mycompany.easymartpos.model.BatchResolutionStatus;
import com.mycompany.easymartpos.model.BatchStock;
import com.mycompany.easymartpos.model.CustomerChoice;
import com.mycompany.easymartpos.model.PaymentMethod;
import com.mycompany.easymartpos.model.PaymentRequest;
import com.mycompany.easymartpos.model.PaymentPart;
import com.mycompany.easymartpos.model.Product;
import com.mycompany.easymartpos.model.ReceiptData;
import com.mycompany.easymartpos.model.SaleResult;
import com.mycompany.easymartpos.repository.CustomerRepository;
import com.mycompany.easymartpos.repository.ProductRepository;
import com.mycompany.easymartpos.service.ReceiptService;
import com.mycompany.easymartpos.service.SalesService;
import com.mycompany.easymartpos.service.Session;
import com.mycompany.easymartpos.service.CheckoutCalculator;
import com.mycompany.easymartpos.service.BatchResolver;
import com.mycompany.easymartpos.service.LoyaltyPointCalculator;
import com.mycompany.easymartpos.service.LoyaltyCardService;
import com.mycompany.easymartpos.service.Authorization;
import com.mycompany.easymartpos.service.PromotionService;
import com.mycompany.easymartpos.service.ShiftService;
import com.mycompany.easymartpos.util.Money;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.DefaultListCellRenderer;
import javax.swing.SwingConstants;
import javax.swing.text.JTextComponent;
import javax.swing.table.DefaultTableModel;

public class CheckoutFrame extends JFrame {

    /** Shared right-panel grid width: keeps amounts, controls and actions on the same visual edges. */
    private static final int PAYMENT_CONTENT_WIDTH = 470;
    private static final String CUSTOMER_EDITOR_LOOKUP_INSTALLED = "customer-editor-lookup-installed";

    private final ProductRepository products = new ProductRepository();
    private final BatchResolver batchResolver = new BatchResolver();
    private final CustomerRepository customers = new CustomerRepository();
    private final LoyaltyCardService loyaltyCards = new LoyaltyCardService();
    private final SalesService sales = new SalesService();
    private final ShiftService shifts = new ShiftService();
    private final PromotionService promotions = new PromotionService();
    private final ReceiptService receipts = new ReceiptService();
    private final Runnable successfulSaleListener;
    private JButton completeSaleButton;
    private boolean saleCompletionInProgress;
    private KeyEventDispatcher paymentShortcutDispatcher;
    private boolean paymentShortcutDispatcherRegistered;
    private final Map<java.awt.Component, List<java.awt.Component>> labelledFields = new LinkedHashMap<>();
    private final JLabel barcodeNotice = new JLabel("Ready to scan");
    private javax.swing.Timer inlineNoticeTimer;
    private final JLabel successBanner = new JLabel(" ");
    private final JLabel sessionSummary = new JLabel();
    private final JPanel paymentButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
    private final JButton cashButton = new RoundedPaymentButton("CASH (F5)");
    private final JButton cardButton = new RoundedPaymentButton("CARD (F6)");
    private final JButton transferButton = new RoundedPaymentButton("TRANSFER (F7)");
    private final JButton splitButton = new RoundedPaymentButton("SPLIT (F8)");
    private final JPanel quickCashButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
    private JPanel paymentCard;
    private JPanel paymentContent;
    private JPanel standardPaymentContentWrapper;
    private JPanel completeSaleWrapper;
    private JPanel splitPaymentStack;
    private JScrollPane splitPaymentScroll;
    private boolean splitPaymentLayoutActive;
    private static final List<HeldSale> HELD_SALES = new ArrayList<>();

    private final List<CartItem> cart = new ArrayList<>();
    private final List<Product> allProducts = new ArrayList<>();
    private final List<CustomerChoice> allCustomers = new ArrayList<>();
    /** Scan lookup cache. SalesService still locks and revalidates stock before commit. */
    private final Map<String, Product> productsByBarcode = new LinkedHashMap<>();

    private final JTextField invoice = new JTextField(18);
    private final JComboBox<CustomerChoice> customer = new JComboBox<>();
    private JTextField customerEditor;
    private final JButton clearCustomerButton = new JButton("×");
    private javax.swing.JLayeredPane customerFieldControl;

    private final JTextField barcode = new JTextField(14);
    private final JComboBox<Product> product = new JComboBox<>();
    private JTextField productEditor;

    private boolean updatingProductCombo = false;
    private boolean filteringProductSearch;
    private boolean productFilterUpdateQueued;
    private boolean suppressCustomerPopup;
    private boolean filteringCustomerSearch;
    private boolean customerFilterUpdateQueued;
    /** Value last deliberately chosen with Enter or mouse; popup navigation must never replace it. */
    private String confirmedProductSearchText = "";
    private Product confirmedProductSearch;
    /** Popup-only preview state; this is never the JComboBox's committed selection. */
    private int productPopupHighlightedIndex = -1;
    private boolean committingProductSelection;
    private boolean productPopupMouseHandlerInstalled;

    private final JTextField quantity = new JTextField("1", 6);
    private final JTextField discount = new JTextField("0", 8);

    private final JComboBox<PaymentMethod> method =
            new JComboBox<>(PaymentMethod.values());

    private final JTextField tendered = new JTextField(8);
    private final JTextField cardReference = new JTextField(12);
    private final JTextField cardBankName = new JTextField(12);
    private final JTextField transferReference = new JTextField(12);
    private final JTextField bankName = new JTextField(12);
    private final JTextField cashAmount = new JTextField(8);
    private final JTextField cardAmount = new JTextField(8);
    private final JTextField transferAmount = new JTextField(8);

    private final JLabel total = new JLabel("LKR 0.00");
    private final JLabel change = new JLabel("LKR 0.00");
    private final JLabel remaining = new JLabel("LKR 0.00");
    private final JLabel subtotalDisplay = new JLabel("LKR 0.00");
    private final JLabel promotionSavingDisplay = new JLabel("LKR 0.00");
    private final JLabel savingDisplay = new JLabel("LKR 0.00");
    private final JLabel manualDiscountDisplay = new JLabel("LKR 0.00");
    private final JLabel loyaltyRedemptionDisplay = new JLabel("-LKR 0.00");
    private final JLabel discountMessage = new JLabel(" ");
    private JPanel manualDiscountSummaryRow;
    private JPanel loyaltyRedemptionSummaryRow;
    private JPanel loyaltySection;
    private final JLabel currentPointsDisplay = new JLabel("0");
    private final JTextField redeemPointsField = new JTextField(6);
    private final JButton applyLoyaltyButton = new JButton("Apply");
    private final JLabel loyaltyMinimumMessage = new JLabel("Minimum 100 points required");
    private int appliedRedemptionPoints;
    private Integer loyaltyCustomerId;

    private final DefaultTableModel tableModel =
            new DefaultTableModel(
                    new String[]{"Item", "Qty", "Our Price", "Promo", "Final Price", "Total"},
                    0) {

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    private final JTable table = new JTable(tableModel);

    public CheckoutFrame() {
        this(null);
    }

    /**
     * Allows the parent dashboard to refresh its current-shift figures only
     * after this checkout has committed successfully.
     */
    public CheckoutFrame(Runnable successfulSaleListener) {

        super("New Sale");
        Authorization.requireCheckout();
        this.successfulSaleListener = successfulSaleListener;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(1240, 760);
        setMinimumSize(new Dimension(1020, 650));
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setLocationByPlatform(true);

        invoice.setEditable(false);
        invoice.setText(sales.nextInvoiceNumber());

        // Product search box
        product.setEditable(true);
        customer.setEditable(true);

        productEditor =
                (JTextField) product.getEditor().getEditorComponent();
        customerEditor = (JTextField) customer.getEditor().getEditorComponent();

        // The scan lane is intentionally just three compact rows.  All money controls live in the right panel.
        JPanel scanForm = new JPanel(new GridBagLayout());
        scanForm.setOpaque(false);
        JPanel paymentForm = verticalPanel();
        JPanel totalsForm = verticalPanel();

        addScanComponent(scanForm, new JLabel("BARCODE"), 0, 0, 1, 0);
        barcode.setPreferredSize(new Dimension(470, barcode.getPreferredSize().height));
        addFixedScanComponent(scanForm, barcode, 1, 0, 1, 0);
        addOffsetScanComponent(scanForm, new JLabel("Product Search"), 2, 0, 1, 0, -15);
        Dimension compactField = barcode.getMinimumSize();
        Dimension productField = new Dimension(compactField.width + 50, compactField.height);
        product.setMinimumSize(productField); product.setPreferredSize(productField);
        addFixedOffsetScanComponent(scanForm, product, 3, 0, 1, .40, -15, 252);
        addScanComponent(scanForm, new JLabel("Customer"), 4, 0, 1, 0);
        customer.setMinimumSize(productField); customer.setPreferredSize(productField);
        customerFieldControl = buildCustomerFieldControl(productField);
        addFixedOffsetScanComponent(scanForm, customerFieldControl, 5, 0, 1, .32, 0, 252);
        addScanComponent(scanForm, new JLabel("Quantity"), 0, 1, 1, 0);
        quantity.setColumns(barcode.getColumns());
        addFixedScanComponent(scanForm, quantity, 1, 1, 1, .38);
        addOffsetScanComponent(scanForm, new JLabel("Discount"), 2, 1, 1, 0, -15);
        discount.setColumns(barcode.getColumns());
        addFixedOffsetScanComponent(scanForm, discount, 3, 1, 1, .40, -15, 50);
        addScanComponent(scanForm, new JLabel("Invoice"), 4, 1, 1, 0);
        invoice.setColumns(barcode.getColumns());
        invoice.setMinimumSize(compactField); invoice.setPreferredSize(compactField);
        addFixedOffsetScanComponent(scanForm, invoice, 5, 1, 1, .32, 0, 50);
        addPaymentRow(paymentForm, "Tendered cash", tendered);
        addPaymentRow(paymentForm, "Change", change);
        addPaymentRow(paymentForm, "Quick Cash", quickCashButtons);
        addPaymentRow(paymentForm, "Cash Amount", cashAmount);
        addPaymentRow(paymentForm, "Card Amount", cardAmount);
        addPaymentRow(paymentForm, "Transfer Amount", transferAmount);
        addPaymentRow(paymentForm, "Remaining", remaining);
        addPaymentRow(paymentForm, "Card reference", cardReference);
        addPaymentRow(paymentForm, "Bank name", cardBankName);
        addPaymentRow(paymentForm, "Transfer reference", transferReference);
        addPaymentRow(paymentForm, "Bank name", bankName);
        addTotalsRow(totalsForm, "Subtotal", subtotalDisplay);
        addTotalsRow(totalsForm, "Promotion Saving", promotionSavingDisplay);
        addTotalsRow(totalsForm, "Customer Saving", savingDisplay);
        manualDiscountSummaryRow = addTotalsRow(totalsForm, "Manual Discount", manualDiscountDisplay);
        loyaltyRedemptionSummaryRow = addTotalsRow(totalsForm, "Loyalty Redemption", loyaltyRedemptionDisplay);
        addTotalsRow(totalsForm, "GRAND TOTAL", total);
        manualDiscountSummaryRow.setVisible(false);
        // Keep the redemption in the summary at all times so the total's calculation is explicit.
        loyaltyRedemptionSummaryRow.setVisible(true);
        barcodeNotice.setForeground(UITheme.GREY); barcodeNotice.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 12));
        successBanner.setForeground(UITheme.GREEN); successBanner.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 12));
        discountMessage.setForeground(java.awt.Color.RED);
        JPanel statusLine = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        statusLine.setOpaque(false); statusLine.add(barcodeNotice); statusLine.add(successBanner); statusLine.add(discountMessage);
        addScanComponent(scanForm, statusLine, 0, 2, 6, 1);
        barcode.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 17));
        barcode.setBackground(new java.awt.Color(237, 247, 255));
        total.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 32)); total.setForeground(UITheme.NAVY);
        change.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 32)); change.setForeground(UITheme.GREEN);
        subtotalDisplay.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 20)); subtotalDisplay.setForeground(UITheme.GREY);
        promotionSavingDisplay.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 20)); promotionSavingDisplay.setForeground(UITheme.GREEN);
        savingDisplay.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 20)); savingDisplay.setForeground(UITheme.GREEN);
        manualDiscountDisplay.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 20)); manualDiscountDisplay.setForeground(UITheme.GREY);
        remaining.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 24)); remaining.setForeground(UITheme.NAVY);

        JButton add = new JButton("Add to cart");
        add.addActionListener(event -> addSelectedProduct());

        JButton plusQty = new JButton("+ Qty");
        plusQty.addActionListener(event -> changeQuantity(1));

        JButton minusQty = new JButton("- Qty");
        minusQty.addActionListener(event -> changeQuantity(-1));

        JButton remove = new JButton("Remove selected item");
        remove.addActionListener(event -> removeSelectedWithConfirmation());

        completeSaleButton = new JButton("COMPLETE SALE  -  F12");
        completeSaleButton.addActionListener(event -> completeSale());

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 4));
        actions.setBackground(UITheme.CARD);
        actions.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                javax.swing.BorderFactory.createMatteBorder(1, 0, 0, 0, UITheme.BORDER),
                javax.swing.BorderFactory.createEmptyBorder(1, 10, 1, 10)));
        actions.add(minusQty);
        actions.add(plusQty);
        actions.add(remove);
        JButton hold = new JButton("Hold Sale (F9)"); hold.addActionListener(event -> holdSale());
        JButton recall = new JButton("Recall (F10)"); recall.addActionListener(event -> recallSale());
        actions.add(hold);
        actions.add(recall);
        JButton clear = new JButton("Clear / New Sale"); clear.addActionListener(event -> clearSaleWithConfirmation());
        actions.add(clear);
        actions.add(add);
        configurePaymentButtons();

        // Payment change
        method.addActionListener(event -> updatePaymentFields());

        // Product selected manually
        product.addActionListener(event -> {

            if (updatingProductCombo) {
                return;
            }

            // Product navigation is preview-only.  Only Enter or a popup-list mouse click may commit.
            if (!committingProductSelection) {
                return;
            }

            Object selectedObject = product.getSelectedItem();

            if (selectedObject instanceof Product selected) {
                applyCommittedProductSearchSelection(selected);
            }
        });

        customer.addActionListener(event -> {
            if (customer.getSelectedItem() instanceof CustomerChoice choice && choice.loyaltyCustomer()) {
                showInlineNotice("Loyalty points: " + choice.loyaltyPoints());
            }
            refreshLoyaltyCustomer();
            if (!cart.isEmpty() && customer.getSelectedItem() instanceof CustomerChoice) {
                try { refreshCart(); }
                catch (Exception exception) { showInlineNotice(exception.getMessage()); }
            }
        });

        // Live Change
        tendered.getDocument().addDocumentListener(
                new javax.swing.event.DocumentListener() {

            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                updateChange();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                updateChange();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                updateChange();
            }
        });

        javax.swing.event.DocumentListener paymentAmountsChanged = new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent event) { updateRemaining(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent event) { updateRemaining(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent event) { updateRemaining(); }
        };
        cashAmount.getDocument().addDocumentListener(paymentAmountsChanged);
        cardAmount.getDocument().addDocumentListener(paymentAmountsChanged);
        transferAmount.getDocument().addDocumentListener(paymentAmountsChanged);

        discount.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent event) { refreshTotals(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent event) { refreshTotals(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent event) { refreshTotals(); }
        });
        discount.addActionListener(event -> addSelectedProduct());

        // Tendered Cash Enter -> Complete Sale
        tendered.addActionListener(event -> completeSale());
        
        // Card details stay together before completing or continuing to split-transfer details.
        cardReference.addActionListener(event -> cardBankName.requestFocusInWindow());
        cardBankName.addActionListener(event -> { if (method.getSelectedItem() == PaymentMethod.SPLIT) transferReference.requestFocusInWindow(); else completeSale(); });
        cashAmount.addActionListener(event -> { suggestSplitRemaining(cardAmount); cardAmount.requestFocusInWindow(); });
        cardAmount.addActionListener(event -> { suggestSplitRemaining(transferAmount); transferAmount.requestFocusInWindow(); });
        transferAmount.addActionListener(event -> transferReference.requestFocusInWindow());
        transferReference.addActionListener(event -> completeSale());
        

        // F2 -> Payment
        getRootPane().registerKeyboardAction(
        e -> selectedPaymentButton().requestFocusInWindow(),
                javax.swing.KeyStroke.getKeyStroke(
                        KeyEvent.VK_F2, 0
                ),
                javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW
        );
        
        
        
        // F3 -> Cart
getRootPane().registerKeyboardAction(
        e -> {
            if (table.getRowCount() > 0) {

                table.requestFocusInWindow();

                if (table.getSelectedRow() == -1) {
                    table.setRowSelectionInterval(0, 0);
                }
            }
        },
        javax.swing.KeyStroke.getKeyStroke(
                KeyEvent.VK_F3, 0
        ),
        javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW
);

        registerShortcut(KeyEvent.VK_F1, 0, () -> focus(barcode));
        registerShortcut(KeyEvent.VK_F4, 0, () -> { customer.requestFocusInWindow(); customer.showPopup(); });
        registerPaymentShortcut(KeyEvent.VK_F5, PaymentMethod.CASH);
        registerPaymentShortcut(KeyEvent.VK_F7, PaymentMethod.TRANSFER);
        installPaymentShortcutDispatcher();
        registerShortcut(KeyEvent.VK_F9, 0, this::holdSale);
        registerShortcut(KeyEvent.VK_F10, 0, this::recallSale);
        registerShortcut(KeyEvent.VK_F12, 0, this::completeSale);
        registerShortcut(KeyEvent.VK_F, KeyEvent.CTRL_DOWN_MASK, () -> { productEditor.requestFocusInWindow(); product.showPopup(); });
        registerShortcut(KeyEvent.VK_L, KeyEvent.CTRL_DOWN_MASK, () -> { customer.requestFocusInWindow(); customer.showPopup(); });
        registerShortcut(KeyEvent.VK_N, KeyEvent.CTRL_DOWN_MASK, this::clearSaleWithConfirmation);
        registerShortcut(KeyEvent.VK_ESCAPE, 0, () -> focus(barcode));
        getRootPane().registerKeyboardAction(event -> changeCartQuantityIfSafe(1), javax.swing.KeyStroke.getKeyStroke('+'), javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
        getRootPane().registerKeyboardAction(event -> changeCartQuantityIfSafe(-1), javax.swing.KeyStroke.getKeyStroke('-'), javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
        getRootPane().registerKeyboardAction(event -> {
            if (!isTextEntryActive()) removeSelectedWithConfirmation();
        }, javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
        registerShortcut(KeyEvent.VK_F11, 0, this::showPriceCheck);
        
        
        

        // Payment selected -> correct field
        method.addActionListener(event -> {

            updatePaymentFields();

            if (method.hasFocus()) {

                javax.swing.SwingUtilities.invokeLater(() -> {

                    if (method.getSelectedItem() == PaymentMethod.CASH) {

                        tendered.requestFocusInWindow();
                        tendered.selectAll();

                    } else if (method.getSelectedItem() == PaymentMethod.SPLIT) {
                        cashAmount.requestFocusInWindow(); cashAmount.selectAll();
                    } else if (method.getSelectedItem() == PaymentMethod.TRANSFER) {
                        transferReference.requestFocusInWindow(); transferReference.selectAll();
                    } else {

                        cardReference.requestFocusInWindow();
                        cardReference.selectAll();
                    }
                });
            }
        });

        // Barcode Enter / Scanner
        barcode.addActionListener(event -> addBarcodeProduct());

        // Barcode Down -> Quantity
        barcode.addKeyListener(new KeyAdapter() {

            @Override
            public void keyPressed(KeyEvent e) {

                if (e.getKeyCode() == KeyEvent.VK_DOWN) {

                    quantity.requestFocusInWindow();
                    quantity.selectAll();
                }
            }
        });

        // Quantity Enter -> Cart
        quantity.addActionListener(event -> applyQuantityOrAdd());
        installQuantityFieldIncrementKeys();

        // Quantity +/- and Arrow navigation
        quantity.addKeyListener(new KeyAdapter() {

            @Override
            public void keyPressed(KeyEvent e) {

                if (e.getKeyCode() == KeyEvent.VK_UP) {

                    barcode.requestFocusInWindow();
                    barcode.selectAll();
                }

                if (e.getKeyCode() == KeyEvent.VK_DOWN) {

                    discount.requestFocusInWindow();
                    discount.selectAll();
                }
            }

        });

        // Discount Arrow navigation
        discount.addKeyListener(new KeyAdapter() {

            @Override
            public void keyPressed(KeyEvent e) {

                if (e.getKeyCode() == KeyEvent.VK_UP) {

                    quantity.requestFocusInWindow();
                    quantity.selectAll();
                }

                if (e.getKeyCode() == KeyEvent.VK_DOWN) {

                    method.requestFocusInWindow();
                    method.showPopup();
                }
            }
        });

        // NORTH: a compact header and scan lane; no payment controls belong here.
        JPanel headerAndScan = new JPanel(new BorderLayout(0, 4));
        headerAndScan.setBackground(UITheme.CARD);
        headerAndScan.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                javax.swing.BorderFactory.createMatteBorder(0, 0, 1, 0, UITheme.BORDER),
                javax.swing.BorderFactory.createEmptyBorder(6, 14, 4, 14)));
        JPanel checkoutHeader = new JPanel(new BorderLayout()); checkoutHeader.setOpaque(false);
        JPanel titleBlock = new JPanel(new BorderLayout(0, 1)); titleBlock.setOpaque(false);
        JLabel heading = new JLabel("NEW SALE"); heading.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 18)); heading.setForeground(UITheme.NAVY);
        JLabel ready = new JLabel("Ready to scan"); ready.setFont(new java.awt.Font("Dialog", java.awt.Font.PLAIN, 11)); ready.setForeground(UITheme.GREY);
        titleBlock.add(heading, BorderLayout.NORTH); titleBlock.add(ready, BorderLayout.SOUTH);
        checkoutHeader.add(titleBlock, BorderLayout.WEST);
        sessionSummary.setHorizontalAlignment(SwingConstants.RIGHT); sessionSummary.setForeground(UITheme.GREY);
        sessionSummary.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 12));
        JButton promotionsButton = new JButton("Promotions");
        UITheme.styleSecondary(promotionsButton);
        promotionsButton.setFocusable(false);
        promotionsButton.setToolTipText(Authorization.isAllowed(Authorization.Permission.ADMINISTRATION)
                ? "Add or manage promotions" : "View active promotions");
        promotionsButton.addActionListener(event -> openPromotionsFromSale());
        JPanel headerRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0)); headerRight.setOpaque(false);
        headerRight.add(promotionsButton); headerRight.add(sessionSummary);
        checkoutHeader.add(headerRight, BorderLayout.EAST);
        headerAndScan.add(checkoutHeader, BorderLayout.NORTH);
        headerAndScan.add(scanForm, BorderLayout.CENTER);
        add(headerAndScan, BorderLayout.NORTH);

        // CENTER: the cart owns roughly 70% of the working area; all payment controls stay beside totals.
        JPanel cartPanel = new JPanel(new BorderLayout(0, 8));
        cartPanel.setBackground(UITheme.CARD);
        cartPanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 12, 10, 8));
        cartPanel.setMinimumSize(new Dimension(600, 0));
        JLabel cartTitle = new JLabel("CURRENT CART"); cartTitle.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 16)); cartTitle.setForeground(UITheme.NAVY);
        JScrollPane cartScroll = new JScrollPane(table);
        cartPanel.add(cartTitle, BorderLayout.NORTH); cartPanel.add(cartScroll, BorderLayout.CENTER);

        paymentCard = new JPanel(new BorderLayout(0, 8));
        paymentCard.setBackground(UITheme.CARD);
        paymentCard.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 12, 10, 12));
        paymentCard.setMinimumSize(new Dimension(500, 0));
        JLabel paymentTitle = new JLabel("ORDER SUMMARY"); paymentTitle.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 20)); paymentTitle.setForeground(UITheme.NAVY);
        JPanel totalsSection = verticalPanel(); totalsSection.add(totalsForm); loyaltySection = buildLoyaltySection(); totalsSection.add(loyaltySection);
        totalsSection.setBorder(javax.swing.BorderFactory.createMatteBorder(0, 0, 1, 0, UITheme.BORDER));
        JLabel paymentSectionTitle = sectionTitle("PAYMENT");
        paymentButtons.setLayout(new GridLayout(2, 2, 6, 6));
        paymentButtons.setOpaque(false); paymentButtons.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 0, 4, 0));
        paymentButtons.setPreferredSize(new Dimension(PAYMENT_CONTENT_WIDTH, 104));
        paymentButtons.setMinimumSize(new Dimension(PAYMENT_CONTENT_WIDTH, 104));
        paymentButtons.setMaximumSize(new Dimension(PAYMENT_CONTENT_WIDTH, 104));
        paymentButtons.setAlignmentX(Component.LEFT_ALIGNMENT);
        paymentContent = verticalPanel();
        paymentContent.setMaximumSize(new Dimension(PAYMENT_CONTENT_WIDTH, Integer.MAX_VALUE));
        paymentContent.setAlignmentX(Component.LEFT_ALIGNMENT);
        paymentContent.add(totalsSection); paymentContent.add(paymentSectionTitle); paymentContent.add(paymentButtons); paymentContent.add(paymentForm);
        refreshPaymentContentSize();
        JPanel titleContent = new JPanel(new BorderLayout());
        titleContent.setOpaque(false); titleContent.setPreferredSize(new Dimension(PAYMENT_CONTENT_WIDTH, paymentTitle.getPreferredSize().height));
        titleContent.add(paymentTitle, BorderLayout.WEST);
        completeSaleButton.setPreferredSize(new Dimension(PAYMENT_CONTENT_WIDTH, 62));
        completeSaleButton.setMinimumSize(new Dimension(PAYMENT_CONTENT_WIDTH, 62));
        completeSaleButton.setMaximumSize(new Dimension(PAYMENT_CONTENT_WIDTH, 62));
        standardPaymentContentWrapper = centeredPaymentContent(paymentContent);
        completeSaleWrapper = centeredPaymentContent(completeSaleButton);
        paymentCard.add(centeredPaymentContent(titleContent), BorderLayout.NORTH);
        paymentCard.add(standardPaymentContentWrapper, BorderLayout.CENTER);
        paymentCard.add(completeSaleWrapper, BorderLayout.SOUTH);

        JSplitPane checkoutBody = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, cartPanel, paymentCard);
        checkoutBody.setResizeWeight(.50); checkoutBody.setDividerLocation(.50); checkoutBody.setDividerSize(6);
        checkoutBody.setBorder(javax.swing.BorderFactory.createEmptyBorder());
        checkoutBody.setContinuousLayout(true); checkoutBody.setOneTouchExpandable(false);
        final boolean[] initialDividerPositioned = {false};
        checkoutBody.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent event) {
                if (!initialDividerPositioned[0] && checkoutBody.getWidth() > 0) {
                    checkoutBody.setDividerLocation(.50);
                    initialDividerPositioned[0] = true;
                }
            }
        });
        javax.swing.SwingUtilities.invokeLater(() -> checkoutBody.setDividerLocation(.60));
        add(checkoutBody, BorderLayout.CENTER);

        // SOUTH: secondary actions and shortcut hints are separate rows, so they can never collide.
        JPanel shortcutBar = new JPanel(new BorderLayout()); shortcutBar.setBackground(UITheme.BACKGROUND);
        shortcutBar.setBorder(javax.swing.BorderFactory.createEmptyBorder(3, 14, 5, 14));
        JLabel hints = new JLabel("F1 Scan | F4 Customer | F5 Cash | F6 Card | F7 Transfer | F8 Split | F9 Hold | F10 Recall | F11 Price Check | F12 Complete");
        hints.setToolTipText("F1 Scan | F3 Cart | F4 Customer | F5 Cash | F6 Card | F7 Transfer | F8 Split | F9 Hold | F10 Recall | F11 Price Check | F12 Complete | +/- Qty | Del Remove");
        hints.setForeground(UITheme.GREY); hints.setFont(new java.awt.Font("Dialog", java.awt.Font.PLAIN, 11));
        shortcutBar.add(hints, BorderLayout.WEST);
        JPanel bottomContainer = new JPanel(); bottomContainer.setLayout(new javax.swing.BoxLayout(bottomContainer, javax.swing.BoxLayout.Y_AXIS));
        bottomContainer.setBackground(UITheme.BACKGROUND);
        actions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44)); shortcutBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        bottomContainer.add(actions); bottomContainer.add(shortcutBar);
        add(bottomContainer, BorderLayout.SOUTH);

        loadChoices();
        refreshSessionSummary();
        updatePaymentFields();
        UITheme.applyWindow(this);
        styleCustomerClearButton();
        stylePaymentDetailFields();
        makeTopInputTextBold();
        styleCheckoutCombo(product, productEditor);
        product.setMaximumRowCount(10);
        styleCheckoutCombo(customer, customerEditor);
        productEditor = (JTextField) product.getEditor().getEditorComponent();
        productEditor.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 8, 0, 6));
        customerEditor = (JTextField) customer.getEditor().getEditorComponent();
        customerEditor.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 8, 0, 6));
        installCustomerEditorLookup();
        // Styling replaces the editable combo's editor. Restore the visible default
        // on the final editor, after its lookup listener has been installed.
        selectWalkInCustomer();
        configureCustomerAutocompletePopup();
        updateCustomerAutocompletePopupHeight();
        installProductSearchTypingFilter();
        installProductPopupKeyboardNavigation();
        Dimension productMinimum = product.getMinimumSize();
        Dimension productPreferred = product.getPreferredSize();
        product.setMinimumSize(new Dimension(productMinimum.width, discount.getMinimumSize().height));
        product.setPreferredSize(new Dimension(productPreferred.width, discount.getPreferredSize().height));
        customer.setMinimumSize(product.getMinimumSize());
        customer.setPreferredSize(product.getPreferredSize());
        UITheme.styleSecondary(add); UITheme.styleSecondary(plusQty); UITheme.styleSecondary(minusQty); UITheme.styleDanger(remove); UITheme.styleSecondary(hold); UITheme.stylePrimary(recall); UITheme.styleSecondary(clear); UITheme.styleSuccess(completeSaleButton);
        stylePaymentButtonCorners();
        for (JButton paymentButton : List.of(cashButton, cardButton, transferButton, splitButton)) paymentButton.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 16));
        completeSaleButton.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 20));
        for (Component component : quickCashButtons.getComponents()) {
            if (component instanceof JButton button) {
                button.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 14));
                button.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 3, 2, 3));
            }
        }
        updatePaymentFields();
        table.setRowHeight(32);
        installPromotionCartRenderers();
        table.getColumnModel().getColumn(0).setPreferredWidth(220);
        table.getColumnModel().getColumn(1).setPreferredWidth(52);
        configureManualDiscountAccess();

        // New Sale -> Barcode ready
        javax.swing.SwingUtilities.invokeLater(() ->
                barcode.requestFocusInWindow()
        );
    }

    private void addRow(
            JPanel panel,
            String firstLabel,
            java.awt.Component first,
            String secondLabel,
            java.awt.Component second) {

        JLabel firstName = new JLabel(firstLabel);
        panel.add(firstName);
        panel.add(first);

        JLabel secondName = new JLabel(secondLabel);
        panel.add(secondName);
        panel.add(second);
        labelledFields.put(first, List.of(firstName, first));
        labelledFields.put(second, List.of(secondName, second));
    }

    private void addScanComponent(JPanel panel, java.awt.Component component, int column, int row, int width, double weight) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = column; constraints.gridy = row; constraints.gridwidth = width;
        constraints.weightx = weight; constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.WEST; constraints.insets = new java.awt.Insets(1, 0, 1, 4);
        panel.add(component, constraints);
    }

    /** Keeps the clear affordance inside the existing Customer field footprint, immediately before its arrow. */
    private javax.swing.JLayeredPane buildCustomerFieldControl(Dimension fieldSize) {
        javax.swing.JLayeredPane control = new javax.swing.JLayeredPane() {
            @Override public void doLayout() {
                customer.setBounds(0, 0, getWidth(), getHeight());
                int buttonSize = 22;
                int arrowReservation = 24;
                clearCustomerButton.setBounds(Math.max(0, getWidth() - arrowReservation - buttonSize),
                        Math.max(0, (getHeight() - buttonSize) / 2), buttonSize, buttonSize);
            }
        };
        control.setOpaque(false);
        control.setMinimumSize(fieldSize);
        control.setPreferredSize(fieldSize);
        clearCustomerButton.setToolTipText("Clear customer");
        styleCustomerClearButton();
        clearCustomerButton.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        clearCustomerButton.addActionListener(event -> clearCheckoutCustomerInput());
        control.add(customer, javax.swing.JLayeredPane.DEFAULT_LAYER);
        control.add(clearCustomerButton, javax.swing.JLayeredPane.PALETTE_LAYER);
        return control;
    }

    /** Reapplies the icon-only treatment after the general button theme has styled the window. */
    private void styleCustomerClearButton() {
        clearCustomerButton.setText("\u00D7");
        clearCustomerButton.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 17));
        clearCustomerButton.setForeground(UITheme.RED);
        clearCustomerButton.setBackground(java.awt.Color.WHITE);
        clearCustomerButton.setHorizontalAlignment(SwingConstants.CENTER);
        clearCustomerButton.setVerticalAlignment(SwingConstants.CENTER);
        clearCustomerButton.setFocusable(false);
        clearCustomerButton.setFocusPainted(false);
        clearCustomerButton.setOpaque(true);
        clearCustomerButton.setContentAreaFilled(true);
        clearCustomerButton.setBorderPainted(true);
        clearCustomerButton.setBorder(javax.swing.BorderFactory.createLineBorder(UITheme.BORDER));
        clearCustomerButton.setMargin(new java.awt.Insets(0, 0, 0, 0));
    }

    /** Clears checkout-only customer input; reset/new-sale continues to restore Walk-in Customer. */
    private void clearCheckoutCustomerInput() {
        suppressCustomerPopup = true;
        try {
            customer.setSelectedItem(null);
            customerEditor.setText("");
            customer.setPopupVisible(false);
            clearAppliedRedemption();
            refreshLoyaltyCustomer();
        } finally {
            javax.swing.SwingUtilities.invokeLater(() -> {
                customer.setPopupVisible(false);
                suppressCustomerPopup = false;
                customerEditor.requestFocusInWindow();
                customerEditor.setCaretPosition(0);
            });
        }
    }

    /** Applies emphasis to entered/displayed values only; labels and layout are deliberately untouched. */
    private void makeTopInputTextBold() {
        for (JTextField field : List.of(barcode, quantity, discount, invoice, productEditor, customerEditor)) {
            field.setFont(field.getFont().deriveFont(java.awt.Font.BOLD));
        }
        product.setFont(product.getFont().deriveFont(java.awt.Font.BOLD));
        customer.setFont(customer.getFont().deriveFont(java.awt.Font.BOLD));
        for (JTextField field : List.of(tendered, cashAmount, cardAmount, transferAmount,
                cardReference, cardBankName, transferReference, bankName)) {
            field.setFont(field.getFont().deriveFont(java.awt.Font.BOLD));
        }
    }

    /** Applies the same high-visibility border to every editable payment-detail field only. */
    private void stylePaymentDetailFields() {
        for (JTextField field : List.of(tendered, cashAmount, cardAmount, transferAmount,
                cardReference, cardBankName, transferReference, bankName)) {
            field.setBackground(java.awt.Color.WHITE);
            field.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                    javax.swing.BorderFactory.createLineBorder(java.awt.Color.BLACK, 2, true),
                    javax.swing.BorderFactory.createEmptyBorder(5, 8, 5, 8)));
        }
    }

    /** Visual treatment only: keeps the existing editable-combo behavior and dimensions intact. */
    private void styleCheckoutCombo(JComboBox<?> combo, JTextField editor) {
        combo.setOpaque(true);
        combo.setBackground(java.awt.Color.WHITE);
        combo.setBorder(javax.swing.BorderFactory.createLineBorder(UITheme.BORDER));
        combo.setFont(combo.getFont().deriveFont(java.awt.Font.BOLD));
        combo.setMaximumRowCount(10);
        editor.setBackground(java.awt.Color.WHITE);
        editor.setFont(editor.getFont().deriveFont(java.awt.Font.BOLD));
        editor.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 8, 0, 6));

        combo.setRenderer(new DefaultListCellRenderer() {
            @Override public java.awt.Component getListCellRendererComponent(
                    javax.swing.JList<?> list, Object value, int index, boolean selected, boolean focused) {
                super.getListCellRendererComponent(list, value, index, selected, focused);
                setFont(combo.getFont());
                setBorder(javax.swing.BorderFactory.createEmptyBorder(5, 10, 5, 10));
                setBackground(selected ? UITheme.BLUE : java.awt.Color.WHITE);
                setForeground(selected ? java.awt.Color.WHITE : UITheme.NAVY);
                return this;
            }
        });

        combo.setUI(new javax.swing.plaf.basic.BasicComboBoxUI() {
            @Override protected JButton createArrowButton() {
                JButton arrow = new JButton() {
                    @Override protected void paintComponent(java.awt.Graphics graphics) {
                        java.awt.Graphics2D g = (java.awt.Graphics2D) graphics.create();
                        g.setColor(UITheme.BLUE);
                        g.fillRect(0, 0, getWidth(), getHeight());
                        int centerX = getWidth() / 2;
                        int centerY = getHeight() / 2;
                        g.setColor(java.awt.Color.WHITE);
                        g.fillPolygon(new int[]{centerX - 6, centerX + 6, centerX},
                                new int[]{centerY - 3, centerY - 3, centerY + 4}, 3);
                        g.dispose();
                    }
                };
                arrow.setOpaque(true);
                arrow.setContentAreaFilled(false);
                arrow.setBorderPainted(false);
                arrow.setPreferredSize(new Dimension(40, 0));
                arrow.setMargin(new java.awt.Insets(0, 0, 0, 0));
                arrow.setFocusPainted(false);
                return arrow;
            }
        });
    }

    /** Makes the Customer-only autocomplete popup an opaque, result-sized list. */
    private void configureCustomerAutocompletePopup() {
        javax.accessibility.Accessible popupChild = customer.getUI().getAccessibleChild(customer, 0);
        if (!(popupChild instanceof javax.swing.plaf.basic.BasicComboPopup popup)) return;

        popup.setOpaque(true);
        popup.setBackground(java.awt.Color.WHITE);
        popup.setBorder(javax.swing.BorderFactory.createLineBorder(UITheme.BORDER));

        javax.swing.JList<?> list = popup.getList();
        list.setOpaque(true);
        list.setBackground(java.awt.Color.WHITE);
        customer.setRenderer(new DefaultListCellRenderer() {
            @Override public java.awt.Component getListCellRendererComponent(
                    javax.swing.JList<?> popupList, Object value, int index, boolean selected, boolean focused) {
                super.getListCellRendererComponent(popupList, value, index, selected, focused);
                setOpaque(true);
                setFont(customer.getFont());
                setBorder(javax.swing.BorderFactory.createEmptyBorder(5, 10, 5, 10));
                setBackground(selected ? UITheme.BLUE : java.awt.Color.WHITE);
                setForeground(selected ? java.awt.Color.WHITE : UITheme.NAVY);
                return this;
            }
        });

        JScrollPane scrollPane = findScrollPane(popup);
        if (scrollPane != null) {
            scrollPane.setOpaque(true);
            scrollPane.setBackground(java.awt.Color.WHITE);
            scrollPane.setBorder(javax.swing.BorderFactory.createEmptyBorder());
            scrollPane.getViewport().setOpaque(true);
            scrollPane.getViewport().setBackground(java.awt.Color.WHITE);
        }
    }

    /** Limits Customer suggestions to their actual count, capped at eight visible rows. */
    private void updateCustomerAutocompletePopupHeight() {
        int rows = Math.min(Math.max(customer.getItemCount(), 1), 8);
        customer.setMaximumRowCount(rows);
        javax.accessibility.Accessible popupChild = customer.getUI().getAccessibleChild(customer, 0);
        if (popupChild instanceof javax.swing.plaf.basic.BasicComboPopup popup) {
            popup.getList().setVisibleRowCount(rows);
        }
    }

    private JScrollPane findScrollPane(Component component) {
        if (component instanceof JScrollPane scrollPane) return scrollPane;
        if (!(component instanceof java.awt.Container container)) return null;
        for (Component child : container.getComponents()) {
            JScrollPane scrollPane = findScrollPane(child);
            if (scrollPane != null) return scrollPane;
        }
        return null;
    }

    /** Keeps the scan field compact while preserving the quantity column's existing grid position. */
    private void addFixedScanComponent(JPanel panel, java.awt.Component component, int column, int row, int width, double weight) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = column; constraints.gridy = row; constraints.gridwidth = width;
        constraints.weightx = weight; constraints.fill = GridBagConstraints.NONE;
        constraints.ipadx = 50;
        constraints.anchor = GridBagConstraints.WEST; constraints.insets = new java.awt.Insets(1, 0, 1, 4);
        panel.add(component, constraints);
    }

    /** Shifts Product Search into Barcode's unused grid space without altering either control's size. */
    private void addOffsetScanComponent(JPanel panel, java.awt.Component component, int column, int row, int width, double weight, int horizontalOffset) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = column; constraints.gridy = row; constraints.gridwidth = width;
        constraints.weightx = weight; constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new java.awt.Insets(1, horizontalOffset, 1, 4 - horizontalOffset);
        panel.add(component, constraints);
    }

    /** Applies Barcode/Quantity field sizing while preserving the Discount field's aligned starting point. */
    private void addFixedOffsetScanComponent(JPanel panel, java.awt.Component component, int column, int row, int width, double weight, int horizontalOffset, int internalPadding) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = column; constraints.gridy = row; constraints.gridwidth = width;
        constraints.weightx = weight; constraints.fill = GridBagConstraints.NONE;
        constraints.ipadx = internalPadding; constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new java.awt.Insets(1, horizontalOffset, 1, 4 - horizontalOffset);
        panel.add(component, constraints);
    }

    /** A stable full-width row avoids GridLayout reserving space for hidden payment fields. */
    private void addPaymentRow(JPanel panel, String label, java.awt.Component field) {
        JPanel row = new JPanel(new BorderLayout(10, 0)); row.setOpaque(false);
        row.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        boolean compactCashRow = "Tendered cash".equals(label) || "Quick Cash".equals(label);
        row.setBorder(javax.swing.BorderFactory.createEmptyBorder(compactCashRow ? 0 : 2, 0, compactCashRow ? 0 : 2, 0));
        JLabel name = new JLabel(label); name.setPreferredSize(new java.awt.Dimension(150, 38)); name.setFont(new java.awt.Font("Dialog", java.awt.Font.PLAIN, 16));
        if (field instanceof JTextField textField) { textField.setPreferredSize(new java.awt.Dimension(280, 42)); textField.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 17)); }
        if (field instanceof JLabel value) { value.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT); value.setPreferredSize(new java.awt.Dimension(280, 42)); }
        if ("Change".equals(label)) {
            name.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 20));
            if (field instanceof JLabel value) value.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 30));
            row.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                    javax.swing.BorderFactory.createMatteBorder(1, 0, 1, 0, UITheme.BORDER),
                    javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0)));
            row.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 50));
        } else if ("Remaining".equals(label)) {
            name.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 20));
            if (field instanceof JLabel value) value.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 30));
            row.setBorder(javax.swing.BorderFactory.createCompoundBorder(javax.swing.BorderFactory.createMatteBorder(1, 0, 1, 0, UITheme.BORDER), javax.swing.BorderFactory.createEmptyBorder(7, 0, 7, 0)));
            row.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 62));
        } else if ("Quick Cash".equals(label)) {
            row.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 44));
        } else {
            row.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, compactCashRow ? 46 : 50));
        }
        row.add(name, BorderLayout.WEST); row.add(field, BorderLayout.CENTER);
        panel.add(row);
        labelledFields.put(field, List.of(row));
    }

    private JPanel addTotalsRow(JPanel panel, String label, JLabel value) {
        JPanel row = new JPanel(new BorderLayout(10, 0)); row.setOpaque(false);
        row.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        row.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 0, 2, 0));
        int labelWidth = "Loyalty Redemption".equals(label) ? 190 : 150;
        JLabel name = new JLabel(label); name.setPreferredSize(new java.awt.Dimension(labelWidth, 36)); name.setFont(new java.awt.Font("Dialog", java.awt.Font.PLAIN, 16));
        value.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT); value.setPreferredSize(new java.awt.Dimension(280, 40));
        if ("GRAND TOTAL".equals(label)) { name.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 18)); value.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 30)); value.setForeground(UITheme.NAVY); row.setBorder(javax.swing.BorderFactory.createCompoundBorder(javax.swing.BorderFactory.createMatteBorder(1, 0, 1, 0, UITheme.BORDER), javax.swing.BorderFactory.createEmptyBorder(5, 0, 5, 0))); row.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 64)); }
        else row.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 44));
        row.add(name, BorderLayout.WEST); row.add(value, BorderLayout.CENTER); panel.add(row);
        return row;
    }

    /** Compact, conditional controls placed inside Order Summary without changing existing checkout controls. */
    private JPanel buildLoyaltySection() {
        JPanel section = verticalPanel();
        section.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                javax.swing.BorderFactory.createMatteBorder(1, 0, 0, 0, UITheme.BORDER),
                javax.swing.BorderFactory.createEmptyBorder(3, 0, 3, 0)));
        JLabel heading = sectionTitle("LOYALTY"); heading.setAlignmentX(Component.LEFT_ALIGNMENT); section.add(heading);
        section.add(loyaltyRow("Current Points", currentPointsDisplay));
        JPanel redeemControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0)); redeemControls.setOpaque(false);
        redeemPointsField.setPreferredSize(new Dimension(64, 24));
        redeemPointsField.setMinimumSize(new Dimension(64, 24));
        UITheme.stylePrimary(applyLoyaltyButton);
        applyLoyaltyButton.setMargin(new java.awt.Insets(2, 7, 2, 7));
        redeemControls.add(redeemPointsField); redeemControls.add(applyLoyaltyButton);
        section.add(loyaltyRow("Redeem Points", redeemControls));
        loyaltyMinimumMessage.setFont(loyaltyMinimumMessage.getFont().deriveFont(java.awt.Font.PLAIN, 11));
        loyaltyMinimumMessage.setForeground(UITheme.GREY);
        loyaltyMinimumMessage.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        loyaltyMinimumMessage.setAlignmentX(Component.LEFT_ALIGNMENT);
        loyaltyMinimumMessage.setMinimumSize(new Dimension(PAYMENT_CONTENT_WIDTH, 14));
        loyaltyMinimumMessage.setPreferredSize(new Dimension(PAYMENT_CONTENT_WIDTH, 14));
        loyaltyMinimumMessage.setMaximumSize(new Dimension(PAYMENT_CONTENT_WIDTH, 14));
        loyaltyMinimumMessage.setVisible(false);
        section.add(loyaltyMinimumMessage);
        applyLoyaltyButton.addActionListener(event -> applyLoyaltyRedemption());
        redeemPointsField.addActionListener(event -> applyLoyaltyRedemption());
        section.setVisible(false);
        return section;
    }

    private JPanel loyaltyRow(String label, Component value) {
        JPanel row = new JPanel(new BorderLayout(10, 0)); row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT); row.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 0, 1, 0));
        JLabel name = new JLabel(label); name.setPreferredSize(new Dimension(110, 24));
        if (value instanceof JLabel display) { display.setHorizontalAlignment(SwingConstants.RIGHT); display.setPreferredSize(new Dimension(190, 24)); }
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        row.add(name, BorderLayout.WEST); row.add(value, BorderLayout.CENTER); return row;
    }

    /** Cart paint only reads the already-calculated item state; it never queries promotions. */
    private void installPromotionCartRenderers() {
        for (int column = 0; column < table.getColumnCount(); column++) {
            final int cartColumn = column;
            javax.swing.table.DefaultTableCellRenderer renderer = new javax.swing.table.DefaultTableCellRenderer() {
                @Override public Component getTableCellRendererComponent(JTable source, Object value, boolean selected,
                        boolean focused, int row, int column) {
                    String text = value instanceof BigDecimal amount
                            ? (cartColumn == 1 ? amount.stripTrailingZeros().toPlainString() : UITheme.money(amount))
                            : value == null ? "" : value.toString();
                    javax.swing.JLabel cell = (javax.swing.JLabel) super.getTableCellRendererComponent(source, text, selected, focused, row, column);
                    CartItem item = row >= 0 && row < cart.size() ? cart.get(source.convertRowIndexToModel(row)) : null;
                    boolean promoted = item != null && item.hasPromotion();
                    cell.setBackground(promoted ? (selected ? new java.awt.Color(205, 232, 212) : new java.awt.Color(237, 248, 240))
                            : (selected ? new java.awt.Color(218, 234, 248) : java.awt.Color.WHITE));
                    cell.setForeground(UITheme.NAVY);
                    cell.setFont(cell.getFont().deriveFont(java.awt.Font.BOLD));
                    cell.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 7, 0, 7));
                    cell.setHorizontalAlignment(cartColumn == 1 || cartColumn == 3 ? SwingConstants.CENTER
                            : cartColumn >= 2 ? SwingConstants.RIGHT : SwingConstants.LEFT);
                    return cell;
                }
            };
            table.getColumnModel().getColumn(column).setCellRenderer(renderer);
        }
    }

    /** Manual bill discounts are an admin-only field; cashiers cannot retain or submit a stale value. */
    private void configureManualDiscountAccess() {
        boolean admin = "ADMIN".equals(Session.currentRole());
        discount.setEnabled(admin);
        discount.setToolTipText("Fixed LKR amount (250) or a percentage of the bill (10%)");
        if (!admin) discount.setText("0");
    }

    private JPanel verticalPanel() {
        JPanel panel = new JPanel(); panel.setOpaque(false); panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS)); panel.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT); return panel;
    }

    /** Centers one fixed-width content column without changing any control's own sizing or behavior. */
    private JPanel centeredPaymentContent(java.awt.Component content) {
        JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        wrapper.setOpaque(false);
        wrapper.add(content);
        return wrapper;
    }

    /** Recalculates the fixed-width payment column after payment rows are shown or hidden. */
    private void refreshPaymentContentSize() {
        if (paymentContent == null) return;
        paymentContent.setPreferredSize(null);
        paymentContent.setMinimumSize(null);
        Dimension preferred = paymentContent.getPreferredSize();
        Dimension size = new Dimension(PAYMENT_CONTENT_WIDTH, preferred.height);
        paymentContent.setPreferredSize(size);
        paymentContent.setMinimumSize(size);
    }

    /**
     * CARD, TRANSFER and SPLIT share the same scroll container whenever their detail rows need
     * more room. Complete Sale stays after the final field inside that existing scroll path.
     */
    private void updateSplitPaymentLayout(boolean scrollablePayment) {
        if (paymentCard == null || paymentContent == null) return;
        refreshPaymentContentSize();

        if (scrollablePayment && !splitPaymentLayoutActive) {
            paymentCard.remove(standardPaymentContentWrapper);
            paymentCard.remove(completeSaleWrapper);

            splitPaymentStack = verticalPanel();
            splitPaymentStack.setMaximumSize(new Dimension(PAYMENT_CONTENT_WIDTH, Integer.MAX_VALUE));
            splitPaymentStack.add(paymentContent);
            splitPaymentStack.add(javax.swing.Box.createVerticalStrut(8));
            splitPaymentStack.add(completeSaleButton);
            refreshSplitPaymentStackSize();

            splitPaymentScroll = new JScrollPane(centeredPaymentContent(splitPaymentStack),
                    JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            splitPaymentScroll.setBorder(javax.swing.BorderFactory.createEmptyBorder());
            splitPaymentScroll.setOpaque(false);
            splitPaymentScroll.getViewport().setOpaque(true);
            splitPaymentScroll.getViewport().setBackground(UITheme.CARD);
            splitPaymentScroll.getVerticalScrollBar().setUnitIncrement(16);
            paymentCard.add(splitPaymentScroll, BorderLayout.CENTER);
            splitPaymentLayoutActive = true;
        } else if (!scrollablePayment && splitPaymentLayoutActive) {
            paymentCard.remove(splitPaymentScroll);
            splitPaymentStack.remove(paymentContent);
            splitPaymentStack.remove(completeSaleButton);
            standardPaymentContentWrapper.add(paymentContent);
            completeSaleWrapper.add(completeSaleButton);
            paymentCard.add(standardPaymentContentWrapper, BorderLayout.CENTER);
            paymentCard.add(completeSaleWrapper, BorderLayout.SOUTH);
            splitPaymentLayoutActive = false;
        }

        if (splitPaymentLayoutActive) refreshSplitPaymentStackSize();
        paymentCard.revalidate();
        paymentCard.repaint();
    }

    /** Cash is the only standard method with three detail rows; remove only its unused card gaps. */
    private void updateCashPaymentPanelSpacing(boolean cash) {
        if (paymentCard != null && paymentCard.getLayout() instanceof BorderLayout layout) {
            layout.setVgap(cash ? 0 : 8);
        }
    }

    private void refreshSplitPaymentStackSize() {
        if (splitPaymentStack == null) return;
        splitPaymentStack.setPreferredSize(null);
        Dimension preferred = splitPaymentStack.getPreferredSize();
        splitPaymentStack.setPreferredSize(new Dimension(PAYMENT_CONTENT_WIDTH, preferred.height));
        splitPaymentStack.setMinimumSize(new Dimension(PAYMENT_CONTENT_WIDTH, preferred.height));
    }

    private JLabel sectionTitle(String value) {
        JLabel title = new JLabel(value); title.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 12)); title.setForeground(UITheme.GREY); title.setBorder(javax.swing.BorderFactory.createEmptyBorder(3, 0, 5, 0)); title.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT); return title;
    }

    private void configurePaymentButtons() {
        paymentButtons.setOpaque(false);
        configurePaymentButton(cashButton, PaymentMethod.CASH);
        configurePaymentButton(cardButton, PaymentMethod.CARD);
        configurePaymentButton(transferButton, PaymentMethod.TRANSFER);
        configurePaymentButton(splitButton, PaymentMethod.SPLIT);
        paymentButtons.add(cashButton); paymentButtons.add(cardButton); paymentButtons.add(transferButton); paymentButtons.add(splitButton);
        quickCashButtons.setOpaque(false); quickCashButtons.setLayout(new GridLayout(1, 5, 2, 0));
        for (String amount : List.of("Exact", "500", "1000", "2000", "5000")) {
            JButton button = new JButton(amount); UITheme.styleSecondary(button);
            button.setBorder(javax.swing.BorderFactory.createEmptyBorder(3, 4, 3, 4));
            button.setPreferredSize(new java.awt.Dimension("Exact".equals(amount) ? 90 : 80, 40));
            button.addActionListener(event -> {
                try { tendered.setText("Exact".equals(amount) ? finalPayableTotal().setScale(2).toPlainString() : amount); tendered.requestFocusInWindow(); tendered.selectAll(); }
                catch (Exception ignored) { }
            });
            quickCashButtons.add(button);
        }
    }

    private void registerShortcut(int keyCode, int modifiers, Runnable action) {
        getRootPane().registerKeyboardAction(event -> action.run(), javax.swing.KeyStroke.getKeyStroke(keyCode, modifiers), javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    /** Explicit named window actions prevent F6/F8 from being lost behind anonymous Swing bindings. */
    private void registerPaymentShortcut(int keyCode, PaymentMethod paymentMethod) {
        String actionKey = "checkout.payment." + paymentMethod.name().toLowerCase(java.util.Locale.ROOT);
        javax.swing.JRootPane root = getRootPane();
        root.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(javax.swing.KeyStroke.getKeyStroke(keyCode, 0), actionKey);
        root.getActionMap().put(actionKey, new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) {
                selectPayment(paymentMethod, true);
            }
        });
    }

    /** F6/F8 are intercepted before focused Swing controls can consume them. */
    private void installPaymentShortcutDispatcher() {
        if (paymentShortcutDispatcherRegistered) return;
        paymentShortcutDispatcher = event -> {
            if (!isDisplayable() || !isActive() || event.getID() != KeyEvent.KEY_PRESSED) return false;
            if (event.getKeyCode() == KeyEvent.VK_F6) {
                cardButton.doClick();
                return true;
            }
            if (event.getKeyCode() == KeyEvent.VK_F8) {
                splitButton.doClick();
                return true;
            }
            return false;
        };
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(paymentShortcutDispatcher);
        paymentShortcutDispatcherRegistered = true;
    }

    private void removePaymentShortcutDispatcher() {
        if (!paymentShortcutDispatcherRegistered) return;
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(paymentShortcutDispatcher);
        paymentShortcutDispatcher = null;
        paymentShortcutDispatcherRegistered = false;
    }

    @Override public void dispose() {
        removePaymentShortcutDispatcher();
        super.dispose();
    }

    private void focus(JTextField field) { field.requestFocusInWindow(); field.selectAll(); }

    private boolean isTextEntryActive() {
        Component focused = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        return focused instanceof JTextComponent || focused instanceof JComboBox<?>;
    }

    /** Window shortcuts must never change a cart line while an amount, reference, or quantity is being typed. */
    private void changeCartQuantityIfSafe(int delta) {
        if (isTextEntryActive()) return;
        if (table.getSelectedRow() >= 0) changeQuantity(delta);
    }

    private void removeSelectedWithConfirmation() {
        if (table.getSelectedRow() < 0) {
            showInlineNotice("Select a cart item to remove.", UITheme.RED);
            return;
        }
        if (Dialogs.confirmYesNo(this, "Remove Item", "Remove the selected item from the cart?")) removeSelected();
    }

    private void highlightScannedRow(int row) {
        table.setRowSelectionInterval(row, row);
        table.scrollRectToVisible(table.getCellRect(row, 0, true));
        table.repaint();
        javax.swing.Timer flash = new javax.swing.Timer(700, event -> table.repaint());
        flash.setRepeats(false);
        flash.start();
    }

    private void refreshSessionSummary() {
        String shiftStatus = "Shift: not applicable";
        if ("CASHIER".equals(Session.currentRole())) {
            try {
                shiftStatus = shifts.currentOpenShiftForCurrentUser().isPresent() ? "Shift: OPEN" : "Shift: CLOSED";
            } catch (Exception ignored) {
                shiftStatus = "Shift: unavailable";
            }
        }
        sessionSummary.setText("Cashier " + Session.currentCashierName() + "  |  " + shiftStatus);
    }

    /** Cashier-safe price lookup.  It is deliberately separate from the cart and exposes no cost information. */
    private void showPriceCheck() {
        JDialog dialog = new JDialog(this, "Price Check", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        JTextField lookup = new JTextField(22);
        JLabel result = new JLabel("Scan a barcode or enter a product name.");
        result.setVerticalAlignment(SwingConstants.TOP);
        result.setForeground(UITheme.NAVY);
        JButton close = new JButton("Close");
        JButton check = new JButton("Check");
        UITheme.stylePrimary(check); UITheme.styleSecondary(close);
        Runnable showProduct = () -> {
            String query = lookup.getText() == null ? "" : lookup.getText().trim();
            Product found = productsByBarcode.get(query);
            if (found == null && !query.isBlank()) {
                String lowered = query.toLowerCase(java.util.Locale.ROOT);
                found = allProducts.stream().filter(p -> p.name().toLowerCase(java.util.Locale.ROOT).contains(lowered)).findFirst().orElse(null);
            }
            if (found == null) {
                result.setForeground(UITheme.RED);
                result.setText("Product not found: " + query);
            } else {
                result.setForeground(UITheme.NAVY);
                String retail = found.retailPrice() == null ? "—" : UITheme.money(found.retailPrice());
                result.setText("<html><b>" + escapeHtml(found.name()) + "</b><br><br>Selling Price: " + UITheme.money(found.sellingPrice())
                        + "<br>Retail / MRP: " + retail + "<br>Available Stock: " + found.stockQuantity().stripTrailingZeros().toPlainString() + "</html>");
            }
        };
        lookup.addActionListener(event -> showProduct.run());
        check.addActionListener(event -> showProduct.run());
        close.addActionListener(event -> dialog.dispose());
        JPanel content = UITheme.card(new BorderLayout(0, 12));
        content.add(UITheme.pageHeader("PRICE CHECK", "This lookup does not add to the cart or change stock."), BorderLayout.NORTH);
        JPanel entry = new JPanel(new BorderLayout(8, 0)); entry.setOpaque(false);
        entry.add(new JLabel("Barcode / Product"), BorderLayout.WEST); entry.add(lookup, BorderLayout.CENTER); entry.add(check, BorderLayout.EAST);
        content.add(entry, BorderLayout.CENTER);
        JPanel lower = new JPanel(new BorderLayout(0, 10)); lower.setOpaque(false); lower.add(result, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0)); buttons.setOpaque(false); buttons.add(close); lower.add(buttons, BorderLayout.SOUTH);
        content.add(lower, BorderLayout.SOUTH);
        dialog.setContentPane(content); dialog.setSize(480, 300); dialog.setLocationRelativeTo(this);
        dialog.getRootPane().setDefaultButton(check);
        dialog.setVisible(true);
        focus(barcode);
    }

    private String escapeHtml(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void clearSaleWithConfirmation() {
        if (cart.isEmpty() || Dialogs.confirmYesNo(this, "Clear Sale", "Clear the current cart and start a new sale?")) resetForNextCustomer();
    }

    private void holdSale() {
        if (cart.isEmpty()) { showInlineNotice("Nothing to hold."); focus(barcode); return; }
        CustomerChoice selected = selectedCustomer();
        List<HeldLine> lines = cart.stream().map(item -> new HeldLine(item.productId(), item.quantity(), item.ourPrice(), item.retailPrice(), item.batchAllocations())).toList();
        synchronized (HELD_SALES) { HELD_SALES.add(new HeldSale(invoice.getText(), selected, discount.getText(), lines)); }
        showInlineNotice("Sale held. Ready for next customer."); resetForNextCustomer();
    }

    private void recallSale() {
        HeldSale selected;
        synchronized (HELD_SALES) {
            if (HELD_SALES.isEmpty()) { showInlineNotice("No held sales."); focus(barcode); return; }
            javax.swing.JComboBox<HeldSale> choices = new javax.swing.JComboBox<>(HELD_SALES.toArray(HeldSale[]::new));
            if (!Dialogs.confirmOkCancel(this, "Recall Held Sale", choices)) return;
            selected = (HeldSale) choices.getSelectedItem();
            if (selected == null) return;
            HELD_SALES.remove(selected);
        }
        try {
            cart.clear();
            for (HeldLine line : selected.lines()) {
                Product heldProduct = products.findActiveById(line.productId()).orElseThrow(() -> new IllegalArgumentException("A held product is no longer available."));
                cart.add(new CartItem(heldProduct, line.quantity(), line.ourPrice(), line.retailPrice(), line.batchAllocations()));
            }
            customer.setSelectedItem(selected.customer());
            discount.setText("ADMIN".equals(Session.currentRole()) ? selected.discount() : "0");
            refreshCart(); showInlineNotice("Held sale recalled."); focus(barcode);
        } catch (Exception exception) { showError(exception); }
    }

    private void configurePaymentButton(JButton button, PaymentMethod paymentMethod) {
        UITheme.stylePrimary(button);
        button.addActionListener(event -> selectPayment(paymentMethod, true));
    }

    /** Payment colors still come from selection state; this changes only their outer corner radius. */
    private void stylePaymentButtonCorners() {
        for (JButton button : List.of(cashButton, cardButton, transferButton, splitButton)) {
            button.setOpaque(false);
            button.setContentAreaFilled(false);
            button.setBorderPainted(false);
            button.setFocusPainted(false);
            button.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 15, 8, 15));
        }
    }

    /** Avoids BasicButtonUI's square fill while retaining the existing button size and centered text. */
    private static final class RoundedPaymentButton extends JButton {
        private RoundedPaymentButton(String text) { super(text); }
        @Override protected void paintComponent(java.awt.Graphics graphics) {
            java.awt.Graphics2D g = (java.awt.Graphics2D) graphics.create();
            g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(getBackground());
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 20, 20);
            g.setColor(getBackground().darker());
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 20, 20);
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    private void selectPayment(PaymentMethod selected, boolean moveFocus) {
        method.setSelectedItem(selected);
        updatePaymentFields();
        if (!moveFocus) return;
        javax.swing.SwingUtilities.invokeLater(() -> {
            if (selected == PaymentMethod.CASH) { tendered.requestFocusInWindow(); tendered.selectAll(); }
            else if (selected == PaymentMethod.CARD) { cardReference.requestFocusInWindow(); cardReference.selectAll(); }
            else if (selected == PaymentMethod.TRANSFER) { transferReference.requestFocusInWindow(); transferReference.selectAll(); }
            else { cashAmount.requestFocusInWindow(); cashAmount.selectAll(); }
        });
    }

    private JButton selectedPaymentButton() {
        return switch ((PaymentMethod) method.getSelectedItem()) {
            case CASH -> cashButton;
            case CARD -> cardButton;
            case TRANSFER -> transferButton;
            case SPLIT -> splitButton;
        };
    }

    private void showPaymentField(java.awt.Component component, boolean visible) {
        List<java.awt.Component> row = labelledFields.get(component);
        if (row != null) row.forEach(value -> value.setVisible(visible));
    }

    private void loadChoices() {

        try {

            customers.ensureWalkInCustomer();
            customer.removeAllItems();
            allCustomers.clear();
            allCustomers.addAll(customers.list());
            allCustomers.forEach(customer::addItem);

            selectWalkInCustomer();

            allProducts.clear();
            productsByBarcode.clear();

            products.search("")
                    .stream()
                    .filter(Product::active)
                    .sorted((p1, p2) ->
                            p1.name()
                                    .compareToIgnoreCase(p2.name()))
                    .forEach(found -> {
                        allProducts.add(found);
                        if (found.barcode() != null && !found.barcode().isBlank()) {
                            productsByBarcode.put(found.barcode().trim(), found);
                        }
                    });

            resetProductBox();

        } catch (Exception exception) {

            showError(exception);
        }
    }

    // Search products by first letters
    private void filterProducts(String searchText) {

        // Matching ignores surrounding spaces, but the editor keeps exactly what the cashier typed,
        // so a space typed between words ("coca cola") is never removed while the list refreshes.
        String typed = searchText == null ? "" : searchText;
        String text = typed.trim();

        updatingProductCombo = true;

        try {

            product.removeAllItems();

            for (Product p : allProducts) {

                if (text.isEmpty()
                        || p.name().toLowerCase(java.util.Locale.ROOT).contains(text.toLowerCase(java.util.Locale.ROOT))
                        || (p.barcode() != null && p.barcode().contains(text))) {

                    product.addItem(p);
                }
            }

            product.setSelectedIndex(-1);

            productEditor.setText(typed);
            productEditor.setCaretPosition(typed.length());

        } finally {

            updatingProductCombo = false;
        }

        refreshProductPopup(text);
    }

    /**
     * A visible combo popup keeps the height of the list it opened with. Resize it to the current match
     * count so a narrower search never leaves an empty white block under the results.
     */
    private void refreshProductPopup(String text) {
        int rows = Math.min(Math.max(product.getItemCount(), 1), 10);
        product.setMaximumRowCount(rows);
        javax.swing.JList<?> list = productPopupList();
        if (list != null) list.setVisibleRowCount(rows);
        if (product.isPopupVisible()) product.hidePopup();
        if (!text.isEmpty() && product.getItemCount() > 0 && product.isShowing()) product.showPopup();
    }

    private void resetProductBox() {

        updatingProductCombo = true;

        try {

            product.removeAllItems();

            for (Product p : allProducts) {
                product.addItem(p);
            }

            product.setSelectedIndex(-1);

            if (productEditor != null) {
                productEditor.setText("");
            }

            barcode.setText("");

        } finally {

            updatingProductCombo = false;
        }
    }

    /** Filters the cached active-product list from the live editable-combo text field. */
    private void installProductSearchTypingFilter() {
        productEditor.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent event) { filterFromEditor(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent event) { filterFromEditor(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent event) { filterFromEditor(); }

            private void filterFromEditor() {
                if (updatingProductCombo || committingProductSelection || filteringProductSearch
                        || productFilterUpdateQueued) return;
                productFilterUpdateQueued = true;
                javax.swing.SwingUtilities.invokeLater(() -> {
                    productFilterUpdateQueued = false;
                    if (updatingProductCombo || committingProductSelection) return;
                    filteringProductSearch = true;
                    try {
                        filterProducts(productEditor.getText());
                    } finally {
                        filteringProductSearch = false;
                    }
                });
            }
        });
    }

    /**
     * Editable JComboBox normally mirrors popup-list navigation into its selected item.
     * Cashier navigation is intentionally preview-only: only Enter or mouse activates a product.
     */
    private void installProductPopupKeyboardNavigation() {
        bindProductPopupKey(KeyEvent.VK_DOWN, "product-popup-next", () -> moveProductPopupHighlight(1));
        bindProductPopupKey(KeyEvent.VK_UP, "product-popup-previous", () -> moveProductPopupHighlight(-1));
        bindProductPopupKey(KeyEvent.VK_ESCAPE, "product-popup-cancel", this::cancelProductPopupSelection);
        javax.swing.Action commitHighlightedProduct = new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) {
                commitProductPopupSelection();
            }
        };
        bindProductPopupEnterKey(product, javax.swing.JComponent.WHEN_FOCUSED, commitHighlightedProduct);
        bindProductPopupEnterKey(product, javax.swing.JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT,
                commitHighlightedProduct);
        JTextField liveProductEditor = (JTextField) product.getEditor().getEditorComponent();
        bindProductPopupEditorNavigationKey(liveProductEditor, KeyEvent.VK_DOWN, "product-popup-next");
        bindProductPopupEditorNavigationKey(liveProductEditor, KeyEvent.VK_UP, "product-popup-previous");
        bindProductPopupEnterKey(liveProductEditor, javax.swing.JComponent.WHEN_FOCUSED,
                commitHighlightedProduct);
        product.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            @Override public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent event) {
                productPopupHighlightedIndex = -1;
                javax.swing.JList<?> list = productPopupList();
                if (list != null) list.clearSelection();
            }

            @Override public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent event) {
                productPopupHighlightedIndex = -1;
            }

            @Override public void popupMenuCanceled(javax.swing.event.PopupMenuEvent event) {
                productPopupHighlightedIndex = -1;
            }
        });
        javax.swing.JList<?> list = productPopupList();
        if (list != null) {
            bindProductPopupEnterKey(list, javax.swing.JComponent.WHEN_FOCUSED,
                    commitHighlightedProduct);
        }
        if (list != null && !productPopupMouseHandlerInstalled) {
            productPopupMouseHandlerInstalled = true;
            list.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseReleased(java.awt.event.MouseEvent event) {
                    if (!javax.swing.SwingUtilities.isLeftMouseButton(event)) return;
                    int index = list.locationToIndex(event.getPoint());
                    java.awt.Rectangle cell = list.getCellBounds(index, index);
                    if (index < 0 || cell == null || !cell.contains(event.getPoint())
                            || index >= product.getItemCount()) return;
                    Product clicked = product.getItemAt(index);
                    javax.swing.SwingUtilities.invokeLater(() -> commitProductSearchSelection(clicked));
                }
            });
        }
    }

    private void bindProductPopupKey(int keyCode, String actionName, Runnable action) {
        javax.swing.KeyStroke keyStroke = javax.swing.KeyStroke.getKeyStroke(keyCode, 0);
        product.getInputMap(javax.swing.JComponent.WHEN_FOCUSED).put(keyStroke, actionName);
        product.getInputMap(javax.swing.JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(keyStroke, actionName);
        product.getActionMap().put(actionName, new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) { action.run(); }
        });
    }

    /** Installs the one shared Enter action wherever an editable combo popup can hold focus. */
    private void bindProductPopupEnterKey(javax.swing.JComponent component, int condition,
            javax.swing.Action commitHighlightedProduct) {
        component.getInputMap(condition).put(javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
                "product-popup-commit");
        component.getActionMap().put("product-popup-commit", commitHighlightedProduct);
    }

    /** The live editable field consumes arrow keys before JComboBox ancestor bindings can see them. */
    private void bindProductPopupEditorNavigationKey(JTextField editor, int keyCode, String actionName) {
        editor.getInputMap(javax.swing.JComponent.WHEN_FOCUSED)
                .put(javax.swing.KeyStroke.getKeyStroke(keyCode, 0), actionName);
        editor.getActionMap().put(actionName, product.getActionMap().get(actionName));
    }

    /** Quantity-field +/- is local numeric editing and must not invoke cart quantity shortcuts. */
    private void installQuantityFieldIncrementKeys() {
        bindQuantityFieldIncrementKey(javax.swing.KeyStroke.getKeyStroke('+'), "quantity-increase", 1);
        bindQuantityFieldIncrementKey(javax.swing.KeyStroke.getKeyStroke('-'), "quantity-decrease", -1);
    }

    private void bindQuantityFieldIncrementKey(javax.swing.KeyStroke keyStroke, String actionName, int amount) {
        quantity.getInputMap(javax.swing.JComponent.WHEN_FOCUSED).put(keyStroke, actionName);
        quantity.getActionMap().put(actionName, new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) {
                adjustQuantityField(amount);
            }
        });
    }

    private void adjustQuantityField(int amount) {
        BigDecimal current;
        try {
            current = new BigDecimal(quantity.getText().trim());
            if (current.compareTo(BigDecimal.ONE) < 0) current = BigDecimal.ONE;
        } catch (Exception exception) {
            current = BigDecimal.ONE;
        }
        BigDecimal adjusted = current.add(BigDecimal.valueOf(amount));
        if (adjusted.compareTo(BigDecimal.ONE) < 0) adjusted = BigDecimal.ONE;
        quantity.setText(adjusted.stripTrailingZeros().toPlainString());
        quantity.selectAll();
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
        if (productPopupHighlightedIndex >= 0
                && productPopupHighlightedIndex < product.getItemCount()) {
            commitProductSearchSelection(product.getItemAt(productPopupHighlightedIndex));
        }
        productPopupHighlightedIndex = -1;
        product.hidePopup();
    }

    /** The only real Product Search commit path, shared by Enter and a popup-list mouse click. */
    private void commitProductSearchSelection(Product selected) {
        committingProductSelection = true;
        try {
            product.setSelectedItem(selected);
        } finally {
            committingProductSelection = false;
        }
        product.hidePopup();
    }

    private void applyCommittedProductSearchSelection(Product selected) {
        confirmedProductSearchText = selected.name();
        confirmedProductSearch = selected;
        ((JTextField) product.getEditor().getEditorComponent()).setText(selected.name());
        barcode.setText(selected.barcode());
        quantity.setText("1");

        javax.swing.SwingUtilities.invokeLater(() -> {
            quantity.requestFocusInWindow();
            quantity.selectAll();
        });
    }

    private void cancelProductPopupSelection() {
        if (!product.isPopupVisible()) return;
        product.hidePopup();
        productPopupHighlightedIndex = -1;
        restoreConfirmedProductSelection();
    }

    private void restoreConfirmedProductSelection() {
        updatingProductCombo = true;
        try {
            if (confirmedProductSearch == null) product.setSelectedIndex(-1);
            else product.setSelectedItem(confirmedProductSearch);
            productEditor.setText(confirmedProductSearchText);
            productEditor.setCaretPosition(productEditor.getText().length());
        } finally {
            updatingProductCombo = false;
        }
    }

    private javax.swing.JList<?> productPopupList() {
        javax.accessibility.Accessible child = product.getUI().getAccessibleChild(product, 0);
        return child instanceof javax.swing.plaf.basic.BasicComboPopup popup ? popup.getList() : null;
    }

    /** Installs lookup handling on the final editable Customer component, never on the combo itself. */
    private void installCustomerEditorLookup() {
        Component runtimeEditor = customer.getEditor().getEditorComponent();
        if (!(runtimeEditor instanceof JTextField finalEditor)) return;

        customerEditor = finalEditor;
        if (Boolean.TRUE.equals(customerEditor.getClientProperty(CUSTOMER_EDITOR_LOOKUP_INSTALLED))) return;
        customerEditor.putClientProperty(CUSTOMER_EDITOR_LOOKUP_INSTALLED, Boolean.TRUE);
        customerEditor.addActionListener(event -> resolveCustomerInput(customerEditor.getText()));
        customerEditor.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent event) { queueCustomerFilter(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent event) { queueCustomerFilter(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent event) { queueCustomerFilter(); }

            private void queueCustomerFilter() {
                if (suppressCustomerPopup || filteringCustomerSearch || customerFilterUpdateQueued) return;
                customerFilterUpdateQueued = true;
                javax.swing.SwingUtilities.invokeLater(() -> {
                    customerFilterUpdateQueued = false;
                    if (suppressCustomerPopup) return;
                    filteringCustomerSearch = true;
                    try { filterCustomers(customerEditor.getText()); }
                    finally { filteringCustomerSearch = false; }
                });
            }
        });
    }

    /** Customer data is loaded once when checkout opens; typing only filters this in-memory list. */
    private void filterCustomers(String searchText) {
        String rawText = searchText == null ? "" : searchText.trim();
        // Card scans are resolved atomically on Enter against loyalty_cards, never from a filtered combo row.
        if (isLoyaltyCardLookup(rawText)) {
            // Earlier characters may have opened the name popup before the complete ITHIRI prefix
            // arrived from a scanner. A card value must never leave that unrelated list visible.
            customer.hidePopup();
            return;
        }
        String text = rawText.toLowerCase(java.util.Locale.ROOT);
        List<CustomerChoice> matches = allCustomers.stream().filter(choice -> choice.matchesLookup(text)).toList();
        // Keep the editor text untouched when no saved customer matches. This lets a
        // cashier finish typing a name instead of the combo box clearing their input.
        if (!text.isEmpty() && matches.isEmpty()) {
            customer.hidePopup();
            return;
        }
        customer.removeAllItems();
        matches.forEach(customer::addItem);
        updateCustomerAutocompletePopupHeight();
        customer.setSelectedIndex(-1);
        customerEditor.setText(searchText == null ? "" : searchText);
        customerEditor.setCaretPosition(customerEditor.getText().length());
        if (!suppressCustomerPopup && !text.isEmpty() && customer.isShowing()) {
            if (customer.getItemCount() == 0) {
                customer.hidePopup();
            } else {
                // A visible BasicComboPopup keeps its old bounds after a model change; reopening it
                // makes the one-to-eight-row limit above take effect immediately.
                if (customer.isPopupVisible()) customer.hidePopup();
                customer.showPopup();
            }
        }
    }

    /** Commits an exact customer name, card scan, or formatted phone number when the cashier presses Enter. */
    private void resolveCustomerInput(String rawInput) {
        String typed = rawInput == null ? "" : rawInput.trim();
        if (typed.isEmpty()) return;
        CustomerChoice exactName = exactCustomerNameChoice(allCustomers, typed);
        if (exactName != null) {
            selectCustomerChoice(exactName);
            return;
        }
        if (isLoyaltyCardLookup(typed)) {
            resolveLoyaltyCardLookup(typed);
            return;
        }
        if (!isPhoneLookup(typed)) {
            showInlineNotice("Customer not found.", UITheme.RED);
            return;
        }

        CustomerChoice exact = allCustomers.stream().filter(choice -> choice.isExactPhoneLookup(typed)).findFirst().orElse(null);
        if (exact == null) {
            showInlineNotice("Customer not found.", UITheme.RED);
            return;
        }
        selectCustomerChoice(exact);
    }

    private boolean isLoyaltyCardLookup(String text) {
        return text != null && text.trim().toUpperCase(java.util.Locale.ROOT).startsWith("ITHIRI");
    }

    private boolean isPhoneLookup(String text) {
        return text != null && text.matches(".*\\d.*") && text.matches("[0-9\\s()\\-]+");
    }

    /** Card status must be checked from loyalty_cards so AVAILABLE cards never look like missing customers. */
    private void resolveLoyaltyCardLookup(String enteredCardCode) {
        try {
            String normalizedCardCode = enteredCardCode.trim().toUpperCase(java.util.Locale.ROOT).replaceAll("\\s+", "");
            java.util.Optional<LoyaltyCardService.CardLookup> result = loyaltyCards.lookupCard(normalizedCardCode);
            if (result.isEmpty()) {
                showInlineNotice("Customer not found.", UITheme.RED);
                return;
            }
            LoyaltyCardService.CardLookup card = result.get();
            String status = card.status() == null ? "" : card.status().trim().toUpperCase(java.util.Locale.ROOT);
            if ("BLOCKED".equals(status)) {
                showInlineNotice("Loyalty card is blocked.", UITheme.RED);
                return;
            }
            if ("AVAILABLE".equals(status)) {
                showInlineNotice("This loyalty card has not been assigned to a customer.", UITheme.RED);
                return;
            }
            if (!"ASSIGNED".equals(status) || card.customerId() == null) {
                showInlineNotice("Customer not found.", UITheme.RED);
                return;
            }
            CustomerChoice linkedCustomer = allCustomers.stream()
                    .filter(choice -> choice.id() == card.customerId())
                    .findFirst().orElse(null);
            if (linkedCustomer == null) {
                showInlineNotice("Customer not found.", UITheme.RED);
                return;
            }
            selectCustomerChoice(linkedCustomer);
        } catch (Exception exception) {
            showInlineNotice("Customer not found.", UITheme.RED);
        }
    }

    /** Restores the full model before committing a filtered phone/card result to the existing combo. */
    private void selectCustomerChoice(CustomerChoice choice) {
        if (customer.getItemCount() != allCustomers.size()) {
            customer.removeAllItems();
            allCustomers.forEach(customer::addItem);
        }
        updateCustomerAutocompletePopupHeight();
        CustomerChoice comboChoice = null;
        for (int index = 0; index < customer.getItemCount(); index++) {
            CustomerChoice item = customer.getItemAt(index);
            if (item.id() == choice.id()) {
                comboChoice = item;
                break;
            }
        }
        if (comboChoice == null) {
            showInlineNotice("Customer not found.", UITheme.RED);
            return;
        }
        suppressCustomerPopup = true;
        try {
            clearAppliedRedemption();
            customer.setSelectedItem(comboChoice);
            customerEditor.setText(comboChoice.name());
            refreshLoyaltyCustomer();
            if (comboChoice.loyaltyCustomer()) showInlineNotice("Loyalty points: " + comboChoice.loyaltyPoints());
        } finally {
            javax.swing.SwingUtilities.invokeLater(() -> {
                customer.setPopupVisible(false);
                suppressCustomerPopup = false;
            });
        }
    }

    private void selectWalkInCustomer() {
        CustomerChoice walkInCustomer = walkInCustomerChoice(allCustomers);
        if (walkInCustomer == null) {
            customer.setSelectedItem(null);
            return;
        }
        // Selecting an item changes the editable combo's document.  Suppress its
        // type-ahead filter until that programmatic change has settled; otherwise
        // the filter clears the selected item and the next sale has no customer.
        suppressCustomerPopup = true;
        try {
            customer.removeAllItems();
            allCustomers.forEach(customer::addItem);
            updateCustomerAutocompletePopupHeight();
            customer.setSelectedItem(walkInCustomer);
            // Do not rely on a look-and-feel's editable-combo synchronization here.
            // The visible value is part of the checkout default, not merely a popup selection.
            customerEditor.setText(walkInCustomer.name());
            customerEditor.setCaretPosition(customerEditor.getText().length());
        } finally {
            javax.swing.SwingUtilities.invokeLater(() -> {
                customer.setPopupVisible(false);
                suppressCustomerPopup = false;
            });
        }
    }

    /** Returns only the seeded default customer; another alphabetical customer is never a substitute. */
    static CustomerChoice walkInCustomerChoice(List<CustomerChoice> choices) {
        if (choices == null) return null;
        return choices.stream()
                .filter(choice -> choice.name() != null && "walk-in customer".equalsIgnoreCase(choice.name().trim()))
                .findFirst()
                .orElse(null);
    }

    /** Customer text in an editable combo is not a selection until this exact lookup succeeds. */
    static CustomerChoice exactCustomerNameChoice(List<CustomerChoice> choices, String enteredName) {
        if (choices == null || enteredName == null || enteredName.isBlank()) return null;
        String normalizedName = enteredName.trim();
        return choices.stream()
                .filter(choice -> choice.name() != null && normalizedName.equalsIgnoreCase(choice.name().trim()))
                .findFirst()
                .orElse(null);
    }

    // Barcode scanned
    private void addBarcodeProduct() {
        try {
            String code = barcode.getText().trim();
            if (code.isEmpty()) return;
            Product found = productsByBarcode.get(code);
            if (found == null) {
                showInlineNotice("Product not found: " + code, UITheme.RED);
                barcode.selectAll(); barcode.requestFocusInWindow();
                return;
            }
            // A scan only selects the product and moves focus to Quantity (default 1).
            // The cashier presses Enter in Quantity to add the line to the cart.
            resetProductBox();
            commitProductSearchSelection(found);
            barcode.setText(code);
            if (found.stockQuantity().compareTo(found.minimumStock()) <= 0) showInlineNotice("Low stock: " + found.name() + " - enter quantity", new java.awt.Color(187, 126, 18));
            else showInlineNotice(found.name() + " - enter quantity, then press Enter", UITheme.GREEN);
        } catch (Exception exception) {
            showInlineNotice(exception.getMessage(), UITheme.RED);
            barcode.selectAll(); barcode.requestFocusInWindow();
        }
    }

    // Add selected product to cart
    private void addSelectedProduct() {
        try {
            Object selectedObject = product.getSelectedItem();
            if (!(selectedObject instanceof Product selected)) throw new IllegalArgumentException("Select or scan a product.");
            CartItem added = addItem(selected);
            if (added == null) { focus(barcode); return; }
            barcode.setText(""); quantity.setText("1"); resetProductBox(); barcode.requestFocusInWindow();
        } catch (Exception exception) {
            showError(exception);
            quantity.requestFocusInWindow();
            quantity.selectAll();
        }
    }

    private CartItem addItem(Product selected) throws Exception {
        return addItem(selected, Money.parse(quantity.getText(), "quantity"));
    }

    /** Product-search fallback adds a product; after a scan, Enter on quantity edits the selected cart line exactly. */
    private void applyQuantityOrAdd() {
        try {
            if (product.getSelectedItem() instanceof Product) { addSelectedProduct(); return; }
            int selectedRow = table.getSelectedRow();
            int row = selectedRow < 0 ? -1 : table.convertRowIndexToModel(selectedRow);
            if (row < 0) throw new IllegalArgumentException("Scan or select a product first.");
            BigDecimal requested = Money.parse(quantity.getText(), "quantity");
            if (requested.signum() <= 0) throw new IllegalArgumentException("Quantity must be at least 1.");
            CartItem item = cart.get(row);
            products.findActiveById(item.productId()).orElseThrow(() -> new IllegalArgumentException("Product is no longer available."));
            replanCartLine(item, requested);
            refreshCart(); table.setRowSelectionInterval(selectedRow, selectedRow); focus(barcode);
        } catch (Exception exception) { showInlineNotice(exception.getMessage()); focus(quantity); }
    }

    /** Resolves every new cart total before changing the in-memory cart. */
    private CartItem addItem(Product selected, BigDecimal requested) throws Exception {
        if (requested.signum() <= 0) throw new IllegalArgumentException("Quantity must be greater than zero.");

        CartItem preferred = preferredCartLine(selected.id());
        if (preferred != null) {
            replanCartLine(preferred, preferred.quantity().add(requested));
            refreshCart();
            return preferred;
        }

        BatchResolution resolution = batchResolver.resolve(selected.id(), requested);
        if (resolution.status() == BatchResolutionStatus.NO_STOCK) throw withExpiredStockNote(insufficientStock(resolution), selected.id());
        if (resolution.status() == BatchResolutionStatus.MULTI_PRICE_SELECTION_REQUIRED) {
            BigDecimal selectedPrice = selectSellingPrice(effectiveBatchAvailability(resolution.sellableBatches(), reservedByBatch(null)));
            if (selectedPrice == null) return null;
            CartItem matchingPriceLine = cartLineAtPrice(selected.id(), selectedPrice);
            BigDecimal totalQuantity = requested.add(matchingPriceLine == null ? BigDecimal.ZERO : matchingPriceLine.quantity());
            BatchResolution selectedPlan = resolveEffectiveForPrice(selected.id(), totalQuantity, selectedPrice, matchingPriceLine);
            if (selectedPlan.status() == BatchResolutionStatus.NO_STOCK) throw insufficientAtPrice(selectedPlan, selectedPrice);
            CartItem added = applyBatchPlan(selected, matchingPriceLine, totalQuantity, selectedPlan);
            refreshCart();
            return added;
        }

        BigDecimal price = resolution.allocations().get(0).sellingPrice();
        CartItem matchingPriceLine = cartLineAtPrice(selected.id(), price);
        BigDecimal totalQuantity = requested.add(matchingPriceLine == null ? BigDecimal.ZERO : matchingPriceLine.quantity());
        BatchResolution plan = resolveEffectiveForPrice(selected.id(), totalQuantity, price, matchingPriceLine);
        if (plan.status() == BatchResolutionStatus.NO_STOCK) throw insufficientAtPrice(plan, price);
        CartItem added = applyBatchPlan(selected, matchingPriceLine, totalQuantity, plan);
        refreshCart();
        return added;
    }

    /** Re-resolves the full line quantity at its already chosen price; it never changes price silently. */
    private void replanCartLine(CartItem item, BigDecimal newQuantity) throws Exception {
        BatchResolution plan = resolveEffectiveForPrice(item.productId(), newQuantity, item.ourPrice(), item);
        if (plan.status() == BatchResolutionStatus.NO_STOCK) throw insufficientAtPrice(plan, item.ourPrice());
        item.updateQuantityAndBatchAllocations(newQuantity, plan.allocations());
    }

    private CartItem applyBatchPlan(Product product, CartItem existing, BigDecimal quantity, BatchResolution plan) {
        if (plan.allocations().isEmpty()) throw new IllegalStateException("No batch allocation was returned for the selected price.");
        BatchAllocation first = plan.allocations().get(0); // FEFO-first retail value is the deterministic sale-line retail price.
        if (existing == null) {
            CartItem item = new CartItem(product, quantity, first.sellingPrice(), first.retailPrice(), plan.allocations());
            cart.add(item);
            return item;
        }
        existing.updateQuantityAndBatchAllocations(quantity, plan.allocations());
        return existing;
    }

    private CartItem preferredCartLine(int productId) {
        int selectedRow = table.getSelectedRow();
        if (selectedRow >= 0) {
            CartItem selected = cart.get(table.convertRowIndexToModel(selectedRow));
            if (selected.productId() == productId) return selected;
        }
        return null;
    }

    private CartItem cartLineAtPrice(int productId, BigDecimal sellingPrice) {
        return cart.stream().filter(item -> item.productId() == productId && item.ourPrice().compareTo(sellingPrice) == 0)
                .findFirst().orElse(null);
    }

    /** Uses fresh database availability, less all in-memory allocations except an edited line being replaced. */
    private BatchResolution resolveEffectiveForPrice(int productId, BigDecimal requestedQuantity, BigDecimal sellingPrice,
            CartItem excludedLine) throws java.sql.SQLException {
        BatchResolution current = batchResolver.resolveForPrice(productId, requestedQuantity, sellingPrice);
        List<BatchStock> effective = effectiveBatchAvailability(current.sellableBatches(), reservedByBatch(excludedLine));
        BigDecimal available = effective.stream().map(BatchStock::quantityAvailable).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (available.compareTo(requestedQuantity) < 0)
            return new BatchResolution(BatchResolutionStatus.NO_STOCK, requestedQuantity, available, List.of(), effective);

        List<BatchAllocation> allocations = new ArrayList<>();
        BigDecimal remaining = requestedQuantity;
        for (BatchStock batch : effective) {
            if (remaining.signum() == 0) break;
            BigDecimal allocated = batch.quantityAvailable().min(remaining);
            if (allocated.signum() > 0) {
                allocations.add(new BatchAllocation(batch.batchId(), allocated, batch.costPrice(), batch.sellingPrice(),
                        batch.retailPrice(), batch.expiryDate()));
                remaining = remaining.subtract(allocated);
            }
        }
        return new BatchResolution(BatchResolutionStatus.SINGLE_PRICE_AUTO, requestedQuantity, available, allocations, effective);
    }

    private Map<Integer, BigDecimal> reservedByBatch(CartItem excludedLine) {
        Map<Integer, BigDecimal> reserved = new LinkedHashMap<>();
        for (CartItem item : cart) {
            if (item == excludedLine) continue;
            for (BatchAllocation allocation : item.batchAllocations()) {
                reserved.merge(allocation.batchId(), allocation.quantity(), BigDecimal::add);
            }
        }
        return reserved;
    }

    static List<BatchStock> effectiveBatchAvailability(List<BatchStock> batches, Map<Integer, BigDecimal> reservedByBatch) {
        List<BatchStock> effective = new ArrayList<>();
        for (BatchStock batch : batches) {
            BigDecimal reserved = reservedByBatch == null ? BigDecimal.ZERO
                    : reservedByBatch.getOrDefault(batch.batchId(), BigDecimal.ZERO);
            BigDecimal remaining = batch.quantityAvailable().subtract(reserved).max(BigDecimal.ZERO);
            effective.add(new BatchStock(batch.batchId(), batch.productId(), batch.costPrice(), batch.sellingPrice(),
                    batch.retailPrice(), batch.expiryDate(), remaining));
        }
        return List.copyOf(effective);
    }

    private IllegalArgumentException insufficientStock(BatchResolution result) {
        return new IllegalArgumentException("Insufficient sellable stock.\nRequested: " + quantityText(result.requestedQuantity())
                + "\nAvailable: " + quantityText(result.availableQuantity()));
    }

    /** Expired batches are excluded from sale, so say so instead of showing only a bare zero-stock message. */
    private IllegalArgumentException withExpiredStockNote(IllegalArgumentException failure, int productId) {
        try {
            BigDecimal expired = products.expiredStockQuantity(productId);
            if (expired.signum() <= 0) return failure;
            return new IllegalArgumentException(failure.getMessage() + "\n\n" + quantityText(expired)
                    + " unit(s) are past their expiry date and cannot be sold. Check Item Management.");
        } catch (Exception ignored) {
            return failure;
        }
    }

    private IllegalArgumentException insufficientAtPrice(BatchResolution result, BigDecimal sellingPrice) {
        return new IllegalArgumentException("Only " + quantityText(result.availableQuantity()) + " units are available at " + UITheme.money(sellingPrice) + ".");
    }

    private String quantityText(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    /** Compact modal selector used only when the barcode cannot identify a unique selling-price batch group. */
    private BigDecimal selectSellingPrice(List<BatchStock> sellableBatches) {
        List<PriceOption> options = priceOptions(sellableBatches);
        if (options.isEmpty()) return null;
        DefaultTableModel optionsModel = new DefaultTableModel(new String[]{"Our Price", "Retail Price", "Nearest Expiry", "Available Qty"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        for (PriceOption option : options) optionsModel.addRow(new Object[]{UITheme.money(option.sellingPrice),
            option.retailPrice == null ? "" : UITheme.money(option.retailPrice),
            option.nearestExpiry == null ? "" : option.nearestExpiry.toString(), quantityText(option.availableQuantity)});

        JTable optionsTable = new JTable(optionsModel);
        optionsTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        optionsTable.setRowSelectionInterval(0, 0);
        JDialog dialog = new JDialog(this, "Select Stock Price", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        JPanel content = UITheme.card(new BorderLayout(0, 10));
        content.setBorder(javax.swing.BorderFactory.createEmptyBorder(14, 16, 14, 16));
        content.add(new JLabel("Choose the price on the physical item."), BorderLayout.NORTH);
        content.add(new JScrollPane(optionsTable), BorderLayout.CENTER);
        JButton choose = new JButton("Choose");
        JButton cancel = new JButton("Cancel");
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0)); actions.setOpaque(false); actions.add(cancel); actions.add(choose);
        content.add(actions, BorderLayout.SOUTH);
        final BigDecimal[] chosen = new BigDecimal[1];
        Runnable select = () -> {
            int row = optionsTable.getSelectedRow();
            if (row < 0) return;
            PriceOption option = options.get(row);
            if (option.availableQuantity.signum() <= 0) {
                JOptionPane.showMessageDialog(dialog,
                        "No remaining stock is available at " + UITheme.money(option.sellingPrice) + " for this cart.",
                        "Select Stock Price", JOptionPane.WARNING_MESSAGE);
                optionsTable.requestFocusInWindow();
                return;
            }
            chosen[0] = option.sellingPrice;
            dialog.dispose();
        };
        Runnable cancelSelection = dialog::dispose;
        choose.addActionListener(event -> select.run());
        cancel.addActionListener(event -> cancelSelection.run());
        optionsTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent event) { if (event.getClickCount() == 2) select.run(); }
        });
        dialog.setContentPane(content); dialog.setSize(560, 250); dialog.setLocationRelativeTo(this);
        dialog.getRootPane().setDefaultButton(choose);
        dialog.getRootPane().getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancelPriceSelection");
        dialog.getRootPane().getActionMap().put("cancelPriceSelection", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) { cancelSelection.run(); }
        });
        dialog.getRootPane().getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "selectPrice");
        dialog.getRootPane().getActionMap().put("selectPrice", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) { select.run(); }
        });
        installPriceDialogTableKeys(optionsTable, select);
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowOpened(java.awt.event.WindowEvent event) {
                optionsTable.setRowSelectionInterval(0, 0);
                optionsTable.requestFocusInWindow();
            }
        });
        dialog.setVisible(true);
        return chosen[0];
    }

    /** Keeps price selection entirely keyboard driven without changing any checkout shortcuts. */
    private void installPriceDialogTableKeys(JTable optionsTable, Runnable chooseSelection) {
        javax.swing.InputMap keys = optionsTable.getInputMap(javax.swing.JComponent.WHEN_FOCUSED);
        keys.put(javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "priceSelectionUp");
        keys.put(javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "priceSelectionDown");
        keys.put(javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "priceSelectionChoose");
        optionsTable.getActionMap().put("priceSelectionUp", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) { movePriceSelection(optionsTable, -1); }
        });
        optionsTable.getActionMap().put("priceSelectionDown", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) { movePriceSelection(optionsTable, 1); }
        });
        optionsTable.getActionMap().put("priceSelectionChoose", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) { chooseSelection.run(); }
        });
    }

    private void movePriceSelection(JTable optionsTable, int direction) {
        int current = optionsTable.getSelectedRow();
        int next = Math.max(0, Math.min(optionsTable.getRowCount() - 1, (current < 0 ? 0 : current) + direction));
        optionsTable.setRowSelectionInterval(next, next);
        optionsTable.scrollRectToVisible(optionsTable.getCellRect(next, 0, true));
    }

    private List<PriceOption> priceOptions(List<BatchStock> sellableBatches) {
        List<PriceOption> options = new ArrayList<>();
        for (BatchStock batch : sellableBatches) {
            PriceOption option = options.stream().filter(candidate -> candidate.sellingPrice.compareTo(batch.sellingPrice()) == 0)
                    .findFirst().orElse(null);
            if (option == null) options.add(new PriceOption(batch)); else option.add(batch);
        }
        return options;
    }

    private void changeQuantity(int amount) {
        try {
            int selectedRow = table.getSelectedRow();
            if (selectedRow >= 0) {
                int modelRow = table.convertRowIndexToModel(selectedRow);
                CartItem item = cart.get(modelRow);
                if (amount > 0) {
                    replanCartLine(item, item.quantity().add(BigDecimal.ONE));
                }
                else if (item.quantity().compareTo(BigDecimal.ONE) > 0) replanCartLine(item, item.quantity().subtract(BigDecimal.ONE));
                refreshCart(); table.setRowSelectionInterval(selectedRow, selectedRow); table.requestFocusInWindow();
                return;
            }
            BigDecimal current;

            if (quantity.getText().isBlank()) {

                current = BigDecimal.ONE;

            } else {

                current =
                        new BigDecimal(
                                quantity.getText().trim()
                        );
            }

            BigDecimal newQty =
                    current.add(
                            BigDecimal.valueOf(amount)
                    );

            if (newQty.compareTo(BigDecimal.ONE) < 0) {
                newQty = BigDecimal.ONE;
            }

            quantity.setText(
                    newQty
                            .stripTrailingZeros()
                            .toPlainString()
            );

            quantity.requestFocusInWindow();
            quantity.selectAll();
        } catch (Exception exception) {
            showInlineNotice(exception.getMessage());
            if (table.getSelectedRow() >= 0) table.requestFocusInWindow();
            else { quantity.setText("1"); quantity.requestFocusInWindow(); quantity.selectAll(); }
        }
    }

    private void removeSelected() {

        int row = table.getSelectedRow();

        if (row < 0) {

            showError(
                    new IllegalArgumentException(
                            "Select a cart item to remove."
                    )
            );

            return;
        }

        cart.remove(row);

        refreshCart();

        barcode.requestFocusInWindow();
    }

    private void refreshCart() {
        reevaluateCartPromotions();
        tableModel.setRowCount(0);
        for (CartItem item : cart) {
            tableModel.addRow(
                    new Object[]{
                        item.productName(),
                        item.quantity(),
                        item.ourPrice(),
                        item.promotionLabel(),
                        item.finalUnitPrice(),
                        item.subtotal()
                    }
            );
        }
        refreshTotals();
    }

    /** Admins add promotions in the full form; cashiers see what is currently running. The cart re-prices afterwards. */
    private void openPromotionsFromSale() {
        try {
            if (Authorization.isAllowed(Authorization.Permission.ADMINISTRATION)) {
                PromotionForm form = new PromotionForm();
                form.addWindowListener(new java.awt.event.WindowAdapter() {
                    @Override public void windowClosed(java.awt.event.WindowEvent event) {
                        try { if (!cart.isEmpty()) refreshCart(); } catch (Exception exception) { showInlineNotice(exception.getMessage()); }
                        focus(barcode);
                    }
                });
                form.setVisible(true);
                return;
            }
            List<PromotionService.ActivePromotion> active = promotions.listActive(java.time.LocalDateTime.now());
            DefaultTableModel promotionModel = new DefaultTableModel(new String[]{"Promotion", "Product", "Discount", "Applies To", "Ends"}, 0) {
                @Override public boolean isCellEditable(int row, int column) { return false; }
            };
            java.time.format.DateTimeFormatter format = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            for (PromotionService.ActivePromotion promotion : active) {
                promotionModel.addRow(new Object[]{promotion.name(), promotion.productName(), promotion.discount(), promotion.customerScope(), promotion.endsAt().format(format)});
            }
            JTable promotionTable = new JTable(promotionModel);
            UITheme.styleTable(promotionTable);
            JScrollPane scroll = new JScrollPane(promotionTable);
            scroll.setPreferredSize(new Dimension(720, 260));
            JOptionPane.showMessageDialog(this, active.isEmpty() ? new JLabel("No promotions are running right now.") : scroll,
                    "Active Promotions", JOptionPane.PLAIN_MESSAGE);
        } catch (Exception exception) {
            showError(exception);
        }
        focus(barcode);
    }

    private void reevaluateCartPromotions() {
        try {
            CustomerChoice selected = customer.getSelectedItem() instanceof CustomerChoice choice ? choice : null;
            promotions.applyEligiblePromotions(cart, selected, java.time.LocalDateTime.now());
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to refresh product promotions.", exception);
        }
    }

    private void updatePaymentFields() {
        PaymentMethod selected = (PaymentMethod) method.getSelectedItem();
        boolean cash = selected == PaymentMethod.CASH;
        boolean card = selected == PaymentMethod.CARD;
        boolean transfer = selected == PaymentMethod.TRANSFER;
        boolean split = selected == PaymentMethod.SPLIT;
        tendered.setEnabled(cash);
        cashAmount.setEnabled(split); cardAmount.setEnabled(split); transferAmount.setEnabled(split);
        cardReference.setEnabled(card || split); cardBankName.setEnabled(card || split);
        transferReference.setEnabled(transfer || split); bankName.setEnabled(transfer || split);
        if (!cash) { tendered.setText(""); change.setText("LKR 0.00"); }
        if (!card && !split) { cardReference.setText(""); cardBankName.setText(""); }
        if (!transfer && !split) { transferReference.setText(""); bankName.setText(""); }
        showPaymentField(tendered, cash);
        showPaymentField(quickCashButtons, cash);
        showPaymentField(change, cash);
        showPaymentField(cashAmount, split);
        showPaymentField(cardAmount, split);
        showPaymentField(transferAmount, split);
        showPaymentField(remaining, split || transfer);
        showPaymentField(cardReference, card || (split && amountEntered(cardAmount)));
        showPaymentField(cardBankName, card || (split && amountEntered(cardAmount)));
        showPaymentField(transferReference, transfer || (split && amountEntered(transferAmount)));
        showPaymentField(bankName, transfer || (split && amountEntered(transferAmount)));
        showPaymentField(method, false);
        // Reuse SPLIT's proven scroll path for the CARD and TRANSFER detail forms too.
        // CASH intentionally keeps its existing non-scrolling layout.
        updateSplitPaymentLayout(card || transfer || split);
        updateCashPaymentPanelSpacing(cash);
        cashButton.setBackground(cash ? UITheme.GREEN : UITheme.BLUE);
        cardButton.setBackground(card ? UITheme.GREEN : UITheme.BLUE);
        transferButton.setBackground(transfer ? UITheme.GREEN : UITheme.BLUE);
        splitButton.setBackground(split ? UITheme.GREEN : UITheme.BLUE);
        if (transfer) remaining.setText("LKR 0.00");
        revalidate(); repaint();
        updateRemaining();
    }

    private void completeSale() {
        if (saleCompletionInProgress) {
            return;
        }
        try {
            if (cart.isEmpty()) {
                throw new IllegalArgumentException("Add at least one item before completing the sale.");
            }
            CustomerChoice resolvedCustomer = checkoutCustomerChoice(allCustomers, customer.getSelectedItem(), customerEditor.getText());
            if (resolvedCustomer == null) {
                throw new IllegalArgumentException(
                        customerEditor.getText() == null || customerEditor.getText().isBlank()
                                ? "Walk-in Customer is unavailable. Add the default customer and try again."
                                : "Select a customer."
                );
            }
            if (!resolvedCustomer.equals(selectedCustomer())) selectCustomerChoice(resolvedCustomer);
            CustomerChoice selectedCustomer = resolvedCustomer;
            // Checkout-time revalidation prevents a held or long-open cart using an expired promotion.
            refreshCart();
            BigDecimal saleDiscount = discountValue();
            PaymentMethod selectedMethod = (PaymentMethod) method.getSelectedItem();
            PaymentRequest payment = paymentRequest(selectedMethod);
            List<CartItem> saleCart = List.copyOf(cart);
            String invoiceNumber = invoice.getText();
            int requestedRedemptionPoints = appliedRedemptionPoints;
            Integer shiftId = "CASHIER".equals(Session.currentRole()) ? shifts.requireOpenShiftIdForCurrentCashier() : null;
            saleCompletionInProgress = true; completeSaleButton.setEnabled(false); completeSaleButton.setText("Processing...");
            new javax.swing.SwingWorker<CompletedSale, Void>() {
                @Override protected CompletedSale doInBackground() throws Exception {
                    SaleResult result = sales.completeSale(invoiceNumber, selectedCustomer.id(), Session.currentUserId(), saleCart, saleDiscount, payment, shiftId, requestedRedemptionPoints);
                    // Capture and validate the committed database snapshot before reset can touch checkout state.
                    try {
                        ReceiptData receipt = receipts.receiptFor(result.saleId());
                        receipt.validateForPrint();
                        return new CompletedSale(result, receipt);
                    } catch (Exception exception) {
                        throw new ReceiptCaptureException(result, exception);
                    }
                }
                @Override protected void done() {
                    try { showReceiptAndPrepareNextSale(get()); }
                    catch (Exception exception) {
                        Throwable cause = exception instanceof java.util.concurrent.ExecutionException ? exception.getCause() : exception;
                        if (cause instanceof ReceiptCaptureException captured) showCommittedSaleWithoutReceipt(captured.result());
                        else showError(cause instanceof Exception error ? error : exception);
                    }
                    finally { saleCompletionInProgress = false; completeSaleButton.setEnabled(true); completeSaleButton.setText("Complete sale"); }
                }
            }.execute();
        } catch (Exception exception) {
            showError(exception);
        }
    }

    /** Runs only after SalesService has committed the sale transaction. */
    private void showReceiptAndPrepareNextSale(CompletedSale completedSale) {
        SaleResult result = completedSale.result();
        successBanner.setText("Sale completed • " + result.invoiceNumber());
        new javax.swing.Timer(3500, event -> successBanner.setText(" ")) {{ setRepeats(false); }}.start();
        if (successfulSaleListener != null) {
            successfulSaleListener.run();
        }
        resetForNextCustomer();
        new javax.swing.SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception { ReceiptDialog.printSilently(completedSale.receipt()); return null; }
            @Override protected void done() { try { get(); } catch (Exception exception) { Dialogs.warning(CheckoutFrame.this, "Receipt Print Failed", "Sale has been saved. Use Reprint from Sales History."); } }
        }.execute();
    }

    private record CompletedSale(SaleResult result, ReceiptData receipt) { }

    /** A receipt-load failure happens after commit; it must never invite a duplicate checkout. */
    private static final class ReceiptCaptureException extends Exception {
        private final SaleResult result;
        private ReceiptCaptureException(SaleResult result, Exception cause) { super(cause); this.result = result; }
        private SaleResult result() { return result; }
    }

    private void showCommittedSaleWithoutReceipt(SaleResult result) {
        successBanner.setText("Sale completed - " + result.invoiceNumber());
        if (successfulSaleListener != null) successfulSaleListener.run();
        resetForNextCustomer();
        Dialogs.warning(this, "Receipt Print Failed", "Sale has been saved, but its receipt data could not be loaded. Use Reprint from Sales History.");
    }

    /** Restores every checkout input after a successful committed sale. */
    private void resetForNextCustomer() {
        cart.clear();
        tableModel.setRowCount(0);
        clearAppliedRedemption();
        loyaltyCustomerId = null;
        quantity.setText("1");
        discount.setText("0");
        tendered.setText("");
        cardReference.setText("");
        cardBankName.setText("");
        transferReference.setText(""); bankName.setText("");
        cashAmount.setText(""); cardAmount.setText(""); transferAmount.setText("");
        method.setSelectedItem(PaymentMethod.CASH);
        updatePaymentFields();
        change.setText("LKR 0.00");
        remaining.setText("LKR 0.00");
        invoice.setText(sales.nextInvoiceNumber());
        refreshSessionSummary();

        selectWalkInCustomer();
        refreshLoyaltyCustomer();
        resetProductBox();
        refreshCart();

        javax.swing.SwingUtilities.invokeLater(() -> {
            barcode.requestFocusInWindow();
            barcode.selectAll();
        });
    }

    /** Refreshes the compact loyalty panel from the already-selected customer without touching their database balance. */
    private void refreshLoyaltyCustomer() {
        if (loyaltySection == null) return;
        CustomerChoice selected = customer.getSelectedItem() instanceof CustomerChoice choice ? choice : null;
        boolean loyalty = selected != null && selected.loyaltyCustomer();
        boolean canRedeem = loyalty && selected.loyaltyPoints() >= 100;
        Integer nextId = loyalty ? selected.id() : null;
        if (!java.util.Objects.equals(loyaltyCustomerId, nextId)) clearAppliedRedemption();
        loyaltyCustomerId = nextId;
        loyaltySection.setVisible(loyalty);
        currentPointsDisplay.setText(loyalty ? String.valueOf(selected.loyaltyPoints()) : "0");
        if (!canRedeem) clearAppliedRedemption();
        updateLoyaltyRedemptionAvailability(canRedeem);
        // Reserve this compact line for every loyalty customer so payment controls never shift by point balance.
        loyaltyMinimumMessage.setText(loyalty && !canRedeem ? "Minimum 100 points required" : " ");
        loyaltyMinimumMessage.setVisible(loyalty);
        refreshPaymentContentSize();
        revalidate(); repaint();
    }

    /** Editable customer combo boxes can hold typed text until a real customer is chosen. */
    private CustomerChoice selectedCustomer() {
        return selectedCustomerChoice(customer.getSelectedItem());
    }

    static CustomerChoice selectedCustomerChoice(Object selected) {
        return selected instanceof CustomerChoice choice ? choice : null;
    }

    /**
     * A sale always needs a persisted customer because the sales table has a customer foreign key.
     * Empty checkout input therefore means the seeded Walk-in Customer, while non-empty unknown
     * text remains an error so a sale is never assigned to the wrong person.
     */
    static CustomerChoice checkoutCustomerChoice(List<CustomerChoice> choices, Object selected, String enteredText) {
        CustomerChoice committed = selectedCustomerChoice(selected);
        if (committed != null) return committed;
        CustomerChoice exactName = exactCustomerNameChoice(choices, enteredText);
        if (exactName != null) return exactName;
        return enteredText == null || enteredText.isBlank() ? walkInCustomerChoice(choices) : null;
    }

    private void applyLoyaltyRedemption() {
        CustomerChoice selected = customer.getSelectedItem() instanceof CustomerChoice choice ? choice : null;
        if (selected == null || !selected.loyaltyCustomer()) {
            showError(new IllegalArgumentException("Select a loyalty customer before redeeming points."));
            return;
        }
        if (selected.loyaltyPoints() < 100) {
            clearAppliedRedemption();
            showError(new IllegalArgumentException("Minimum 100 points required."));
            return;
        }
        String entered = redeemPointsField.getText().trim();
        if (!entered.matches("\\d+")) {
            showError(new IllegalArgumentException("Redeem points must be a whole, non-negative number."));
            return;
        }
        try {
            int requested = Integer.parseInt(entered);
            int permitted = LoyaltyPointCalculator.redeemedPoints(selected.loyaltyPoints(), grandTotalBeforeRedemption(), requested);
            if (permitted != requested) {
                throw new IllegalArgumentException("Maximum redeemable for this sale is " + permitted + " points.");
            }
            appliedRedemptionPoints = requested;
            loyaltyRedemptionDisplay.setText("-LKR " + redemptionValue().setScale(2));
            refreshTotals();
        } catch (NumberFormatException exception) {
            showError(new IllegalArgumentException("Redeem points must be a whole number."));
        } catch (IllegalArgumentException exception) {
            showError(exception);
        }
    }

    private void clearAppliedRedemption() {
        appliedRedemptionPoints = 0;
        redeemPointsField.setText("");
        loyaltyRedemptionDisplay.setText("-LKR 0.00");
        if (loyaltyRedemptionSummaryRow != null) loyaltyRedemptionSummaryRow.setVisible(true);
    }

    /** The disabled redemption action must be visually distinct while retaining the existing compact button size. */
    private void updateLoyaltyRedemptionAvailability(boolean canRedeem) {
        redeemPointsField.setEnabled(canRedeem);
        applyLoyaltyButton.setEnabled(canRedeem);
        UITheme.styleButton(applyLoyaltyButton, canRedeem ? UITheme.BLUE : UITheme.GREY);
    }

    private void validateAppliedRedemptionForCurrentTotal() {
        if (appliedRedemptionPoints == 0) return;
        CustomerChoice selected = customer.getSelectedItem() instanceof CustomerChoice choice ? choice : null;
        try {
            if (selected == null || !selected.loyaltyCustomer()
                    || LoyaltyPointCalculator.redeemedPoints(selected.loyaltyPoints(), grandTotalBeforeRedemption(), appliedRedemptionPoints) != appliedRedemptionPoints) {
                clearAppliedRedemption();
                showInlineNotice("Loyalty redemption was cleared because the customer or total changed.");
            }
        } catch (IllegalArgumentException exception) {
            clearAppliedRedemption();
            showInlineNotice("Loyalty redemption was cleared because the customer or total changed.");
        }
    }

    private BigDecimal grandTotalBeforeRedemption() { return CheckoutCalculator.total(cartSubtotal(), discountValue()); }
    private BigDecimal redemptionValue() { return BigDecimal.valueOf(appliedRedemptionPoints).setScale(2); }
    private BigDecimal finalPayableTotal() { return grandTotalBeforeRedemption().subtract(redemptionValue()); }

    private void updateChange() {

        try {

            BigDecimal saleTotal = finalPayableTotal();

            if (tendered.getText().isBlank()) {

                change.setText("LKR 0.00");
                return;
            }

            BigDecimal cash =
                    Money.parse(
                            tendered.getText(),
                            "tendered cash"
                    );

            BigDecimal changeAmount =
                    cash.subtract(saleTotal);

            if (changeAmount.signum() < 0) {
                changeAmount = Money.zero();
            }

            change.setText(
                    "LKR "
                            + changeAmount.setScale(2)
            );

        } catch (Exception e) {

            change.setText("LKR 0.00");
        }
    }

    private PaymentRequest paymentRequest(PaymentMethod selected) {
        if (selected == PaymentMethod.CASH) return new PaymentRequest(PaymentMethod.CASH, Money.parse(tendered.getText(), "tendered cash"), null);
        if (selected == PaymentMethod.CARD) return PaymentRequest.card(cardReference.getText(), cardBankName.getText());
        if (selected == PaymentMethod.TRANSFER) return PaymentRequest.transfer(transferReference.getText(), bankName.getText());
        List<PaymentPart> parts = new ArrayList<>();
        BigDecimal cash = splitAmount(cashAmount, "cash amount");
        BigDecimal card = splitAmount(cardAmount, "card amount");
        BigDecimal transfer = splitAmount(transferAmount, "transfer amount");
        if (cash.signum() > 0) parts.add(PaymentPart.cash(cash, null, Money.zero()));
        if (card.signum() > 0) parts.add(PaymentPart.card(card, cardReference.getText(), cardBankName.getText()));
        if (transfer.signum() > 0) parts.add(PaymentPart.transfer(transfer, transferReference.getText(), bankName.getText()));
        return PaymentRequest.split(parts);
    }

    private BigDecimal splitAmount(JTextField field, String name) { return field.getText().isBlank() ? Money.zero() : Money.parse(field.getText(), name); }
    private boolean amountEntered(JTextField field) { try { return splitAmount(field, "payment amount").signum() > 0; } catch (Exception ignored) { return false; } }
    /** Fill an empty next tender with the non-negative remainder; the cashier can always overwrite it. */
    private void suggestSplitRemaining(JTextField nextField) {
        if (method.getSelectedItem() != PaymentMethod.SPLIT || !nextField.getText().isBlank()) return;
        try {
            BigDecimal paid = splitAmount(cashAmount, "cash amount").add(splitAmount(cardAmount, "card amount")).add(splitAmount(transferAmount, "transfer amount"));
            BigDecimal value = finalPayableTotal().subtract(paid);
            if (value.signum() > 0) nextField.setText(value.setScale(2).toPlainString());
        } catch (Exception ignored) { }
    }
    private void updateRemaining() {
        try {
            if (method.getSelectedItem() != PaymentMethod.SPLIT) { remaining.setText("LKR 0.00"); return; }
            BigDecimal paid = splitAmount(cashAmount, "cash amount").add(splitAmount(cardAmount, "card amount")).add(splitAmount(transferAmount, "transfer amount"));
            BigDecimal value = finalPayableTotal().subtract(paid);
            remaining.setText(value.signum() < 0 ? "OVER BY LKR " + value.abs().setScale(2) : "LKR " + value.setScale(2));
            remaining.setForeground(value.signum() == 0 ? UITheme.GREEN : UITheme.RED);
            showPaymentField(cardReference, amountEntered(cardAmount));
            showPaymentField(cardBankName, amountEntered(cardAmount));
            showPaymentField(transferReference, amountEntered(transferAmount));
            showPaymentField(bankName, amountEntered(transferAmount));
            updateSplitPaymentLayout(true);
            revalidate(); repaint();
        } catch (Exception exception) { remaining.setText("LKR 0.00"); }
    }

    private BigDecimal cartSubtotal() {
        return cart.stream().map(CartItem::subtotal).reduce(Money.zero(), BigDecimal::add);
    }

    private BigDecimal normalSubtotal() {
        return cart.stream().map(CartItem::baseSubtotal).reduce(Money.zero(), BigDecimal::add);
    }

    private BigDecimal promotionSaving() {
        return cart.stream().map(CartItem::promotionSaving).reduce(Money.zero(), BigDecimal::add);
    }

    /** Fixed LKR ("250") or percentage of the bill ("10%"); a percentage follows every cart change. */
    private BigDecimal discountValue() {
        if (!"ADMIN".equals(Session.currentRole())) return Money.zero();
        return CheckoutCalculator.discountAmount(discount.getText(), cartSubtotal());
    }

    /** Recalculates display values immediately, without silently accepting an invalid discount. */
    private void refreshTotals() {
        try {
            validateAppliedRedemptionForCurrentTotal();
            BigDecimal manualDiscount = discountValue();
            BigDecimal payableBeforeManual = cartSubtotal();
            subtotalDisplay.setText("LKR " + normalSubtotal().setScale(2));
            promotionSavingDisplay.setText("LKR " + promotionSaving().setScale(2));
            savingDisplay.setText("LKR " + CheckoutCalculator.customerSaving(cart).setScale(2));
            BigDecimal saleTotal = CheckoutCalculator.total(payableBeforeManual, manualDiscount).subtract(redemptionValue());
            total.setText("LKR " + saleTotal.setScale(2));
            manualDiscountDisplay.setText("LKR " + manualDiscount.setScale(2));
            manualDiscountSummaryRow.setVisible(manualDiscount.signum() > 0);
            loyaltyRedemptionDisplay.setText("-LKR " + redemptionValue().setScale(2));
            loyaltyRedemptionSummaryRow.setVisible(true);
            discountMessage.setText(" ");
        } catch (IllegalArgumentException exception) {
            total.setText("LKR " + cartSubtotal().setScale(2));
            manualDiscountSummaryRow.setVisible(false);
            discountMessage.setText(exception.getMessage());
        }
        updateChange(); updateRemaining(); revalidate();
        updateChange();
        updateRemaining();
    }

    private void showError(Exception exception) {

        focusPaymentFieldFor(exception == null ? "" : exception.getMessage());
        Dialogs.error(this, "EasyMart POS", exception == null ? "Unexpected checkout error." : exception.getMessage());
    }

    private void focusPaymentFieldFor(String message) {
        String text = message == null ? "" : message.toLowerCase(java.util.Locale.ROOT);
        if (text.contains("tendered")) focus(tendered);
        else if (text.contains("card") && text.contains("reference")) focus(cardReference);
        else if (text.contains("transfer") && text.contains("reference")) focus(transferReference);
        else if ((text.contains("payment") || text.contains("cash, card")) && method.getSelectedItem() == PaymentMethod.SPLIT) focus(cashAmount);
    }

    /** Small recoverable checkout notices must not survive a successful cart action. */
    private void showInlineNotice(String message) {
        showInlineNotice(message, UITheme.RED);
    }

    private void showInlineNotice(String message, java.awt.Color color) {
        barcodeNotice.setText(message == null || message.isBlank() ? " " : message);
        barcodeNotice.setForeground(color == null ? UITheme.RED : color);
        if (inlineNoticeTimer != null) inlineNoticeTimer.stop();
        inlineNoticeTimer = new javax.swing.Timer(3200, event -> clearInlineNotice());
        inlineNoticeTimer.setRepeats(false); inlineNoticeTimer.start();
    }

    private void clearInlineNotice() {
        if (inlineNoticeTimer != null) inlineNoticeTimer.stop();
        barcodeNotice.setForeground(UITheme.GREY);
        barcodeNotice.setText("Ready to scan");
    }

    /** Price-group display data, accumulated in repository FEFO order. */
    private static final class PriceOption {
        private final BigDecimal sellingPrice;
        private final BigDecimal retailPrice;
        private LocalDate nearestExpiry;
        private BigDecimal availableQuantity;

        private PriceOption(BatchStock batch) {
            sellingPrice = batch.sellingPrice();
            retailPrice = batch.retailPrice();
            nearestExpiry = batch.expiryDate();
            availableQuantity = batch.quantityAvailable();
        }

        private void add(BatchStock batch) {
            availableQuantity = availableQuantity.add(batch.quantityAvailable());
            if (batch.expiryDate() != null && (nearestExpiry == null || batch.expiryDate().isBefore(nearestExpiry))) {
                nearestExpiry = batch.expiryDate();
            }
        }
    }

    private record HeldLine(int productId, BigDecimal quantity, BigDecimal ourPrice, BigDecimal retailPrice,
            List<BatchAllocation> batchAllocations) {
        private HeldLine { batchAllocations = List.copyOf(batchAllocations == null ? List.of() : batchAllocations); }
    }
    private record HeldSale(String invoice, CustomerChoice customer, String discount, List<HeldLine> lines) {
        @Override public String toString() { return invoice + "  •  " + lines.size() + " item(s)  •  " + (customer == null ? "Walk-in" : customer.name()); }
    }
}
