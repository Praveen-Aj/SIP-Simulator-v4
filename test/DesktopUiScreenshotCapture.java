package test;

import sipinspector.SIPInspectorMainFrame;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;

/**
 * QA Test Utility: Captures high-resolution visual evidence of all Desktop UI tabs and cards.
 * Non-production test utility.
 */
public class DesktopUiScreenshotCapture {

    private static final String[] DESKTOP_TABS = {
        "Scenario Editor",
        "Scenario & Values",
        "Edit Values",
        "Logs",
        "Campaigns",
        "Network & Protocol Testing",
        "RTP Inspector",
        "SIP Message Explorer"
    };

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("   CAPTURING DESKTOP UI SWING WORKSTATION SCREENSHOTS");
        System.out.println("================================================================================");

        File evidenceDir = new File("test/evidence");
        if (!evidenceDir.exists()) {
            evidenceDir.mkdirs();
        }

        SwingUtilities.invokeLater(() -> {
            try {
                // Apply FlatLaf Dark theme if available
                try {
                    com.formdev.flatlaf.FlatDarkLaf.setup();
                } catch (Throwable t) {
                    // Ignore theme error
                }

                SIPInspectorMainFrame frame = new SIPInspectorMainFrame();
                frame.setSize(1280, 800);
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
                Thread.sleep(500);

                Field cardPanelField = SIPInspectorMainFrame.class.getDeclaredField("cardPanel");
                cardPanelField.setAccessible(true);
                JPanel cardPanel = (JPanel) cardPanelField.get(frame);
                CardLayout cardLayout = (CardLayout) cardPanel.getLayout();

                // 1. Capture Starting Panel (interview_card)
                cardLayout.show(cardPanel, "interview_card");
                frame.validate();
                frame.doLayout();
                captureComponent(frame, new File(evidenceDir, "desktop_00_Starting_Panel.png"));
                System.out.println("   [Card 1/3] Card: Starting Interview Panel -> desktop_00_Starting_Panel.png");

                // 2. Switch to program_card and capture all 8 tabs
                cardLayout.show(cardPanel, "program_card");
                frame.validate();
                frame.doLayout();

                for (int i = 0; i < DESKTOP_TABS.length; i++) {
                    String tabTitle = DESKTOP_TABS[i];
                    frame.selectMainTab(tabTitle);
                    frame.validate();
                    frame.doLayout();

                    String safeTitle = tabTitle.replaceAll("[^a-zA-Z0-9_\\-]", "_");
                    String filename = String.format("desktop_%02d_%s.png", (i + 1), safeTitle);
                    File outFile = new File(evidenceDir, filename);
                    captureComponent(frame, outFile);
                    System.out.println(String.format("   [Tab %d/%d] Tab: %-30s -> %s (%d bytes)",
                            (i + 1), DESKTOP_TABS.length, tabTitle, filename, outFile.length()));
                }

                // 3. Capture statistics_card
                cardLayout.show(cardPanel, "statistics_card");
                frame.validate();
                frame.doLayout();
                captureComponent(frame, new File(evidenceDir, "desktop_09_Statistics_Card.png"));
                System.out.println("   [Card 3/3] Card: Statistics Card -> desktop_09_Statistics_Card.png");

                System.out.println("\n>>> ALL DESKTOP SCREENSHOTS CAPTURED SUCCESSFULLY!");
                frame.dispose();
                System.exit(0);
            } catch (Throwable t) {
                System.err.println("Error capturing desktop screenshots: " + t.getMessage());
                t.printStackTrace();
                System.exit(1);
            }
        });
    }

    private static void captureComponent(Component comp, File outFile) throws Exception {
        BufferedImage image = new BufferedImage(1280, 800, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setColor(new Color(30, 30, 30));
        g2d.fillRect(0, 0, 1280, 800);
        comp.printAll(g2d);
        g2d.dispose();
        ImageIO.write(image, "PNG", outFile);
    }
}
