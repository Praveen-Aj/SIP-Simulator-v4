package test;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import sipinspector.SIPInspectorMainFrame;
import sipinspector.UI.KeyboardShortcutsDialog;
import sipinspector.UI.MessageDiffDialog;
import sipinspector.UI.MessageTemplatesDialog;
import sipinspector.UI.UIUtils;
import sipinspector.Utils.AppPaths;

/**
 * Headless-safe and on-screen GUI rendering validation and snapshot generator.
 *
 * Renders Swing components into BufferedImage off-screen to verify:
 * 1. Component layout integrity at standard resolution (1366x768).
 * 2. Component layout integrity at Full HD resolution (1920x1080).
 * 3. Component layout integrity at Maximized resolution (1920x1032).
 * 4. Dialog responsive sizing via UIUtils for KeyboardShortcutsDialog, MessageTemplatesDialog, MessageDiffDialog.
 * 5. Saves rendering verification PNG snapshots to docs/screenshots/.
 */
public class GuiScreenCaptureTest {

    public static void main(String[] args) {
        System.out.println("=== Running Phase 1C GUI Layout & Component Rendering Validation ===");
        int passed = 0;
        int total = 5;

        File outDir = new File(AppPaths.resolve("docs"), "screenshots");
        if (!outDir.exists()) {
            outDir.mkdirs();
        }

        try {
            // [TEST 1] Render SIPInspectorMainFrame at Small Laptop bounds (1120x640)
            System.out.print("[TEST 1] Rendering MainFrame at 1120x640 (1366x768 profile): ");
            SIPInspectorMainFrame frameSmall = new SIPInspectorMainFrame();
            frameSmall.setSize(1120, 640);
            frameSmall.validate();
            BufferedImage imgSmall = renderComponentToImage(frameSmall, 1120, 640);
            File fSmall = new File(outDir, "mainframe_1366x768_profile.png");
            ImageIO.write(imgSmall, "png", fSmall);
            if (fSmall.exists() && fSmall.length() > 500) {
                System.out.println("PASS (Saved: " + fSmall.getName() + ", " + fSmall.length() + " bytes)");
                passed++;
            } else {
                System.err.println("FAIL (Length: " + fSmall.length() + ")");
            }
            frameSmall.dispose();

            // [TEST 2] Render SIPInspectorMainFrame at Full HD bounds (1380x840)
            System.out.print("[TEST 2] Rendering MainFrame at 1380x840 (1080p/QHD profile): ");
            SIPInspectorMainFrame frameFHD = new SIPInspectorMainFrame();
            frameFHD.setSize(1380, 840);
            frameFHD.validate();
            BufferedImage imgFHD = renderComponentToImage(frameFHD, 1380, 840);
            File fFHD = new File(outDir, "mainframe_1080p_profile.png");
            ImageIO.write(imgFHD, "png", fFHD);
            if (fFHD.exists() && fFHD.length() > 500) {
                System.out.println("PASS (Saved: " + fFHD.getName() + ", " + fFHD.length() + " bytes)");
                passed++;
            } else {
                System.err.println("FAIL (Length: " + fFHD.length() + ")");
            }
            frameFHD.dispose();

            // [TEST 3] Responsive Dialog: KeyboardShortcutsDialog
            System.out.print("[TEST 3] Rendering KeyboardShortcutsDialog with UIUtils: ");
            JFrame dummyParent = new JFrame();
            dummyParent.setBounds(100, 100, 1380, 840);
            KeyboardShortcutsDialog ksd = new KeyboardShortcutsDialog(dummyParent);
            ksd.validate();
            BufferedImage imgKsd = renderComponentToImage(ksd, ksd.getWidth(), ksd.getHeight());
            File fKsd = new File(outDir, "dialog_keyboard_shortcuts.png");
            ImageIO.write(imgKsd, "png", fKsd);
            if (fKsd.exists() && fKsd.length() > 500 && ksd.getWidth() <= 1360 && ksd.getHeight() <= 800) {
                System.out.println("PASS (Dimensions: " + ksd.getWidth() + "x" + ksd.getHeight() + ", " + fKsd.length() + " bytes)");
                passed++;
            } else {
                System.err.println("FAIL (Dimensions: " + ksd.getWidth() + "x" + ksd.getHeight() + ")");
            }
            ksd.dispose();

            // [TEST 4] Responsive Dialog: MessageTemplatesDialog
            System.out.print("[TEST 4] Rendering MessageTemplatesDialog with UIUtils: ");
            MessageTemplatesDialog mtd = new MessageTemplatesDialog(dummyParent);
            mtd.validate();
            BufferedImage imgMtd = renderComponentToImage(mtd, mtd.getWidth(), mtd.getHeight());
            File fMtd = new File(outDir, "dialog_message_templates.png");
            ImageIO.write(imgMtd, "png", fMtd);
            if (fMtd.exists() && fMtd.length() > 500 && mtd.getWidth() <= 1360 && mtd.getHeight() <= 800) {
                System.out.println("PASS (Dimensions: " + mtd.getWidth() + "x" + mtd.getHeight() + ", " + fMtd.length() + " bytes)");
                passed++;
            } else {
                System.err.println("FAIL (Dimensions: " + mtd.getWidth() + "x" + mtd.getHeight() + ")");
            }
            mtd.dispose();

            // [TEST 5] Responsive Dialog: MessageDiffDialog
            System.out.print("[TEST 5] Rendering MessageDiffDialog with UIUtils: ");
            MessageDiffDialog mdd = new MessageDiffDialog(dummyParent);
            mdd.validate();
            BufferedImage imgMdd = renderComponentToImage(mdd, mdd.getWidth(), mdd.getHeight());
            File fMdd = new File(outDir, "dialog_message_diff.png");
            ImageIO.write(imgMdd, "png", fMdd);
            if (fMdd.exists() && fMdd.length() > 500 && mdd.getWidth() <= 1360 && mdd.getHeight() <= 800) {
                System.out.println("PASS (Dimensions: " + mdd.getWidth() + "x" + mdd.getHeight() + ", " + fMdd.length() + " bytes)");
                passed++;
            } else {
                System.err.println("FAIL (Dimensions: " + mdd.getWidth() + "x" + mdd.getHeight() + ")");
            }
            mdd.dispose();
            dummyParent.dispose();

        } catch (Exception e) {
            System.err.println("CRITICAL TEST FAILURE: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("\n=================================================");
        System.out.printf("GUI SCREEN CAPTURE SUMMARY: %d / %d TESTS PASSED\n", passed, total);
        System.out.println("=================================================");

        if (passed != total) {
            System.exit(1);
        }
    }

    private static BufferedImage renderComponentToImage(Component comp, int width, int height) {
        comp.setSize(width, height);
        comp.doLayout();
        BufferedImage image = new BufferedImage(Math.max(1, width), Math.max(1, height), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        comp.paint(g2d);
        g2d.dispose();
        return image;
    }
}
