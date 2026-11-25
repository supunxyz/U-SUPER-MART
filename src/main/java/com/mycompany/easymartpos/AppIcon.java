package com.mycompany.easymartpos;

import java.awt.AWTEvent;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/** Loads the store logo and applies it as the icon of every window the application opens. */
public final class AppIcon {
    private static final String LOGO_RESOURCE = "/images/app-logo.png";
    private static BufferedImage logo;

    private AppIcon() { }

    /** The full-size store logo, or null if the resource is missing. */
    public static synchronized BufferedImage logo() {
        if (logo == null) {
            try (InputStream in = AppIcon.class.getResourceAsStream(LOGO_RESOURCE)) {
                if (in != null) logo = ImageIO.read(in);
            } catch (IOException ignored) { }
        }
        return logo;
    }

    /** The logo scaled to a square size using smooth stepwise down-scaling. */
    public static BufferedImage logo(int size) {
        BufferedImage source = logo();
        if (source == null) return null;
        BufferedImage current = source;
        int width = current.getWidth();
        while (width / 2 >= size) {
            width /= 2;
            current = draw(current, width);
        }
        return current.getWidth() == size ? current : draw(current, size);
    }

    /** Gives every frame and dialog opened from now on the store icon. */
    public static void installOnAllWindows() {
        List<Image> icons = new ArrayList<>();
        for (int size : new int[] {16, 32, 48, 64, 128, 256}) {
            BufferedImage icon = logo(size);
            if (icon != null) icons.add(icon);
        }
        if (icons.isEmpty()) return;
        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (event.getID() == WindowEvent.WINDOW_OPENED && event.getSource() instanceof Window window) {
                window.setIconImages(icons);
            }
        }, AWTEvent.WINDOW_EVENT_MASK);
    }

    private static BufferedImage draw(BufferedImage source, int size) {
        BufferedImage target = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = target.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(source, 0, 0, size, size, null);
        g.dispose();
        return target;
    }
}
