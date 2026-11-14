/*
 * Emoji Icon Helper
 * Creates colored emoji icons for menu items
 */
package sipinspector.Utils;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;

import javax.swing.ImageIcon;

/**
 * Helper class to create lightweight vector icons without relying on system
 * emoji fonts.
 */
public class EmojiIcon {
    private static final int DEFAULT_SIZE = 18;

    @FunctionalInterface
    private interface IconPainter {
        void paint(Graphics2D g2d, int size);
    }

    private static ImageIcon createIcon(int size, IconPainter painter) {
        BufferedImage image = new BufferedImage(size, size,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                RenderingHints.VALUE_STROKE_PURE);
        painter.paint(g2d, size);
        g2d.dispose();
        return new ImageIcon(image);
    }

    public static ImageIcon getFileIcon() {
        return createIcon(DEFAULT_SIZE, (g2d, size) -> {
            int tabHeight = size / 3;
            Polygon tab = new Polygon(
                    new int[] { 3, size / 2, size / 2 + 2, 3 },
                    new int[] { size / 3 - 3, size / 3 - 3, size / 3 + 2,
                            size / 3 + 2 },
                    4);

            g2d.setColor(new Color(255, 213, 128));
            g2d.fillPolygon(tab);
            g2d.fillRoundRect(2, tabHeight, size - 4, size / 2 + 2, 4, 4);
            g2d.setColor(new Color(245, 178, 62));
            g2d.drawRoundRect(2, tabHeight, size - 4, size / 2 + 2, 4, 4);
        });
    }

    public static ImageIcon getEditIcon() {
        return createIcon(DEFAULT_SIZE, (g2d, size) -> {
            int bodyY = size / 2 - 3;
            g2d.setColor(new Color(255, 204, 128));
            g2d.fillRoundRect(3, bodyY, size - 6, 6, 4, 4);
            g2d.setColor(new Color(255, 171, 64));
            g2d.drawRoundRect(3, bodyY, size - 6, 6, 4, 4);

            Polygon tip = new Polygon(
                    new int[] { size - 6, size - 3, size - 6 },
                    new int[] { bodyY - 2, bodyY + 3, bodyY + 8 }, 3);
            g2d.setColor(new Color(94, 53, 177));
            g2d.fillPolygon(tip);
        });
    }

    public static ImageIcon getOptionsIcon() {
        return createIcon(DEFAULT_SIZE, (g2d, size) -> {
            g2d.setStroke(new BasicStroke(2f));
            g2d.setColor(new Color(120, 144, 156));
            g2d.drawLine(3, size / 3, size - 3, size / 3);
            g2d.drawLine(3, size / 2 + 2, size - 3, size / 2 + 2);
            g2d.drawLine(3, size - 4, size - 3, size - 4);

            g2d.setColor(new Color(100, 181, 246));
            g2d.fillOval(size / 2 - 4, size / 3 - 4, 8, 8);
            g2d.setColor(new Color(129, 199, 132));
            g2d.fillOval(size / 3 - 3, size / 2, 8, 8);
            g2d.setColor(new Color(239, 83, 80));
            g2d.fillOval(size - 11, size - 7, 8, 8);
        });
    }

    public static ImageIcon getToolsIcon() {
        return createIcon(DEFAULT_SIZE, (g2d, size) -> {
            g2d.setStroke(new BasicStroke(2f));
            g2d.setColor(new Color(33, 150, 243));
            g2d.drawLine(4, size - 4, size - 4, 4);
            g2d.drawLine(size / 3, size - 4, size - 4, size / 3);

            GeneralPath wrench = new GeneralPath();
            wrench.moveTo(size / 2 + 2, size / 3);
            wrench.quadTo(size - 2, size / 5, size - 2, size / 2);
            wrench.quadTo(size - 2, size / 2 + 2, size / 2 + 2, size / 2 + 2);
            wrench.lineTo(size / 2, size - 4);
            wrench.lineTo(size / 2 - 2, size - 4);
            wrench.closePath();
            g2d.setColor(new Color(255, 213, 79));
            g2d.fill(wrench);
            g2d.setColor(new Color(251, 192, 45));
            g2d.draw(wrench);
        });
    }

    public static ImageIcon getHelpIcon() {
        return createIcon(DEFAULT_SIZE, (g2d, size) -> {
            g2d.setColor(new Color(144, 202, 249));
            g2d.fillOval(2, 2, size - 4, size - 4);
            g2d.setColor(new Color(21, 101, 192));
            g2d.drawOval(2, 2, size - 4, size - 4);
            g2d.setFont(new Font("Segoe UI", Font.BOLD, size / 2));
            FontMetrics fm = g2d.getFontMetrics();
            String q = "?";
            int x = (size - fm.stringWidth(q)) / 2;
            int y = (size - fm.getHeight()) / 2 + fm.getAscent();
            g2d.drawString(q, x, y);
        });
    }

    public static ImageIcon getOpenFolderIcon() {
        return createIcon(DEFAULT_SIZE, (g2d, size) -> {
            int baseY = size / 2;
            g2d.setColor(new Color(255, 224, 178));
            Polygon open = new Polygon(
                    new int[] { 2, size - 3, size - 5, 4 },
                    new int[] { baseY - 2, baseY - 2, size - 4, size - 4 }, 4);
            g2d.fillPolygon(open);
            g2d.setColor(new Color(255, 183, 77));
            g2d.drawPolygon(open);

            Polygon tab = new Polygon(new int[] { 4, size / 2, size / 2 + 1, 4 },
                    new int[] { baseY - 6, baseY - 6, baseY - 2, baseY - 2 }, 4);
            g2d.fillPolygon(tab);
        });
    }

    public static ImageIcon getSaveIcon() {
        return createIcon(DEFAULT_SIZE, (g2d, size) -> {
            g2d.setColor(new Color(100, 181, 246));
            g2d.fillRoundRect(3, 3, size - 6, size - 6, 4, 4);
            g2d.setColor(new Color(30, 136, 229));
            g2d.drawRoundRect(3, 3, size - 6, size - 6, 4, 4);
            g2d.setColor(new Color(255, 255, 255));
            g2d.fillRect(5, 5, size - 10, size / 3);
            g2d.setColor(new Color(30, 136, 229));
            g2d.fillRect(7, 7, size / 2, size / 6);
            g2d.setColor(new Color(255, 255, 255));
            g2d.fillRoundRect(5, size / 2, size - 10, size / 3, 3, 3);
        });
    }

    public static ImageIcon getSearchIcon() {
        return createIcon(DEFAULT_SIZE, (g2d, size) -> {
            g2d.setStroke(new BasicStroke(2f));
            g2d.setColor(new Color(158, 158, 158));
            g2d.drawOval(3, 3, size / 2, size / 2);
            g2d.drawLine(size / 2, size / 2, size - 4, size - 4);
        });
    }

    public static ImageIcon getStatsIcon() {
        return createIcon(DEFAULT_SIZE, (g2d, size) -> {
            g2d.setColor(new Color(120, 144, 156));
            g2d.fillRect(3, size - 5, size - 6, 2);
            g2d.setColor(new Color(93, 173, 226));
            g2d.fillRect(5, size - 8, 3, 5);
            g2d.setColor(new Color(46, 204, 113));
            g2d.fillRect(size / 2 - 1, size - 11, 3, 8);
            g2d.setColor(new Color(244, 143, 177));
            g2d.fillRect(size - 8, size - 14, 3, 11);
        });
    }

    public static ImageIcon getStatusOkIcon() {
        return createIcon(DEFAULT_SIZE, (g2d, size) -> {
            g2d.setColor(new Color(46, 204, 113));
            g2d.fillOval(2, 2, size - 4, size - 4);
            g2d.setColor(Color.WHITE);
            g2d.setStroke(new BasicStroke(2f));
            g2d.drawLine(size / 3, size / 2, size / 2, size - 5);
            g2d.drawLine(size / 2, size - 5, size - 4, 4);
        });
    }

    public static ImageIcon getStatusWarningIcon() {
        return createIcon(DEFAULT_SIZE, (g2d, size) -> {
            Polygon tri = new Polygon(new int[] { size / 2, 3, size - 3 },
                    new int[] { 3, size - 3, size - 3 }, 3);
            g2d.setColor(new Color(255, 193, 7));
            g2d.fillPolygon(tri);
            g2d.setColor(new Color(230, 126, 34));
            g2d.setStroke(new BasicStroke(2f));
            g2d.drawPolygon(tri);
            g2d.drawLine(size / 2, size / 2 - 3, size / 2, size - 7);
            g2d.drawLine(size / 2, size - 5, size / 2, size - 4);
        });
    }

    public static ImageIcon getRunIcon() {
        return createIcon(DEFAULT_SIZE, (g2d, size) -> {
            Polygon play = new Polygon(new int[] { 4, size - 4, 4 },
                    new int[] { 3, size / 2, size - 3 }, 3);
            g2d.setColor(new Color(76, 175, 80));
            g2d.fillPolygon(play);
        });
    }

    public static ImageIcon getArrowUpIcon() {
        return createIcon(DEFAULT_SIZE, (g2d, size) -> {
            Polygon arrow = new Polygon(new int[] { size / 2, 3, size - 3 },
                    new int[] { 3, size - 4, size - 4 }, 3);
            g2d.setColor(new Color(63, 81, 181));
            g2d.fillPolygon(arrow);
        });
    }

    public static ImageIcon getArrowDownIcon() {
        return createIcon(DEFAULT_SIZE, (g2d, size) -> {
            Polygon arrow = new Polygon(new int[] { 3, size - 3, size / 2 },
                    new int[] { 3, 3, size - 4 }, 3);
            g2d.setColor(new Color(229, 57, 53));
            g2d.fillPolygon(arrow);
        });
    }

    public static ImageIcon getCheckIcon() {
        return createIcon(DEFAULT_SIZE, (g2d, size) -> {
            g2d.setColor(new Color(76, 175, 80));
            g2d.fillOval(2, 2, size - 4, size - 4);
            g2d.setColor(Color.WHITE);
            g2d.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2d.drawLine(size / 3 - 1, size / 2, size / 2 - 1, size - 6);
            g2d.drawLine(size / 2 - 1, size - 6, size - 5, 5);
        });
    }

    public static ImageIcon getCloseIcon() {
        return createIcon(DEFAULT_SIZE, (g2d, size) -> {
            g2d.setColor(new Color(239, 83, 80));
            g2d.fillOval(2, 2, size - 4, size - 4);
            g2d.setColor(Color.WHITE);
            g2d.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2d.drawLine(size / 3, size / 3, size - size / 3, size - size / 3);
            g2d.drawLine(size - size / 3, size / 3, size / 3, size - size / 3);
        });
    }

    public static ImageIcon getTemplateIcon() {
        return createIcon(DEFAULT_SIZE, (g2d, size) -> {
            g2d.setColor(new Color(255, 255, 255));
            g2d.fillRoundRect(3, 2, size - 6, size - 4, 3, 3);
            g2d.setColor(new Color(120, 144, 156));
            g2d.drawRoundRect(3, 2, size - 6, size - 4, 3, 3);
            g2d.setColor(new Color(66, 165, 245));
            g2d.fillRect(6, 6, size - 12, 2);
            g2d.fillRect(6, size / 2 - 1, size - 12, 2);
            g2d.setColor(new Color(158, 158, 158));
            g2d.fillRect(6, size - 8, (size - 12) * 2 / 3, 2);
        });
    }

    public static ImageIcon getPreviewIcon() {
        return createIcon(DEFAULT_SIZE, (g2d, size) -> {
            g2d.setStroke(new BasicStroke(1.5f));
            g2d.setColor(new Color(66, 165, 245));
            java.awt.geom.Ellipse2D eye = new java.awt.geom.Ellipse2D.Float(2, size / 3f, size - 4, size / 3f);
            g2d.draw(eye);
            g2d.setColor(new Color(30, 136, 229));
            g2d.fillOval(size / 2 - 3, size / 2 - 3, 6, 6);
        });
    }

    public static ImageIcon getExportImageIcon() {
        return createIcon(DEFAULT_SIZE, (g2d, size) -> {
            g2d.setColor(new Color(255, 255, 255));
            g2d.fillRoundRect(2, 3, size - 4, size - 6, 2, 2);
            g2d.setColor(new Color(120, 144, 156));
            g2d.drawRoundRect(2, 3, size - 4, size - 6, 2, 2);
            g2d.setColor(new Color(255, 213, 79));
            g2d.fillOval(size - 9, 5, 4, 4);
            Polygon mountain = new Polygon(
                    new int[] { 4, size / 2 - 2, size / 2 + 3, size - 4 },
                    new int[] { size - 5, size / 2, size / 2 + 3, size - 5 }, 4);
            g2d.setColor(new Color(102, 187, 106));
            g2d.fillPolygon(mountain);
        });
    }

    public static ImageIcon getExportPdfIcon() {
        return createIcon(DEFAULT_SIZE, (g2d, size) -> {
            Polygon page = new Polygon(
                    new int[] { 4, size - 7, size - 3, size - 3, 4 },
                    new int[] { 2, 2, 6, size - 2, size - 2 }, 5);
            g2d.setColor(new Color(255, 255, 255));
            g2d.fillPolygon(page);
            g2d.setColor(new Color(120, 144, 156));
            g2d.drawPolygon(page);
            g2d.setColor(new Color(239, 83, 80));
            g2d.fillRoundRect(3, size / 2 - 3, size - 8, 8, 2, 2);
            g2d.setFont(new Font("SansSerif", Font.BOLD, 6));
            g2d.setColor(Color.WHITE);
            g2d.drawString("PDF", 4, size / 2 + 3);
        });
    }

    public static ImageIcon getCompareIcon() {
        return createIcon(DEFAULT_SIZE, (g2d, size) -> {
            g2d.setStroke(new BasicStroke(2f));
            g2d.setColor(new Color(120, 144, 156));
            g2d.drawLine(size / 2, 2, size / 2, size - 2);
            g2d.setColor(new Color(66, 165, 245));
            g2d.drawArc(2, size / 2 - 4, size / 2 - 1, 8, 0, 180);
            g2d.setColor(new Color(239, 83, 80));
            g2d.drawArc(size / 2, size / 2 - 4, size / 2 - 1, 8, 180, 180);
        });
    }

    public static ImageIcon getSwapIcon() {
        return createIcon(DEFAULT_SIZE, (g2d, size) -> {
            g2d.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2d.setColor(new Color(126, 87, 194));
            g2d.drawLine(4, size / 3, size - 6, size / 3);
            Polygon rightArrow = new Polygon(
                    new int[] { size - 3, size - 8, size - 8 },
                    new int[] { size / 3, size / 3 - 4, size / 3 + 4 }, 3);
            g2d.fillPolygon(rightArrow);
            g2d.drawLine(6, size - size / 3, size - 4, size - size / 3);
            Polygon leftArrow = new Polygon(
                    new int[] { 3, 8, 8 },
                    new int[] { size - size / 3, size - size / 3 - 4, size - size / 3 + 4 }, 3);
            g2d.fillPolygon(leftArrow);
        });
    }

    public static ImageIcon getToneIcon() {
        return createIcon(DEFAULT_SIZE, (g2d, size) -> {
            g2d.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2d.setColor(new Color(255, 152, 0));
            java.awt.geom.Path2D wave = new java.awt.geom.Path2D.Float();
            wave.moveTo(2, size / 2);
            wave.curveTo(size / 4f, 2, size / 4f, size - 4, size / 2f, size / 2f);
            wave.curveTo(size * 3 / 4f, 2, size * 3 / 4f, size - 4, size - 2, size / 2f);
            g2d.draw(wave);
        });
    }

    public static ImageIcon getStopToneIcon() {
        return createIcon(DEFAULT_SIZE, (g2d, size) -> {
            g2d.setColor(new Color(239, 83, 80));
            g2d.fillRoundRect(4, 4, size - 8, size - 8, 2, 2);
        });
    }

}
