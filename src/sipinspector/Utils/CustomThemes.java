/*
 * Custom Color Themes for SIP Inspector
 * Provides colorful theme options beyond standard FlatLaf themes
 */
package sipinspector.Utils;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.List;

import javax.swing.UIManager;

import com.formdev.flatlaf.FlatLightLaf;

/**
 * Custom colorful themes for SIP Inspector
 * 
 * @author SIP Support Team
 */
public class CustomThemes {

    /**
     * Ocean Blue Theme - Calm blue colors
     */
    public static void applyOceanTheme() {
        try {
            FlatLightLaf.setup();

            // Ocean blue color palette
            Color oceanBlue = new Color(41, 128, 185); // Primary blue
            Color oceanLight = new Color(52, 152, 219); // Light blue
            Color oceanDark = new Color(21, 67, 96); // Dark blue
            Color oceanAccent = new Color(26, 188, 156); // Teal accent
            Color oceanTabBg = new Color(214, 234, 248);
            Color oceanSelected = new Color(72, 149, 239);

            UIManager.put("Button.background", oceanLight);
            UIManager.put("Button.foreground", Color.WHITE);
            UIManager.put("Button.hoverBackground", oceanBlue);
            UIManager.put("Button.pressedBackground", oceanDark);

            UIManager.put("Panel.background", new Color(236, 240, 241));
            UIManager.put("TabbedPane.selectedBackground", oceanSelected);
            UIManager.put("TabbedPane.background", oceanTabBg);
            UIManager.put("TabbedPane.foreground", oceanDark);
            UIManager.put("TabbedPane.selectedForeground", Color.WHITE);
            UIManager.put("TabbedPane.underlineColor", oceanAccent);
            UIManager.put("TabbedPane.tabAreaBackground", oceanTabBg);
            UIManager.put("TabbedPane.contentAreaColor", new Color(236, 240, 241));
            UIManager.put("TabbedPane.disabledForeground", oceanDark.darker());
            UIManager.put("TabbedPane.focusColor", oceanAccent);
            UIManager.put("TabbedPane.hoverColor", oceanAccent);

            UIManager.put("Component.focusColor", oceanAccent);
            UIManager.put("Component.borderColor", oceanBlue);

        } catch (Exception e) {
            System.err.println("Failed to apply Ocean theme: " + e.getMessage());
        }
    }

    /**
     * Sunset Orange Theme - Warm sunset colors
     */
    public static void applySunsetTheme() {
        try {
            FlatLightLaf.setup();

            // Sunset color palette
            Color sunsetOrange = new Color(230, 126, 34); // Primary orange
            Color sunsetRed = new Color(231, 76, 60); // Red accent
            Color sunsetYellow = new Color(241, 196, 15); // Yellow highlight
            Color sunsetPurple = new Color(142, 68, 173); // Purple shadow
            Color sunsetTabBg = new Color(252, 237, 207);
            Color sunsetSelected = new Color(242, 143, 46);

            UIManager.put("Button.background", sunsetOrange);
            UIManager.put("Button.foreground", Color.WHITE);
            UIManager.put("Button.hoverBackground", sunsetRed);
            UIManager.put("Button.pressedBackground", sunsetPurple);

            UIManager.put("Panel.background", new Color(254, 249, 231));
            UIManager.put("TabbedPane.selectedBackground", sunsetSelected);
            UIManager.put("TabbedPane.background", sunsetTabBg);
            UIManager.put("TabbedPane.foreground", new Color(111, 78, 55));
            UIManager.put("TabbedPane.selectedForeground", Color.WHITE);
            UIManager.put("TabbedPane.underlineColor", sunsetYellow);
            UIManager.put("TabbedPane.tabAreaBackground", sunsetTabBg);
            UIManager.put("TabbedPane.contentAreaColor", new Color(254, 249, 231));
            UIManager.put("TabbedPane.disabledForeground", new Color(158, 110, 66));
            UIManager.put("TabbedPane.focusColor", sunsetYellow);
            UIManager.put("TabbedPane.hoverColor", sunsetYellow);

            UIManager.put("Component.focusColor", sunsetYellow);
            UIManager.put("Component.borderColor", sunsetOrange);

        } catch (Exception e) {
            System.err.println("Failed to apply Sunset theme: " + e.getMessage());
        }
    }

    /**
     * Forest Green Theme - Natural green colors
     */
    public static void applyForestTheme() {
        try {
            FlatLightLaf.setup();

            // Forest green color palette
            Color forestGreen = new Color(39, 174, 96); // Primary green
            Color forestDark = new Color(22, 160, 133); // Dark green
            Color forestLight = new Color(46, 204, 113); // Light green
            Color forestBrown = new Color(121, 85, 72); // Brown accent
            Color forestTabBg = new Color(223, 240, 216);
            Color forestSelected = new Color(63, 190, 123);

            UIManager.put("Button.background", forestGreen);
            UIManager.put("Button.foreground", Color.WHITE);
            UIManager.put("Button.hoverBackground", forestDark);
            UIManager.put("Button.pressedBackground", forestBrown);

            UIManager.put("Panel.background", new Color(232, 246, 233));
            UIManager.put("TabbedPane.selectedBackground", forestSelected);
            UIManager.put("TabbedPane.background", forestTabBg);
            UIManager.put("TabbedPane.foreground", new Color(27, 94, 32));
            UIManager.put("TabbedPane.selectedForeground", Color.WHITE);
            UIManager.put("TabbedPane.underlineColor", forestLight);
            UIManager.put("TabbedPane.tabAreaBackground", forestTabBg);
            UIManager.put("TabbedPane.contentAreaColor", new Color(232, 246, 233));
            UIManager.put("TabbedPane.disabledForeground", new Color(18, 73, 22));
            UIManager.put("TabbedPane.focusColor", forestLight);
            UIManager.put("TabbedPane.hoverColor", forestLight);

            UIManager.put("Component.focusColor", forestLight);
            UIManager.put("Component.borderColor", forestGreen);

        } catch (Exception e) {
            System.err.println("Failed to apply Forest theme: " + e.getMessage());
        }
    }

    /**
     * Purple Dream Theme - Elegant purple colors
     */
    public static void applyPurpleTheme() {
        try {
            FlatLightLaf.setup();

            // Purple color palette
            Color purplePrimary = new Color(155, 89, 182); // Primary purple
            Color purpleDark = new Color(142, 68, 173); // Dark purple
            Color purpleLight = new Color(187, 143, 206); // Light purple
            Color purplePink = new Color(232, 67, 147); // Pink accent
            Color purpleTabBg = new Color(237, 231, 246);
            Color purpleSelected = new Color(168, 103, 198);

            UIManager.put("Button.background", purplePrimary);
            UIManager.put("Button.foreground", Color.WHITE);
            UIManager.put("Button.hoverBackground", purpleDark);
            UIManager.put("Button.pressedBackground", purplePink);

            UIManager.put("Panel.background", new Color(243, 238, 246));
            UIManager.put("TabbedPane.selectedBackground", purpleSelected);
            UIManager.put("TabbedPane.background", purpleTabBg);
            UIManager.put("TabbedPane.foreground", new Color(74, 20, 140));
            UIManager.put("TabbedPane.selectedForeground", Color.WHITE);
            UIManager.put("TabbedPane.underlineColor", purpleLight);
            UIManager.put("TabbedPane.tabAreaBackground", purpleTabBg);
            UIManager.put("TabbedPane.contentAreaColor", new Color(243, 238, 246));
            UIManager.put("TabbedPane.disabledForeground", new Color(55, 10, 120));
            UIManager.put("TabbedPane.focusColor", purplePink);
            UIManager.put("TabbedPane.hoverColor", purpleLight);

            UIManager.put("Component.focusColor", purplePink);
            UIManager.put("Component.borderColor", purplePrimary);

        } catch (Exception e) {
            System.err.println("Failed to apply Purple theme: " + e.getMessage());
        }
    }

    /**
     * Midnight Blue Theme - Dark blue professional theme
     */
    public static void applyMidnightTheme() {
        try {
            FlatLightLaf.setup();

            // Midnight color palette
            Color midnightBlue = new Color(44, 62, 80); // Primary dark blue
            Color midnightLight = new Color(52, 73, 94); // Light blue
            Color midnightAccent = new Color(52, 152, 219); // Bright blue accent
            Color midnightText = new Color(236, 240, 241); // Light text
            Color midnightSelected = new Color(64, 167, 229);

            UIManager.put("Panel.background", midnightBlue);
            UIManager.put("Button.background", midnightLight);
            UIManager.put("Button.foreground", midnightText);
            UIManager.put("Button.hoverBackground", midnightAccent);

            UIManager.put("TextField.background", midnightLight);
            UIManager.put("TextField.foreground", midnightText);
            UIManager.put("TextArea.background", midnightLight);
            UIManager.put("TextArea.foreground", midnightText);

            UIManager.put("Label.foreground", midnightText);
            UIManager.put("TabbedPane.selectedBackground", midnightSelected);
            UIManager.put("TabbedPane.background", midnightBlue);
            UIManager.put("TabbedPane.foreground", midnightText);
            UIManager.put("TabbedPane.selectedForeground", midnightText);
            UIManager.put("TabbedPane.underlineColor", midnightAccent);
            UIManager.put("TabbedPane.tabAreaBackground", midnightBlue);
            UIManager.put("TabbedPane.contentAreaColor", midnightBlue);
            UIManager.put("TabbedPane.disabledForeground", midnightText.darker());
            UIManager.put("TabbedPane.focusColor", midnightAccent);

            UIManager.put("Component.focusColor", midnightAccent);
            UIManager.put("Component.borderColor", midnightAccent);

        } catch (Exception e) {
            System.err.println("Failed to apply Midnight theme: " + e.getMessage());
        }
    }

    /**
     * Apply standard font settings after theme application
     */
    public static void applyStandardFonts() {
        registerExternalEmojiFont();
        Font baseFont = getEmojiCapableFont(Font.PLAIN, 13);
        Font boldFont = baseFont.deriveFont(Font.BOLD);
        Font italicFont = baseFont.deriveFont(Font.ITALIC);

        UIManager.put("defaultFont", baseFont);
        UIManager.put("Label.font", baseFont);
        UIManager.put("Button.font", baseFont);
        UIManager.put("TextField.font", baseFont);
        UIManager.put("ComboBox.font", baseFont);
        UIManager.put("TextArea.font", baseFont);
        UIManager.put("Menu.font", baseFont);
        UIManager.put("MenuItem.font", baseFont);
        UIManager.put("CheckBoxMenuItem.font", baseFont);
        UIManager.put("RadioButtonMenuItem.font", baseFont);
        UIManager.put("TabbedPane.font", boldFont);
        UIManager.put("Table.font", baseFont);
        UIManager.put("TableHeader.font", boldFont);
        UIManager.put("ToolTip.font", italicFont);

        UIManager.put("Component.minimumWidth", 0);
        UIManager.put("Button.arc", 5);
        UIManager.put("Component.arc", 5);
        UIManager.put("TextComponent.arc", 5);
    }

    private static Font getEmojiCapableFont(int style, int size) {
        // Try modern, clean fonts in order of preference
        String[] preferredFamilies = {
                "Segoe UI", // Modern Windows font (clean, professional)
                "Roboto", // Android/Google font (if installed)
                "SF Pro Display", // macOS font (if installed)
                "Helvetica Neue", // Clean sans-serif
                "Calibri", // Microsoft Office font
                "Tahoma", // Classic Windows font
                "Verdana", // Web-safe font
                "Arial", // Universal fallback
                "Segoe UI Emoji", // Emoji support
                "Segoe UI Symbol" // Symbol support
        };

        List<String> availableFamilies = Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment()
                        .getAvailableFontFamilyNames());

        for (String family : preferredFamilies) {
            if (availableFamilies.contains(family)) {
                return new Font(family, style, size);
            }
        }

        return new Font(Font.SANS_SERIF, style, size);
    }

    private static void registerExternalEmojiFont() {
        try {
            java.io.File fontDir = new java.io.File("fonts");
            if (!fontDir.exists())
                return;
            java.io.File noto = new java.io.File(fontDir, "NotoEmoji-Regular.ttf");
            if (noto.exists()) {
                java.awt.Font f = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, noto);
                GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(f);
            }
        } catch (Exception ignore) {
        }
    }
}
