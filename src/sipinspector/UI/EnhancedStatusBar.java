/*
 * Enhanced Status Bar
 * Shows connection status, transport, message counts, and session info
 */
package sipinspector.UI;

import java.awt.Color;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;
import javax.swing.Timer;

import net.miginfocom.swing.MigLayout;

/**
 * Enhanced status bar with real-time information
 * 
 * @author SIP Support Team
 */
public class EnhancedStatusBar extends JPanel {

    private JLabel statusLabel;
    private JLabel transportLabel;
    private JLabel messageCountLabel;
    private JLabel durationLabel;
    private JLabel memoryLabel;
    private JLabel scenarioStatusLabel;
    private JLabel valuesStatusLabel;
    private JLabel logsStatusLabel;
    private JLabel inviteTypeLabel;

    private long sessionStartTime;
    private int messagesSent = 0;
    private int messagesReceived = 0;
    private Timer updateTimer;
    private boolean scenarioLoaded = false;
    private boolean valuesLoaded = false;
    private boolean logsEnabled = false;
    private String currentInviteType = "None";

    public EnhancedStatusBar() {
        sessionStartTime = System.currentTimeMillis();
        initComponents();
        startUpdateTimer();
    }

    private void initComponents() {
        setLayout(new MigLayout("fill, insets 2", "[][][grow][][][][][][]", "[]"));
        setBorder(BorderFactory.createEtchedBorder());

        // Status indicator
        statusLabel = new JLabel("Disconnected");
        statusLabel.setToolTipText("Connection status");
        add(statusLabel, "");

        add(new JSeparator(SwingConstants.VERTICAL), "h 20!");

        // Transport type
        transportLabel = new JLabel("Transport: UDP");
        transportLabel.setToolTipText("Current transport protocol");
        add(transportLabel, "");

        add(new JSeparator(SwingConstants.VERTICAL), "h 20!");

        // Message count
        messageCountLabel = new JLabel("Sent: 0 | Received: 0");
        messageCountLabel.setToolTipText("Message statistics");
        add(messageCountLabel, "");

        add(new JSeparator(SwingConstants.VERTICAL), "h 20!");

        // Session duration
        durationLabel = new JLabel("Duration: 00:00:00");
        durationLabel.setToolTipText("Session duration");
        add(durationLabel, "");

        add(new JSeparator(SwingConstants.VERTICAL), "h 20!");

        // Scenario status
        scenarioStatusLabel = new JLabel("Scenario: No");
        scenarioStatusLabel.setToolTipText("Scenario loaded status");
        scenarioStatusLabel.setForeground(Color.RED);
        add(scenarioStatusLabel, "");

        add(new JSeparator(SwingConstants.VERTICAL), "h 20!");

        // Values status
        valuesStatusLabel = new JLabel("Values: No");
        valuesStatusLabel.setToolTipText("Values loaded status");
        valuesStatusLabel.setForeground(Color.RED);
        add(valuesStatusLabel, "");

        add(new JSeparator(SwingConstants.VERTICAL), "h 20!");

        // Logs status
        logsStatusLabel = new JLabel("Logs: Off");
        logsStatusLabel.setToolTipText("Logging enabled status");
        logsStatusLabel.setForeground(Color.RED);
        add(logsStatusLabel, "");

        add(new JSeparator(SwingConstants.VERTICAL), "h 20!");

        // INVITE type
        inviteTypeLabel = new JLabel("INVITE: None");
        inviteTypeLabel.setToolTipText("INVITE message type (Initial or Re-INVITE)");
        inviteTypeLabel.setForeground(Color.GRAY);
        add(inviteTypeLabel, "");

        add(new JSeparator(SwingConstants.VERTICAL), "h 20!");

        // Memory usage
        memoryLabel = new JLabel("Memory: 0 MB");
        memoryLabel.setToolTipText("Memory usage");
        add(memoryLabel, "");
    }

    private void startUpdateTimer() {
        updateTimer = new Timer(1000, e -> updateStatus());
        updateTimer.start();
    }

    private void updateStatus() {
        // Update duration
        long duration = System.currentTimeMillis() - sessionStartTime;
        long seconds = (duration / 1000) % 60;
        long minutes = (duration / (1000 * 60)) % 60;
        long hours = (duration / (1000 * 60 * 60));

        durationLabel.setText(String.format("Duration: %02d:%02d:%02d", hours, minutes, seconds));

        // Update memory
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        memoryLabel.setText(String.format("Memory: %d MB", usedMemory));
    }

    public void setConnected(boolean connected) {
        if (connected) {
            statusLabel.setText("Connected");
            statusLabel.setForeground(new Color(0, 150, 0));
        } else {
            statusLabel.setText("Disconnected");
            statusLabel.setForeground(Color.RED);
        }
    }

    public void setTransport(String transport) {
        transportLabel.setText("Transport: " + transport.toUpperCase());

        // Color code by transport
        switch (transport.toUpperCase()) {
            case "UDP":
                transportLabel.setForeground(new Color(0, 100, 200));
                break;
            case "TCP":
                transportLabel.setForeground(new Color(0, 150, 0));
                break;
            case "TLS":
                transportLabel.setForeground(new Color(150, 0, 150));
                break;
        }
    }

    public void incrementMessagesSent() {
        messagesSent++;
        updateMessageCount();
    }

    public void incrementMessagesReceived() {
        messagesReceived++;
        updateMessageCount();
    }

    public void setMessagesSent(int count) {
        messagesSent = count;
        updateMessageCount();
    }

    public void setMessagesReceived(int count) {
        messagesReceived = count;
        updateMessageCount();
    }

    private void updateMessageCount() {
        messageCountLabel.setText(String.format("Sent: %d | Received: %d",
                messagesSent, messagesReceived));
    }

    public void resetSession() {
        sessionStartTime = System.currentTimeMillis();
        messagesSent = 0;
        messagesReceived = 0;
        updateMessageCount();
        updateStatus();
    }

    public void stopTimer() {
        if (updateTimer != null) {
            updateTimer.stop();
        }
    }

    public void setScenarioLoaded(boolean loaded) {
        scenarioLoaded = loaded;
        if (loaded) {
            scenarioStatusLabel.setText("Scenario: Yes");
            scenarioStatusLabel.setForeground(new Color(0, 150, 0));
        } else {
            scenarioStatusLabel.setText("Scenario: No");
            scenarioStatusLabel.setForeground(Color.RED);
        }
    }

    public void setValuesLoaded(boolean loaded) {
        valuesLoaded = loaded;
        if (loaded) {
            valuesStatusLabel.setText("Values: Yes");
            valuesStatusLabel.setForeground(new Color(0, 150, 0));
        } else {
            valuesStatusLabel.setText("Values: No");
            valuesStatusLabel.setForeground(Color.RED);
        }
    }

    public void setLogsEnabled(boolean enabled) {
        logsEnabled = enabled;
        if (enabled) {
            logsStatusLabel.setText("Logs: On");
            logsStatusLabel.setForeground(new Color(0, 150, 0));
        } else {
            logsStatusLabel.setText("Logs: Off");
            logsStatusLabel.setForeground(Color.RED);
        }
    }

    public void setInviteType(String inviteType) {
        currentInviteType = inviteType;
        if (inviteType == null || inviteType.trim().isEmpty() || inviteType.equalsIgnoreCase("None")) {
            inviteTypeLabel.setText("INVITE: --");
            inviteTypeLabel.setForeground(Color.GRAY);
            return;
        }

        String normalized = inviteType.trim();
        if (normalized.equalsIgnoreCase("Initial INVITE") || normalized.equalsIgnoreCase("INVITE")) {
            inviteTypeLabel.setText("INVITE: INVITE");
            inviteTypeLabel.setForeground(new Color(0, 150, 0));
        } else if (normalized.equalsIgnoreCase("Re-INVITE") || normalized.equalsIgnoreCase("REINVITE")) {
            inviteTypeLabel.setText("INVITE: RE-INVITE");
            inviteTypeLabel.setForeground(new Color(200, 100, 0));
        } else {
            inviteTypeLabel.setText("INVITE: " + normalized);
            inviteTypeLabel.setForeground(Color.BLUE);
        }
    }
}
