# 🎨 Phase 2 Implementation Guide - Modern Look & Feel

## 📋 Overview

**Goal:** Transform SIP Inspector with a modern, professional UI

**Timeline:** 1-2 weeks

**Features:**
1. ✅ FlatLaf modern theme
2. ✅ Dark mode support
3. ✅ Enhanced status bar
4. ✅ Keyboard shortcuts
5. ✅ Statistics dashboard

---

## 🚀 Step 1: Download FlatLaf Library

### Option A: Direct Download
1. Go to: https://repo1.maven.org/maven2/com/formdev/flatlaf/3.2.5/
2. Download: `flatlaf-3.2.5.jar`
3. Save to: `SIPInspector/trunk/lib/flatlaf-3.2.5.jar`

### Option B: Maven Repository
```xml
<!-- For reference -->
<dependency>
    <groupId>com.formdev</groupId>
    <artifactId>flatlaf</artifactId>
    <version>3.2.5</version>
</dependency>
```

### Direct Download Link:
```
https://repo1.maven.org/maven2/com/formdev/flatlaf/3.2.5/flatlaf-3.2.5.jar
```

---

## 📦 Step 2: Integration Steps

### 1. Add JAR to lib folder
```cmd
# Place flatlaf-3.2.5.jar in:
trunk/lib/flatlaf-3.2.5.jar
```

### 2. Update build.bat
Add FlatLaf to manifest Class-Path:
```batch
echo Class-Path: lib/miglayout-3.7.3.1-swing.jar lib/miglayout-swing-4.2.jar lib/jpcap.jar lib/flatlaf-3.2.5.jar >> build\MANIFEST.MF
```

### 3. Modify SIPInspectorMainFrame.java
Add FlatLaf initialization in main method:
```java
public static void main(String args[]) {
    // Set FlatLaf Look and Feel BEFORE creating GUI
    try {
        UIManager.setLookAndFeel(new FlatLightLaf());
    } catch (Exception ex) {
        System.err.println("Failed to initialize FlatLaf");
    }
    
    // Rest of existing code...
    java.awt.EventQueue.invokeLater(new Runnable() {
        public void run() {
            new SIPInspectorMainFrame().setVisible(true);
        }
    });
}
```

---

## 🎨 Step 3: Theme Implementation

### Light Theme (Default)
```java
import com.formdev.flatlaf.FlatLightLaf;

UIManager.setLookAndFeel(new FlatLightLaf());
```

### Dark Theme
```java
import com.formdev.flatlaf.FlatDarkLaf;

UIManager.setLookAndFeel(new FlatDarkLaf());
```

### IntelliJ Theme
```java
import com.formdev.flatlaf.FlatIntelliJLaf;

UIManager.setLookAndFeel(new FlatIntelliJLaf());
```

### Darcula Theme
```java
import com.formdev.flatlaf.FlatDarculaLaf;

UIManager.setLookAndFeel(new FlatDarculaLaf());
```

---

## 🔧 Step 4: Add Theme Switcher

### Add to Options Menu:
```java
// In SIPInspectorMainFrame.java initComponents()

JMenu themeMenu = new JMenu("Theme");

JMenuItem lightThemeItem = new JMenuItem("Light Theme");
lightThemeItem.addActionListener(evt -> switchTheme("light"));
themeMenu.add(lightThemeItem);

JMenuItem darkThemeItem = new JMenuItem("Dark Theme");
darkThemeItem.addActionListener(evt -> switchTheme("dark"));
themeMenu.add(darkThemeItem);

OptionsMenu.add(themeMenu);
```

### Add Theme Switcher Method:
```java
private void switchTheme(String theme) {
    try {
        if ("light".equals(theme)) {
            UIManager.setLookAndFeel(new FlatLightLaf());
        } else if ("dark".equals(theme)) {
            UIManager.setLookAndFeel(new FlatDarkLaf());
        }
        
        // Update all components
        SwingUtilities.updateComponentTreeUI(this);
        
        // Save preference
        GlobalSettings.setSetting("theme", theme);
        
        JOptionPane.showMessageDialog(this,
            "Theme changed to: " + theme,
            "Theme Switcher",
            JOptionPane.INFORMATION_MESSAGE);
    } catch (Exception ex) {
        JOptionPane.showMessageDialog(this,
            "Failed to change theme: " + ex.getMessage(),
            "Theme Error",
            JOptionPane.ERROR_MESSAGE);
    }
}
```

---

## 📊 Step 5: Enhanced Status Bar

### Create StatusBar Class:
```java
package sipinspector.Utils;

import javax.swing.*;
import java.awt.*;

public class StatusBar extends JPanel {
    private JLabel connectionLabel;
    private JLabel transportLabel;
    private JLabel messageCountLabel;
    private JLabel memoryLabel;
    
    public StatusBar() {
        setLayout(new FlowLayout(FlowLayout.LEFT, 10, 2));
        setBorder(BorderFactory.createEtchedBorder());
        
        connectionLabel = new JLabel("Disconnected");
        transportLabel = new JLabel("UDP");
        messageCountLabel = new JLabel("Messages: 0");
        memoryLabel = new JLabel("Memory: 0 MB");
        
        add(connectionLabel);
        add(new JSeparator(SwingConstants.VERTICAL));
        add(transportLabel);
        add(new JSeparator(SwingConstants.VERTICAL));
        add(messageCountLabel);
        add(new JSeparator(SwingConstants.VERTICAL));
        add(memoryLabel);
    }
    
    public void setConnectionStatus(String status) {
        connectionLabel.setText(status);
        if ("Connected".equals(status)) {
            connectionLabel.setForeground(Color.GREEN);
        } else {
            connectionLabel.setForeground(Color.RED);
        }
    }
    
    public void setTransport(String transport) {
        transportLabel.setText("Transport: " + transport);
    }
    
    public void setMessageCount(int count) {
        messageCountLabel.setText("Messages: " + count);
    }
    
    public void updateMemory() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
        memoryLabel.setText("Memory: " + usedMemory + " MB");
    }
}
```

---

## 📈 Step 6: Statistics Dashboard

### Create StatisticsPanel Class:
```java
package sipinspector.Statistics;

import javax.swing.*;
import net.miginfocom.swing.MigLayout;
import java.awt.*;

public class StatisticsPanel extends JPanel {
    private JLabel totalSentLabel;
    private JLabel totalReceivedLabel;
    private JLabel successRateLabel;
    private JLabel avgResponseTimeLabel;
    
    private int messagesSent = 0;
    private int messagesReceived = 0;
    private int successCount = 0;
    private long totalResponseTime = 0;
    
    public StatisticsPanel() {
        setLayout(new MigLayout("fill, wrap 2", "[grow][grow]"));
        setBorder(BorderFactory.createTitledBorder("Statistics"));
        
        // Create labels
        totalSentLabel = createStatLabel("Messages Sent:", "0");
        totalReceivedLabel = createStatLabel("Messages Received:", "0");
        successRateLabel = createStatLabel("Success Rate:", "0%");
        avgResponseTimeLabel = createStatLabel("Avg Response Time:", "0 ms");
        
        // Add to panel
        add(new JLabel("Messages Sent:"), "align right");
        add(totalSentLabel, "grow");
        
        add(new JLabel("Messages Received:"), "align right");
        add(totalReceivedLabel, "grow");
        
        add(new JLabel("Success Rate:"), "align right");
        add(successRateLabel, "grow");
        
        add(new JLabel("Avg Response Time:"), "align right");
        add(avgResponseTimeLabel, "grow");
        
        // Add reset button
        JButton resetButton = new JButton("Reset Statistics");
        resetButton.addActionListener(e -> resetStatistics());
        add(resetButton, "span 2, center");
    }
    
    private JLabel createStatLabel(String text, String value) {
        JLabel label = new JLabel(value);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 14f));
        return label;
    }
    
    public void incrementSent() {
        messagesSent++;
        totalSentLabel.setText(String.valueOf(messagesSent));
    }
    
    public void incrementReceived() {
        messagesReceived++;
        totalReceivedLabel.setText(String.valueOf(messagesReceived));
    }
    
    public void recordSuccess() {
        successCount++;
        updateSuccessRate();
    }
    
    public void recordResponseTime(long timeMs) {
        totalResponseTime += timeMs;
        updateAvgResponseTime();
    }
    
    private void updateSuccessRate() {
        if (messagesSent > 0) {
            double rate = (successCount * 100.0) / messagesSent;
            successRateLabel.setText(String.format("%.1f%%", rate));
        }
    }
    
    private void updateAvgResponseTime() {
        if (messagesReceived > 0) {
            long avg = totalResponseTime / messagesReceived;
            avgResponseTimeLabel.setText(avg + " ms");
        }
    }
    
    private void resetStatistics() {
        messagesSent = 0;
        messagesReceived = 0;
        successCount = 0;
        totalResponseTime = 0;
        
        totalSentLabel.setText("0");
        totalReceivedLabel.setText("0");
        successRateLabel.setText("0%");
        avgResponseTimeLabel.setText("0 ms");
    }
}
```

---

## ⌨️ Step 7: Keyboard Shortcuts

### Add Shortcuts Reference Dialog:
```java
private void showKeyboardShortcuts() {
    String shortcuts = 
        "Keyboard Shortcuts:\n\n" +
        "Ctrl+L - Toggle SIP Logging\n" +
        "Ctrl+N - New Scenario\n" +
        "Ctrl+O - Open Scenario\n" +
        "Ctrl+S - Save Scenario\n" +
        "Ctrl+F - Find/Replace\n" +
        "Ctrl+R - Run Scenario\n" +
        "Ctrl+Q - Quit\n" +
        "F1 - Help\n" +
        "F5 - Refresh\n";
    
    JOptionPane.showMessageDialog(this,
        shortcuts,
        "Keyboard Shortcuts",
        JOptionPane.INFORMATION_MESSAGE);
}
```

---

## 🎯 Implementation Checklist

### Phase 2.1 - FlatLaf Integration (Day 1-2)
- [ ] Download flatlaf-3.2.5.jar
- [ ] Add to lib folder
- [ ] Update build.bat manifest
- [ ] Add FlatLaf imports to SIPInspectorMainFrame
- [ ] Initialize FlatLaf in main method
- [ ] Test light theme
- [ ] Test dark theme

### Phase 2.2 - Theme Switcher (Day 3)
- [ ] Add Theme submenu to Options
- [ ] Implement switchTheme() method
- [ ] Save theme preference
- [ ] Load theme on startup
- [ ] Test theme switching

### Phase 2.3 - Status Bar (Day 4)
- [ ] Create StatusBar class
- [ ] Add to main frame
- [ ] Update connection status
- [ ] Update transport info
- [ ] Update message count
- [ ] Add memory monitor

### Phase 2.4 - Statistics Dashboard (Day 5-6)
- [ ] Create StatisticsPanel class
- [ ] Add as new tab
- [ ] Integrate with message sending
- [ ] Integrate with message receiving
- [ ] Add reset functionality
- [ ] Test statistics tracking

### Phase 2.5 - Keyboard Shortcuts (Day 7)
- [ ] Add more keyboard shortcuts
- [ ] Create shortcuts reference dialog
- [ ] Add to Help menu
- [ ] Test all shortcuts

### Phase 2.6 - Testing & Documentation (Day 8-9)
- [ ] Test all new features
- [ ] Update documentation
- [ ] Create Phase 2 summary
- [ ] Create backup

---

## 📚 Resources

### FlatLaf Documentation:
- https://www.formdev.com/flatlaf/
- https://github.com/JFormDesigner/FlatLaf

### Download Links:
- FlatLaf JAR: https://repo1.maven.org/maven2/com/formdev/flatlaf/3.2.5/flatlaf-3.2.5.jar

---

## 🎉 Expected Results

### Before (Current):
- Basic Swing look
- No theme options
- Simple status display
- No statistics

### After (Phase 2):
- ✅ Modern FlatLaf theme
- ✅ Light/Dark mode toggle
- ✅ Enhanced status bar
- ✅ Statistics dashboard
- ✅ Better keyboard shortcuts
- ✅ Professional appearance

---

**Ready to start implementation!** 🚀

**First step: Download FlatLaf JAR and place in lib folder!**
