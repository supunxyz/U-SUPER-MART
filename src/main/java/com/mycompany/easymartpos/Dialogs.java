package com.mycompany.easymartpos;

import com.mycompany.easymartpos.ui.UITheme;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

/** Consistent, keyboard-friendly dialogs for normal EasyMartPOS workflows. */
public final class Dialogs {
    private Dialogs() { }

    public static void success(Component owner, String title, String message) { message(owner, title, message, JOptionPane.INFORMATION_MESSAGE, UITheme.GREEN, "SUCCESS"); }
    public static void information(Component owner, String title, String message) { message(owner, title, message, JOptionPane.INFORMATION_MESSAGE, UITheme.BLUE, "INFORMATION"); }
    public static void warning(Component owner, String title, String message) { message(owner, title, message, JOptionPane.WARNING_MESSAGE, new Color(187, 126, 18), "ATTENTION"); }
    public static void error(Component owner, String title, String message) { message(owner, title, message, JOptionPane.ERROR_MESSAGE, UITheme.RED, "ACTION REQUIRED"); }

    public static boolean confirmYesNo(Component owner, String title, String message) {
        return show(owner, title, message, JOptionPane.QUESTION_MESSAGE, JOptionPane.YES_NO_OPTION, "CONFIRM", UITheme.BLUE) == JOptionPane.YES_OPTION;
    }

    public static boolean confirmOkCancel(Component owner, String title, String message) {
        return show(owner, title, message, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION, "CONFIRM", UITheme.BLUE) == JOptionPane.OK_OPTION;
    }

    public static boolean confirmOkCancel(Component owner, String title, Component content) {
        return show(owner, title, content, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION;
    }

    public static String input(Component owner, String title, String prompt) {
        JTextField field = new JTextField(24);
        JPanel body = body("INPUT REQUIRED", UITheme.BLUE, prompt);
        body.add(field, BorderLayout.SOUTH);
        int result = show(owner, title, body, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
        return result == JOptionPane.OK_OPTION ? field.getText() : null;
    }

    public static String multilineInput(Component owner, String title, String prompt) {
        JTextArea field = new JTextArea(4, 28); field.setLineWrap(true); field.setWrapStyleWord(true); field.setFont(UITheme.BODY);
        JPanel body = body("INPUT REQUIRED", UITheme.BLUE, prompt);
        body.add(new JScrollPane(field), BorderLayout.SOUTH);
        int result = show(owner, title, body, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
        return result == JOptionPane.OK_OPTION ? field.getText() : null;
    }

    private static void message(Component owner, String title, String text, int type, Color accent, String heading) {
        show(owner, title, body(heading, accent, text), type, JOptionPane.DEFAULT_OPTION);
    }

    private static int show(Component owner, String title, String text, int type, int optionType, String heading, Color accent) {
        return show(owner, title, body(heading, accent, text), type, optionType);
    }

    private static int show(Component owner, String title, Object message, int type, int optionType) {
        JOptionPane pane = new JOptionPane(message, type, optionType);
        JDialog dialog = pane.createDialog(owner, title == null || title.isBlank() ? "EasyMartPOS" : title);
        dialog.setResizable(false);
        styleDialog(dialog);
        dialog.setVisible(true);
        Object value = pane.getValue();
        return value instanceof Integer option ? option : JOptionPane.CLOSED_OPTION;
    }

    private static JPanel body(String heading, Color accent, String text) {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBackground(Color.WHITE); panel.setBorder(BorderFactory.createEmptyBorder(14, 18, 10, 18));
        JLabel title = new JLabel(heading); title.setFont(new Font("Dialog", Font.BOLD, 11)); title.setForeground(accent);
        JLabel message = new JLabel("<html><div style='width:320px'>" + escape(text).replace("\n", "<br>") + "</div></html>");
        message.setFont(UITheme.BODY); message.setForeground(UITheme.NAVY);
        panel.add(title, BorderLayout.NORTH); panel.add(message, BorderLayout.CENTER);
        return panel;
    }

    private static String escape(String value) {
        return (value == null ? "" : value).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static void styleDialog(JDialog dialog) {
        dialog.getContentPane().setBackground(Color.WHITE);
        for (JButton button : buttons(dialog)) styleButton(button);
        dialog.getRootPane().setDefaultButton(buttons(dialog).stream().filter(JButton::isEnabled).findFirst().orElse(null));
        dialog.setMinimumSize(new Dimension(390, dialog.getPreferredSize().height));
    }

    private static List<JButton> buttons(Container parent) {
        List<JButton> result = new ArrayList<>();
        for (Component component : parent.getComponents()) {
            if (component instanceof JButton button) result.add(button);
            if (component instanceof Container nested) result.addAll(buttons(nested));
        }
        return result;
    }

    private static void styleButton(JButton button) {
        String text = button.getText() == null ? "" : button.getText().trim().toLowerCase();
        if (text.equals("cancel") || text.equals("no")) UITheme.styleSecondary(button); else UITheme.stylePrimary(button);
        button.setFocusPainted(true);
        button.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent event) { button.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(UITheme.NAVY, 2), BorderFactory.createEmptyBorder(7, 14, 7, 14))); }
            @Override public void focusLost(FocusEvent event) { if (text.equals("cancel") || text.equals("no")) UITheme.styleSecondary(button); else UITheme.stylePrimary(button); }
        });
    }
}
