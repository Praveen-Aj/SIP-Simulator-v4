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
package sipinspector.Socket;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import sipinspector.MyPacket;
import sipinspector.SIPInspectorMainFrame;
import sipinspector.Statistics.StatisticsPanel;
import sipinspector.UI.EnhancedStatusBar;
import sipinspector.Utils.LogManager;
import sipinspector.Utils.PcapCapture;
import sipinspector.Utils.SIPParser;
import sipinspector.Utils.SIPStateTracker;

/**
 *
 * @author Zarko Coklin
 */
public abstract class MySocketAbstract {

    private static final ConcurrentHashMap<String, Long> requestTimers = new ConcurrentHashMap<>();
    private static final Set<String> inviteDialogs = ConcurrentHashMap.newKeySet();

    public abstract boolean open(InetAddress remoteAddress,
            int remotePort);

    public abstract boolean isOpen();

    public abstract Object getSocket();

    public abstract Selector getSelector();

    public abstract void close();

    public abstract InetAddress getLocalAddress();

    public abstract int getLocalPort();

    public abstract MyPacket receive() throws IOException;

    public abstract void send(MyPacket packet) throws IOException;

    public abstract String getTransportType();

    public abstract boolean isTransportUDP();

    public abstract SelectionKey getInitialKey();

    public abstract boolean isDaemon();

    public void logOutgoingMsg(MyPacket packet) {
        SIPStateTracker.updateState(packet, false);
        FileOutputStream fos = LogManager.getSipLogStream();
        if (fos != null) {
            Calendar cal = Calendar.getInstance();
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.S");
            String time = sdf.format(cal.getTime());
            String txt = "From:" + getLocalAddress().getHostAddress() +
                    ":" + getLocalPort() + " - To:" + packet.getAddress().getHostAddress() +
                    ":" + packet.getPort() + "    " + System.lineSeparator() +
                    "---------->" + System.lineSeparator();
            try {
                synchronized (fos) {
                    fos.write(txt.getBytes());
                    fos.write(packet.getBytes(), 0, packet.getLength());
                    fos.write(System.lineSeparator().getBytes());
                }
            } catch (IOException ignore) {
            }
        }

        capturePacket(packet, true);
        updateOutgoingStatistics(packet);
    }

    public void logIncomingMsg(MyPacket packet) {
        SIPStateTracker.updateState(packet, true);
        FileOutputStream fos = LogManager.getSipLogStream();
        if (fos != null) {
            Calendar cal = Calendar.getInstance();
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.S");
            String time = sdf.format(cal.getTime());
            String txt = "From:" + packet.getAddress().getHostAddress() + ":" + packet.getPort() +
                    " - To:" + getLocalAddress().getHostAddress() + ":" + getLocalPort() +
                    "    " + System.lineSeparator() + "<----------" + System.lineSeparator();
            try {
                synchronized (fos) {
                    fos.write(txt.getBytes());
                    fos.write(packet.getBytes(), 0, packet.getLength());
                    fos.write(System.lineSeparator().getBytes());
                }
            } catch (IOException ignore) {
            }
        }

        capturePacket(packet, false);
        updateIncomingStatistics(packet);
    }

    public abstract void setQos();

    /**
     * If a packet capture is active, write this SIP packet to the .pcap.
     * Uses TCP framing for TCP/TLS transports (TLS is captured in cleartext).
     */
    private void capturePacket(MyPacket packet, boolean outgoing) {
        try {
            PcapCapture cap = PcapCapture.getInstance();
            if (!cap.isCapturing()) {
                return;
            }
            InetAddress local = getLocalAddress();
            InetAddress remote = packet.getAddress();
            if (local == null || remote == null) {
                return;
            }
            boolean tcp = !isTransportUDP();
            int localPort = getLocalPort();
            int remotePort = packet.getPort();
            if (outgoing) {
                cap.writePacket(local, localPort, remote, remotePort, tcp, packet.getBytes(), packet.getLength());
            } else {
                cap.writePacket(remote, remotePort, local, localPort, tcp, packet.getBytes(), packet.getLength());
            }
        } catch (Exception ignore) {
        }
    }

    private void updateOutgoingStatistics(MyPacket packet) {
        try {
            StatisticsPanel stats = StatisticsPanel.getInstance();
            boolean counted = false;
            String message = packet.getMessageTxt();
            if (message != null && !message.isEmpty() && SIPParser.isMsgRequest(message)) {
                String method = SIPParser.getRequestMethod(message);
                if (method != null) {
                    method = method.toUpperCase();
                }
                String callId = SIPParser.getCallID(message);
                long now = System.currentTimeMillis();
                if (callId != null && method != null) {
                    requestTimers.put(callId + method, now);
                }
                if (method != null) {
                    switch (method) {
                        case "INVITE":
                            handleInviteTracking(callId, true);
                            stats.incrementInvite();
                            counted = true;
                            break;
                        case "REGISTER":
                            stats.incrementRegister();
                            counted = true;
                            break;
                        case "BYE":
                            endInviteDialog(callId);
                            stats.incrementBye();
                            counted = true;
                            break;
                        case "NOTIFY":
                            stats.incrementNotify();
                            counted = true;
                            break;
                        case "OPTIONS":
                            stats.incrementOptions();
                            counted = true;
                            break;
                        case "REFER":
                            stats.incrementRefer();
                            counted = true;
                            break;
                        default:
                            break;
                    }
                }
            }
            if (!counted) {
                stats.incrementSent();
            }
            EnhancedStatusBar sb = SIPInspectorMainFrame.getStatusBar();
            if (sb != null) {
                sb.incrementMessagesSent();
            }
        } catch (Exception ignore) {
        }
    }

    private void updateIncomingStatistics(MyPacket packet) {
        try {
            StatisticsPanel stats = StatisticsPanel.getInstance();
            boolean counted = false;
            String message = packet.getMessageTxt();
            if (message != null && !message.isEmpty()) {
                if (SIPParser.isMsgRequest(message)) {
                    String method = SIPParser.getRequestMethod(message);
                    String callId = SIPParser.getCallID(message);
                    if (method != null && method.equalsIgnoreCase("INVITE")) {
                        handleInviteTracking(callId, false);
                    } else if (method != null && method.equalsIgnoreCase("BYE")) {
                        endInviteDialog(callId);
                    }
                }
            }

            if (message != null && !message.isEmpty() && !SIPParser.isMsgRequest(message)) {
                String code = SIPParser.getResponseCode(message);
                String callId = SIPParser.getCallID(message);
                String requestMethod = extractMethodFromCSeq(message);
                if (callId != null && requestMethod != null) {
                    Long sentTime = requestTimers.remove(callId + requestMethod);
                    if (sentTime != null) {
                        stats.recordResponseTime(System.currentTimeMillis() - sentTime);
                    }
                }
                switch (code) {
                    case "200":
                        stats.increment200OK();
                        counted = true;
                        break;
                    case "401":
                        stats.increment401();
                        counted = true;
                        break;
                    case "403":
                        stats.increment403();
                        counted = true;
                        break;
                    case "404":
                        stats.increment404();
                        counted = true;
                        break;
                    case "488":
                        stats.increment488();
                        counted = true;
                        break;
                    case "500":
                        stats.increment500();
                        counted = true;
                        break;
                    case "604":
                        stats.increment604();
                        counted = true;
                        break;
                    default:
                        break;
                }
            }
            if (!counted) {
                stats.incrementReceived();
            }
            EnhancedStatusBar sb = SIPInspectorMainFrame.getStatusBar();
            if (sb != null) {
                sb.incrementMessagesReceived();
            }
        } catch (Exception ignore) {
        }
    }

    private String extractMethodFromCSeq(String message) {
        String cseq = SIPParser.getHeaderValue(message, "CSeq");
        if (cseq == null) {
            return null;
        }
        cseq = cseq.trim();
        int space = cseq.lastIndexOf(' ');
        if (space == -1 || space == cseq.length() - 1) {
            return null;
        }
        return cseq.substring(space + 1).toUpperCase();
    }

    private void handleInviteTracking(String callId, boolean outgoing) {
        if (callId == null || callId.isEmpty()) {
            return;
        }
        boolean isReinvite = !inviteDialogs.add(callId);
        updateInviteStatus(isReinvite ? "Re-INVITE" : "Initial INVITE");
    }

    private void endInviteDialog(String callId) {
        if (callId == null) {
            return;
        }
        if (inviteDialogs.remove(callId)) {
            updateInviteStatus("None");
        }
    }

    private void updateInviteStatus(String inviteType) {
        EnhancedStatusBar sb = SIPInspectorMainFrame.getStatusBar();
        if (sb != null) {
            sb.setInviteType(inviteType);
        }
    }
}
