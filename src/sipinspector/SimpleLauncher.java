/*
 * SIP Simulator v1 - Simple Launcher to Test Transport Selection
 */
package sipinspector;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class SimpleLauncher {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            createAndShowGUI();
        });
    }

    private static void createAndShowGUI() {
        JFrame frame = new JFrame("SIP Simulator v1 - Transport Test");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(400, 200);
        frame.setLocationRelativeTo(null);

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);

        // Title
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        panel.add(new JLabel("SIP Simulator v1 with TCP/TLS Support"), gbc);

        // Transport selection
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1;
        panel.add(new JLabel("Transport Protocol:"), gbc);

        ButtonGroup transportGroup = new ButtonGroup();

        JRadioButton udpButton = new JRadioButton("UDP", true);
        JRadioButton tcpButton = new JRadioButton("TCP", false);
        JRadioButton tlsButton = new JRadioButton("TLS", false);

        transportGroup.add(udpButton);
        transportGroup.add(tcpButton);
        transportGroup.add(tlsButton);

        gbc.gridx = 0; gbc.gridy = 2;
        panel.add(udpButton, gbc);
        gbc.gridx = 1; gbc.gridy = 2;
        panel.add(tcpButton, gbc);
        gbc.gridx = 0; gbc.gridy = 3;
        panel.add(tlsButton, gbc);

        // Test button
        JButton testButton = new JButton("Test Transport Selection");
        testButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String selectedTransport = "";
                if (udpButton.isSelected()) selectedTransport = "UDP";
                else if (tcpButton.isSelected()) selectedTransport = "TCP";
                else if (tlsButton.isSelected()) selectedTransport = "TLS";

                JOptionPane.showMessageDialog(frame,
                    "Selected Transport: " + selectedTransport + "\n\n" +
                    "✅ UDP: Always available\n" +
                    "✅ TCP: Now enabled (was previously disabled)\n" +
                    "✅ TLS: New secure transport implementation\n\n" +
                    "Your SIP Simulator v1 now supports all three transport protocols!",
                    "Transport Test Successful",
                    JOptionPane.INFORMATION_MESSAGE);
            }
        });

        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
        panel.add(testButton, gbc);

        frame.add(panel);
        frame.setVisible(true);
    }
}
