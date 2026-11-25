package com.mycompany.easymartpos.ui;

import com.mycompany.easymartpos.Dialogs;
import com.mycompany.easymartpos.LoginForm;
import com.mycompany.easymartpos.MySqlBackupService;
import com.mycompany.easymartpos.service.Authorization;
import com.mycompany.easymartpos.service.Session;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Window;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Admin-only MySQL logical backup and restore screen. */
public final class BackupRestoreFrame extends JFrame {
    private final MySqlBackupService databaseTools = new MySqlBackupService();
    private final JButton backupButton = new JButton("Backup Database");
    private final JButton restoreButton = new JButton("Restore Database");
    private final JLabel backupStatus = new JLabel("No backup has been created in this session.");
    private final JLabel restoreStatus = new JLabel("Select a .sql backup file to begin restore.");

    public BackupRestoreFrame() {
        super("Backup / Restore");
        Authorization.requireAdmin();
        setDefaultCloseOperation(DISPOSE_ON_CLOSE); setSize(920, 510); setMinimumSize(new java.awt.Dimension(760, 440)); setLocationRelativeTo(null);
        JPanel root = new JPanel(new BorderLayout(14, 14)); root.setBackground(UITheme.BACKGROUND); root.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        root.add(UITheme.pageHeader("BACKUP / RESTORE", "Create safe MySQL backups and restore only after careful confirmation"), BorderLayout.NORTH);
        JPanel cards = new JPanel(new GridLayout(1, 2, 16, 0)); cards.setOpaque(false); cards.add(backupCard()); cards.add(restoreCard()); root.add(cards, BorderLayout.CENTER);
        add(root);
    }

    private JPanel backupCard() {
        JPanel card = UITheme.card(new BorderLayout(0, 12)); card.add(title("BACKUP DATABASE", UITheme.GREEN), BorderLayout.NORTH);
        JLabel text = new JLabel("<html>Create a complete logical SQL copy of table structures, data, indexes, keys, triggers, events and routines.</html>"); card.add(text, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout(0, 7)); bottom.setOpaque(false); backupStatus.setFont(new Font("Dialog", Font.PLAIN, 11)); backupStatus.setForeground(UITheme.GREY); bottom.add(backupStatus, BorderLayout.NORTH);
        UITheme.styleSuccess(backupButton); backupButton.addActionListener(event -> chooseBackupDestination()); bottom.add(backupButton, BorderLayout.SOUTH); card.add(bottom, BorderLayout.SOUTH); return card;
    }

    private JPanel restoreCard() {
        JPanel card = UITheme.card(new BorderLayout(0, 12)); card.add(title("RESTORE DATABASE", UITheme.RED), BorderLayout.NORTH);
        JLabel text = new JLabel("<html><b>Warning:</b> restoring can replace current POS business data.<br><br>A verified safety backup of the current database is required before restore continues.</html>"); card.add(text, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout(0, 7)); bottom.setOpaque(false); restoreStatus.setFont(new Font("Dialog", Font.PLAIN, 11)); restoreStatus.setForeground(UITheme.GREY); bottom.add(restoreStatus, BorderLayout.NORTH);
        UITheme.styleDanger(restoreButton); restoreButton.addActionListener(event -> chooseRestoreSource()); bottom.add(restoreButton, BorderLayout.SOUTH); card.add(bottom, BorderLayout.SOUTH); return card;
    }

    private void chooseBackupDestination() {
        JFileChooser chooser = sqlChooser(); chooser.setDialogTitle("Save EasyMartPOS Database Backup");
        chooser.setSelectedFile(new java.io.File("easymart_pos_backup_" + DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm").format(java.time.LocalDateTime.now()) + ".sql"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path destination = sqlPath(chooser.getSelectedFile().toPath());
        if (Files.exists(destination) && JOptionPane.showConfirmDialog(this, "The selected file already exists. Replace it?\n" + destination, "Replace Backup", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;
        runBackup(destination);
    }

    private void chooseRestoreSource() {
        JFileChooser chooser = sqlChooser(); chooser.setDialogTitle("Select EasyMartPOS SQL Backup to Restore");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path source = chooser.getSelectedFile().toPath().toAbsolutePath();
        try { MySqlBackupService.validateRestoreFile(source); }
        catch (Exception exception) { showError(exception); return; }
        String warning = "WARNING\n\nRestoring a database backup can replace current POS business data.\n\nSelected backup:\n" + source + "\n\nA safety backup of the current database will be created first.\n\nContinue with restore?";
        Object[] options = {"Restore", "Cancel"};
        int choice = JOptionPane.showOptionDialog(this, warning, "Confirm Database Restore", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[1]);
        if (choice != 0) return;
        runRestore(source);
    }

    private void runBackup(Path destination) {
        setBusy(true, "Creating backup...");
        new SwingWorker<MySqlBackupService.BackupResult, Void>() {
            @Override protected MySqlBackupService.BackupResult doInBackground() throws Exception { return databaseTools.backup(destination); }
            @Override protected void done() {
                setBusy(false, null);
                try {
                    MySqlBackupService.BackupResult result = get();
                    String message = "Backup completed successfully.\n\nSaved file:\n" + result.path() + "\n\nCompleted: " + result.completedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + "\nSize: " + fileSize(result.bytes());
                    backupStatus.setText("Last backup: " + result.path()); Dialogs.success(BackupRestoreFrame.this, "Backup Complete", message);
                } catch (Exception exception) { backupStatus.setText("Backup failed. No success was recorded."); showError(exception); }
            }
        }.execute();
    }

    private void runRestore(Path source) {
        setBusy(true, "Creating required safety backup before restore...");
        new SwingWorker<MySqlBackupService.RestoreResult, Void>() {
            @Override protected MySqlBackupService.RestoreResult doInBackground() throws Exception { return databaseTools.restore(source, source.getParent()); }
            @Override protected void done() {
                setBusy(false, null);
                try {
                    MySqlBackupService.RestoreResult result = get(); restoreStatus.setText("Restore verified. Safety backup: " + result.safetyBackup()); finishSuccessfulRestore(result);
                } catch (Exception exception) { restoreStatus.setText("Restore was cancelled or failed; no success was recorded."); showError(exception); }
            }
        }.execute();
    }

    private void finishSuccessfulRestore(MySqlBackupService.RestoreResult result) {
        Dialogs.success(this, "Restore Complete", "Database restored successfully.\n\nSafety backup:\n" + result.safetyBackup() + "\n\nThe POS will now sign out so restored users, products, sales and shifts are loaded safely. Sign in again (or restart the POS) before continuing.");
        Session.clear(); for (Window window : Window.getWindows()) if (window != this) window.dispose(); dispose();
        SwingUtilities.invokeLater(() -> { LoginForm login = new LoginForm(); login.setLocationRelativeTo(null); login.setVisible(true); });
    }

    private void setBusy(boolean value, String message) {
        backupButton.setEnabled(!value); restoreButton.setEnabled(!value);
        if (message != null) { backupStatus.setText(message); restoreStatus.setText(message); }
        setCursor(value ? java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR) : java.awt.Cursor.getDefaultCursor());
    }

    private JFileChooser sqlChooser() { JFileChooser chooser = new JFileChooser(); chooser.setFileFilter(new FileNameExtensionFilter("SQL database backup (*.sql)", "sql")); chooser.setAcceptAllFileFilterUsed(false); return chooser; }
    private Path sqlPath(Path path) { return path.getFileName().toString().toLowerCase().endsWith(".sql") ? path : path.resolveSibling(path.getFileName() + ".sql"); }
    private String fileSize(long bytes) { return bytes >= 1_048_576 ? String.format(java.util.Locale.US, "%.2f MB", bytes / 1_048_576d) : String.format(java.util.Locale.US, "%.1f KB", bytes / 1024d); }
    private JLabel title(String value, Color color) { JLabel label = new JLabel(value); label.setFont(new Font("Dialog", Font.BOLD, 16)); label.setForeground(color); return label; }
    private void showError(Exception exception) { Throwable cause = exception instanceof java.util.concurrent.ExecutionException && exception.getCause() != null ? exception.getCause() : exception; String text = cause.getMessage() == null ? "The operation could not be completed." : cause.getMessage(); Dialogs.error(this, "Backup / Restore", text); }
}
