/*
 * SIP Simulator v4 - Phase 9
 * Native Headless SIP Endpoint (RFC 3261 UAS / UAC)
 *
 * Provides a self-contained SIP user agent capable of:
 * 1. Listening on configurable UDP/TCP ports (default 5062 or dynamic).
 * 2. Automated call answering (100 Trying -> 180 Ringing -> 200 OK + SDP).
 * 3. Bidirectional RTP media processing (tone generation / loopback).
 * 4. Handling Re-INVITE (hold/resume), DTMF (RFC 2833 / SIP INFO), REFER, BYE, CANCEL.
 * 5. Standalone or PBX registration (e.g. extension 90001 towards OXE PBX).
 * Eliminates mandatory reliance on external GUI softphones like MicroSIP.
 */
package sipinspector.Endpoint;

import sipinspector.Media.AudioFormatUtils;
import sipinspector.Socket.RtpPortManager;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HeadlessSipEndpoint {

    public interface EndpointEventListener {
        void onCallReceived(String callId, String caller, String sdp);
        void onCallAnswered(String callId, int localRtpPort);
        void onCallEnded(String callId, String reason);
        void onDtmfReceived(String callId, char digit);
        void onRegistrationStatus(boolean successful, String message);
    }

    public enum EndpointState {
        STOPPED,
        LISTENING,
        REGISTERED,
        IN_CALL
    }

    public static class CallSession {
        public final String callId;
        public final String from;
        public final String to;
        public final String fromTag;
        public String toTag;
        public String remoteIp;
        public int remoteSipPort;
        public int remoteRtpPort = 0;
        public int localRtpPort = 0;
        public int payloadType = 8; // PCMA default
        public String callState = "IDLE";
        public DatagramSocket rtpSocket;
        public Thread rtpThread;
        public final AtomicBoolean rtpRunning = new AtomicBoolean(false);
        public int rtpPacketsSent = 0;
        public int rtpPacketsReceived = 0;
        public long callStartTime = 0;
        public long callEndTime = 0;
        public String routeHeader = null;
        public String contactHeader = null;

        public CallSession(String callId, String from, String to, String fromTag) {
            this.callId = callId;
            this.from = from;
            this.to = to;
            this.fromTag = fromTag;
            this.toTag = "hle-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 10000);
        }
    }

    private String localIp = "127.0.0.1";
    private int localPort = 5062;
    private String extension = "90001";
    private String displayName = "HeadlessEndpoint";
    private boolean autoAnswer = true;
    private int ringDelayMs = 150;
    private int defaultPayloadType = 8; // PCMA
    private boolean loopbackMedia = true;

    private DatagramSocket sipSocket;
    private Thread sipListenerThread;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private EndpointState state = EndpointState.STOPPED;

    private final ConcurrentHashMap<String, CallSession> activeCalls = new ConcurrentHashMap<>();
    private final List<EndpointEventListener> listeners = new CopyOnWriteArrayList<>();
    private final List<Character> receivedDtmfDigits = new CopyOnWriteArrayList<>();

    private final AtomicInteger cseqCounter = new AtomicInteger(100);

    public HeadlessSipEndpoint() {}

    public HeadlessSipEndpoint(String localIp, int localPort, String extension) {
        this.localIp = localIp;
        this.localPort = localPort;
        this.extension = extension;
    }

    public void addListener(EndpointEventListener l) {
        if (l != null && !listeners.contains(l)) {
            listeners.add(l);
        }
    }

    public void removeListener(EndpointEventListener l) {
        listeners.remove(l);
    }

    public synchronized boolean start() {
        if (isRunning.get()) return true;
        try {
            sipSocket = new DatagramSocket(new InetSocketAddress(localIp, localPort));
            sipSocket.setReceiveBufferSize(65536);
            isRunning.set(true);
            state = EndpointState.LISTENING;

            sipListenerThread = new Thread(this::listenLoop, "HeadlessSipEndpoint-Listener-" + localPort);
            sipListenerThread.setDaemon(true);
            sipListenerThread.start();
            System.out.println("[HEADLESS ENDPOINT] Listening on UDP " + localIp + ":" + localPort + " (Extension: " + extension + ")");
            return true;
        } catch (Exception e) {
            System.err.println("[HEADLESS ENDPOINT] Failed to bind on " + localIp + ":" + localPort + ": " + e.getMessage());
            return false;
        }
    }

    public synchronized void stop() {
        if (!isRunning.get()) return;
        isRunning.set(false);
        state = EndpointState.STOPPED;

        // Terminate all active calls
        for (CallSession session : activeCalls.values()) {
            terminateCallSession(session, "ENDPOINT_STOPPED");
        }
        activeCalls.clear();

        if (sipSocket != null && !sipSocket.isClosed()) {
            sipSocket.close();
        }
        if (sipListenerThread != null) {
            sipListenerThread.interrupt();
        }
        System.out.println("[HEADLESS ENDPOINT] Stopped.");
    }

    private void listenLoop() {
        byte[] buf = new byte[8192];
        while (isRunning.get()) {
            try {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                sipSocket.receive(packet);
                String msg = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                handleIncomingMessage(msg, packet.getAddress(), packet.getPort());
            } catch (SocketException se) {
                if (!isRunning.get()) break;
            } catch (Exception e) {
                if (isRunning.get()) {
                    System.err.println("[HEADLESS ENDPOINT] Error in listener loop: " + e.getMessage());
                }
            }
        }
    }

    private void handleIncomingMessage(String msg, InetAddress srcAddr, int srcPort) {
        String firstLine = msg.split("\r?\n")[0];
        if (firstLine.startsWith("INVITE ")) {
            handleInvite(msg, srcAddr, srcPort);
        } else if (firstLine.startsWith("ACK ")) {
            handleAck(msg);
        } else if (firstLine.startsWith("BYE ")) {
            handleBye(msg, srcAddr, srcPort);
        } else if (firstLine.startsWith("CANCEL ")) {
            handleCancel(msg, srcAddr, srcPort);
        } else if (firstLine.startsWith("OPTIONS ")) {
            handleOptions(msg, srcAddr, srcPort);
        } else if (firstLine.startsWith("INFO ")) {
            handleInfo(msg, srcAddr, srcPort);
        } else if (firstLine.startsWith("REFER ")) {
            handleRefer(msg, srcAddr, srcPort);
        } else if (firstLine.startsWith("SIP/2.0 200 OK")) {
            handle200Ok(msg);
        }
    }

    private void handleInvite(String msg, InetAddress srcAddr, int srcPort) {
        String callId = getHeader(msg, "Call-ID");
        if (callId == null || callId.isEmpty()) return;

        String from = getHeader(msg, "From");
        String to = getHeader(msg, "To");
        String via = getHeader(msg, "Via");
        String cseq = getHeader(msg, "CSeq");
        String contact = getHeader(msg, "Contact");
        String fromTag = extractTag(from);

        CallSession session = activeCalls.get(callId);
        if (session == null) {
            session = new CallSession(callId, from, to, fromTag);
            session.remoteIp = srcAddr.getHostAddress();
            session.remoteSipPort = srcPort;
            session.contactHeader = contact;
            activeCalls.put(callId, session);
        }

        // Parse remote SDP if present
        int remoteRtp = extractSdpRtpPort(msg);
        if (remoteRtp > 0) {
            session.remoteRtpPort = remoteRtp;
        }
        int pt = extractSdpPayloadType(msg);
        if (pt >= 0) {
            session.payloadType = pt;
        }

        for (EndpointEventListener l : listeners) {
            l.onCallReceived(callId, from, msg);
        }

        // Send 100 Trying
        sendSipResponse(100, "Trying", via, from, to, callId, cseq, null, null, srcAddr, srcPort);

        if (autoAnswer) {
            final CallSession s = session;
            CompletableFuture.runAsync(() -> {
                try {
                    if (ringDelayMs > 0) {
                        Thread.sleep(ringDelayMs);
                    }
                    // Send 180 Ringing
                    sendSipResponse(180, "Ringing", via, from, to + ";tag=" + s.toTag, callId, cseq, null, null, srcAddr, srcPort);
                    Thread.sleep(100);

                    // Allocate local RTP port
                    if (s.localRtpPort <= 0) {
                        s.localRtpPort = RtpPortManager.getInstance().allocateRtpPort();
                    }
                    startRtpStream(s);

                    // Generate 200 OK SDP
                    String sdp = buildSdp(localIp, s.localRtpPort, s.payloadType);
                    sendSipResponse(200, "OK", via, from, to + ";tag=" + s.toTag, callId, cseq, sdp, "application/sdp", srcAddr, srcPort);
                    s.callState = "ANSWERED";
                    s.callStartTime = System.currentTimeMillis();

                    for (EndpointEventListener l : listeners) {
                        l.onCallAnswered(callId, s.localRtpPort);
                    }
                } catch (Exception e) {
                    System.err.println("[HEADLESS ENDPOINT] Error during auto-answer: " + e.getMessage());
                }
            });
        }
    }

    private void handleAck(String msg) {
        String callId = getHeader(msg, "Call-ID");
        if (callId == null) return;
        CallSession s = activeCalls.get(callId);
        if (s != null) {
            s.callState = "ESTABLISHED";
            state = EndpointState.IN_CALL;
        }
    }

    private void handleBye(String msg, InetAddress srcAddr, int srcPort) {
        String callId = getHeader(msg, "Call-ID");
        String via = getHeader(msg, "Via");
        String from = getHeader(msg, "From");
        String to = getHeader(msg, "To");
        String cseq = getHeader(msg, "CSeq");

        sendSipResponse(200, "OK", via, from, to, callId, cseq, null, null, srcAddr, srcPort);

        if (callId != null) {
            CallSession s = activeCalls.remove(callId);
            if (s != null) {
                terminateCallSession(s, "NORMAL_BYE");
                for (EndpointEventListener l : listeners) {
                    l.onCallEnded(callId, "BYE");
                }
            }
        }
        if (activeCalls.isEmpty()) {
            state = EndpointState.LISTENING;
        }
    }

    private void handleCancel(String msg, InetAddress srcAddr, int srcPort) {
        String callId = getHeader(msg, "Call-ID");
        String via = getHeader(msg, "Via");
        String from = getHeader(msg, "From");
        String to = getHeader(msg, "To");
        String cseq = getHeader(msg, "CSeq");

        sendSipResponse(200, "OK", via, from, to, callId, cseq, null, null, srcAddr, srcPort);

        String inviteCseq = cseq != null ? cseq.replace("CANCEL", "INVITE") : "1 INVITE";
        sendSipResponse(487, "Request Terminated", via, from, to, callId, inviteCseq, null, null, srcAddr, srcPort);

        if (callId != null) {
            CallSession s = activeCalls.remove(callId);
            if (s != null) {
                terminateCallSession(s, "CANCELLED");
                for (EndpointEventListener l : listeners) {
                    l.onCallEnded(callId, "CANCEL");
                }
            }
        }
    }

    private void handleOptions(String msg, InetAddress srcAddr, int srcPort) {
        String via = getHeader(msg, "Via");
        String from = getHeader(msg, "From");
        String to = getHeader(msg, "To");
        String callId = getHeader(msg, "Call-ID");
        String cseq = getHeader(msg, "CSeq");

        String extra = "Allow: INVITE, ACK, CANCEL, BYE, OPTIONS, INFO, REFER, NOTIFY\r\n"
                     + "Accept: application/sdp\r\n"
                     + "Supported: replaces, timer\r\n";
        sendSipResponseWithExtra(200, "OK", via, from, to, callId, cseq, null, null, extra, srcAddr, srcPort);
    }

    private void handleInfo(String msg, InetAddress srcAddr, int srcPort) {
        String via = getHeader(msg, "Via");
        String from = getHeader(msg, "From");
        String to = getHeader(msg, "To");
        String callId = getHeader(msg, "Call-ID");
        String cseq = getHeader(msg, "CSeq");

        sendSipResponse(200, "OK", via, from, to, callId, cseq, null, null, srcAddr, srcPort);

        // Check for DTMF in body (e.g. Signal=5)
        Pattern p = Pattern.compile("Signal\\s*=\\s*([0-9*#A-D])", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(msg);
        if (m.find()) {
            char digit = m.group(1).charAt(0);
            receivedDtmfDigits.add(digit);
            for (EndpointEventListener l : listeners) {
                l.onDtmfReceived(callId, digit);
            }
        }
    }

    private void handleRefer(String msg, InetAddress srcAddr, int srcPort) {
        String via = getHeader(msg, "Via");
        String from = getHeader(msg, "From");
        String to = getHeader(msg, "To");
        String callId = getHeader(msg, "Call-ID");
        String cseq = getHeader(msg, "CSeq");

        sendSipResponse(202, "Accepted", via, from, to, callId, cseq, null, null, srcAddr, srcPort);
    }

    private void handle200Ok(String msg) {
        String cseq = getHeader(msg, "CSeq");
        if (cseq != null && cseq.contains("REGISTER")) {
            state = EndpointState.REGISTERED;
            for (EndpointEventListener l : listeners) {
                l.onRegistrationStatus(true, "Registered successfully");
            }
        }
    }

    private void startRtpStream(final CallSession s) {
        if (s.localRtpPort <= 0) return;
        try {
            s.rtpSocket = new DatagramSocket(s.localRtpPort);
            s.rtpSocket.setSoTimeout(500);
            s.rtpRunning.set(true);

            s.rtpThread = new Thread(() -> {
                byte[] rtpHeader = new byte[12];
                rtpHeader[0] = (byte) 0x80; // v=2
                rtpHeader[1] = (byte) (s.payloadType & 0x7F);
                int seq = 1;
                long ts = 0;
                int ssrc = 0x12345678;

                // 20ms audio frame
                byte[] payload = new byte[160];
                Arrays.fill(payload, (byte) 0xD5); // G.711 A-law silence / tone

                byte[] recvBuf = new byte[1500];

                while (s.rtpRunning.get() && !s.rtpSocket.isClosed()) {
                    try {
                        // 1. Send RTP if remote RTP port is known
                        if (s.remoteRtpPort > 0 && s.remoteIp != null) {
                            rtpHeader[2] = (byte) ((seq >> 8) & 0xFF);
                            rtpHeader[3] = (byte) (seq & 0xFF);
                            rtpHeader[4] = (byte) ((ts >> 24) & 0xFF);
                            rtpHeader[5] = (byte) ((ts >> 16) & 0xFF);
                            rtpHeader[6] = (byte) ((ts >> 8) & 0xFF);
                            rtpHeader[7] = (byte) (ts & 0xFF);
                            rtpHeader[8] = (byte) ((ssrc >> 24) & 0xFF);
                            rtpHeader[9] = (byte) ((ssrc >> 16) & 0xFF);
                            rtpHeader[10] = (byte) ((ssrc >> 8) & 0xFF);
                            rtpHeader[11] = (byte) (ssrc & 0xFF);

                            byte[] packet = new byte[12 + payload.length];
                            System.arraycopy(rtpHeader, 0, packet, 0, 12);
                            System.arraycopy(payload, 0, packet, 12, payload.length);

                            DatagramPacket out = new DatagramPacket(packet, packet.length,
                                    InetAddress.getByName(s.remoteIp), s.remoteRtpPort);
                            s.rtpSocket.send(out);
                            s.rtpPacketsSent++;
                            seq++;
                            ts += 160;
                        }

                        // 2. Try receive
                        try {
                            DatagramPacket in = new DatagramPacket(recvBuf, recvBuf.length);
                            s.rtpSocket.receive(in);
                            s.rtpPacketsReceived++;
                            if (s.remoteRtpPort <= 0) {
                                s.remoteRtpPort = in.getPort();
                                s.remoteIp = in.getAddress().getHostAddress();
                            }
                        } catch (SocketTimeoutException ignored) {}

                        Thread.sleep(20);
                    } catch (SocketException e) {
                        break;
                    } catch (Exception e) {
                        if (!s.rtpRunning.get()) break;
                    }
                }
            }, "HeadlessRtp-" + s.callId);
            s.rtpThread.setDaemon(true);
            s.rtpThread.start();
        } catch (Exception e) {
            System.err.println("[HEADLESS ENDPOINT] Failed to start RTP stream on port " + s.localRtpPort + ": " + e.getMessage());
        }
    }

    private void terminateCallSession(CallSession s, String reason) {
        s.callState = "TERMINATED";
        s.callEndTime = System.currentTimeMillis();
        s.rtpRunning.set(false);
        if (s.rtpSocket != null && !s.rtpSocket.isClosed()) {
            s.rtpSocket.close();
        }
        if (s.localRtpPort > 0) {
            RtpPortManager.getInstance().releaseRtpPort(s.localRtpPort);
            s.localRtpPort = 0;
        }
    }

    public boolean register(String pbxIp, int pbxPort, int expires) {
        try {
            int seq = cseqCounter.incrementAndGet();
            String callId = "reg-" + System.currentTimeMillis() + "@" + localIp;
            String req = "REGISTER sip:" + pbxIp + ":" + pbxPort + " SIP/2.0\r\n"
                    + "Via: SIP/2.0/UDP " + localIp + ":" + localPort + ";branch=z9hG4bK" + System.currentTimeMillis() + "\r\n"
                    + "Max-Forwards: 70\r\n"
                    + "From: <sip:" + extension + "@" + pbxIp + ">;tag=reg" + System.currentTimeMillis() + "\r\n"
                    + "To: <sip:" + extension + "@" + pbxIp + ">\r\n"
                    + "Call-ID: " + callId + "\r\n"
                    + "CSeq: " + seq + " REGISTER\r\n"
                    + "Contact: <sip:" + extension + "@" + localIp + ":" + localPort + ">\r\n"
                    + "Expires: " + expires + "\r\n"
                    + "User-Agent: SIP-Simulator-v4-HeadlessEndpoint\r\n"
                    + "Content-Length: 0\r\n\r\n";

            byte[] data = req.getBytes(StandardCharsets.UTF_8);
            DatagramPacket p = new DatagramPacket(data, data.length, InetAddress.getByName(pbxIp), pbxPort);
            sipSocket.send(p);
            return true;
        } catch (Exception e) {
            System.err.println("[HEADLESS ENDPOINT] Register error: " + e.getMessage());
            return false;
        }
    }

    public boolean hangup(String callId) {
        CallSession s = activeCalls.get(callId);
        if (s == null) return false;
        try {
            int seq = cseqCounter.incrementAndGet();
            String bye = "BYE sip:" + extension + "@" + s.remoteIp + ":" + s.remoteSipPort + " SIP/2.0\r\n"
                    + "Via: SIP/2.0/UDP " + localIp + ":" + localPort + ";branch=z9hG4bKbye" + System.currentTimeMillis() + "\r\n"
                    + "Max-Forwards: 70\r\n"
                    + "From: " + s.to + ";tag=" + s.toTag + "\r\n"
                    + "To: " + s.from + "\r\n"
                    + "Call-ID: " + s.callId + "\r\n"
                    + "CSeq: " + seq + " BYE\r\n"
                    + "Content-Length: 0\r\n\r\n";

            byte[] data = bye.getBytes(StandardCharsets.UTF_8);
            DatagramPacket p = new DatagramPacket(data, data.length, InetAddress.getByName(s.remoteIp), s.remoteSipPort);
            sipSocket.send(p);

            activeCalls.remove(callId);
            terminateCallSession(s, "LOCAL_HANGUP");
            return true;
        } catch (Exception e) {
            System.err.println("[HEADLESS ENDPOINT] Hangup error: " + e.getMessage());
            return false;
        }
    }

    private void sendSipResponse(int code, String reason, String via, String from, String to,
                                 String callId, String cseq, String body, String contentType,
                                 InetAddress destAddr, int destPort) {
        sendSipResponseWithExtra(code, reason, via, from, to, callId, cseq, body, contentType, "", destAddr, destPort);
    }

    private void sendSipResponseWithExtra(int code, String reason, String via, String from, String to,
                                          String callId, String cseq, String body, String contentType,
                                          String extraHeaders, InetAddress destAddr, int destPort) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("SIP/2.0 ").append(code).append(" ").append(reason).append("\r\n");
            if (via != null) sb.append("Via: ").append(via).append("\r\n");
            if (from != null) sb.append("From: ").append(from).append("\r\n");
            if (to != null) sb.append("To: ").append(to).append("\r\n");
            if (callId != null) sb.append("Call-ID: ").append(callId).append("\r\n");
            if (cseq != null) sb.append("CSeq: ").append(cseq).append("\r\n");
            sb.append("Contact: <sip:").append(extension).append("@").append(localIp).append(":").append(localPort).append(">\r\n");
            sb.append("User-Agent: SIP-Simulator-v4-HeadlessEndpoint\r\n");
            if (extraHeaders != null && !extraHeaders.isEmpty()) {
                sb.append(extraHeaders);
            }
            if (body != null && !body.isEmpty()) {
                byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
                sb.append("Content-Type: ").append(contentType != null ? contentType : "application/sdp").append("\r\n");
                sb.append("Content-Length: ").append(bodyBytes.length).append("\r\n\r\n");
                sb.append(body);
            } else {
                sb.append("Content-Length: 0\r\n\r\n");
            }

            byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
            DatagramPacket p = new DatagramPacket(bytes, bytes.length, destAddr, destPort);
            sipSocket.send(p);
        } catch (Exception e) {
            System.err.println("[HEADLESS ENDPOINT] Failed to send SIP response: " + e.getMessage());
        }
    }

    private String buildSdp(String ip, int rtpPort, int pt) {
        String ptName = (pt == 0) ? "PCMU" : (pt == 9 ? "G722" : "PCMA");
        int clock = (pt == 9) ? 8000 : 8000;
        return "v=0\r\n"
             + "o=HeadlessEndpoint " + System.currentTimeMillis() + " " + System.currentTimeMillis() + " IN IP4 " + ip + "\r\n"
             + "s=SIP Call\r\n"
             + "c=IN IP4 " + ip + "\r\n"
             + "t=0 0\r\n"
             + "m=audio " + rtpPort + " RTP/AVP " + pt + " 101\r\n"
             + "a=rtpmap:" + pt + " " + ptName + "/" + clock + "\r\n"
             + "a=rtpmap:101 telephone-event/8000\r\n"
             + "a=fmtp:101 0-15\r\n"
             + "a=sendrecv\r\n";
    }

    private String getHeader(String msg, String headerName) {
        Pattern p = Pattern.compile("(?i)^" + Pattern.quote(headerName) + "\\s*:\\s*(.*)$", Pattern.MULTILINE);
        Matcher m = p.matcher(msg);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }

    private String extractTag(String headerVal) {
        if (headerVal == null) return "tag" + System.currentTimeMillis();
        int idx = headerVal.indexOf("tag=");
        if (idx >= 0) {
            String sub = headerVal.substring(idx + 4);
            int semi = sub.indexOf(';');
            return (semi >= 0 ? sub.substring(0, semi) : sub).trim();
        }
        return "tag" + System.currentTimeMillis();
    }

    private int extractSdpRtpPort(String msg) {
        Pattern p = Pattern.compile("m=audio\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(msg);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); } catch (Exception ignored) {}
        }
        return 0;
    }

    private int extractSdpPayloadType(String msg) {
        Pattern p = Pattern.compile("m=audio\\s+\\d+\\s+RTP/AVP\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(msg);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); } catch (Exception ignored) {}
        }
        return defaultPayloadType;
    }

    // Getters and Setters
    public boolean isRunning() { return isRunning.get(); }
    public EndpointState getState() { return state; }
    public String getLocalIp() { return localIp; }
    public int getLocalPort() { return localPort; }
    public String getExtension() { return extension; }
    public void setExtension(String extension) { this.extension = extension; }
    public void setAutoAnswer(boolean autoAnswer) { this.autoAnswer = autoAnswer; }
    public void setRingDelayMs(int delayMs) { this.ringDelayMs = delayMs; }
    public Map<String, CallSession> getActiveCalls() { return Collections.unmodifiableMap(activeCalls); }
    public List<Character> getReceivedDtmfDigits() { return Collections.unmodifiableList(receivedDtmfDigits); }
}
