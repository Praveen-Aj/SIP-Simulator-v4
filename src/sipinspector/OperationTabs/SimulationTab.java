package sipinspector.OperationTabs;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import net.miginfocom.swing.MigLayout;
import sipinspector.GlobalSettings;
import sipinspector.SIPInspectorMainFrame;
import sipinspector.Utils.SIPStateTracker;

/**
 * Operation Tab for setting packet delay, drop simulation,
 * and malformed header injection, plus viewing registration/dialog states.
 * 
 * @author SIP Support Team
 */
public class SimulationTab extends JPanel {

    private SIPInspectorMainFrame mainFrame;
    
    private JSlider delaySlider;
    private JLabel delayValueLabel;
    
    private JSlider lossSlider;
    private JLabel lossValueLabel;
    
    private JComboBox<String> malformedCombo;
    
    private JLabel regStateLabel;
    private JLabel dialogCountLabel;
    private Timer refreshTimer;

    public SimulationTab(SIPInspectorMainFrame mainFrame) {
        this.mainFrame = mainFrame;
        initComponents();
        loadSettings();
        startTracking();
    }

    private void initComponents() {
        setLayout(new MigLayout("fill, insets 15", "[grow]", "[][][grow]"));

        // --- Network Emulation Panel ---
        JPanel networkPanel = new JPanel(new MigLayout("fillx, insets 10", "[120!][grow][]", "[][][]"));
        TitledBorder netBorder = BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(51, 65, 85)), "Network Degradation Emulation");
        netBorder.setTitleFont(new Font("SansSerif", Font.BOLD, 12));
        networkPanel.setBorder(netBorder);

        // Latency Slider
        networkPanel.add(new JLabel("Latency Delay (ms):"));
        delaySlider = new JSlider(0, 2000, 0);
        delaySlider.setMajorTickSpacing(500);
        delaySlider.setMinorTickSpacing(100);
        delaySlider.setPaintTicks(true);
        delaySlider.addChangeListener(e -> {
            delayValueLabel.setText(delaySlider.getValue() + " ms");
            saveSettings();
        });
        networkPanel.add(delaySlider, "growx");
        delayValueLabel = new JLabel("0 ms");
        delayValueLabel.setFont(new Font("Monospaced", Font.BOLD, 12));
        networkPanel.add(delayValueLabel, "wrap");

        // Packet Loss Slider
        networkPanel.add(new JLabel("Packet Loss (%):"));
        lossSlider = new JSlider(0, 100, 0);
        lossSlider.setMajorTickSpacing(25);
        lossSlider.setMinorTickSpacing(5);
        lossSlider.setPaintTicks(true);
        lossSlider.addChangeListener(e -> {
            lossValueLabel.setText(lossSlider.getValue() + " %");
            saveSettings();
        });
        networkPanel.add(lossSlider, "growx");
        lossValueLabel = new JLabel("0 %");
        lossValueLabel.setFont(new Font("Monospaced", Font.BOLD, 12));
        networkPanel.add(lossValueLabel, "wrap");

        add(networkPanel, "growx, wrap");

        // --- Protocol Realism / Malformed Panel ---
        JPanel protocolPanel = new JPanel(new MigLayout("fillx, insets 10", "[120!][grow]", "[]"));
        TitledBorder protBorder = BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(51, 65, 85)), "SIP Protocol Anomaly Injection");
        protBorder.setTitleFont(new Font("SansSerif", Font.BOLD, 12));
        protocolPanel.setBorder(protBorder);

        protocolPanel.add(new JLabel("Malformed Header:"));
        malformedCombo = new JComboBox<>(new String[]{"None", "CSeq", "Call-ID", "Via", "Expires"});
        malformedCombo.addActionListener(e -> saveSettings());
        protocolPanel.add(malformedCombo, "growx, wrap");

        add(protocolPanel, "growx, wrap");

        // --- State Observability Monitor ---
        JPanel monitorPanel = new JPanel(new MigLayout("fill, insets 15", "[150!][grow]", "[][]"));
        TitledBorder monBorder = BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(6, 182, 212)), "SIP State Observability Monitor"); // cyan border
        monBorder.setTitleFont(new Font("SansSerif", Font.BOLD, 12));
        monitorPanel.setBorder(monBorder);

        // Registration State
        monitorPanel.add(new JLabel("Registration State:"));
        regStateLabel = new JLabel("Unregistered");
        regStateLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        regStateLabel.setForeground(new Color(239, 68, 68)); // red by default
        monitorPanel.add(regStateLabel, "wrap");

        // Active Dialogs
        monitorPanel.add(new JLabel("Active Call Dialogs:"));
        dialogCountLabel = new JLabel("0 Dialogs");
        dialogCountLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        dialogCountLabel.setForeground(new Color(34, 197, 94)); // green
        monitorPanel.add(dialogCountLabel, "wrap");

        add(monitorPanel, "grow, push");
    }

    private void loadSettings() {
        try {
            String delayVal = GlobalSettings.getSetting("sim.delay");
            if (delayVal != null) {
                delaySlider.setValue(Integer.parseInt(delayVal.trim()));
            }
            
            String lossVal = GlobalSettings.getSetting("sim.loss");
            if (lossVal != null) {
                lossSlider.setValue(Integer.parseInt(lossVal.trim()));
            }

            String malformedVal = GlobalSettings.getSetting("sim.malformed");
            if (malformedVal != null) {
                malformedCombo.setSelectedItem(malformedVal.trim());
            }
        } catch (Exception e) {
            // Default fallbacks handled by initialization
        }
        
        delayValueLabel.setText(delaySlider.getValue() + " ms");
        lossValueLabel.setText(lossSlider.getValue() + " %");
    }

    private void saveSettings() {
        GlobalSettings.setSetting("sim.delay", String.valueOf(delaySlider.getValue()));
        GlobalSettings.setSetting("sim.loss", String.valueOf(lossSlider.getValue()));
        GlobalSettings.setSetting("sim.malformed", (String) malformedCombo.getSelectedItem());
        GlobalSettings.saveSettings();
    }

    private void startTracking() {
        refreshTimer = new Timer(1000, e -> {
            // Update registration state display
            String state = SIPStateTracker.getRegistrationState();
            regStateLabel.setText(state);
            if (state.startsWith("Registered")) {
                regStateLabel.setForeground(new Color(34, 197, 94)); // green
            } else if (state.startsWith("Registering")) {
                regStateLabel.setForeground(new Color(6, 182, 212)); // cyan
            } else {
                regStateLabel.setForeground(new Color(239, 68, 68)); // red
            }

            // Update active dialog count
            int count = SIPStateTracker.getActiveDialogCount();
            dialogCountLabel.setText(count + " active call dialog" + (count == 1 ? "" : "s"));
            if (count > 0) {
                dialogCountLabel.setForeground(new Color(34, 197, 94)); // green
            } else {
                dialogCountLabel.setForeground(new Color(148, 163, 184)); // slate-400
            }
        });
        refreshTimer.start();
    }

    public void stopTimer() {
        if (refreshTimer != null) {
            refreshTimer.stop();
        }
    }
}
