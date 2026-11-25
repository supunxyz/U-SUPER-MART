package com.mycompany.easymartpos;

import com.mycompany.easymartpos.service.Session;
import com.mycompany.easymartpos.service.UserAccounts;
import com.mycompany.easymartpos.ui.UITheme;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.AbstractBorder;

/** Secure staff login screen. Authentication behaviour remains compatible with existing users. */
public class LoginForm extends JFrame {
    private static final Color NAVY = new Color(15, 23, 42);
    private static final Color NAVY_SOFT = new Color(30, 41, 59);
    private static final Color BACKGROUND = new Color(244, 246, 248);
    private static final Color CARD = Color.WHITE;
    private static final Color PRIMARY = new Color(37, 99, 235);
    private static final Color TEXT = new Color(15, 23, 42);
    private static final Color MUTED = new Color(100, 116, 139);
    private static final Color BORDER = new Color(100, 116, 139);
    private static final Logger LOGGER = Logger.getLogger(LoginForm.class.getName());
    private final JTextField usernameField = new JTextField();
    private final JPasswordField passwordField = new JPasswordField();
    private final JButton loginButton = new LoginButton("LOGIN");

    public LoginForm() {
        super("USHANTHA SUPER MART | Staff Login");
        buildUi();
    }

    private void buildUi() {
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(840, 500); setMinimumSize(new Dimension(840, 500)); setResizable(false); setLocationRelativeTo(null);
        JPanel root = new JPanel(new BorderLayout()); root.setBackground(BACKGROUND);
        root.add(brandPanel(), BorderLayout.WEST);
        JPanel workspace = new JPanel(new GridBagLayout()); workspace.setBackground(BACKGROUND);
        workspace.add(loginCard()); root.add(workspace, BorderLayout.CENTER); add(root);
        usernameField.addActionListener(event -> passwordField.requestFocusInWindow());
        passwordField.addActionListener(event -> attemptLogin());
        getRootPane().setDefaultButton(loginButton);
        SwingUtilities.invokeLater(() -> usernameField.requestFocusInWindow());
    }

    private JPanel brandPanel() {
        JPanel panel = new JPanel(new GridBagLayout()); panel.setBackground(NAVY); panel.setPreferredSize(new Dimension(330, 1));
        GridBagConstraints constraints = new GridBagConstraints(); constraints.gridx = 0; constraints.fill = GridBagConstraints.HORIZONTAL;
        LoginBrandMark mark = new LoginBrandMark(); constraints.gridy = 0; constraints.insets = new Insets(0, 0, 16, 0); panel.add(mark, constraints);
        if (AppIcon.logo() == null) { // The logo already carries the store name.
            JLabel store = centered("USHANTHA SUPER MART", new Font("Dialog", Font.BOLD, 23), Color.WHITE);
            constraints.gridy = 1; constraints.insets = new Insets(0, 28, 0, 28); panel.add(store, constraints);
        }
        JLabel system = centered("POS MANAGEMENT SYSTEM", new Font("Dialog", Font.BOLD, 11), new Color(191, 219, 254));
        constraints.gridy = 2; constraints.insets = new Insets(6, 28, 0, 28); panel.add(system, constraints);
        JLabel subtitle = centered("Secure Staff Access", new Font("Dialog", Font.PLAIN, 13), new Color(148, 163, 184));
        constraints.gridy = 3; constraints.insets = new Insets(17, 28, 0, 28); panel.add(subtitle, constraints);
        return panel;
    }

    private JPanel loginCard() {
        JPanel card = new LoginCard(new GridBagLayout()); card.setBorder(BorderFactory.createEmptyBorder(27, 36, 24, 36));
        card.setPreferredSize(new Dimension(372, 348));
        GridBagConstraints constraints = new GridBagConstraints(); constraints.gridx = 0; constraints.fill = GridBagConstraints.HORIZONTAL; constraints.weightx = 1; constraints.insets = new Insets(0, 0, 0, 0);
        JLabel title = new JLabel("Staff Login"); title.setFont(new Font("Dialog", Font.BOLD, 25)); title.setForeground(TEXT);
        constraints.gridy = 0; card.add(title, constraints);
        JLabel subtitle = new JLabel("Sign in to continue"); subtitle.setFont(new Font("Dialog", Font.PLAIN, 13)); subtitle.setForeground(MUTED);
        constraints.gridy = 1; constraints.insets = new Insets(5, 0, 21, 0); card.add(subtitle, constraints);
        constraints.gridy = 2; constraints.insets = new Insets(0, 0, 7, 0); card.add(fieldLabel("Username"), constraints);
        configureField(usernameField); constraints.gridy = 3; constraints.insets = new Insets(0, 0, 15, 0); card.add(usernameField, constraints);
        constraints.gridy = 4; constraints.insets = new Insets(0, 0, 7, 0); card.add(fieldLabel("Password"), constraints);
        configureField(passwordField); constraints.gridy = 5; constraints.insets = new Insets(0, 0, 20, 0); card.add(passwordField, constraints);
        loginButton.addActionListener(event -> attemptLogin());
        constraints.gridy = 6; constraints.insets = new Insets(0, 0, 15, 0); card.add(loginButton, constraints);
        JLabel footer = centered("Authorized staff only", new Font("Dialog", Font.PLAIN, 11), MUTED);
        constraints.gridy = 7; constraints.insets = new Insets(0, 0, 0, 0); card.add(footer, constraints);
        return card;
    }

    private JLabel centered(String text, Font font, Color color) {
        JLabel label = new JLabel(text, SwingConstants.CENTER); label.setFont(font); label.setForeground(color); return label;
    }
    private JLabel fieldLabel(String text) { JLabel label = new JLabel(text); label.setFont(new Font("Dialog", Font.BOLD, 12)); label.setForeground(TEXT); return label; }
    private void configureField(JTextField field) {
        field.setFont(new Font("Dialog", Font.PLAIN, 15)); field.setForeground(TEXT); field.setBackground(CARD); field.setPreferredSize(new Dimension(300, 46));
        field.setBorder(loginFieldBorder(false));
        field.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent event) { field.setBorder(loginFieldBorder(true)); }
            @Override public void focusLost(FocusEvent event) { field.setBorder(loginFieldBorder(false)); }
        });
    }

    private javax.swing.border.Border loginFieldBorder(boolean focused) {
        return BorderFactory.createCompoundBorder(new LoginFieldBorder(focused ? PRIMARY : BORDER, focused ? 2 : 1),
                BorderFactory.createEmptyBorder(8, 12, 8, 12));
    }

    /** Login-only card painter; it does not affect shared UI or Checkout components. */
    private static final class LoginCard extends JPanel {
        private LoginCard(java.awt.LayoutManager layout) { super(layout); setOpaque(false); }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(15, 23, 42, 18)); g.fillRoundRect(3, 4, getWidth() - 4, getHeight() - 4, 18, 18);
            g.setColor(CARD); g.fillRoundRect(0, 0, getWidth() - 4, getHeight() - 4, 18, 18);
            g.setColor(new Color(203, 213, 225)); g.drawRoundRect(0, 0, getWidth() - 4, getHeight() - 4, 18, 18);
            g.dispose(); super.paintComponent(graphics);
        }
    }

    /** Store logo shown on the login page; falls back to the vector cart mark if the image is unavailable. */
    private static final class LoginBrandMark extends JPanel {
        private static final int LOGO_SIZE = 210;
        private final java.awt.image.BufferedImage logo = AppIcon.logo(LOGO_SIZE);

        private LoginBrandMark() {
            setOpaque(false);
            Dimension size = logo == null ? new Dimension(76, 76) : new Dimension(LOGO_SIZE + 8, LOGO_SIZE + 8);
            setMinimumSize(size); setPreferredSize(size); setMaximumSize(size);
        }

        @Override protected void paintComponent(Graphics graphics) {
            if (logo != null) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                int x = (getWidth() - LOGO_SIZE) / 2; int y = (getHeight() - LOGO_SIZE) / 2;
                g.setClip(new java.awt.geom.Ellipse2D.Float(x, y, LOGO_SIZE, LOGO_SIZE));
                g.drawImage(logo, x, y, null);
                g.dispose();
                return;
            }
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int tile = 68; int left = (getWidth() - tile) / 2; int top = (getHeight() - tile) / 2;
            g.setColor(PRIMARY); g.fillRoundRect(left, top, tile, tile, 20, 20);
            g.setColor(Color.WHITE); g.setStroke(new java.awt.BasicStroke(3f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
            int cartLeft = left + 17; int cartTop = top + 20; int cartRight = left + 51; int basketBottom = top + 43;
            g.drawLine(cartLeft - 6, cartTop, cartLeft + 1, cartTop);
            g.drawLine(cartLeft + 1, cartTop, cartLeft + 6, basketBottom - 4);
            g.drawLine(cartLeft + 6, basketBottom - 4, cartRight, basketBottom - 4);
            g.drawLine(cartLeft + 8, cartTop + 7, cartRight - 2, cartTop + 7);
            g.drawLine(cartLeft + 11, cartTop + 15, cartRight - 5, cartTop + 15);
            g.fillOval(cartLeft + 8, basketBottom + 2, 7, 7); g.fillOval(cartRight - 10, basketBottom + 2, 7, 7);
            g.dispose();
        }
    }

    /** Local primary button painter with hover/pressed feedback. */
    private static final class LoginButton extends JButton {
        private LoginButton(String text) {
            super(text); setFont(new Font("Dialog", Font.BOLD, 14)); setForeground(Color.WHITE); setFocusPainted(false); setFocusable(false);
            setContentAreaFilled(false); setOpaque(false); setPreferredSize(new Dimension(1, 46)); setBorder(BorderFactory.createEmptyBorder(13, 18, 13, 18)); setRolloverEnabled(true);
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color fill = getModel().isPressed() ? new Color(29, 78, 216) : getModel().isRollover() ? new Color(30, 88, 216) : PRIMARY;
            g.setColor(fill); g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            g.setColor(new Color(255, 255, 255, 45)); g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12); g.dispose(); super.paintComponent(graphics);
        }
    }

    private static final class LoginFieldBorder extends AbstractBorder {
        private final Color color;
        private final int thickness;

        private LoginFieldBorder(Color color, int thickness) { this.color = color; this.thickness = thickness; }

        @Override public void paintBorder(Component component, Graphics graphics, int x, int y, int width, int height) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); g.setColor(color);
            for (int offset = 0; offset < thickness; offset++) g.drawRoundRect(x + offset, y + offset, width - 1 - 2 * offset, height - 1 - 2 * offset, 9, 9);
            g.dispose();
        }

        @Override public Insets getBorderInsets(Component component) { return new Insets(thickness, thickness, thickness, thickness); }
    }

    private void attemptLogin() {
        String username = usernameField.getText().trim();
        char[] password = passwordField.getPassword();
        if (username.isEmpty() || password.length == 0) {
            Dialogs.warning(this, "Login", "Please enter username and password.");
            java.util.Arrays.fill(password, '\0');
            return;
        }
        Session.clear();
        try {
            UserAccounts.User user = new UserAccounts().authenticate(username, password);
            if (user == null) {
                    Dialogs.error(this, "Login", "Invalid username or password.");
                    passwordField.selectAll(); passwordField.requestFocusInWindow(); return;
            }
            Session.start(user.id(), user.username(), user.role());
            PosDashboard dashboard = new PosDashboard(); dashboard.setVisible(true); dispose();
        } catch (Exception exception) {
            Session.clear();
            LOGGER.log(Level.SEVERE, "Login could not be completed.", exception);
            Dialogs.error(this, "Login", "Unable to sign in right now. Please contact the administrator.");
        } finally {
            java.util.Arrays.fill(password, '\0');
            passwordField.setText("");
        }
    }

    public static void main(String[] args) {
        EasyMartPOS.main(args);
    }
}
