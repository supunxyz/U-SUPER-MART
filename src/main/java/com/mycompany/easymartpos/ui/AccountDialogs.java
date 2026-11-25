package com.mycompany.easymartpos.ui;

import com.mycompany.easymartpos.service.Authorization;
import com.mycompany.easymartpos.service.UserAccounts;
import java.awt.Component;
import java.awt.GridLayout;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

/** Small account dialogs that keep passwords out of the dashboard and source configuration. */
public final class AccountDialogs {
    private AccountDialogs() { }

    /** Returns false only when the operator cancels initial setup. */
    public static boolean firstRun(Component owner) {
        UserAccounts accounts = new UserAccounts();
        while (true) {
            JTextField username = new JTextField(20);
            JPasswordField password = new JPasswordField(20);
            JPasswordField confirm = new JPasswordField(20);
            JPanel panel = fields("Administrator username", username, "Password (12+ characters)", password,
                    "Confirm password", confirm);
            if (JOptionPane.showConfirmDialog(owner, panel, "Create the first administrator",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return false;
            char[] entered = password.getPassword(); char[] repeated = confirm.getPassword();
            try {
                if (!Arrays.equals(entered, repeated)) throw new IllegalArgumentException("Passwords do not match.");
                accounts.createFirstAdmin(username.getText(), entered);
                JOptionPane.showMessageDialog(owner, "Administrator created. Sign in with the password you chose.");
                return true;
            } catch (Exception exception) { showError(owner, exception); }
            finally { Arrays.fill(entered, '\0'); Arrays.fill(repeated, '\0'); }
        }
    }

    public static boolean resetLegacyAdmin(Component owner) {
        while (true) {
            JPasswordField password = new JPasswordField(20), confirm = new JPasswordField(20);
            JPanel panel = fields("New administrator password (12+ characters)", password,
                    "Confirm password", confirm);
            if (JOptionPane.showConfirmDialog(owner, panel, "Secure existing administrator account",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return false;
            char[] entered = password.getPassword(), repeated = confirm.getPassword();
            try {
                if (!Arrays.equals(entered, repeated)) throw new IllegalArgumentException("Passwords do not match.");
                new UserAccounts().replaceLegacyAdminPassword(entered);
                JOptionPane.showMessageDialog(owner, "Administrator password updated. Sign in with the new password.");
                return true;
            } catch (Exception exception) { showError(owner, exception); }
            finally { Arrays.fill(entered, '\0'); Arrays.fill(repeated, '\0'); }
        }
    }

    public static void changeOwnPassword(Component owner) {
        JPasswordField current = new JPasswordField(20), replacement = new JPasswordField(20), confirm = new JPasswordField(20);
        JPanel panel = fields("Current password", current, "New password (12+ characters)", replacement,
                "Confirm new password", confirm);
        if (JOptionPane.showConfirmDialog(owner, panel, "Change password", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        char[] oldValue = current.getPassword(), newValue = replacement.getPassword(), repeated = confirm.getPassword();
        try {
            if (!Arrays.equals(newValue, repeated)) throw new IllegalArgumentException("Passwords do not match.");
            new UserAccounts().changeOwnPassword(oldValue, newValue);
            JOptionPane.showMessageDialog(owner, "Password changed.");
        } catch (Exception exception) { showError(owner, exception); }
        finally { Arrays.fill(oldValue, '\0'); Arrays.fill(newValue, '\0'); Arrays.fill(repeated, '\0'); }
    }

    public static void manageUsers(Component owner) {
        Authorization.requireAdmin();
        UserAccounts accounts = new UserAccounts();
        while (true) {
            String action = (String) JOptionPane.showInputDialog(owner, "Choose an account action", "User accounts",
                    JOptionPane.PLAIN_MESSAGE, null, new String[]{"Add user", "Reset password", "Enable / disable user"}, "Add user");
            if (action == null) return;
            try {
                switch (action) {
                    case "Add user" -> addUser(owner, accounts);
                    case "Reset password" -> resetPassword(owner, accounts);
                    default -> toggleUser(owner, accounts);
                }
            } catch (Exception exception) { showError(owner, exception); }
        }
    }

    private static void addUser(Component owner, UserAccounts accounts) throws SQLException {
        JTextField username = new JTextField(20);
        JComboBox<String> role = new JComboBox<>(new String[]{"CASHIER", "ADMIN"});
        JPasswordField password = new JPasswordField(20), confirm = new JPasswordField(20);
        JPanel panel = fields("Username", username, "Role", role, "Password (12+ characters)", password,
                "Confirm password", confirm);
        if (JOptionPane.showConfirmDialog(owner, panel, "Add user", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        char[] entered = password.getPassword(), repeated = confirm.getPassword();
        try {
            if (!Arrays.equals(entered, repeated)) throw new IllegalArgumentException("Passwords do not match.");
            accounts.createUser(username.getText(), entered, (String) role.getSelectedItem());
            JOptionPane.showMessageDialog(owner, "User created.");
        } finally { Arrays.fill(entered, '\0'); Arrays.fill(repeated, '\0'); }
    }

    private static void resetPassword(Component owner, UserAccounts accounts) throws SQLException {
        UserAccounts.User target = chooseUser(owner, accounts.listUsers()); if (target == null) return;
        JPasswordField password = new JPasswordField(20), confirm = new JPasswordField(20);
        JPanel panel = fields("New password (12+ characters)", password, "Confirm password", confirm);
        if (JOptionPane.showConfirmDialog(owner, panel, "Reset " + target.username(), JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        char[] entered = password.getPassword(), repeated = confirm.getPassword();
        try {
            if (!Arrays.equals(entered, repeated)) throw new IllegalArgumentException("Passwords do not match.");
            accounts.resetPassword(target.id(), entered);
            JOptionPane.showMessageDialog(owner, "Password reset.");
        } finally { Arrays.fill(entered, '\0'); Arrays.fill(repeated, '\0'); }
    }

    private static void toggleUser(Component owner, UserAccounts accounts) throws SQLException {
        UserAccounts.User target = chooseUser(owner, accounts.listUsers()); if (target == null) return;
        boolean newState = !target.active();
        if (JOptionPane.showConfirmDialog(owner, (newState ? "Enable " : "Disable ") + target.username() + "?",
                "User accounts", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        accounts.setActive(target.id(), newState);
        JOptionPane.showMessageDialog(owner, "Account " + (newState ? "enabled." : "disabled."));
    }

    private static UserAccounts.User chooseUser(Component owner, List<UserAccounts.User> users) {
        if (users.isEmpty()) return null;
        String[] labels = users.stream().map(user -> user.username() + " (" + user.role() + ", "
                + (user.active() ? "active" : "disabled") + ")").toArray(String[]::new);
        String chosen = (String) JOptionPane.showInputDialog(owner, "Select user", "User accounts",
                JOptionPane.PLAIN_MESSAGE, null, labels, labels[0]);
        if (chosen == null) return null;
        for (int i = 0; i < labels.length; i++) if (labels[i].equals(chosen)) return users.get(i);
        return null;
    }

    private static JPanel fields(Object... items) {
        JPanel panel = new JPanel(new GridLayout(0, 2, 8, 8));
        for (int i = 0; i < items.length; i += 2) {
            panel.add(new JLabel((String) items[i])); panel.add((Component) items[i + 1]);
        }
        return panel;
    }

    private static void showError(Component owner, Exception exception) {
        JOptionPane.showMessageDialog(owner, exception.getMessage(), "User accounts", JOptionPane.ERROR_MESSAGE);
    }
}
