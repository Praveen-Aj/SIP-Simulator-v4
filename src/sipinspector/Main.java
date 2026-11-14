/*
 *  SIP Inspector - tool for VoIP engineers to create, duplicate and 
 *  troubleshoot SIP signaling. 
 * 
 *  Copyright (C) 2008  Zarko Coklin (zarko_coklin at yahoo dot com)
 * 
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 * 
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 * 
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package sipinspector;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;

import sipinspector.Utils.CustomThemes;
import sipinspector.versionUpdater.MessageDialog;

/**
 *
 * @author Zarko Coklin
 */
public class Main {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        // Initialize FlatLaf theme
        try {
            // Force classic menu bar placement (below title bar)
            UIManager.put("TitlePane.menuBarEmbedded", Boolean.FALSE);
            UIManager.put("TitlePane.unifiedBackground", Boolean.FALSE);

            // Check if user prefers dark mode
            String theme = null;
            try {
                theme = GlobalSettings.getSetting("theme");
            } catch (Exception e) {
                // Settings not available yet, use default
                theme = "light";
            }

            if ("dark".equals(theme)) {
                FlatDarkLaf.setup();
            } else if ("intellij".equals(theme)) {
                com.formdev.flatlaf.FlatIntelliJLaf.setup();
            } else if ("darcula".equals(theme)) {
                com.formdev.flatlaf.FlatDarculaLaf.setup();
            } else if ("ocean".equals(theme)) {
                CustomThemes.applyOceanTheme();
            } else if ("sunset".equals(theme)) {
                CustomThemes.applySunsetTheme();
            } else if ("forest".equals(theme)) {
                CustomThemes.applyForestTheme();
            } else if ("purple".equals(theme)) {
                CustomThemes.applyPurpleTheme();
            } else {
                // Default to light theme
                FlatLightLaf.setup();
            }

            // Apply standard fonts for custom themes (emoji-capable where available)
            CustomThemes.applyStandardFonts();

            // Ensure consistent component sizes

            UIManager.put("Component.minimumWidth", 0);
            UIManager.put("Button.arc", 5);
            UIManager.put("Component.arc", 5);
            UIManager.put("TextComponent.arc", 5);

        } catch (Exception e) {
            // Fallback to system Look and Feel if FlatLaf fails
            System.err.println("✗ Failed to load FlatLaf: " + e.getMessage());
            e.printStackTrace();
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                System.out.println("✓ Using system Look & Feel as fallback");
            } catch (Exception ex) {
                System.err.println("✗ Failed to set system L&F: " + ex.getMessage());
            }
        }

        SwingUtilities.invokeLater(new Runnable() {

            public void run() {
                SIPInspectorMainFrame mainFrame = new SIPInspectorMainFrame();
                new MessageDialog(mainFrame);
            }
        });
    }
}
