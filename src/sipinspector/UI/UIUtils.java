package sipinspector.UI;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;

/**
 * Screen, DPI, and Window geometry helper utilities for SIP Simulator v4.
 *
 * Provides:
 * 1. Screen-aware dialog sizing that clamps preferred dimensions to usable screen bounds (respecting taskbars).
 * 2. Multi-monitor awareness (resolves screen insets and bounds for the monitor hosting the parent window).
 * 3. Safe minimum/maximum boundaries preventing dialogs from extending beyond viewports on smaller screens.
 */
public final class UIUtils {

    private UIUtils() {
        // Utility class
    }

    /**
     * Gets the usable screen bounds (subtracting taskbar insets) for the screen containing the given window.
     */
    public static Rectangle getUsableScreenBounds(Window window) {
        if (window != null) {
            GraphicsConfiguration gc = window.getGraphicsConfiguration();
            if (gc != null) {
                Rectangle bounds = gc.getBounds();
                Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
                return new Rectangle(
                    bounds.x + insets.left,
                    bounds.y + insets.top,
                    Math.max(640, bounds.width - insets.left - insets.right),
                    Math.max(480, bounds.height - insets.top - insets.bottom)
                );
            }
        }
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        Rectangle maxBounds = ge.getMaximumWindowBounds();
        if (maxBounds != null && maxBounds.width > 0 && maxBounds.height > 0) {
            return maxBounds;
        }
        return new Rectangle(0, 0, 1280, 720);
    }

    /**
     * Sizes a dialog to target preferred dimensions while clamping safely within the usable
     * bounds of the monitor currently displaying it or its parent.
     *
     * @param dialog Window or JDialog to size and position
     * @param targetWidth Ideal preferred width
     * @param targetHeight Ideal preferred height
     * @param minWidth Absolute minimum width required for component layout
     * @param minHeight Absolute minimum height required for component layout
     * @param parent Component to center relative to (or null for screen center)
     */
    public static void sizeAndCenterDialog(Window dialog, int targetWidth, int targetHeight,
                                           int minWidth, int minHeight, Component parent) {
        if (dialog == null) return;

        Window parentWindow = (parent instanceof Window) ? (Window) parent : 
                              (parent != null ? javax.swing.SwingUtilities.getWindowAncestor(parent) : null);
        Rectangle usable = getUsableScreenBounds(parentWindow != null ? parentWindow : dialog);

        // Clamp width and height so dialogs never overflow on small or high-DPI displays (e.g. 1366x768)
        int effectiveWidth = Math.min(targetWidth, usable.width - 20);
        int effectiveHeight = Math.min(targetHeight, usable.height - 20);

        int effectiveMinWidth = Math.min(minWidth, effectiveWidth);
        int effectiveMinHeight = Math.min(minHeight, effectiveHeight);

        dialog.setMinimumSize(new Dimension(effectiveMinWidth, effectiveMinHeight));
        dialog.setSize(new Dimension(effectiveWidth, effectiveHeight));

        if (parent != null && parent.isShowing()) {
            dialog.setLocationRelativeTo(parent);
            // Verify within usable bounds
            Rectangle current = dialog.getBounds();
            int x = Math.max(usable.x, Math.min(current.x, usable.x + usable.width - current.width));
            int y = Math.max(usable.y, Math.min(current.y, usable.y + usable.height - current.height));
            dialog.setLocation(x, y);
        } else {
            int x = usable.x + (usable.width - effectiveWidth) / 2;
            int y = usable.y + (usable.height - effectiveHeight) / 2;
            dialog.setLocation(x, y);
        }
    }
}
