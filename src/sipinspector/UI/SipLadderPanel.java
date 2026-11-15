package sipinspector.UI;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import sipinspector.GlobalSettings;

public class SipLadderPanel extends JPanel {

    public static class SipMessage {
        public String source = "";
        public String target = "";
        public String direction = ""; // "in" or "out"
        public String label = "";
        public String callId = "";
        public String cseq = "";
        public String rawText = "";
    }

    public interface MessageSelectionListener {
        void messageSelected(SipMessage msg);
    }

    private List<SipMessage> messages = new ArrayList<>();
    private List<String> hosts = new ArrayList<>();
    private SipMessage selectedMessage = null;
    private MessageSelectionListener selectionListener;

    private final int leftMargin = 150;
    private final int hostSpacing = 300;
    private final int startY = 80;
    private final int ySpacing = 50;
    private final int hostLineThickness = 2;
    private final int arrowSize = 8;

    // Palette
    private final Color bgColor = new Color(15, 23, 42); // slate-900
    private final Color hostLineColor = new Color(71, 85, 105); // slate-600
    private final Color hostBoxBg = new Color(30, 41, 59); // slate-800
    private final Color hostBoxBorder = new Color(51, 65, 85); // slate-700
    private final Color textColor = new Color(241, 245, 249); // slate-100
    private final Color textMutedColor = new Color(148, 163, 184); // slate-400
    private final Color outArrowColor = new Color(6, 182, 212); // cyan-500
    private final Color inArrowColor = new Color(34, 197, 94); // green-500
    private final Color selectedArrowColor = new Color(236, 72, 153); // pink-500

    public SipLadderPanel() {
        setBackground(bgColor);
        setDoubleBuffered(true);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                handleMouseClick(e.getPoint());
            }
        });
    }

    public void setSelectionListener(MessageSelectionListener listener) {
        this.selectionListener = listener;
    }

    public void loadLogFile(File file) {
        this.messages = parseLog(file);
        computeHosts();
        this.selectedMessage = null;
        updatePreferredSize();
        repaint();
    }

    public void setMessages(List<SipMessage> msgs) {
        this.messages = msgs != null ? new ArrayList<>(msgs) : new ArrayList<>();
        computeHosts();
        this.selectedMessage = null;
        updatePreferredSize();
        revalidate();
        repaint();
    }

    public void clear() {
        this.messages.clear();
        this.hosts.clear();
        this.selectedMessage = null;
        updatePreferredSize();
        repaint();
    }

    /** @return true if there is a diagram to display/export. */
    public boolean hasMessages() {
        return !messages.isEmpty();
    }

    /**
     * Render the full ladder diagram (the entire scenario, not just the visible
     * viewport) to an image at the given integer scale factor. Used for PNG/PDF
     * export so the whole call flow is captured crisply.
     *
     * @param scale integer upscale factor (&gt;=1); 2 gives a sharp export.
     */
    public BufferedImage renderToImage(int scale) {
        if (scale < 1) {
            scale = 1;
        }
        updatePreferredSize();
        Dimension d = getPreferredSize();
        int w = Math.max(1, d.width);
        int h = Math.max(1, d.height);
        // paintComponent uses getWidth()/getHeight() for the host timelines, so
        // make the component report the full (preferred) size before painting.
        setSize(w, h);
        BufferedImage img = new BufferedImage(w * scale, h * scale, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        g2.scale(scale, scale);
        g2.setColor(bgColor);
        g2.fillRect(0, 0, w, h);
        print(g2);
        g2.dispose();
        return img;
    }

    private void computeHosts() {
        String remoteIp = null;
        try {
            remoteIp = GlobalSettings.getRemoteIPAddr();
        } catch (Exception e) {}
        
        // If remote IP is not set in settings, try to find one from messages
        if (remoteIp == null || remoteIp.isEmpty()) {
            for (SipMessage msg : messages) {
                if (msg.source != null && !msg.source.contains("LOCAL")) {
                    remoteIp = msg.source.replace("(Remote)", "").trim();
                    break;
                }
                if (msg.target != null && !msg.target.contains("LOCAL")) {
                    remoteIp = msg.target.replace("(Remote)", "").trim();
                    break;
                }
            }
        }
        
        if (remoteIp == null || remoteIp.isEmpty()) {
            remoteIp = "192.168.0.70";
        }
        
        this.hosts = new ArrayList<>();
        this.hosts.add("SIP Simulator(LOCAL)");
        this.hosts.add(remoteIp + "(Remote)");
    }

    private void updatePreferredSize() {
        int height = startY + (messages.size() + 1) * ySpacing;
        int width = leftMargin * 2 + (hosts.size() - 1) * hostSpacing;
        setPreferredSize(new Dimension(Math.max(600, width), Math.max(400, height)));
        revalidate();
    }

    private void handleMouseClick(Point p) {
        if (messages.isEmpty()) return;

        for (int i = 0; i < messages.size(); i++) {
            int y = startY + i * ySpacing;
            if (Math.abs(p.y - y) <= 15) {
                selectedMessage = messages.get(i);
                repaint();
                if (selectionListener != null) {
                    selectionListener.messageSelected(selectedMessage);
                }
                return;
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        if (messages.isEmpty()) {
            g2.setColor(textMutedColor);
            g2.setFont(new Font("SansSerif", Font.PLAIN, 14));
            String msg = "No SIP messages to display. Select a log file to view ladder diagram.";
            int w = g2.getFontMetrics().stringWidth(msg);
            g2.drawString(msg, (getWidth() - w) / 2, getHeight() / 2);
            return;
        }

        // Draw vertical host timelines
        int height = getHeight();
        Map<String, Integer> hostXMap = new HashMap<>();

        for (int i = 0; i < hosts.size(); i++) {
            String host = hosts.get(i);
            int x = leftMargin + i * hostSpacing;
            hostXMap.put(host, x);

            // Draw line
            g2.setColor(hostLineColor);
            g2.setStroke(new BasicStroke(hostLineThickness, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{9}, 0));
            g2.drawLine(x, startY - 20, x, height - 30);

            // Draw Host Box at the top
            g2.setStroke(new BasicStroke(1));
            g2.setColor(hostBoxBg);
            int boxWidth = 180;
            int boxHeight = 36;
            int boxX = x - boxWidth / 2;
            int boxY = startY - 50;
            g2.fillRoundRect(boxX, boxY, boxWidth, boxHeight, 10, 10);
            g2.setColor(hostBoxBorder);
            g2.drawRoundRect(boxX, boxY, boxWidth, boxHeight, 10, 10);

            // Host Label
            g2.setColor(textColor);
            g2.setFont(new Font("SansSerif", Font.BOLD, 12));
            FontMetrics fm = g2.getFontMetrics();
            int strW = fm.stringWidth(host);
            g2.drawString(host, x - strW / 2, boxY + 22);
        }

        // Draw SIP arrows
        for (int i = 0; i < messages.size(); i++) {
            SipMessage msg = messages.get(i);
            int y = startY + i * ySpacing;

            Integer xSource = hostXMap.get(msg.source);
            Integer xTarget = hostXMap.get(msg.target);

            if (xSource == null) xSource = leftMargin;
            if (xTarget == null) xTarget = leftMargin + hostSpacing;

            boolean isSelected = (msg == selectedMessage);
            Color arrowColor = isSelected ? selectedArrowColor 
                             : (msg.direction.equals("out") ? outArrowColor : inArrowColor);

            // Draw line
            g2.setColor(arrowColor);
            g2.setStroke(new BasicStroke(isSelected ? 3.0f : 1.5f));
            g2.drawLine(xSource, y, xTarget, y);

            // Draw arrowhead pointing to target
            drawArrowHead(g2, xSource, xTarget, y, arrowSize);

            // Draw CSeq & Label
            g2.setFont(new Font("Monospaced", isSelected ? Font.BOLD : Font.PLAIN, 11));
            String displayLabel = msg.label;
            if (msg.cseq != null && !msg.cseq.isEmpty()) {
                displayLabel += " (" + msg.cseq + ")";
            }

            FontMetrics fm = g2.getFontMetrics();
            int labelW = fm.stringWidth(displayLabel);
            int labelX = xSource + (xTarget - xSource) / 2 - labelW / 2;
            int labelY = y - 6;

            // Draw subtle background for text to make it readable
            g2.setColor(new Color(15, 23, 42, 220));
            g2.fillRect(labelX - 4, labelY - 10, labelW + 8, 14);

            g2.setColor(isSelected ? selectedArrowColor : textColor);
            g2.drawString(displayLabel, labelX, labelY);
        }
    }

    private void drawArrowHead(Graphics2D g2, int x1, int x2, int y, int size) {
        Polygon arrowHead = new Polygon();
        if (x1 < x2) {
            // Pointing Right ->
            arrowHead.addPoint(x2, y);
            arrowHead.addPoint(x2 - size, y - size / 2);
            arrowHead.addPoint(x2 - size, y + size / 2);
        } else {
            // Pointing Left <-
            arrowHead.addPoint(x2, y);
            arrowHead.addPoint(x2 + size, y - size / 2);
            arrowHead.addPoint(x2 + size, y + size / 2);
        }
        g2.fill(arrowHead);
    }

    private static String normalizeHost(String host) {
        if (host == null || host.isEmpty()) {
            return "SIP Simulator(LOCAL)";
        }
        String clean = host.trim();
        String cleanLower = clean.toLowerCase();
        
        boolean isLocal = false;
        if (cleanLower.equals("local") || cleanLower.contains("127.0.0.1") || cleanLower.contains("localhost") || cleanLower.contains("inspector") || cleanLower.contains("simulator")) {
            isLocal = true;
        } else {
            try {
                String localIp = GlobalSettings.getLocalIP();
                if (localIp != null && !localIp.isEmpty() && clean.contains(localIp)) {
                    isLocal = true;
                }
            } catch (Exception e) {}
        }
        
        if (isLocal) {
            return "SIP Simulator(LOCAL)";
        }
        
        // Otherwise, it is remote
        String remoteIp = null;
        try {
            remoteIp = GlobalSettings.getRemoteIPAddr();
        } catch (Exception e) {}
        
        if (remoteIp == null || remoteIp.isEmpty()) {
            String parsedHost = clean;
            if (parsedHost.startsWith("rc/")) {
                parsedHost = parsedHost.substring(3);
            }
            if (parsedHost.startsWith("rc /")) {
                parsedHost = parsedHost.substring(4);
            }
            if (parsedHost.contains(":")) {
                parsedHost = parsedHost.split(":")[0];
            }
            parsedHost = parsedHost.trim();
            if (parsedHost.isEmpty() || parsedHost.equalsIgnoreCase("remote") || parsedHost.equalsIgnoreCase("server")) {
                return "192.168.0.70(Remote)";
            }
            return parsedHost + "(Remote)";
        }
        
        return remoteIp + "(Remote)";
    }

    public static List<SipMessage> parseLog(File file) {
        List<SipMessage> messages = new ArrayList<>();
        if (!file.exists()) return messages;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
            String line;
            SipMessage currentMsg = null;
            boolean parsingHeader = false;
            boolean insideMessageBody = false;
            List<String> rawTextLines = new ArrayList<>();
            String lastMetadataLine = "";

            // For debug format tracking
            String debugDirection = ""; // "out" or "in"
            String debugRemote = "";

            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();

                // Strip timestamp prefix if present (e.g. "2026-06-21 08:54:48.718 ")
                if (trimmed.length() >= 23 && trimmed.substring(0, 10).contains("-") && trimmed.substring(10, 19).contains(":")) {
                    String possibleTs = trimmed.substring(0, 23);
                    if (possibleTs.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}")) {
                        trimmed = trimmed.substring(23).trim();
                        // Also strip from the original line for clean raw headers view
                        line = line.substring(line.indexOf(possibleTs) + 23);
                        if (line.startsWith(" ")) {
                            line = line.substring(1);
                        }
                    }
                }

                // Check Format B (Debug log Send/Receive markers)
                if (trimmed.contains("SEND DEBUG") || trimmed.contains("RECEIVE DEBUG") || trimmed.contains("RECV DEBUG")) {
                    debugDirection = trimmed.contains("SEND DEBUG") ? "out" : "in";
                    debugRemote = "";
                    continue;
                }

                if ((trimmed.startsWith("To:") || trimmed.startsWith("From:")) && !debugDirection.isEmpty() && !parsingHeader && !insideMessageBody) {
                    // Extract remote address from debug format
                    String addr = trimmed.substring(3).trim();
                    if (addr.startsWith("/")) {
                        addr = addr.substring(1);
                    }
                    debugRemote = addr;
                    continue;
                }

                if (trimmed.equals("--- SIP MESSAGE START ---")) {
                    if (currentMsg != null) {
                        currentMsg.rawText = String.join("\n", rawTextLines);
                        messages.add(currentMsg);
                    }
                    currentMsg = new SipMessage();
                    currentMsg.direction = debugDirection;
                    if (debugDirection.equals("out")) {
                        currentMsg.source = normalizeHost("Local");
                        currentMsg.target = normalizeHost(debugRemote.isEmpty() ? "Remote" : debugRemote);
                    } else {
                        currentMsg.source = normalizeHost(debugRemote.isEmpty() ? "Remote" : debugRemote);
                        currentMsg.target = normalizeHost("Local");
                    }
                    currentMsg.label = "";
                    currentMsg.callId = "";
                    currentMsg.cseq = "";
                    currentMsg.rawText = "";
                    rawTextLines.clear();
                    parsingHeader = true;
                    insideMessageBody = true;
                    continue;
                }

                if (trimmed.equals("--- SIP MESSAGE END ---")) {
                    if (currentMsg != null) {
                        currentMsg.rawText = String.join("\n", rawTextLines);
                        messages.add(currentMsg);
                        currentMsg = null;
                    }
                    parsingHeader = false;
                    insideMessageBody = false;
                    debugDirection = "";
                    debugRemote = "";
                    continue;
                }

                // Check Format A (Standard clean SIP logs metadata lines)
                if ((trimmed.startsWith("From:") || trimmed.startsWith("From")) && trimmed.contains(" - To:") && !insideMessageBody) {
                    lastMetadataLine = trimmed;
                    continue;
                }

                if ((trimmed.equals("---------->") || trimmed.equals("<----------")) && !insideMessageBody) {
                    if (currentMsg != null) {
                        currentMsg.rawText = String.join("\n", rawTextLines);
                        messages.add(currentMsg);
                    }

                    String source = "Local";
                    String target = "Remote";
                    if (!lastMetadataLine.isEmpty()) {
                        java.util.regex.Matcher m = java.util.regex.Pattern.compile("From:?([^\\s]+)\\s+-\\s+To:([^\\s]+)").matcher(lastMetadataLine);
                        if (m.find()) {
                            source = m.group(1);
                            target = m.group(2);
                        }
                    } else {
                        if (trimmed.equals("---------->")) {
                            source = "Local";
                            target = "Remote";
                        } else {
                            source = "Remote";
                            target = "Local";
                        }
                    }

                    currentMsg = new SipMessage();
                    currentMsg.source = normalizeHost(source);
                    currentMsg.target = normalizeHost(target);
                    currentMsg.direction = trimmed.equals("---------->") ? "out" : "in";
                    currentMsg.label = "";
                    currentMsg.callId = "";
                    currentMsg.cseq = "";
                    currentMsg.rawText = "";
                    
                    rawTextLines.clear();
                    parsingHeader = true;
                    continue;
                }

                if ((trimmed.equals("----------") || trimmed.startsWith("cmd_") || trimmed.startsWith("//")) && !insideMessageBody) {
                    if (currentMsg != null) {
                        currentMsg.rawText = String.join("\n", rawTextLines);
                        messages.add(currentMsg);
                        currentMsg = null;
                    }
                    parsingHeader = false;
                    lastMetadataLine = "";
                    continue;
                }

                if (currentMsg != null) {
                    rawTextLines.add(line);

                    if (parsingHeader && !trimmed.isEmpty()) {
                        if (currentMsg.label.isEmpty()) {
                            if (trimmed.startsWith("SIP/2.0")) {
                                // Guard: a bare "SIP/2.0" line (len 7) would throw
                                // StringIndexOutOfBoundsException on substring(8),
                                // crashing the ladder load for the whole file.
                                currentMsg.label = trimmed.length() > 8 ? trimmed.substring(8) : trimmed;
                            } else if (trimmed.contains(" SIP/2.0")) {
                                String[] parts = trimmed.split(" ");
                                currentMsg.label = parts[0];
                            } else {
                                currentMsg.label = trimmed;
                            }
                        }

                        if (trimmed.toLowerCase().startsWith("call-id:")) {
                            currentMsg.callId = trimmed.substring(8).trim();
                        }

                        if (trimmed.toLowerCase().startsWith("cseq:")) {
                            currentMsg.cseq = trimmed.substring(5).trim();
                        }
                    } else if (trimmed.isEmpty()) {
                        parsingHeader = false;
                    }
                }
            }

            if (currentMsg != null) {
                currentMsg.rawText = String.join("\n", rawTextLines);
                messages.add(currentMsg);
            }

            // Post-process to normalize source and target to exactly two columns
            String localHost = null;
            String remoteHost = null;

            try {
                localHost = GlobalSettings.getLocalIP();
                remoteHost = GlobalSettings.getRemoteIPAddr();
            } catch (Exception e) {}

            for (SipMessage msg : messages) {
                String srcClean = cleanHostString(msg.source);
                String tgtClean = cleanHostString(msg.target);
                
                if (msg.direction.equals("out")) {
                    if (localHost == null || localHost.isEmpty()) {
                        if (!srcClean.equalsIgnoreCase("Local") && !srcClean.equalsIgnoreCase("Remote") && !srcClean.isEmpty() && !srcClean.contains("LOCAL") && !srcClean.contains("Remote")) {
                            localHost = srcClean;
                        }
                    }
                    if (remoteHost == null || remoteHost.isEmpty()) {
                        if (!tgtClean.equalsIgnoreCase("Local") && !tgtClean.equalsIgnoreCase("Remote") && !tgtClean.isEmpty() && !tgtClean.contains("LOCAL") && !tgtClean.contains("Remote")) {
                            remoteHost = tgtClean;
                        }
                    }
                } else if (msg.direction.equals("in")) {
                    if (remoteHost == null || remoteHost.isEmpty()) {
                        if (!srcClean.equalsIgnoreCase("Local") && !srcClean.equalsIgnoreCase("Remote") && !srcClean.isEmpty() && !srcClean.contains("LOCAL") && !srcClean.contains("Remote")) {
                            remoteHost = srcClean;
                        }
                    }
                    if (localHost == null || localHost.isEmpty()) {
                        if (!tgtClean.equalsIgnoreCase("Local") && !tgtClean.equalsIgnoreCase("Remote") && !tgtClean.isEmpty() && !tgtClean.contains("LOCAL") && !tgtClean.contains("Remote")) {
                            localHost = tgtClean;
                        }
                    }
                }
            }

            if (localHost == null || localHost.isEmpty()) {
                localHost = "SIP Simulator";
            }
            if (remoteHost == null || remoteHost.isEmpty()) {
                remoteHost = "Remote";
            }

            for (SipMessage msg : messages) {
                String srcClean = cleanHostString(msg.source);
                String tgtClean = cleanHostString(msg.target);

                boolean srcIsLocal = false;
                if (srcClean.equalsIgnoreCase("Local") || srcClean.equalsIgnoreCase("SIP Simulator") || srcClean.equalsIgnoreCase("SIP Inspector") || srcClean.contains("LOCAL")) {
                    srcIsLocal = true;
                } else if (localHost != null && srcClean.equalsIgnoreCase(localHost)) {
                    srcIsLocal = true;
                } else if (msg.direction.equals("out") && (srcClean.equalsIgnoreCase("Local") || srcClean.isEmpty())) {
                    srcIsLocal = true;
                }

                boolean tgtIsLocal = false;
                if (tgtClean.equalsIgnoreCase("Local") || tgtClean.equalsIgnoreCase("SIP Simulator") || tgtClean.equalsIgnoreCase("SIP Inspector") || tgtClean.contains("LOCAL")) {
                    tgtIsLocal = true;
                } else if (localHost != null && tgtClean.equalsIgnoreCase(localHost)) {
                    tgtIsLocal = true;
                } else if (msg.direction.equals("in") && (tgtClean.equalsIgnoreCase("Local") || tgtClean.isEmpty())) {
                    tgtIsLocal = true;
                }

                msg.source = srcIsLocal ? "SIP Simulator(LOCAL)" : remoteHost + "(Remote)";
                msg.target = tgtIsLocal ? "SIP Simulator(LOCAL)" : remoteHost + "(Remote)";
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return messages;
    }

    private static String cleanHostString(String host) {
        if (host == null) return "";
        String clean = host.trim();
        if (clean.startsWith("/")) {
            clean = clean.substring(1);
        }
        if (clean.startsWith("rc/")) {
            clean = clean.substring(3);
        }
        if (clean.startsWith("rc /")) {
            clean = clean.substring(4);
        }
        if (clean.contains(":")) {
            clean = clean.split(":")[0];
        }
        return clean.trim();
    }
}
