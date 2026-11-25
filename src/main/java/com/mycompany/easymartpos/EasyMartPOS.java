/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */

package com.mycompany.easymartpos;

import com.mycompany.easymartpos.ui.UITheme;
import com.mycompany.easymartpos.ui.AccountDialogs;
import com.mycompany.easymartpos.service.UserAccounts;
import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;

public class EasyMartPOS {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            javax.swing.UIManager.put("swing.boldMetal", Boolean.FALSE);
            UITheme.install();
            AppIcon.installOnAllWindows();
            if (!EmbeddedDatabase.isAvailable()) {
                showLogin();
                return;
            }
            if (!EmbeddedDatabase.acquireSingleInstance()) {
                JOptionPane.showMessageDialog(null, "Ushantha Super Mart POS is already running on this computer.",
                        "Already running", JOptionPane.INFORMATION_MESSAGE);
                System.exit(0);
                return;
            }
            startWithEmbeddedDatabase();
        });
    }

    private static void startWithEmbeddedDatabase() {
        JLabel message = new JLabel("Starting Ushantha Super Mart POS...", JLabel.CENTER);
        message.setFont(message.getFont().deriveFont(15f));
        JProgressBar bar = new JProgressBar();
        bar.setIndeterminate(true);
        JWindow splash = new JWindow();
        splash.getContentPane().setLayout(new BorderLayout(0, 14));
        ((javax.swing.JComponent) splash.getContentPane()).setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new java.awt.Color(0x1E3A8A), 2), BorderFactory.createEmptyBorder(28, 32, 28, 32)));
        java.awt.image.BufferedImage logo = AppIcon.logo(150);
        if (logo != null) splash.getContentPane().add(new JLabel(new javax.swing.ImageIcon(logo)), BorderLayout.NORTH);
        splash.getContentPane().add(message, BorderLayout.CENTER);
        splash.getContentPane().add(bar, BorderLayout.SOUTH);
        splash.setPreferredSize(new Dimension(420, logo == null ? 120 : 300));
        splash.pack();
        splash.setLocationRelativeTo(null);
        splash.setVisible(true);

        Thread worker = new Thread(() -> {
            try {
                EmbeddedDatabase.start(text -> SwingUtilities.invokeLater(() -> message.setText(text)));
                SwingUtilities.invokeLater(() -> { splash.dispose(); showLogin(); });
            } catch (Exception exception) {
                SwingUtilities.invokeLater(() -> {
                    splash.dispose();
                    JOptionPane.showMessageDialog(null, "The database could not be started:\n" + exception.getMessage(),
                            "Ushantha Super Mart POS", JOptionPane.ERROR_MESSAGE);
                    System.exit(1);
                });
            }
        }, "easymart-db-startup");
        worker.setDaemon(true);
        worker.start();
    }

    private static void showLogin() {
        try {
            UserAccounts accounts = new UserAccounts();
            if (accounts.needsSetup() && !AccountDialogs.firstRun(null)) {
                System.exit(0); return;
            }
            if (accounts.needsLegacyAdminReset() && !AccountDialogs.resetLegacyAdmin(null)) {
                System.exit(0); return;
            }
            accounts.upgradeLegacyPasswords();
            LoginForm login = new LoginForm();
            login.setLocationRelativeTo(null);
            login.setVisible(true);
        } catch (Exception exception) {
            JOptionPane.showMessageDialog(null, "Unable to prepare administrator sign-in:\n" + exception.getMessage(),
                    "EasyMart POS", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }
}
