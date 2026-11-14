/*
 * Statistics Dashboard Panel
 * Displays real-time statistics for SIP messages
 */
package sipinspector.Statistics;

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;

/**
 * Statistics Dashboard Panel
 * Shows message counts, success rates, and response times
 * 
 * @author SIP Support Team
 */
public class StatisticsPanel extends JPanel {

    // Statistics counters
    private int messagesSent = 0;
    private int messagesReceived = 0;
    private int invitesSent = 0;
    private int registersSent = 0;
    private int byesSent = 0;
    private int notifySent = 0;
    private int optionsSent = 0;
    private int referSent = 0;
    private int response200 = 0;
    private int response401 = 0;
    private int response403 = 0;
    private int response404 = 0;
    private int response488 = 0;
    private int response500 = 0;
    private int response604 = 0;
    private long totalResponseTime = 0;
    private int responseCount = 0;

    // UI Components
    private JLabel totalSentLabel;
    private JLabel totalReceivedLabel;
    private JLabel invitesLabel;
    private JLabel registersLabel;
    private JLabel byesLabel;
    private JLabel notifyLabel;
    private JLabel optionsLabel;
    private JLabel referLabel;
    private JLabel response200Label;
    private JLabel response401Label;
    private JLabel response403Label;
    private JLabel response404Label;
    private JLabel response488Label;
    private JLabel response500Label;
    private JLabel response604Label;
    private JLabel successRateLabel;
    private JLabel avgResponseTimeLabel;
    private JLabel sessionStartLabel;

    private Date sessionStart;

    public StatisticsPanel() {
        sessionStart = new Date();
        initComponents();
    }

    private void initComponents() {
        setLayout(new MigLayout("fill, wrap 1", "[grow]", "[]8[]8[grow]8[]"));

        // Header
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        JButton backButton = new JButton("< Back");
        backButton.setToolTipText("Return to the main screen");
        backButton.addActionListener(e -> goBack());
        headerPanel.add(backButton);

        JLabel titleLabel = new JLabel("Statistics Dashboard");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        headerPanel.add(titleLabel);
        add(headerPanel, "growx");

        JPanel totalsPanel = new JPanel(new MigLayout("fill, wrap 4",
                "[grow][grow][grow][grow]", "[]6[]"));
        totalsPanel.setBorder(BorderFactory.createTitledBorder("Overall"));
        addStatRow(totalsPanel, "Messages Sent:", totalSentLabel = createStatLabel("0"));
        addStatRow(totalsPanel, "Messages Received:", totalReceivedLabel = createStatLabel("0"));
        addStatRow(totalsPanel, "Success Rate:", successRateLabel = createStatLabel("0%"));
        addStatRow(totalsPanel, "Avg Response:", avgResponseTimeLabel = createStatLabel("0 ms"));
        addStatRow(totalsPanel, "Session Time:", sessionStartLabel = createStatLabel("0:00"));
        add(totalsPanel, "grow");

        JPanel requestsPanel = new JPanel(new MigLayout("fill, wrap 2",
                "[right]8[grow]", "[]6[]6[]6[]6[]6[]"));
        requestsPanel.setBorder(BorderFactory.createTitledBorder("Requests"));
        addStatRow(requestsPanel, "INVITE:", invitesLabel = createStatLabel("0"));
        addStatRow(requestsPanel, "REGISTER:", registersLabel = createStatLabel("0"));
        addStatRow(requestsPanel, "BYE:", byesLabel = createStatLabel("0"));
        addStatRow(requestsPanel, "NOTIFY:", notifyLabel = createStatLabel("0"));
        addStatRow(requestsPanel, "OPTIONS:", optionsLabel = createStatLabel("0"));
        addStatRow(requestsPanel, "REFER:", referLabel = createStatLabel("0"));

        JPanel responsesPanel = new JPanel(new MigLayout("fill, wrap 2",
                "[right]8[grow]", "[]6[]6[]6[]6[]6[]6[]"));
        responsesPanel.setBorder(BorderFactory.createTitledBorder("Responses"));
        addStatRow(responsesPanel, "200 OK:", response200Label = createStatLabel("0"));
        addStatRow(responsesPanel, "401 Unauthorized:", response401Label = createStatLabel("0"));
        addStatRow(responsesPanel, "403 Forbidden:", response403Label = createStatLabel("0"));
        addStatRow(responsesPanel, "404 Not Found:", response404Label = createStatLabel("0"));
        addStatRow(responsesPanel, "488 Not Acceptable:", response488Label = createStatLabel("0"));
        addStatRow(responsesPanel, "500 Error:", response500Label = createStatLabel("0"));
        addStatRow(responsesPanel, "604 Does Not Exist:", response604Label = createStatLabel("0"));

        JPanel midPanel = new JPanel(new MigLayout("fill, insets 0", "[grow,fill]20[grow,fill]", "[grow]"));
        midPanel.add(requestsPanel, "grow");
        midPanel.add(responsesPanel, "grow");
        add(midPanel, "grow");

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton resetButton = new JButton("Reset Statistics");
        resetButton.addActionListener(e -> resetStatistics());
        buttonPanel.add(resetButton);
        JButton exportButton = new JButton("Export to CSV");
        exportButton.addActionListener(e -> exportStatistics());
        buttonPanel.add(exportButton);
        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> updateSessionTime());
        buttonPanel.add(refreshButton);
        add(buttonPanel, "align center");
    }

    private void addStatRow(JPanel panel, String label, JLabel valueLabel) {
        JLabel lbl = new JLabel(label);
        lbl.setFont(lbl.getFont().deriveFont(Font.PLAIN, 12f));
        panel.add(lbl, "align right");
        panel.add(valueLabel);
    }

    private JLabel createStatLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 14f));
        label.setForeground(new Color(0, 100, 200));
        return label;
    }

    // Public methods to update statistics

    public void incrementSent() {
        messagesSent++;
        totalSentLabel.setText(String.valueOf(messagesSent));
        updateSuccessRate();
        updateSessionTime();
    }

    public void incrementReceived() {
        messagesReceived++;
        totalReceivedLabel.setText(String.valueOf(messagesReceived));
        updateSessionTime();
    }

    public void incrementInvite() {
        invitesSent++;
        invitesLabel.setText(String.valueOf(invitesSent));
        incrementSent();
    }

    public void incrementRegister() {
        registersSent++;
        registersLabel.setText(String.valueOf(registersSent));
        incrementSent();
    }

    public void incrementBye() {
        byesSent++;
        byesLabel.setText(String.valueOf(byesSent));
        incrementSent();
    }

    public void incrementNotify() {
        notifySent++;
        notifyLabel.setText(String.valueOf(notifySent));
        incrementSent();
    }

    public void incrementOptions() {
        optionsSent++;
        optionsLabel.setText(String.valueOf(optionsSent));
        incrementSent();
    }

    public void incrementRefer() {
        referSent++;
        referLabel.setText(String.valueOf(referSent));
        incrementSent();
    }

    public void increment200OK() {
        response200++;
        response200Label.setText(String.valueOf(response200));
        incrementReceived();
        updateSuccessRate();
    }

    public void increment401() {
        response401++;
        response401Label.setText(String.valueOf(response401));
        incrementReceived();
    }

    public void increment403() {
        response403++;
        response403Label.setText(String.valueOf(response403));
        incrementReceived();
    }

    public void increment404() {
        response404++;
        response404Label.setText(String.valueOf(response404));
        incrementReceived();
    }

    public void increment488() {
        response488++;
        response488Label.setText(String.valueOf(response488));
        incrementReceived();
    }

    public void increment500() {
        response500++;
        response500Label.setText(String.valueOf(response500));
        incrementReceived();
    }

    public void increment604() {
        response604++;
        response604Label.setText(String.valueOf(response604));
        incrementReceived();
    }

    public void recordResponseTime(long timeMs) {
        totalResponseTime += timeMs;
        responseCount++;
        updateAvgResponseTime();
    }

    private void updateSuccessRate() {
        if (messagesSent > 0) {
            double rate = (response200 * 100.0) / messagesSent;
            successRateLabel.setText(String.format("%.1f%%", rate));

            // Color code based on success rate
            if (rate >= 90) {
                successRateLabel.setForeground(new Color(0, 150, 0)); // Green
            } else if (rate >= 70) {
                successRateLabel.setForeground(new Color(200, 150, 0)); // Orange
            } else {
                successRateLabel.setForeground(new Color(200, 0, 0)); // Red
            }
        }
    }

    private void updateAvgResponseTime() {
        if (responseCount > 0) {
            long avg = totalResponseTime / responseCount;
            avgResponseTimeLabel.setText(avg + " ms");

            // Color code based on response time
            if (avg < 100) {
                avgResponseTimeLabel.setForeground(new Color(0, 150, 0)); // Green
            } else if (avg < 500) {
                avgResponseTimeLabel.setForeground(new Color(200, 150, 0)); // Orange
            } else {
                avgResponseTimeLabel.setForeground(new Color(200, 0, 0)); // Red
            }
        }
    }

    private void updateSessionTime() {
        long duration = System.currentTimeMillis() - sessionStart.getTime();
        long seconds = duration / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        sessionStartLabel.setText(String.format("%d:%02d:%02d", hours, minutes % 60, seconds % 60));
    }

    private void resetStatistics() {
        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to reset all statistics?",
                "Reset Statistics",
                JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            messagesSent = 0;
            messagesReceived = 0;
            invitesSent = 0;
            registersSent = 0;
            byesSent = 0;
            notifySent = 0;
            optionsSent = 0;
            referSent = 0;
            response200 = 0;
            response401 = 0;
            response403 = 0;
            response404 = 0;
            response488 = 0;
            response500 = 0;
            response604 = 0;
            totalResponseTime = 0;
            responseCount = 0;
            sessionStart = new Date();

            totalSentLabel.setText("0");
            totalReceivedLabel.setText("0");
            invitesLabel.setText("0");
            registersLabel.setText("0");
            byesLabel.setText("0");
            notifyLabel.setText("0");
            optionsLabel.setText("0");
            referLabel.setText("0");
            response200Label.setText("0");
            response401Label.setText("0");
            response403Label.setText("0");
            response404Label.setText("0");
            response488Label.setText("0");
            response500Label.setText("0");
            response604Label.setText("0");
            successRateLabel.setText("0%");
            avgResponseTimeLabel.setText("0 ms");
            updateSessionTime();

            JOptionPane.showMessageDialog(this,
                    "Statistics reset successfully!",
                    "Reset Complete",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void exportStatistics() {
        String desktopPath = System.getProperty("user.home")
                + java.io.File.separator + "Desktop";
        java.io.File outFile = new java.io.File(desktopPath, "SIP_Simulator_statistics.csv");

        if (writeStatisticsCsv(outFile)) {
            JOptionPane.showMessageDialog(this,
                    "Statistics exported to:\n" + outFile.getAbsolutePath(),
                    "Export Successful",
                    JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this,
                    "Failed to export statistics.",
                    "Export Failed",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Write the current (live) statistics to the given CSV file.
     * Shared by the dashboard's "Export to CSV" button and the
     * Tools -> Export Statistics menu, so both emit real data.
     *
     * @return true on success, false on any I/O error.
     */
    public boolean writeStatisticsCsv(java.io.File outFile) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        try (java.io.PrintWriter writer = new java.io.PrintWriter(outFile)) {
            writer.println("SIP Simulator Statistics Export");
            writer.println("Session Started," + sdf.format(sessionStart));
            writer.println("Exported," + sdf.format(new Date()));
            writer.println();
            writer.println("Metric,Value");
            writer.println("Messages Sent," + messagesSent);
            writer.println("Messages Received," + messagesReceived);
            writer.println("Total Messages," + (messagesSent + messagesReceived));
            writer.println("INVITE," + invitesSent);
            writer.println("REGISTER," + registersSent);
            writer.println("BYE," + byesSent);
            writer.println("NOTIFY," + notifySent);
            writer.println("OPTIONS," + optionsSent);
            writer.println("REFER," + referSent);
            writer.println("200 OK," + response200);
            writer.println("401 Unauthorized," + response401);
            writer.println("403 Forbidden," + response403);
            writer.println("404 Not Found," + response404);
            writer.println("488 Not Acceptable," + response488);
            writer.println("500 Server Error," + response500);
            writer.println("604 Does Not Exist," + response604);

            if (messagesSent > 0) {
                writer.println("Success Rate (%)," + String.format("%.1f", (response200 * 100.0) / messagesSent));
            }
            if (responseCount > 0) {
                writer.println("Avg Response Time (ms)," + (totalResponseTime / responseCount));
            }
            return true;
        } catch (Exception ex) {
            ex.printStackTrace();
            return false;
        }
    }

    private void goBack() {
        // Get the card panel (parent of parent) and switch back to the interview card
        JPanel parent = (JPanel) getParent();
        if (parent != null) {
            JPanel cardPanel = (JPanel) parent.getParent();
            if (cardPanel != null && cardPanel.getLayout() instanceof java.awt.CardLayout) {
                java.awt.CardLayout cl = (java.awt.CardLayout) cardPanel.getLayout();
                cl.show(cardPanel, "interview_card");
            }
        }
    }

    // Singleton instance for global access
    private static StatisticsPanel instance;

    public static StatisticsPanel getInstance() {
        if (instance == null) {
            instance = new StatisticsPanel();
        }
        return instance;
    }
}
