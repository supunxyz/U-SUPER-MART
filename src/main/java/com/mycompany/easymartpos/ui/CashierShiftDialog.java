package com.mycompany.easymartpos.ui;

import com.mycompany.easymartpos.Dialogs;
import com.mycompany.easymartpos.model.CashierShift;
import com.mycompany.easymartpos.model.ShiftTotals;
import com.mycompany.easymartpos.service.ShiftService;
import com.mycompany.easymartpos.service.ShiftCalculator;
import com.mycompany.easymartpos.util.Money;
import java.awt.GridLayout;
import java.awt.Window;
import java.math.BigDecimal;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/** Compact dialogs for a cashier's own start/close workflow. */
public final class CashierShiftDialog {
    private CashierShiftDialog() { }

    public static boolean start(Window owner, ShiftService shifts) {
        JTextField opening = new JTextField("0.00", 12);
        JPanel panel = new JPanel(new GridLayout(0, 1, 6, 6));
        panel.add(new JLabel("Opening Cash (LKR)")); panel.add(opening);
        if (!Dialogs.confirmOkCancel(owner, "Start Shift", panel)) return false;
        try { shifts.startCurrentCashierShift(Money.parse(opening.getText(), "opening cash")); return true; }
        catch (Exception exception) { showError(owner, exception); return false; }
    }

    public static boolean close(Window owner, ShiftService shifts) {
        try {
            CashierShift shift = shifts.currentOpenShiftForCurrentUser().orElseThrow(() -> new IllegalStateException("You do not have an open shift to close."));
            ShiftTotals totals = shifts.totalsForShift(shift.shiftId());
            BigDecimal expected = ShiftCalculator.expectedCash(shift.openingCash(), totals.cashSales());
            JTextField closing = new JTextField(12);
            JPanel panel = new JPanel(new GridLayout(0, 2, 8, 6));
            add(panel, "Opening Cash", amount(shift.openingCash())); add(panel, "Cash Sales", amount(totals.cashSales()));
            add(panel, "Card Sales", amount(totals.cardSales())); add(panel, "Transfer Sales", amount(totals.transferSales())); add(panel, "Total Sales", amount(totals.sales()));
            add(panel, "Transactions", Long.toString(totals.transactions())); add(panel, "Expected Cash", amount(expected));
            panel.add(new JLabel("Closing Cash (LKR)")); panel.add(closing);
            if (!Dialogs.confirmOkCancel(owner, "Close Shift", panel)) return false;
            shifts.closeCurrentCashierShift(Money.parse(closing.getText(), "closing cash")); return true;
        } catch (Exception exception) { showError(owner, exception); return false; }
    }

    private static void add(JPanel panel, String label, String value) { panel.add(new JLabel(label)); panel.add(new JLabel(value)); }
    private static String amount(BigDecimal value) { return "LKR " + value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(); }
    private static void showError(Window owner, Exception exception) { Dialogs.error(owner, "Cashier Shift", exception.getMessage()); }
}
