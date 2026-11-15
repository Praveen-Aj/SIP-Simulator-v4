package sipinspector.Call;

import sipinspector.GlobalSettings;
import sipinspector.Media.AudioDeviceManager;
import sipinspector.Media.AudioFormatUtils;
import sipinspector.Media.CallState;
import sipinspector.Media.LiveAudioCodec;
import sipinspector.Media.LiveAudioController;
import sipinspector.Media.LiveAudioSenderThread;
import sipinspector.Media.RtcpMonitor;
import sipinspector.Media.RtpWavRecorder;
import sipinspector.MyPacket;
import sipinspector.Socket.MySocketAbstract;
import sipinspector.Socket.TCPClientSocket;
import sipinspector.Socket.TLSClientSocket_V6;
import sipinspector.Socket.UDPClientSocket;
import sipinspector.Utils.AppPaths;
import sipinspector.Utils.SIPParser;
import sipinspector.Utils.SIPStateTracker;
import sipinspector.Utils.TLSDebugLogger;

import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Production Call Engine for SIP Simulator v4.
 *
 * Centralized orchestrator that manages:
 * 1. RFC 3261 Signaling (Outbound/Inbound calls, Registration, Hold/Resume Re-INVITEs, BYE, CANCEL, Rejection).
 * 2. Multi-dialog session tracking with RFC 3261 CallState lifecycle verification.
 * 3. Bidirectional RTP/RTCP media engine integration via LiveAudioController.
 * 4. Dual WAV recording (TX and RX) per Call-ID with audio fidelity and RMS energy analysis.
 * 5. Media controls (Mute, Speaker Mute, DTMF injection, Hold/Resume).
 * 6. Multi-codec support (PCMA PT=8, PCMU PT=0, G.722 PT=9).
 */
public class SipCallEngine {

    private static volatile SipCallEngine instance = null;

    public static synchronized SipCallEngine getInstance() {
        if (instance == null) {
            instance = new SipCallEngine();
        }
        return instance;
    }

    private MySocketAbstract signalingSocket = null;
    private String transportType = "UDP";
    private Thread listenerThread = null;
    private volatile boolean running = false;

    private String localIp = null;
    private int localSipPort = 5060;
    private String pbxIp = "192.168.0.20";
    private int pbxSipPort = 5060;
    private String registeredExtension = null;

    private int rtpPortRangeStart = 42000;
    private int rtpPortRangeEnd = 50000;
    private final Map<String, CallSession> activeSessions = new ConcurrentHashMap<>();
    private final AtomicInteger nextRtpPort = new AtomicInteger(42000);
    private final java.util.concurrent.atomic.AtomicLong callSeq = new java.util.concurrent.atomic.AtomicLong(0);

    public enum CallDirection {
        OUTBOUND,
        INBOUND
    }

    /**
     * Resolves the active local IP towards the given remote target using the OS routing table.
     */
    public static String resolveLocalIpTowards(String remoteIp) {
        if (remoteIp == null || remoteIp.trim().isEmpty()) {
            remoteIp = "192.168.0.20";
        }
        try {
            java.net.DatagramSocket s = new java.net.DatagramSocket();
            s.connect(InetAddress.getByName(remoteIp.trim()), 5060);
            InetAddress addr = s.getLocalAddress();
            s.close();
            if (addr != null && !addr.isAnyLocalAddress() && !addr.isLoopbackAddress()) {
                return addr.getHostAddress();
            }
        } catch (Exception ignored) {}
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }

    /**
     * Represents a single live call dialog session.
     */
    public static class CallSession {
        public final String callId;
        public final CallDirection direction;
        public String caller;
        public String callee;
        public volatile CallState state = CallState.IDLE;

        public int cseq = 1;
        public String localTag;
        public String remoteTag;
        public String inviteBranch;
        public String remoteContact;
        public List<String> recordRoutes = new ArrayList<>();

        public int localRtpPort;
        public String remoteRtpIp;
        public int remoteRtpPort;
        public int payloadType = 8;
        public String codecName = "PCMA";
        public int dtmfPayloadType = 101;

        public volatile boolean isOnHold = false;
        public volatile boolean isRemoteHold = false;
        public volatile boolean isMuted = false;
        public volatile boolean isCallWaiting = false;

        // Identity & Header Telemetry
        public String remoteDisplayName = "";
        public String pAssertedIdentity = "";
        public String remotePartyId = "";
        public String diversion = "";
        public String privacy = "";
        public String replacesDialog = null;
        public String referredBy = null;
        public String transport = "UDP";
        public final StringBuilder receivedDtmf = new StringBuilder();

        public LiveAudioController mediaController = null;
        public String txWavPath = null;
        public String rxWavPath = null;

        public long startTimeMs = System.currentTimeMillis();
        public long establishedTimeMs = 0;
        public long endTimeMs = 0;
        public long setupTimeMs = 0;

        // Transaction timers and retry tracking
        public String rawInviteMsg = null;
        public long lastInviteTransmitMs = 0;
        public int inviteRetransmitIntervalMs = 500;
        public long inviteTimeoutMs = 16000;
        public String failureReason = null;
        public String targetIp = null;
        public int targetPort = 5060;

        // Pending response synchronization
        public final Object lock = new Object();
        public String lastResponseTxt = null;
        public String lastResponseCode = null;

        public CallSession(String callId, CallDirection direction, String caller, String callee, int localRtpPort) {
            this.callId = callId;
            this.direction = direction;
            this.caller = caller;
            this.callee = callee;
            this.localRtpPort = localRtpPort;
            this.localTag = "tag" + (int)(Math.random() * 1000000);
        }

        public synchronized boolean setState(CallState next) {
            if (next == null) return false;
            if (state == next) return true;
            if (state.canTransitionTo(next)) {
                CallState old = state;
                state = next;
                if (TLSDebugLogger.isEnabled()) {
                    TLSDebugLogger.logf("[CALL ENGINE] Call %s state changed: %s -> %s", callId, old, next);
                }
                if (next == CallState.ESTABLISHED && establishedTimeMs == 0) {
                    establishedTimeMs = System.currentTimeMillis();
                } else if (next.isTerminal() && endTimeMs == 0) {
                    endTimeMs = System.currentTimeMillis();
                }
                return true;
            } else {
                if (TLSDebugLogger.isEnabled()) {
                    TLSDebugLogger.logf("[CALL ENGINE WARN] Invalid state transition rejected for Call %s: %s -> %s", callId, state, next);
                }
                return false;
            }
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new HashMap<>();
            m.put("callId", callId);
            m.put("direction", direction.name());
            m.put("caller", caller);
            m.put("callee", callee);
            m.put("state", state.name());
            m.put("codec", codecName);
            m.put("payloadType", payloadType);
            m.put("dtmfPayloadType", dtmfPayloadType);
            m.put("localRtpPort", localRtpPort);
            m.put("remoteRtpIp", remoteRtpIp);
            m.put("remoteRtpPort", remoteRtpPort);
            m.put("isOnHold", isOnHold);
            m.put("isRemoteHold", isRemoteHold);
            m.put("isMuted", isMuted);
            m.put("isCallWaiting", isCallWaiting);
            m.put("remoteDisplayName", remoteDisplayName);
            m.put("pAssertedIdentity", pAssertedIdentity);
            m.put("remotePartyId", remotePartyId);
            m.put("diversion", diversion);
            m.put("privacy", privacy);
            m.put("replacesDialog", replacesDialog);
            m.put("referredBy", referredBy);
            m.put("transport", transport);
            m.put("receivedDtmf", receivedDtmf.toString());
            m.put("txWavPath", txWavPath);
            m.put("rxWavPath", rxWavPath);
            m.put("durationSeconds", establishedTimeMs > 0 ? (System.currentTimeMillis() - establishedTimeMs) / 1000L : 0);
            m.put("setupTimeMs", setupTimeMs);
            m.put("failureReason", failureReason);

            if (mediaController != null) {
                m.put("mediaStats", mediaController.getStats());
                m.put("mediaHealth", mediaController.getMediaHealthSummary());
            }
            return m;
        }
    }

    private SipCallEngine() {
        loadNetworkConfig();
    }

    /**
     * Loads network and RTP configuration from GlobalSettings/settings.properties.
     */
    public synchronized void loadNetworkConfig() {
        try {
            GlobalSettings.loadSettings();
        } catch (Exception ignored) {}

        String cfgPbxIp = GlobalSettings.getSetting("sip.pbxIp");
        if (cfgPbxIp != null && !cfgPbxIp.trim().isEmpty()) {
            this.pbxIp = cfgPbxIp.trim();
        }
        String cfgPbxPort = GlobalSettings.getSetting("sip.pbxPort");
        if (cfgPbxPort != null && !cfgPbxPort.trim().isEmpty()) {
            try { this.pbxSipPort = Integer.parseInt(cfgPbxPort.trim()); } catch (Exception ignored) {}
        }
        String cfgLocalPort = GlobalSettings.getSetting("sip.localPort");
        if (cfgLocalPort != null && !cfgLocalPort.trim().isEmpty()) {
            try { this.localSipPort = Integer.parseInt(cfgLocalPort.trim()); } catch (Exception ignored) {}
        }
        String cfgTransport = GlobalSettings.getSetting("sip.transport");
        if (cfgTransport != null && !cfgTransport.trim().isEmpty()) {
            this.transportType = cfgTransport.trim().toUpperCase();
        }
        String cfgRtpStart = GlobalSettings.getSetting("rtp.portRangeStart");
        if (cfgRtpStart != null && !cfgRtpStart.trim().isEmpty()) {
            try { this.rtpPortRangeStart = Integer.parseInt(cfgRtpStart.trim()); } catch (Exception ignored) {}
        }
        String cfgRtpEnd = GlobalSettings.getSetting("rtp.portRangeEnd");
        if (cfgRtpEnd != null && !cfgRtpEnd.trim().isEmpty()) {
            try { this.rtpPortRangeEnd = Integer.parseInt(cfgRtpEnd.trim()); } catch (Exception ignored) {}
        }
        this.nextRtpPort.set(this.rtpPortRangeStart);

        String cfgLocalIp = GlobalSettings.getSetting("sip.localIp");
        if (cfgLocalIp != null && !cfgLocalIp.trim().isEmpty() && !"auto".equalsIgnoreCase(cfgLocalIp.trim())) {
            this.localIp = cfgLocalIp.trim();
        } else {
            this.localIp = resolveLocalIpTowards(this.pbxIp);
        }
    }

    /**
     * Start the signaling listener and initialize SIP Call Engine (defaulting to UDP).
     */
    public synchronized boolean startEngine(String localIp, int localSipPort, String pbxIp, int pbxSipPort) {
        return startEngine(localIp, localSipPort, pbxIp, pbxSipPort, "UDP");
    }

    /**
     * Start the signaling listener and initialize SIP Call Engine with specified transport (UDP, TCP, TLS).
     */
    public synchronized boolean startEngine(String localIp, int localSipPort, String pbxIp, int pbxSipPort, String transport) {
        if (transport == null || transport.trim().isEmpty()) transport = "UDP";
        this.transportType = transport.trim().toUpperCase();

        if (running && signalingSocket != null) {
            return true;
        }

        this.pbxIp = (pbxIp != null && !pbxIp.trim().isEmpty()) ? pbxIp.trim() : "192.168.0.20";
        this.pbxSipPort = pbxSipPort > 0 ? pbxSipPort : ("TLS".equalsIgnoreCase(this.transportType) ? 5061 : 5060);
        this.localSipPort = localSipPort > 0 ? localSipPort : 5060;

        String candidateIp = (localIp != null && !localIp.trim().isEmpty() && !"auto".equalsIgnoreCase(localIp.trim()))
                ? localIp.trim() : resolveLocalIpTowards(this.pbxIp);

        try {
            if ("TCP".equalsIgnoreCase(this.transportType)) {
                TCPClientSocket tcp = new TCPClientSocket(InetAddress.getByName(candidateIp), 0);
                if (tcp.open(InetAddress.getByName(this.pbxIp), this.pbxSipPort)) {
                    signalingSocket = tcp;
                    this.localIp = candidateIp;
                    this.localSipPort = tcp.getLocalPort();
                } else {
                    throw new IOException("TCP connection to " + this.pbxIp + ":" + this.pbxSipPort + " failed");
                }
            } else if ("TLS".equalsIgnoreCase(this.transportType)) {
                TLSClientSocket_V6 tls = new TLSClientSocket_V6(InetAddress.getByName(candidateIp), 0);
                if (tls.open(InetAddress.getByName(this.pbxIp), this.pbxSipPort)) {
                    signalingSocket = tls;
                    this.localIp = candidateIp;
                    this.localSipPort = tls.getLocalPort();
                } else {
                    throw new IOException("TLS connection to " + this.pbxIp + ":" + this.pbxSipPort + " failed");
                }
            } else {
                signalingSocket = new UDPClientSocket(InetAddress.getByName(candidateIp), this.localSipPort);
                this.localIp = candidateIp;
            }
        } catch (Exception e) {
            if ("UDP".equalsIgnoreCase(this.transportType)) {
                String autoIp = resolveLocalIpTowards(this.pbxIp);
                if (!autoIp.equals(candidateIp)) {
                    try {
                        signalingSocket = new UDPClientSocket(InetAddress.getByName(autoIp), this.localSipPort);
                        this.localIp = autoIp;
                    } catch (Exception ex2) {
                        System.err.println("SipCallEngine start failed on fallback " + autoIp + ": " + ex2.getMessage());
                        running = false;
                        return false;
                    }
                } else {
                    System.err.println("SipCallEngine start failed on " + candidateIp + ": " + e.getMessage());
                    running = false;
                    return false;
                }
            } else {
                System.err.println("SipCallEngine start failed on " + candidateIp + " (" + this.transportType + "): " + e.getMessage());
                running = false;
                return false;
            }
        }

        try {
            running = true;

            listenerThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    listenLoop();
                }
            }, "SipCallEngine-Listener-" + this.localSipPort);
            listenerThread.setDaemon(true);
            listenerThread.start();

            if (TLSDebugLogger.isEnabled()) {
                TLSDebugLogger.logf("[CALL ENGINE] Engine started on %s:%d [%s], target PBX=%s:%d",
                        this.localIp, this.localSipPort, this.transportType, this.pbxIp, this.pbxSipPort);
            }
            return true;
        } catch (Exception e) {
            System.err.println("SipCallEngine start failed: " + e.getMessage());
            running = false;
            return false;
        }
    }

    /**
     * Stop the engine, tear down all active calls and release signaling socket.
     */
    public synchronized void stopEngine() {
        running = false;

        // Hangup any remaining active calls
        for (String id : new ArrayList<>(activeSessions.keySet())) {
            try {
                hangupCall(id);
            } catch (Exception ignored) {}
        }
        activeSessions.clear();

        if (signalingSocket != null) {
            try {
                signalingSocket.close();
            } catch (Exception ignored) {}
            signalingSocket = null;
        }

        if (listenerThread != null) {
            try {
                listenerThread.join(1000);
            } catch (Exception ignored) {}
            listenerThread = null;
        }
    }

    /**
     * Restart the engine with clean resource release.
     */
    public synchronized boolean restartEngine() {
        String savedLocalIp = this.localIp;
        int savedLocalPort = this.localSipPort;
        String savedPbxIp = this.pbxIp;
        int savedPbxPort = this.pbxSipPort;
        String savedTransport = this.transportType;
        stopEngine();
        try {
            Thread.sleep(200);
        } catch (InterruptedException ignored) {}
        return startEngine(savedLocalIp, savedLocalPort, savedPbxIp, savedPbxPort, savedTransport);
    }

    public boolean isRunning() {
        return running;
    }

    public MySocketAbstract getSocket() {
        return signalingSocket;
    }

    public UDPClientSocket getSignalingSocket() {
        return (signalingSocket instanceof UDPClientSocket) ? (UDPClientSocket) signalingSocket : null;
    }

    public String getTransportType() {
        return transportType;
    }

    public String getLocalIp() {
        if (localIp == null || localIp.isEmpty() || "192.168.0.9".equals(localIp)) {
            localIp = resolveLocalIpTowards(pbxIp);
        }
        return localIp;
    }

    public int getLocalSipPort() {
        return localSipPort;
    }

    public String getPbxIp() {
        return pbxIp;
    }

    public int getPbxSipPort() {
        return pbxSipPort;
    }

    public String getRegisteredExtension() {
        return registeredExtension;
    }

    public Map<String, CallSession> getActiveSessions() {
        return Collections.unmodifiableMap(new HashMap<>(activeSessions));
    }

    public CallSession getCallSession(String callId) {
        if (callId == null) return null;
        CallSession s = activeSessions.get(callId);
        if (s == null) {
            s = activeSessions.get(normalizeCallId(callId));
        }
        return s;
    }

    private static String normalizeCallId(String id) {
        if (id == null) return "";
        int at = id.indexOf('@');
        return at != -1 ? id.substring(0, at).trim() : id.trim();
    }

    public List<CallSession> getActiveCalls() {
        Set<CallSession> unique = new LinkedHashSet<>(activeSessions.values());
        return new ArrayList<>(unique);
    }

    public void registerCallSession(CallSession session) {
        if (session != null && session.callId != null) {
            activeSessions.put(session.callId, session);
            String pfx = normalizeCallId(session.callId);
            if (!pfx.isEmpty()) activeSessions.put(pfx, session);
        }
    }

    /**
     * Register an extension with the OXE PBX.
     */
    public synchronized boolean register(String extension, int expiresSeconds) throws Exception {
        if (!running || signalingSocket == null) {
            startEngine(localIp, localSipPort, pbxIp, pbxSipPort);
        }

        String callPrefix = "reg" + (System.currentTimeMillis() % 10000000);
        String callId = callPrefix + "@" + localIp;
        CallSession session = new CallSession(callPrefix, CallDirection.OUTBOUND, extension, extension, 0);
        activeSessions.put(callPrefix, session);
        activeSessions.put(callId, session);

        String branch = "z9hG4bKreg" + System.currentTimeMillis();
        String regMsg =
                "REGISTER sip:" + pbxIp + " SIP/2.0\r\n" +
                "Via: SIP/2.0/UDP " + localIp + ":" + localSipPort + ";rport;branch=" + branch + "\r\n" +
                "Max-Forwards: 70\r\n" +
                "From: \"" + extension + "\" <sip:" + extension + "@" + pbxIp + ">;tag=reg" + (int)(Math.random() * 100000) + "\r\n" +
                "To: \"" + extension + "\" <sip:" + extension + "@" + pbxIp + ">\r\n" +
                "Call-ID: " + callId + "\r\n" +
                "CSeq: 1 REGISTER\r\n" +
                "Contact: \"" + extension + "\" <sip:" + extension + "@" + localIp + ":" + localSipPort + ";transport=udp>\r\n" +
                "Expires: " + expiresSeconds + "\r\n" +
                "Allow: PRACK, INVITE, ACK, BYE, CANCEL, UPDATE, INFO, SUBSCRIBE, NOTIFY, REFER, MESSAGE, OPTIONS\r\n" +
                "User-Agent: SIP Simulator v4\r\n" +
                "Content-Length: 0\r\n\r\n";

        MyPacket pkt = new MyPacket(regMsg);
        pkt.setAddress(InetAddress.getByName(pbxIp));
        pkt.setPort(pbxSipPort);
        signalingSocket.send(pkt);

        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < 4000) {
            CallSession s = getCallSession(callPrefix);
            if (s != null && "200".equals(s.lastResponseCode)) {
                registeredExtension = extension;
                SIPStateTracker.updateState(new MyPacket(s.lastResponseTxt), true);
                activeSessions.remove(callPrefix);
                activeSessions.remove(callId);
                return true;
            }
            Thread.sleep(50);
        }
        activeSessions.remove(callPrefix);
        activeSessions.remove(callId);
        return false;
    }

    /**
     * Unregister extension from OXE PBX.
     */
    public synchronized boolean unregister(String extension) throws Exception {
        return register(extension, 0);
    }

    /**
     * Initiate an outbound call (INVITE) to callee.
     */
    public synchronized CallSession makeCall(String caller, String callee, int requestedPt) throws Exception {
        return makeCall(caller, callee, requestedPt, this.pbxIp, this.pbxSipPort);
    }

    public synchronized CallSession makeCall(String caller, String callee, int requestedPt, String targetIp, int targetPort) throws Exception {
        if (!running) {
            startEngine(localIp, localSipPort, pbxIp, pbxSipPort);
        }

        if (targetIp == null || targetIp.trim().isEmpty()) targetIp = this.pbxIp;
        if (targetPort <= 0) targetPort = this.pbxSipPort;

        int rtpPort = allocateRtpPort();
        long seqNum = callSeq.incrementAndGet();
        String callPrefix = "call" + seqNum + "_" + System.currentTimeMillis();
        String fullCallId = callPrefix + "@" + localIp;
        CallSession session = new CallSession(fullCallId, CallDirection.OUTBOUND, caller, callee, rtpPort);
        session.targetIp = targetIp;
        session.targetPort = targetPort;
        session.payloadType = requestedPt > 0 ? requestedPt : 8;
        session.codecName = LiveAudioCodec.isSupported(requestedPt) ?
                LiveAudioCodec.create(requestedPt).rtpmapName() : "PCMA";
        session.startTimeMs = System.currentTimeMillis();
        session.lastInviteTransmitMs = session.startTimeMs;
        session.inviteRetransmitIntervalMs = 500;
        session.inviteTimeoutMs = 16000;

        activeSessions.put(fullCallId, session);
        activeSessions.put(callPrefix, session);
        session.setState(CallState.CALLING);

        String sdpOffer = buildSdpOffer(localIp, rtpPort, session.payloadType);
        session.inviteBranch = "z9hG4bKinv" + System.currentTimeMillis() + "_" + seqNum;
        session.transport = transportType;

        String inviteMsg =
                "INVITE sip:" + callee + "@" + targetIp + " SIP/2.0\r\n" +
                "Via: SIP/2.0/" + transportType + " " + localIp + ":" + localSipPort + ";rport;branch=" + session.inviteBranch + "\r\n" +
                "Max-Forwards: 70\r\n" +
                "From: \"" + caller + "\" <sip:" + caller + "@" + targetIp + ">;tag=" + session.localTag + "\r\n" +
                "To: \"" + callee + "\" <sip:" + callee + "@" + targetIp + ">\r\n" +
                "Call-ID: " + fullCallId + "\r\n" +
                "CSeq: " + session.cseq + " INVITE\r\n" +
                "Contact: \"" + caller + "\" <sip:" + caller + "@" + localIp + ":" + localSipPort + ";transport=" + transportType.toLowerCase() + ">\r\n" +
                "Allow: PRACK, INVITE, ACK, BYE, CANCEL, UPDATE, INFO, SUBSCRIBE, NOTIFY, REFER, MESSAGE, OPTIONS\r\n" +
                "Supported: replaces, timer, 100rel\r\n" +
                "P-Asserted-Identity: \"" + caller + "\" <sip:" + caller + "@" + targetIp + ">\r\n" +
                "User-Agent: SIP Simulator v4\r\n" +
                "Content-Type: application/sdp\r\n" +
                "Content-Length: " + sdpOffer.getBytes().length + "\r\n\r\n" +
                sdpOffer;

        session.rawInviteMsg = inviteMsg;

        MyPacket pkt = new MyPacket(inviteMsg);
        pkt.setAddress(InetAddress.getByName(targetIp));
        pkt.setPort(targetPort);
        signalingSocket.send(pkt);

        if (TLSDebugLogger.isEnabled()) {
            TLSDebugLogger.logf("[CALL ENGINE] INVITE sent for call %s (%s -> %s, PT=%d, Target=%s:%d)",
                    fullCallId, caller, callee, session.payloadType, targetIp, targetPort);
        }

        return session;
    }

    /**
     * Answer an incoming call.
     */
    public synchronized boolean answerCall(String callId) throws Exception {
        CallSession session = activeSessions.get(callId);
        if (session == null || session.direction != CallDirection.INBOUND) {
            throw new IllegalArgumentException("No inbound call found with Call-ID: " + callId);
        }

        String sdpAnswer = buildSdpOffer(localIp, session.localRtpPort, session.payloadType);

        StringBuilder okMsg = new StringBuilder();
        okMsg.append("SIP/2.0 200 OK\r\n");
        okMsg.append("Via: SIP/2.0/UDP ").append(pbxIp).append(":").append(pbxSipPort).append(";branch=").append(session.inviteBranch).append("\r\n");
        for (String rr : session.recordRoutes) {
            okMsg.append("Record-Route: ").append(rr).append("\r\n");
        }
        okMsg.append("From: <sip:").append(session.caller).append("@").append(pbxIp).append(">;tag=").append(session.remoteTag).append("\r\n");
        okMsg.append("To: <sip:").append(session.callee).append("@").append(pbxIp).append(">;tag=").append(session.localTag).append("\r\n");
        okMsg.append("Call-ID: ").append(callId).append("\r\n");
        okMsg.append("CSeq: ").append(session.cseq).append(" INVITE\r\n");
        okMsg.append("Contact: <sip:").append(session.callee).append("@").append(localIp).append(":").append(localSipPort).append(";transport=udp>\r\n");
        okMsg.append("Content-Type: application/sdp\r\n");
        okMsg.append("Content-Length: ").append(sdpAnswer.getBytes().length).append("\r\n\r\n");
        okMsg.append(sdpAnswer);

        MyPacket pkt = new MyPacket(okMsg.toString());
        pkt.setAddress(InetAddress.getByName(pbxIp));
        pkt.setPort(pbxSipPort);
        signalingSocket.send(pkt);

        session.setState(CallState.ESTABLISHED);
        startMediaForSession(session);
        return true;
    }

    /**
     * Reject an incoming call.
     */
    public synchronized boolean rejectCall(String callId, int statusCode, String reasonPhrase) throws Exception {
        CallSession session = activeSessions.get(callId);
        if (session == null) {
            return false;
        }

        if (statusCode <= 0) statusCode = 486;
        if (reasonPhrase == null) reasonPhrase = "Busy Here";

        StringBuilder resp = new StringBuilder();
        resp.append("SIP/2.0 ").append(statusCode).append(" ").append(reasonPhrase).append("\r\n");
        resp.append("Via: SIP/2.0/UDP ").append(pbxIp).append(":").append(pbxSipPort).append(";branch=").append(session.inviteBranch).append("\r\n");
        resp.append("From: <sip:").append(session.caller).append("@").append(pbxIp).append(">;tag=").append(session.remoteTag).append("\r\n");
        resp.append("To: <sip:").append(session.callee).append("@").append(pbxIp).append(">;tag=").append(session.localTag).append("\r\n");
        resp.append("Call-ID: ").append(callId).append("\r\n");
        resp.append("CSeq: ").append(session.cseq).append(" INVITE\r\n");
        resp.append("Content-Length: 0\r\n\r\n");

        MyPacket pkt = new MyPacket(resp.toString());
        pkt.setAddress(InetAddress.getByName(pbxIp));
        pkt.setPort(pbxSipPort);
        signalingSocket.send(pkt);

        session.setState(CallState.FAILED);
        activeSessions.remove(callId);
        return true;
    }

    /**
     * Place an established call on hold using Re-INVITE with a=sendonly.
     */
    public synchronized boolean holdCall(String callId) throws Exception {
        CallSession session = getCallSession(callId);
        if (session == null || session.state != CallState.ESTABLISHED) {
            return false;
        }

        session.cseq++;
        session.isOnHold = true;
        String sdp = buildSdpHold(localIp, session.localRtpPort, session.payloadType);
        sendReInvite(session, sdp);

        if (session.mediaController != null) {
            session.mediaController.pauseAudio();
        }
        return true;
    }

    /**
     * Resume a held call using Re-INVITE with a=sendrecv.
     */
    public synchronized boolean resumeCall(String callId) throws Exception {
        CallSession session = getCallSession(callId);
        if (session == null || session.state != CallState.ESTABLISHED) {
            return false;
        }

        session.cseq++;
        session.isOnHold = false;
        String sdp = buildSdpOffer(localIp, session.localRtpPort, session.payloadType);
        sendReInvite(session, sdp);

        if (session.mediaController != null) {
            session.mediaController.resumeAudio();
        }
        return true;
    }

    /**
     * Set microphone muting for a call.
     */
    public synchronized boolean setMute(String callId, boolean muted) {
        CallSession session = activeSessions.get(callId);
        if (session == null) return false;
        session.isMuted = muted;
        if (session.mediaController != null) {
            session.mediaController.setMicrophoneMuted(muted);
        }
        return true;
    }

    /**
     * Send DTMF telephone-event via RFC 2833 / RFC 4733 (default).
     */
    public synchronized boolean sendDtmf(String callId, char digit) {
        return sendDtmf(callId, digit, "RFC2833");
    }

    /**
     * Send DTMF digit using specified method ("RFC2833" or "INFO").
     */
    public synchronized boolean sendDtmf(String callId, char digit, String method) {
        CallSession session = getCallSession(callId);
        if (session == null || session.state != CallState.ESTABLISHED) return false;

        if ("INFO".equalsIgnoreCase(method)) {
            try {
                return sendInfoDtmf(callId, digit, 160);
            } catch (Exception e) {
                if (TLSDebugLogger.isEnabled()) {
                    TLSDebugLogger.log("[DTMF INFO ERROR] " + e.getMessage());
                }
                return false;
            }
        } else {
            // RFC 2833 / 4733 RTP telephone-event
            if (session.mediaController != null) {
                return session.mediaController.sendDtmf(digit, session.dtmfPayloadType);
            } else {
                try {
                    java.net.DatagramSocket s = new java.net.DatagramSocket();
                    LiveAudioController.sendDtmfDirect(digit, s, InetAddress.getByName(session.remoteRtpIp), session.remoteRtpPort, session.dtmfPayloadType);
                    s.close();
                    return true;
                } catch (Exception ex) {
                    if (TLSDebugLogger.isEnabled()) {
                        TLSDebugLogger.log("[DTMF 2833 ERROR] " + ex.getMessage());
                    }
                    return false;
                }
            }
        }
    }

    /**
     * Send DTMF via SIP INFO (RFC 6086 / RFC 2976).
     */
    public synchronized boolean sendInfoDtmf(String callId, char digit, int durationMs) throws Exception {
        CallSession session = getCallSession(callId);
        if (session == null || session.state != CallState.ESTABLISHED) return false;

        session.cseq++;
        String targetUri = session.remoteContact != null ? session.remoteContact : ("sip:" + (session.direction == CallDirection.OUTBOUND ? session.callee : session.caller) + "@" + pbxIp);
        if (targetUri.startsWith("<") && targetUri.contains(">")) {
            targetUri = targetUri.substring(1, targetUri.indexOf('>'));
        }

        String body = "Signal=" + digit + "\r\nDuration=" + (durationMs > 0 ? durationMs : 160) + "\r\n";

        StringBuilder info = new StringBuilder();
        info.append("INFO ").append(targetUri).append(" SIP/2.0\r\n");
        info.append("Via: SIP/2.0/").append(transportType).append(" ").append(localIp).append(":").append(localSipPort).append(";rport;branch=z9hG4bKinfo").append(System.currentTimeMillis()).append("\r\n");
        info.append("Max-Forwards: 70\r\n");
        if (session.direction == CallDirection.OUTBOUND) {
            info.append("From: <sip:").append(session.caller).append("@").append(pbxIp).append(">;tag=").append(session.localTag).append("\r\n");
            info.append("To: <sip:").append(session.callee).append("@").append(pbxIp).append(">;tag=").append(session.remoteTag).append("\r\n");
        } else {
            info.append("From: <sip:").append(session.callee).append("@").append(pbxIp).append(">;tag=").append(session.localTag).append("\r\n");
            info.append("To: <sip:").append(session.caller).append("@").append(pbxIp).append(">;tag=").append(session.remoteTag).append("\r\n");
        }
        info.append("Call-ID: ").append(session.callId).append("\r\n");
        info.append("CSeq: ").append(session.cseq).append(" INFO\r\n");
        info.append("Content-Type: application/dtmf-relay\r\n");
        info.append("Content-Length: ").append(body.getBytes().length).append("\r\n\r\n");
        info.append(body);

        if (signalingSocket != null) {
            MyPacket pkt = new MyPacket(info.toString());
            pkt.setAddress(InetAddress.getByName(session.targetIp != null ? session.targetIp : pbxIp));
            pkt.setPort(session.targetPort > 0 ? session.targetPort : pbxSipPort);
            signalingSocket.send(pkt);
        }
        return true;
    }

    /**
     * Transfer an established call using SIP REFER (RFC 3515).
     * Supports both Blind transfer (replacesCallId=null) and Attended transfer (with Replaces).
     */
    public synchronized boolean transferCall(String callId, String targetExtension, String replacesCallId) throws Exception {
        CallSession session = getCallSession(callId);
        if (session == null || session.state != CallState.ESTABLISHED) return false;

        session.cseq++;
        String targetUri = session.remoteContact != null ? session.remoteContact : ("sip:" + (session.direction == CallDirection.OUTBOUND ? session.callee : session.caller) + "@" + pbxIp);
        if (targetUri.startsWith("<") && targetUri.contains(">")) {
            targetUri = targetUri.substring(1, targetUri.indexOf('>'));
        }

        StringBuilder referTo = new StringBuilder();
        referTo.append("<sip:").append(targetExtension).append("@").append(pbxIp);
        if (replacesCallId != null && !replacesCallId.trim().isEmpty()) {
            CallSession repSession = getCallSession(replacesCallId);
            if (repSession != null) {
                referTo.append("?Replaces=").append(repSession.callId)
                        .append("%3Bto-tag%3D").append(repSession.remoteTag)
                        .append("%3Bfrom-tag%3D").append(repSession.localTag);
            } else {
                referTo.append("?Replaces=").append(replacesCallId.trim());
            }
        }
        referTo.append(">");

        StringBuilder refer = new StringBuilder();
        refer.append("REFER ").append(targetUri).append(" SIP/2.0\r\n");
        refer.append("Via: SIP/2.0/").append(transportType).append(" ").append(localIp).append(":").append(localSipPort).append(";rport;branch=z9hG4bKref").append(System.currentTimeMillis()).append("\r\n");
        refer.append("Max-Forwards: 70\r\n");
        if (session.direction == CallDirection.OUTBOUND) {
            refer.append("From: <sip:").append(session.caller).append("@").append(pbxIp).append(">;tag=").append(session.localTag).append("\r\n");
            refer.append("To: <sip:").append(session.callee).append("@").append(pbxIp).append(">;tag=").append(session.remoteTag).append("\r\n");
        } else {
            refer.append("From: <sip:").append(session.callee).append("@").append(pbxIp).append(">;tag=").append(session.localTag).append("\r\n");
            refer.append("To: <sip:").append(session.caller).append("@").append(pbxIp).append(">;tag=").append(session.remoteTag).append("\r\n");
        }
        refer.append("Call-ID: ").append(session.callId).append("\r\n");
        refer.append("CSeq: ").append(session.cseq).append(" REFER\r\n");
        refer.append("Refer-To: ").append(referTo).append("\r\n");
        refer.append("Referred-By: <sip:").append(session.caller != null ? session.caller : registeredExtension).append("@").append(pbxIp).append(">\r\n");
        refer.append("Contact: <sip:").append(registeredExtension != null ? registeredExtension : "90008").append("@").append(localIp).append(":").append(localSipPort).append(";transport=").append(transportType.toLowerCase()).append(">\r\n");
        refer.append("User-Agent: SIP Simulator v4\r\n");
        refer.append("Content-Length: 0\r\n\r\n");

        if (signalingSocket != null) {
            MyPacket pkt = new MyPacket(refer.toString());
            pkt.setAddress(InetAddress.getByName(session.targetIp != null ? session.targetIp : pbxIp));
            pkt.setPort(session.targetPort > 0 ? session.targetPort : pbxSipPort);
            signalingSocket.send(pkt);
        }
        return true;
    }

    /**
     * Terminate an established call using BYE.
     */
    public synchronized boolean hangupCall(String callId) throws Exception {
        CallSession session = getCallSession(callId);
        if (session == null) {
            return false;
        }

        if (session.state == CallState.CALLING || session.state == CallState.RINGING || session.state == CallState.PROCEEDING) {
            try {
                cancelCall(session.callId);
            } catch (Exception ignored) {}
            stopMediaForSession(session);
            session.setState(CallState.TERMINATED);
            cleanupSessionMaps(session, callId);
            return true;
        }

        if (session.state == CallState.FAILED || session.state == CallState.TERMINATED) {
            stopMediaForSession(session);
            cleanupSessionMaps(session, callId);
            return true;
        }

        session.setState(CallState.TERMINATING);
        session.cseq++;

        String targetUri = session.remoteContact != null ? session.remoteContact : ("sip:" + (session.direction == CallDirection.OUTBOUND ? session.callee : session.caller) + "@" + pbxIp);
        if (targetUri.startsWith("<") && targetUri.contains(">")) {
            targetUri = targetUri.substring(1, targetUri.indexOf('>'));
        }

        StringBuilder bye = new StringBuilder();
        bye.append("BYE ").append(targetUri).append(" SIP/2.0\r\n");
        bye.append("Via: SIP/2.0/").append(session.transport != null ? session.transport : transportType).append(" ").append(localIp).append(":").append(localSipPort).append(";rport;branch=z9hG4bKbye").append(System.currentTimeMillis()).append("\r\n");
        bye.append("Max-Forwards: 70\r\n");

        if (!session.recordRoutes.isEmpty()) {
            for (String r : session.recordRoutes) {
                bye.append("Route: ").append(r).append("\r\n");
            }
        }

        if (session.direction == CallDirection.OUTBOUND) {
            bye.append("From: <sip:").append(session.caller).append("@").append(pbxIp).append(">;tag=").append(session.localTag).append("\r\n");
            bye.append("To: <sip:").append(session.callee).append("@").append(pbxIp).append(">;tag=").append(session.remoteTag).append("\r\n");
        } else {
            bye.append("From: <sip:").append(session.callee).append("@").append(pbxIp).append(">;tag=").append(session.localTag).append("\r\n");
            bye.append("To: <sip:").append(session.caller).append("@").append(pbxIp).append(">;tag=").append(session.remoteTag).append("\r\n");
        }

        bye.append("Call-ID: ").append(session.callId).append("\r\n");
        bye.append("CSeq: ").append(session.cseq).append(" BYE\r\n");
        bye.append("User-Agent: SIP Simulator v4\r\n");
        bye.append("Content-Length: 0\r\n\r\n");

        if (signalingSocket != null) {
            MyPacket pkt = new MyPacket(bye.toString());
            pkt.setAddress(InetAddress.getByName(pbxIp));
            pkt.setPort(pbxSipPort);
            signalingSocket.send(pkt);
        }

        stopMediaForSession(session);
        session.setState(CallState.TERMINATED);
        cleanupSessionMaps(session, callId);
        return true;
    }

    private void cleanupSessionMaps(CallSession session, String callId) {
        if (callId != null) activeSessions.remove(callId);
        if (session != null && session.callId != null) {
            activeSessions.remove(session.callId);
            String pfx = normalizeCallId(session.callId);
            if (!pfx.isEmpty()) activeSessions.remove(pfx);
        }
    }

    /**
     * Cancel a pending outbound call using CANCEL prior to 200 OK.
     */
    public synchronized boolean cancelCall(String callId) throws Exception {
        CallSession session = getCallSession(callId);
        if (session == null || (session.state != CallState.CALLING && session.state != CallState.RINGING && session.state != CallState.PROCEEDING)) {
            return false;
        }

        session.setState(CallState.TERMINATING);
        StringBuilder cancel = new StringBuilder();
        cancel.append("CANCEL sip:").append(session.callee).append("@").append(pbxIp).append(" SIP/2.0\r\n");
        cancel.append("Via: SIP/2.0/").append(session.transport != null ? session.transport : transportType).append(" ").append(localIp).append(":").append(localSipPort).append(";rport;branch=").append(session.inviteBranch).append("\r\n");
        cancel.append("Max-Forwards: 70\r\n");
        cancel.append("From: <sip:").append(session.caller).append("@").append(pbxIp).append(">;tag=").append(session.localTag).append("\r\n");
        cancel.append("To: <sip:").append(session.callee).append("@").append(pbxIp).append(">\r\n");
        cancel.append("Call-ID: ").append(session.callId).append("\r\n");
        cancel.append("CSeq: ").append(session.cseq).append(" CANCEL\r\n");
        cancel.append("User-Agent: SIP Simulator v4\r\n");
        cancel.append("Content-Length: 0\r\n\r\n");

        MyPacket pkt = new MyPacket(cancel.toString());
        pkt.setAddress(InetAddress.getByName(pbxIp));
        pkt.setPort(pbxSipPort);
        signalingSocket.send(pkt);

        if (TLSDebugLogger.isEnabled()) {
            TLSDebugLogger.logf("[CALL ENGINE] CANCEL sent for call %s", session.callId);
        }
        return true;
    }

    private void sendReInvite(CallSession session, String sdp) throws Exception {
        String targetUri = session.remoteContact != null ? session.remoteContact : ("sip:" + (session.direction == CallDirection.OUTBOUND ? session.callee : session.caller) + "@" + pbxIp);
        if (targetUri.startsWith("<") && targetUri.contains(">")) {
            targetUri = targetUri.substring(1, targetUri.indexOf('>'));
        }

        StringBuilder reInv = new StringBuilder();
        reInv.append("INVITE ").append(targetUri).append(" SIP/2.0\r\n");
        reInv.append("Via: SIP/2.0/").append(session.transport != null ? session.transport : transportType).append(" ").append(localIp).append(":").append(localSipPort).append(";rport;branch=z9hG4bKreinv").append(System.currentTimeMillis()).append("\r\n");
        reInv.append("Max-Forwards: 70\r\n");

        if (!session.recordRoutes.isEmpty()) {
            for (String r : session.recordRoutes) {
                reInv.append("Route: ").append(r).append("\r\n");
            }
        }

        reInv.append("From: <sip:").append(session.caller).append("@").append(pbxIp).append(">;tag=").append(session.localTag).append("\r\n");
        reInv.append("To: <sip:").append(session.callee).append("@").append(pbxIp).append(">;tag=").append(session.remoteTag).append("\r\n");
        reInv.append("Call-ID: ").append(session.callId).append("\r\n");
        reInv.append("CSeq: ").append(session.cseq).append(" INVITE\r\n");
        reInv.append("Contact: <sip:").append(session.caller).append("@").append(localIp).append(":").append(localSipPort).append(";transport=").append(session.transport != null ? session.transport.toLowerCase() : transportType.toLowerCase()).append(">\r\n");
        reInv.append("Content-Type: application/sdp\r\n");
        reInv.append("Content-Length: ").append(sdp.getBytes().length).append("\r\n\r\n");
        reInv.append(sdp);

        MyPacket pkt = new MyPacket(reInv.toString());
        pkt.setAddress(InetAddress.getByName(pbxIp));
        pkt.setPort(pbxSipPort);
        signalingSocket.send(pkt);
    }

    private void sendAck(CallSession session) throws Exception {
        String ackTarget = session.remoteContact != null ? session.remoteContact : ("sip:" + session.callee + "@" + pbxIp);
        if (ackTarget.startsWith("<") && ackTarget.contains(">")) {
            ackTarget = ackTarget.substring(1, ackTarget.indexOf('>'));
        }

        StringBuilder ack = new StringBuilder();
        ack.append("ACK ").append(ackTarget).append(" SIP/2.0\r\n");
        ack.append("Via: SIP/2.0/").append(session.transport != null ? session.transport : transportType).append(" ").append(localIp).append(":").append(localSipPort).append(";rport;branch=z9hG4bKack").append(System.currentTimeMillis()).append("\r\n");
        ack.append("Max-Forwards: 70\r\n");

        if (!session.recordRoutes.isEmpty()) {
            for (String r : session.recordRoutes) {
                ack.append("Route: ").append(r).append("\r\n");
            }
        }

        ack.append("From: <sip:").append(session.caller).append("@").append(pbxIp).append(">;tag=").append(session.localTag).append("\r\n");
        ack.append("To: <sip:").append(session.callee).append("@").append(pbxIp).append(">;tag=").append(session.remoteTag).append("\r\n");
        ack.append("Call-ID: ").append(session.callId).append("\r\n");
        ack.append("CSeq: ").append(session.cseq).append(" ACK\r\n");
        ack.append("User-Agent: SIP Simulator v4\r\n");
        ack.append("Content-Length: 0\r\n\r\n");

        MyPacket pkt = new MyPacket(ack.toString());
        pkt.setAddress(InetAddress.getByName(pbxIp));
        pkt.setPort(pbxSipPort);
        signalingSocket.send(pkt);

        if (TLSDebugLogger.isEnabled()) {
            TLSDebugLogger.logf("[CALL ENGINE] ACK sent for call %s", session.callId);
        }
    }

    private void startMediaForSession(CallSession session) {
        if (session.remoteRtpPort <= 0 || session.remoteRtpIp == null) {
            session.remoteRtpIp = pbxIp;
            session.remoteRtpPort = 10000;
        }

        try {
            GlobalSettings.setSetting("RTP_Remote_Address", session.remoteRtpIp);
            GlobalSettings.setSetting("RTP_Remote_Port", String.valueOf(session.remoteRtpPort));
            GlobalSettings.setSetting("RTP_Local_Port", String.valueOf(session.localRtpPort));
            GlobalSettings.setSetting("RTP_Remote_PT", String.valueOf(session.payloadType));

            LiveAudioController c = new LiveAudioController();
            c.setDtmfPayloadType(session.dtmfPayloadType);
            c.initializeForCall(session.callId, session.remoteRtpIp, session.remoteRtpPort,
                    localIp, session.localRtpPort, session.payloadType, false, null);

            session.mediaController = c;
            c.startRecording(session.callId);
            c.startAudio();

            RtpWavRecorder tx = c.getTxRecorder();
            RtpWavRecorder rx = c.getRxRecorder();
            if (tx != null) session.txWavPath = tx.getWavPath();
            if (rx != null) session.rxWavPath = rx.getWavPath();

            if (TLSDebugLogger.isEnabled()) {
                TLSDebugLogger.logf("[CALL ENGINE] Media active for call %s (Local:%d <-> Remote:%s:%d, Codec:%s, PT:%d)",
                        session.callId, session.localRtpPort, session.remoteRtpIp, session.remoteRtpPort,
                        session.codecName, session.payloadType);
            }
        } catch (Exception ex) {
            System.err.println("Failed to start media for call " + session.callId + ": " + ex.getMessage());
        }
    }

    private void stopMediaForSession(CallSession session) {
        if (session.mediaController != null) {
            try {
                session.mediaController.stopAudio();
            } catch (Exception ignored) {}
            session.mediaController = null;
        }
    }

    private void listenLoop() {
        while (running && signalingSocket != null) {
            try {
                checkPendingTransactions();
                MyPacket packet = signalingSocket.receive();
                if (packet == null || packet.getMessageTxt() == null || packet.getMessageTxt().trim().isEmpty()) {
                    continue;
                }

                String msg = packet.getMessageTxt();
                String callId = SIPParser.getCallID(msg);
                String rawCallId = SIPParser.getHeaderValue(msg, "Call-ID", "i");
                if (rawCallId != null) rawCallId = rawCallId.trim();

                CallSession session = getCallSession(callId);
                if (session == null && rawCallId != null) {
                    session = getCallSession(rawCallId);
                }

                boolean isRequest = SIPParser.isMsgRequest(msg);

                if (isRequest) {
                    handleIncomingRequest(packet, msg, callId, rawCallId, session);
                } else {
                    handleIncomingResponse(packet, msg, callId, rawCallId, session);
                }
            } catch (Exception e) {
                if (running) {
                    // loop continues
                }
            }
        }
    }

    /**
     * Process a raw SIP message directly (used for offline deterministic testing and protocol simulation).
     */
    public void processMessage(String msg) {
        if (msg == null || msg.trim().isEmpty()) return;
        try {
            MyPacket pkt = new MyPacket(msg);
            String callId = SIPParser.getCallID(msg);
            String rawCallId = SIPParser.getHeaderValue(msg, "Call-ID", "i");
            if (rawCallId != null) rawCallId = rawCallId.trim();

            CallSession session = getCallSession(callId);
            if (session == null && rawCallId != null) {
                session = getCallSession(rawCallId);
            }

            boolean isRequest = SIPParser.isMsgRequest(msg);
            if (isRequest) {
                handleIncomingRequest(pkt, msg, callId, rawCallId, session);
            } else {
                handleIncomingResponse(pkt, msg, callId, rawCallId, session);
            }
        } catch (Exception ignored) {}
    }

    private void checkPendingTransactions() {
        long now = System.currentTimeMillis();
        for (CallSession session : new ArrayList<>(activeSessions.values())) {
            if (session.direction == CallDirection.OUTBOUND && session.state == CallState.CALLING) {
                // Check Timer B (timeout)
                if (now - session.startTimeMs >= session.inviteTimeoutMs) {
                    session.failureReason = "408 Request Timeout";
                    session.lastResponseCode = "408";
                    session.setState(CallState.FAILED);
                    synchronized (session.lock) {
                        session.lock.notifyAll();
                    }
                    if (TLSDebugLogger.isEnabled()) {
                        TLSDebugLogger.logf("[CALL ENGINE] Timer B expired for call %s: 408 Request Timeout", session.callId);
                    }
                } else if (session.rawInviteMsg != null && (now - session.lastInviteTransmitMs) >= session.inviteRetransmitIntervalMs) {
                    // Timer A retransmission
                    try {
                        MyPacket pkt = new MyPacket(session.rawInviteMsg);
                        pkt.setAddress(InetAddress.getByName(session.targetIp != null ? session.targetIp : pbxIp));
                        pkt.setPort(session.targetPort > 0 ? session.targetPort : pbxSipPort);
                        signalingSocket.send(pkt);
                        session.lastInviteTransmitMs = now;
                        session.inviteRetransmitIntervalMs = Math.min(session.inviteRetransmitIntervalMs * 2, 4000);
                        if (TLSDebugLogger.isEnabled()) {
                            TLSDebugLogger.logf("[CALL ENGINE] Timer A retransmitted INVITE for call %s (next interval %d ms)",
                                    session.callId, session.inviteRetransmitIntervalMs);
                        }
                    } catch (Exception ex) {
                        // ignore send failure in timer
                    }
                }
            }
        }
    }

    private void handleIncomingRequest(MyPacket pkt, String msg, String callId, String rawCallId, CallSession session) {
        String method = SIPParser.getRequestMethod(msg);
        if (method == null) return;
        method = method.toUpperCase();

        if ("INVITE".equals(method)) {
            // Check if existing session and matching local tag (Re-INVITE)
            String toTag = SIPParser.getParamValue(SIPParser.getHeader(msg, "To", "t"), "tag");
            if (session != null && session.localTag != null && session.localTag.equals(toTag)) {
                handleReInvite(session, msg);
                return;
            }

            // Inbound Call
            String caller = extractUserFromHeader(SIPParser.getHeader(msg, "From", "f"));
            String callee = extractUserFromHeader(SIPParser.getHeader(msg, "To", "t"));
            int rtpPort = allocateRtpPort();

            CallSession newSession = new CallSession(callId, CallDirection.INBOUND, caller, callee, rtpPort);
            newSession.inviteBranch = SIPParser.getParamValue(SIPParser.getHeader(msg, "Via", "v"), "branch");
            newSession.remoteTag = SIPParser.getParamValue(SIPParser.getHeader(msg, "From", "f"), "tag");
            newSession.remoteContact = SIPParser.getHeaderValue(msg, "Contact");
            newSession.transport = transportType;

            // Extract Record-Route headers
            newSession.recordRoutes.clear();
            for (String line : msg.split("\r?\n")) {
                if (line.toLowerCase().startsWith("record-route:") || line.toLowerCase().startsWith("r:")) {
                    newSession.recordRoutes.add(line.substring(line.indexOf(':') + 1).trim());
                }
            }

            // Parse Identity & Special Headers
            extractIdentityHeaders(newSession, msg);

            // Check Replaces header (RFC 3891)
            String replaces = SIPParser.getHeaderValue(msg, "Replaces");
            if (replaces != null && !replaces.trim().isEmpty()) {
                newSession.replacesDialog = replaces.trim();
                if (TLSDebugLogger.isEnabled()) {
                    TLSDebugLogger.logf("[CALL ENGINE] Inbound INVITE contains Replaces: %s", replaces);
                }
            }

            // Check Call Waiting (if another call is already active)
            for (CallSession s : activeSessions.values()) {
                if (s != newSession && (s.state == CallState.ESTABLISHED || s.state == CallState.EARLY_MEDIA || s.state == CallState.CALLING)) {
                    newSession.isCallWaiting = true;
                    break;
                }
            }

            // Parse SDP from caller
            parseSdpBody(newSession, msg);

            activeSessions.put(callId, newSession);
            if (rawCallId != null && !rawCallId.equals(callId)) {
                activeSessions.put(rawCallId, newSession);
            }
            newSession.setState(CallState.RINGING);

            // Send 100 Trying & 180 Ringing
            try {
                sendTrying(newSession);
                sendRinging(newSession);
            } catch (Exception ex) {
                System.err.println("Error sending ringing: " + ex.getMessage());
            }

        } else if ("INFO".equals(method)) {
            // SIP INFO DTMF (RFC 6086 / RFC 2976)
            String body = getMessageBody(msg);
            char digit = '?';
            if (body != null) {
                for (String line : body.split("\r?\n")) {
                    if (line.toLowerCase().startsWith("signal=")) {
                        String s = line.substring(7).trim();
                        if (!s.isEmpty()) digit = s.charAt(0);
                    }
                }
            }
            if (session != null && digit != '?') {
                session.receivedDtmf.append(digit);
                if (TLSDebugLogger.isEnabled()) {
                    TLSDebugLogger.logf("[SIP INFO DTMF] Received digit '%c' on call %s", digit, session.callId);
                }
            }
            try {
                sendOkForInfo(pkt, msg, session);
            } catch (Exception ignored) {}

        } else if ("REFER".equals(method)) {
            // SIP REFER Call Transfer (RFC 3515)
            String referTo = SIPParser.getHeaderValue(msg, "Refer-To");
            if (TLSDebugLogger.isEnabled()) {
                TLSDebugLogger.logf("[SIP REFER] Received transfer request for call %s -> Refer-To: %s", callId, referTo);
            }
            try {
                sendAcceptedForRefer(pkt, msg, session);
                if (session != null) {
                    sendNotify(session, "refer;id=1", "active;expires=60", "SIP/2.0 100 Trying\r\n");
                }
            } catch (Exception ex) {
                System.err.println("Error responding to REFER: " + ex.getMessage());
            }

        } else if ("NOTIFY".equals(method)) {
            // SIP NOTIFY (RFC 3515 Refer Event or Subscription)
            try {
                sendOkForNotify(pkt, msg, session);
            } catch (Exception ignored) {}

            String event = SIPParser.getHeaderValue(msg, "Event");
            String body = getMessageBody(msg);
            if (event != null && event.toLowerCase().contains("refer") && body != null && body.contains("200 OK")) {
                if (TLSDebugLogger.isEnabled()) {
                    TLSDebugLogger.logf("[SIP NOTIFY] Transfer target answered (200 OK); hanging up original call %s", callId);
                }
                if (session != null) {
                    try {
                        hangupCall(session.callId);
                    } catch (Exception ignored) {}
                }
            }

        } else if ("OPTIONS".equals(method)) {
            // SIP OPTIONS ping / health check from PBX
            try {
                sendOkForOptions(pkt, msg);
            } catch (Exception ignored) {}

        } else if ("CANCEL".equals(method)) {
            try {
                sendOkForCancel(pkt, msg);
            } catch (Exception ignored) {}
            if (session != null) {
                try {
                    sendRequestTerminated487(session);
                } catch (Exception ignored) {}
                stopMediaForSession(session);
                session.failureReason = "487 Request Terminated (Cancelled)";
                session.setState(CallState.FAILED);
                cleanupSessionMaps(session, callId);
                if (rawCallId != null) cleanupSessionMaps(session, rawCallId);
            }
        } else if ("BYE".equals(method)) {
            if (session != null) {
                try {
                    sendOkForBye(session, msg);
                } catch (Exception ignored) {}
                stopMediaForSession(session);
                session.setState(CallState.TERMINATED);
                cleanupSessionMaps(session, callId);
                if (rawCallId != null) cleanupSessionMaps(session, rawCallId);
            } else {
                try {
                    sendOkForByeWithoutSession(msg);
                } catch (Exception ignored) {}
            }
        } else if ("ACK".equals(method)) {
            if (session != null && session.state != CallState.ESTABLISHED) {
                session.setState(CallState.ESTABLISHED);
            }
        }
    }

    private void handleIncomingResponse(MyPacket pkt, String msg, String callId, String rawCallId, CallSession session) {
        String code = SIPParser.getResponseCode(msg);
        System.out.println("<<< [SIP RESPONSE " + code + " for Call " + (session != null ? session.callId : callId) + "]: " + SIPParser.getStartline(msg));
        if (session != null) {
            session.lastResponseCode = code;
            session.lastResponseTxt = msg;

            extractIdentityHeaders(session, msg);

            if ("100".equals(code)) {
                session.setState(CallState.PROCEEDING);
            } else if ("180".equals(code)) {
                session.setState(CallState.RINGING);
                session.remoteTag = SIPParser.getParamValue(SIPParser.getHeader(msg, "To", "t"), "tag");
            } else if ("183".equals(code)) {
                session.remoteTag = SIPParser.getParamValue(SIPParser.getHeader(msg, "To", "t"), "tag");
                if (msg.contains("v=0")) {
                    parseSdpBody(session, msg);
                    if (session.remoteRtpPort > 0 && session.remoteRtpIp != null && !"0.0.0.0".equals(session.remoteRtpIp)) {
                        session.setState(CallState.EARLY_MEDIA);
                        if (!session.isOnHold) {
                            startMediaForSession(session);
                        }
                    } else {
                        session.setState(CallState.RINGING);
                    }
                } else {
                    session.setState(CallState.RINGING);
                }
            } else if ("200".equals(code) || "202".equals(code)) {
                String cseq = SIPParser.getHeaderValue(msg, "CSeq");
                if (cseq != null && cseq.toUpperCase().contains("INVITE")) {
                    session.remoteTag = SIPParser.getParamValue(SIPParser.getHeader(msg, "To", "t"), "tag");
                    session.remoteContact = SIPParser.getHeaderValue(msg, "Contact");

                    // Extract Record-Route headers
                    session.recordRoutes.clear();
                    for (String line : msg.split("\r?\n")) {
                        if (line.toLowerCase().startsWith("record-route:") || line.toLowerCase().startsWith("r:")) {
                            session.recordRoutes.add(line.substring(line.indexOf(':') + 1).trim());
                        }
                    }

                    parseSdpBody(session, msg);

                    if (session.state != CallState.ESTABLISHED) {
                        session.setupTimeMs = System.currentTimeMillis() - session.startTimeMs;
                        session.setState(CallState.ESTABLISHED);
                        try {
                            sendAck(session);
                        } catch (Exception ignored) {}

                        if (!session.isOnHold) {
                            startMediaForSession(session);
                        }
                    } else {
                        // Duplicate 200 OK - retransmit ACK
                        try {
                            sendAck(session);
                        } catch (Exception ignored) {}
                    }
                } else if (cseq != null && cseq.toUpperCase().contains("BYE")) {
                    session.setState(CallState.TERMINATED);
                    activeSessions.remove(callId);
                } else if (cseq != null && cseq.toUpperCase().contains("CANCEL")) {
                    if (TLSDebugLogger.isEnabled()) {
                        TLSDebugLogger.logf("[CALL ENGINE] 200 OK received for CANCEL on call %s", session.callId);
                    }
                } else if (cseq != null && cseq.toUpperCase().contains("REFER")) {
                    if (TLSDebugLogger.isEnabled()) {
                        TLSDebugLogger.logf("[CALL ENGINE] %s Accepted received for REFER on call %s", code, session.callId);
                    }
                } else if (cseq != null && cseq.toUpperCase().contains("INFO")) {
                    if (TLSDebugLogger.isEnabled()) {
                        TLSDebugLogger.logf("[CALL ENGINE] 200 OK received for INFO on call %s", session.callId);
                    }
                }
            } else if (code.startsWith("4") || code.startsWith("5") || code.startsWith("6")) {
                System.out.println("<<< [SIP FAILURE " + code + "]:\n" + msg);
                String startLine = SIPParser.getStartline(msg);
                String cseq = SIPParser.getHeaderValue(msg, "CSeq");

                // Mid-dialog request failure per RFC 3261 Section 14.1 & RFC 6086 Section 4.2.2
                // A failure response to a mid-dialog request (e.g. INFO, REFER, Re-INVITE) DOES NOT terminate the established call.
                if (session.state == CallState.ESTABLISHED) {
                    if (cseq != null && cseq.toUpperCase().contains("INFO")) {
                        System.out.println(">>> [CALL ENGINE] Mid-dialog INFO rejected with " + code + "; dialog remains ESTABLISHED.");
                    } else if (cseq != null && cseq.toUpperCase().contains("INVITE")) {
                        System.out.println(">>> [CALL ENGINE] Mid-dialog Re-INVITE rejected with " + code + "; acknowledging and preserving ESTABLISHED dialog.");
                        sendNon2xxAck(session, msg);
                    } else if (cseq != null && cseq.toUpperCase().contains("REFER")) {
                        System.out.println(">>> [CALL ENGINE] Mid-dialog REFER rejected with " + code + "; dialog remains ESTABLISHED.");
                    }
                    return;
                }

                session.failureReason = (startLine != null && !startLine.isEmpty()) ? startLine : ("SIP " + code);
                session.setState(CallState.FAILED);
                if (cseq != null && (cseq.toUpperCase().contains("INVITE") || cseq.toUpperCase().contains("CANCEL"))) {
                    sendNon2xxAck(session, msg);
                }
                stopMediaForSession(session);
                cleanupSessionMaps(session, callId);
                if (rawCallId != null) cleanupSessionMaps(session, rawCallId);
            }
        }
    }

    private void sendNon2xxAck(CallSession session, String failureMsg) {
        try {
            String branch = SIPParser.getParamValue(SIPParser.getHeader(failureMsg, "Via", "v"), "branch");
            StringBuilder ack = new StringBuilder();
            ack.append("ACK sip:").append(session.callee).append("@").append(pbxIp).append(" SIP/2.0\r\n");
            ack.append("Via: SIP/2.0/").append(session.transport != null ? session.transport : transportType).append(" ").append(localIp).append(":").append(localSipPort).append(";rport;branch=").append(branch != null ? branch : "z9hG4bKack").append("\r\n");
            ack.append("Max-Forwards: 70\r\n");
            ack.append("From: <sip:").append(session.caller).append("@").append(pbxIp).append(">;tag=").append(session.localTag).append("\r\n");
            ack.append("To: <sip:").append(session.callee).append("@").append(pbxIp).append(">");
            String toTag = SIPParser.getParamValue(SIPParser.getHeader(failureMsg, "To", "t"), "tag");
            if (toTag != null && !toTag.isEmpty()) {
                ack.append(";tag=").append(toTag);
            }
            ack.append("\r\n");
            ack.append("Call-ID: ").append(session.callId).append("\r\n");
            ack.append("CSeq: ").append(session.cseq).append(" ACK\r\n");
            ack.append("Content-Length: 0\r\n\r\n");

            MyPacket pkt = new MyPacket(ack.toString());
            pkt.setAddress(InetAddress.getByName(pbxIp));
            pkt.setPort(pbxSipPort);
            signalingSocket.send(pkt);
        } catch (Exception ignore) {}
    }

    private void handleReInvite(CallSession session, String msg) {
        parseSdpBody(session, msg);
        boolean remoteHold = msg.contains("a=sendonly") || msg.contains("c=IN IP4 0.0.0.0") || msg.contains("a=inactive");
        session.isRemoteHold = remoteHold;

        if (session.mediaController != null) {
            if (remoteHold || session.isOnHold) {
                session.mediaController.pauseAudio();
            } else {
                session.mediaController.resumeAudio();
            }
        }

        try {
            String sdp = remoteHold ? buildSdpRecvOnly(localIp, session.localRtpPort, session.payloadType)
                    : buildSdpOffer(localIp, session.localRtpPort, session.payloadType);

            StringBuilder ok = new StringBuilder();
            ok.append("SIP/2.0 200 OK\r\n");
            String viaHdr = SIPParser.getHeader(msg, "Via", "v");
            String branch = SIPParser.getParamValue(viaHdr, "branch");
            ok.append("Via: SIP/2.0/").append(transportType).append(" ").append(pbxIp).append(":").append(pbxSipPort).append(";branch=").append(branch != null ? branch : "z9hG4bK").append("\r\n");
            ok.append("From: <sip:").append(session.caller).append("@").append(pbxIp).append(">;tag=").append(session.remoteTag).append("\r\n");
            ok.append("To: <sip:").append(session.callee).append("@").append(pbxIp).append(">;tag=").append(session.localTag).append("\r\n");
            ok.append("Call-ID: ").append(session.callId).append("\r\n");
            ok.append("CSeq: ").append(SIPParser.getHeaderValue(msg, "CSeq")).append("\r\n");
            ok.append("Contact: <sip:").append(session.callee).append("@").append(localIp).append(":").append(localSipPort).append(";transport=").append(transportType.toLowerCase()).append(">\r\n");
            ok.append("Content-Type: application/sdp\r\n");
            ok.append("Content-Length: ").append(sdp.getBytes().length).append("\r\n\r\n");
            ok.append(sdp);

            MyPacket pkt = new MyPacket(ok.toString());
            pkt.setAddress(InetAddress.getByName(pbxIp));
            pkt.setPort(pbxSipPort);
            signalingSocket.send(pkt);
        } catch (Exception ex) {
            System.err.println("Error replying to Re-INVITE: " + ex.getMessage());
        }
    }

    public static String getMessageBody(String msg) {
        if (msg == null) return null;
        int idx = msg.indexOf("\r\n\r\n");
        if (idx != -1) return msg.substring(idx + 4);
        idx = msg.indexOf("\n\n");
        if (idx != -1) return msg.substring(idx + 2);
        return null;
    }

    public static void extractIdentityHeaders(CallSession session, String msg) {
        if (session == null || msg == null) return;
        String pai = SIPParser.getHeaderValue(msg, "P-Asserted-Identity");
        if (pai != null && !pai.isEmpty()) session.pAssertedIdentity = pai.trim();

        String rpid = SIPParser.getHeaderValue(msg, "Remote-Party-ID");
        if (rpid != null && !rpid.isEmpty()) session.remotePartyId = rpid.trim();

        String div = SIPParser.getHeaderValue(msg, "Diversion");
        if (div != null && !div.isEmpty()) session.diversion = div.trim();

        String priv = SIPParser.getHeaderValue(msg, "Privacy");
        if (priv != null && !priv.isEmpty()) session.privacy = priv.trim();

        String fromHdr = SIPParser.getHeader(msg, "From", "f");
        if (fromHdr != null) {
            int q1 = fromHdr.indexOf('"');
            int q2 = fromHdr.lastIndexOf('"');
            if (q1 >= 0 && q2 > q1) {
                session.remoteDisplayName = fromHdr.substring(q1 + 1, q2).trim();
            }
        }
    }

    private void sendOkForInfo(MyPacket inPkt, String infoMsg, CallSession session) throws Exception {
        StringBuilder ok = new StringBuilder();
        ok.append("SIP/2.0 200 OK\r\n");
        ok.append("Via: ").append(SIPParser.getHeader(infoMsg, "Via", "v")).append("\r\n");
        ok.append("From: ").append(SIPParser.getHeader(infoMsg, "From", "f")).append("\r\n");
        ok.append("To: ").append(SIPParser.getHeader(infoMsg, "To", "t"));
        if (!SIPParser.getHeader(infoMsg, "To", "t").contains("tag=") && session != null) {
            ok.append(";tag=").append(session.localTag);
        }
        ok.append("\r\n");
        ok.append("Call-ID: ").append(SIPParser.getHeaderValue(infoMsg, "Call-ID", "i")).append("\r\n");
        ok.append("CSeq: ").append(SIPParser.getHeaderValue(infoMsg, "CSeq")).append("\r\n");
        ok.append("User-Agent: SIP Simulator v4\r\n");
        ok.append("Content-Length: 0\r\n\r\n");

        if (signalingSocket != null) {
            MyPacket pkt = new MyPacket(ok.toString());
            pkt.setAddress(inPkt != null && inPkt.getAddress() != null ? inPkt.getAddress() : InetAddress.getByName(pbxIp));
            pkt.setPort(inPkt != null && inPkt.getPort() > 0 ? inPkt.getPort() : pbxSipPort);
            signalingSocket.send(pkt);
        }
    }

    private void sendAcceptedForRefer(MyPacket inPkt, String referMsg, CallSession session) throws Exception {
        StringBuilder acc = new StringBuilder();
        acc.append("SIP/2.0 202 Accepted\r\n");
        acc.append("Via: ").append(SIPParser.getHeader(referMsg, "Via", "v")).append("\r\n");
        acc.append("From: ").append(SIPParser.getHeader(referMsg, "From", "f")).append("\r\n");
        acc.append("To: ").append(SIPParser.getHeader(referMsg, "To", "t"));
        if (!SIPParser.getHeader(referMsg, "To", "t").contains("tag=") && session != null) {
            acc.append(";tag=").append(session.localTag);
        }
        acc.append("\r\n");
        acc.append("Call-ID: ").append(SIPParser.getHeaderValue(referMsg, "Call-ID", "i")).append("\r\n");
        acc.append("CSeq: ").append(SIPParser.getHeaderValue(referMsg, "CSeq")).append("\r\n");
        acc.append("Contact: <sip:").append(registeredExtension != null ? registeredExtension : "90008").append("@").append(localIp).append(":").append(localSipPort).append(";transport=").append(transportType.toLowerCase()).append(">\r\n");
        acc.append("User-Agent: SIP Simulator v4\r\n");
        acc.append("Content-Length: 0\r\n\r\n");

        if (signalingSocket != null) {
            MyPacket pkt = new MyPacket(acc.toString());
            pkt.setAddress(inPkt != null && inPkt.getAddress() != null ? inPkt.getAddress() : InetAddress.getByName(pbxIp));
            pkt.setPort(inPkt != null && inPkt.getPort() > 0 ? inPkt.getPort() : pbxSipPort);
            signalingSocket.send(pkt);
        }
    }

    private void sendNotify(CallSession session, String event, String subState, String body) throws Exception {
        if (session == null || signalingSocket == null) return;
        session.cseq++;
        String targetUri = session.remoteContact != null ? session.remoteContact : ("sip:" + (session.direction == CallDirection.OUTBOUND ? session.callee : session.caller) + "@" + pbxIp);
        if (targetUri.startsWith("<") && targetUri.contains(">")) {
            targetUri = targetUri.substring(1, targetUri.indexOf('>'));
        }

        StringBuilder notif = new StringBuilder();
        notif.append("NOTIFY ").append(targetUri).append(" SIP/2.0\r\n");
        notif.append("Via: SIP/2.0/").append(transportType).append(" ").append(localIp).append(":").append(localSipPort).append(";rport;branch=z9hG4bKnot").append(System.currentTimeMillis()).append("\r\n");
        notif.append("Max-Forwards: 70\r\n");
        if (session.direction == CallDirection.OUTBOUND) {
            notif.append("From: <sip:").append(session.caller).append("@").append(pbxIp).append(">;tag=").append(session.localTag).append("\r\n");
            notif.append("To: <sip:").append(session.callee).append("@").append(pbxIp).append(">;tag=").append(session.remoteTag).append("\r\n");
        } else {
            notif.append("From: <sip:").append(session.callee).append("@").append(pbxIp).append(">;tag=").append(session.localTag).append("\r\n");
            notif.append("To: <sip:").append(session.caller).append("@").append(pbxIp).append(">;tag=").append(session.remoteTag).append("\r\n");
        }
        notif.append("Call-ID: ").append(session.callId).append("\r\n");
        notif.append("CSeq: ").append(session.cseq).append(" NOTIFY\r\n");
        notif.append("Event: ").append(event).append("\r\n");
        notif.append("Subscription-State: ").append(subState).append("\r\n");
        notif.append("Content-Type: message/sipfrag\r\n");
        notif.append("Content-Length: ").append(body.getBytes().length).append("\r\n\r\n");
        notif.append(body);

        MyPacket pkt = new MyPacket(notif.toString());
        pkt.setAddress(InetAddress.getByName(session.targetIp != null ? session.targetIp : pbxIp));
        pkt.setPort(session.targetPort > 0 ? session.targetPort : pbxSipPort);
        signalingSocket.send(pkt);
    }

    private void sendOkForNotify(MyPacket inPkt, String notifyMsg, CallSession session) throws Exception {
        StringBuilder ok = new StringBuilder();
        ok.append("SIP/2.0 200 OK\r\n");
        ok.append("Via: ").append(SIPParser.getHeader(notifyMsg, "Via", "v")).append("\r\n");
        ok.append("From: ").append(SIPParser.getHeader(notifyMsg, "From", "f")).append("\r\n");
        ok.append("To: ").append(SIPParser.getHeader(notifyMsg, "To", "t"));
        if (!SIPParser.getHeader(notifyMsg, "To", "t").contains("tag=") && session != null) {
            ok.append(";tag=").append(session.localTag);
        }
        ok.append("\r\n");
        ok.append("Call-ID: ").append(SIPParser.getHeaderValue(notifyMsg, "Call-ID", "i")).append("\r\n");
        ok.append("CSeq: ").append(SIPParser.getHeaderValue(notifyMsg, "CSeq")).append("\r\n");
        ok.append("User-Agent: SIP Simulator v4\r\n");
        ok.append("Content-Length: 0\r\n\r\n");

        if (signalingSocket != null) {
            MyPacket pkt = new MyPacket(ok.toString());
            pkt.setAddress(inPkt != null && inPkt.getAddress() != null ? inPkt.getAddress() : InetAddress.getByName(pbxIp));
            pkt.setPort(inPkt != null && inPkt.getPort() > 0 ? inPkt.getPort() : pbxSipPort);
            signalingSocket.send(pkt);
        }
    }

    private void sendOkForOptions(MyPacket inPkt, String optionsMsg) throws Exception {
        StringBuilder ok = new StringBuilder();
        ok.append("SIP/2.0 200 OK\r\n");
        ok.append("Via: ").append(SIPParser.getHeader(optionsMsg, "Via", "v")).append("\r\n");
        ok.append("From: ").append(SIPParser.getHeader(optionsMsg, "From", "f")).append("\r\n");
        ok.append("To: ").append(SIPParser.getHeader(optionsMsg, "To", "t")).append(";tag=tagopt").append(System.currentTimeMillis() % 10000).append("\r\n");
        ok.append("Call-ID: ").append(SIPParser.getHeaderValue(optionsMsg, "Call-ID", "i")).append("\r\n");
        ok.append("CSeq: ").append(SIPParser.getHeaderValue(optionsMsg, "CSeq")).append("\r\n");
        ok.append("Allow: PRACK, INVITE, ACK, BYE, CANCEL, UPDATE, INFO, SUBSCRIBE, NOTIFY, REFER, MESSAGE, OPTIONS\r\n");
        ok.append("Supported: replaces, timer, 100rel\r\n");
        ok.append("User-Agent: SIP Simulator v4\r\n");
        ok.append("Content-Length: 0\r\n\r\n");

        if (signalingSocket != null) {
            MyPacket pkt = new MyPacket(ok.toString());
            pkt.setAddress(inPkt != null && inPkt.getAddress() != null ? inPkt.getAddress() : InetAddress.getByName(pbxIp));
            pkt.setPort(inPkt != null && inPkt.getPort() > 0 ? inPkt.getPort() : pbxSipPort);
            signalingSocket.send(pkt);
        }
    }

    private String buildSdpRecvOnly(String ip, int rtpPort, int pt) {
        StringBuilder sb = new StringBuilder();
        sb.append("v=0\r\n");
        sb.append("o=SIPSimulator 90008 90008 IN IP4 ").append(ip).append("\r\n");
        sb.append("s=SIP Call\r\n");
        sb.append("c=IN IP4 ").append(ip).append("\r\n");
        sb.append("t=0 0\r\n");

        if (pt == 9) {
            sb.append("m=audio ").append(rtpPort).append(" RTP/AVP 9 8 0 101\r\n");
            sb.append("a=rtpmap:9 G722/8000\r\n");
            sb.append("a=rtpmap:8 PCMA/8000\r\n");
        } else if (pt == 0) {
            sb.append("m=audio ").append(rtpPort).append(" RTP/AVP 0 8 9 101\r\n");
            sb.append("a=rtpmap:0 PCMU/8000\r\n");
            sb.append("a=rtpmap:8 PCMA/8000\r\n");
        } else {
            sb.append("m=audio ").append(rtpPort).append(" RTP/AVP 8 0 9 101\r\n");
            sb.append("a=rtpmap:8 PCMA/8000\r\n");
        }

        sb.append("a=rtpmap:101 telephone-event/8000\r\n");
        sb.append("a=fmtp:101 0-15\r\n");
        sb.append("a=recvonly\r\n");
        sb.append("a=ptime:20\r\n");
        return sb.toString();
    }

    private void sendTrying(CallSession session) throws Exception {
        String trying =
                "SIP/2.0 100 Trying\r\n" +
                "Via: SIP/2.0/UDP " + pbxIp + ":" + pbxSipPort + ";branch=" + session.inviteBranch + "\r\n" +
                "From: <sip:" + session.caller + "@" + pbxIp + ">;tag=" + session.remoteTag + "\r\n" +
                "To: <sip:" + session.callee + "@" + pbxIp + ">\r\n" +
                "Call-ID: " + session.callId + "\r\n" +
                "CSeq: " + session.cseq + " INVITE\r\n" +
                "Content-Length: 0\r\n\r\n";

        MyPacket pkt = new MyPacket(trying);
        pkt.setAddress(InetAddress.getByName(pbxIp));
        pkt.setPort(pbxSipPort);
        signalingSocket.send(pkt);
    }

    private void sendRinging(CallSession session) throws Exception {
        String ringing =
                "SIP/2.0 180 Ringing\r\n" +
                "Via: SIP/2.0/UDP " + pbxIp + ":" + pbxSipPort + ";branch=" + session.inviteBranch + "\r\n" +
                "From: <sip:" + session.caller + "@" + pbxIp + ">;tag=" + session.remoteTag + "\r\n" +
                "To: <sip:" + session.callee + "@" + pbxIp + ">;tag=" + session.localTag + "\r\n" +
                "Call-ID: " + session.callId + "\r\n" +
                "CSeq: " + session.cseq + " INVITE\r\n" +
                "Contact: <sip:" + session.callee + "@" + localIp + ":" + localSipPort + ";transport=udp>\r\n" +
                "Content-Length: 0\r\n\r\n";

        MyPacket pkt = new MyPacket(ringing);
        pkt.setAddress(InetAddress.getByName(pbxIp));
        pkt.setPort(pbxSipPort);
        signalingSocket.send(pkt);
    }

    private void sendOkForBye(CallSession session, String byeMsg) throws Exception {
        String cseq = SIPParser.getHeaderValue(byeMsg, "CSeq");
        String viaHdr = SIPParser.getHeader(byeMsg, "Via", "v");
        String branch = SIPParser.getParamValue(viaHdr, "branch");
        String ok =
                "SIP/2.0 200 OK\r\n" +
                "Via: SIP/2.0/UDP " + pbxIp + ":" + pbxSipPort + ";branch=" + (branch != null ? branch : "z9hG4bK") + "\r\n" +
                "From: " + SIPParser.getHeaderValue(byeMsg, "From") + "\r\n" +
                "To: " + SIPParser.getHeaderValue(byeMsg, "To") + "\r\n" +
                "Call-ID: " + session.callId + "\r\n" +
                "CSeq: " + (cseq != null ? cseq : (session.cseq + " BYE")) + "\r\n" +
                "Content-Length: 0\r\n\r\n";

        MyPacket pkt = new MyPacket(ok);
        pkt.setAddress(InetAddress.getByName(pbxIp));
        pkt.setPort(pbxSipPort);
        signalingSocket.send(pkt);
    }

    private void sendOkForByeWithoutSession(String byeMsg) throws Exception {
        String cseq = SIPParser.getHeaderValue(byeMsg, "CSeq");
        String viaHdr = SIPParser.getHeader(byeMsg, "Via", "v");
        String branch = SIPParser.getParamValue(viaHdr, "branch");
        String callId = SIPParser.getHeaderValue(byeMsg, "Call-ID", "i");
        String ok =
                "SIP/2.0 200 OK\r\n" +
                "Via: SIP/2.0/UDP " + pbxIp + ":" + pbxSipPort + ";branch=" + (branch != null ? branch : "z9hG4bK") + "\r\n" +
                "From: " + SIPParser.getHeaderValue(byeMsg, "From") + "\r\n" +
                "To: " + SIPParser.getHeaderValue(byeMsg, "To") + "\r\n" +
                "Call-ID: " + (callId != null ? callId : "") + "\r\n" +
                "CSeq: " + (cseq != null ? cseq : "1 BYE") + "\r\n" +
                "Content-Length: 0\r\n\r\n";

        MyPacket pkt = new MyPacket(ok);
        pkt.setAddress(InetAddress.getByName(pbxIp));
        pkt.setPort(pbxSipPort);
        signalingSocket.send(pkt);
    }

    private void sendOkForCancel(MyPacket inPkt, String cancelMsg) throws Exception {
        String cseq = SIPParser.getHeaderValue(cancelMsg, "CSeq");
        String viaHdr = SIPParser.getHeader(cancelMsg, "Via", "v");
        String branch = SIPParser.getParamValue(viaHdr, "branch");
        String callId = SIPParser.getHeaderValue(cancelMsg, "Call-ID", "i");
        String ok =
                "SIP/2.0 200 OK\r\n" +
                "Via: SIP/2.0/UDP " + pbxIp + ":" + pbxSipPort + ";branch=" + (branch != null ? branch : "z9hG4bK") + "\r\n" +
                "From: " + SIPParser.getHeaderValue(cancelMsg, "From") + "\r\n" +
                "To: " + SIPParser.getHeaderValue(cancelMsg, "To") + "\r\n" +
                "Call-ID: " + (callId != null ? callId : "") + "\r\n" +
                "CSeq: " + (cseq != null ? cseq : "1 CANCEL") + "\r\n" +
                "Content-Length: 0\r\n\r\n";

        MyPacket pkt = new MyPacket(ok);
        pkt.setAddress(InetAddress.getByName(pbxIp));
        pkt.setPort(pbxSipPort);
        signalingSocket.send(pkt);
    }

    private void sendRequestTerminated487(CallSession session) throws Exception {
        String term487 =
                "SIP/2.0 487 Request Terminated\r\n" +
                "Via: SIP/2.0/UDP " + pbxIp + ":" + pbxSipPort + ";branch=" + session.inviteBranch + "\r\n" +
                "From: <sip:" + session.caller + "@" + pbxIp + ">;tag=" + session.remoteTag + "\r\n" +
                "To: <sip:" + session.callee + "@" + pbxIp + ">;tag=" + session.localTag + "\r\n" +
                "Call-ID: " + session.callId + "\r\n" +
                "CSeq: " + session.cseq + " INVITE\r\n" +
                "Content-Length: 0\r\n\r\n";

        MyPacket pkt = new MyPacket(term487);
        pkt.setAddress(InetAddress.getByName(pbxIp));
        pkt.setPort(pbxSipPort);
        signalingSocket.send(pkt);
    }

    public void parseSdpBody(CallSession session, String msg) {
        if (session == null || msg == null) return;
        for (String rawLine : msg.split("\r?\n")) {
            String line = rawLine.trim();
            if (line.startsWith("c=IN IP4 ")) {
                session.remoteRtpIp = line.substring(9).trim();
                if ("0.0.0.0".equals(session.remoteRtpIp)) {
                    session.isRemoteHold = true;
                }
            } else if (line.startsWith("m=audio ")) {
                String[] parts = line.split("\\s+");
                if (parts.length > 1) {
                    try {
                        session.remoteRtpPort = Integer.parseInt(parts[1].trim());
                    } catch (NumberFormatException ignored) {}
                }
                if (parts.length > 3) {
                    try {
                        int pt = Integer.parseInt(parts[3].trim());
                        if (LiveAudioCodec.isSupported(pt)) {
                            session.payloadType = pt;
                            session.codecName = LiveAudioCodec.create(pt).rtpmapName();
                        }
                    } catch (NumberFormatException ignored) {}
                }
            } else if (line.toLowerCase().startsWith("a=rtpmap:")) {
                // e.g. a=rtpmap:101 telephone-event/8000 or a=rtpmap:120 telephone-event/8000
                String rtpmap = line.substring(9).trim();
                int spaceIdx = rtpmap.indexOf(' ');
                if (spaceIdx > 0) {
                    String ptStr = rtpmap.substring(0, spaceIdx).trim();
                    String encoding = rtpmap.substring(spaceIdx + 1).trim();
                    if (encoding.toLowerCase().startsWith("telephone-event/")) {
                        try {
                            session.dtmfPayloadType = Integer.parseInt(ptStr);
                        } catch (NumberFormatException ignored) {}
                    }
                }
            } else if (line.equalsIgnoreCase("a=sendonly") || line.equalsIgnoreCase("a=inactive")) {
                session.isRemoteHold = true;
            } else if (line.equalsIgnoreCase("a=sendrecv") || line.equalsIgnoreCase("a=recvonly")) {
                session.isRemoteHold = false;
            }
        }
    }

    public int allocateRtpPort() {
        int range = (rtpPortRangeEnd > rtpPortRangeStart) ? (rtpPortRangeEnd - rtpPortRangeStart) : 8000;
        int maxAttempts = Math.min(500, range / 2);
        for (int i = 0; i < maxAttempts; i++) {
            int p = nextRtpPort.getAndAdd(2);
            if (p > rtpPortRangeEnd || p < rtpPortRangeStart) {
                nextRtpPort.set(rtpPortRangeStart);
                p = rtpPortRangeStart;
            }
            try {
                java.net.DatagramSocket s = new java.net.DatagramSocket(p);
                s.close();
                return p;
            } catch (Exception ignored) {
                // Port occupied, advance to next
            }
        }
        return nextRtpPort.getAndAdd(2);
    }

    private String buildSdpOffer(String ip, int rtpPort, int pt) {
        StringBuilder sb = new StringBuilder();
        sb.append("v=0\r\n");
        sb.append("o=SIPSimulator 90008 90008 IN IP4 ").append(ip).append("\r\n");
        sb.append("s=SIP Call\r\n");
        sb.append("c=IN IP4 ").append(ip).append("\r\n");
        sb.append("t=0 0\r\n");

        if (pt == 9) {
            sb.append("m=audio ").append(rtpPort).append(" RTP/AVP 9 8 0 101\r\n");
            sb.append("a=rtpmap:9 G722/8000\r\n");
            sb.append("a=rtpmap:8 PCMA/8000\r\n");
            sb.append("a=rtpmap:0 PCMU/8000\r\n");
        } else if (pt == 0) {
            sb.append("m=audio ").append(rtpPort).append(" RTP/AVP 0 8 9 101\r\n");
            sb.append("a=rtpmap:0 PCMU/8000\r\n");
            sb.append("a=rtpmap:8 PCMA/8000\r\n");
            sb.append("a=rtpmap:9 G722/8000\r\n");
        } else {
            sb.append("m=audio ").append(rtpPort).append(" RTP/AVP 8 0 9 101\r\n");
            sb.append("a=rtpmap:8 PCMA/8000\r\n");
            sb.append("a=rtpmap:0 PCMU/8000\r\n");
            sb.append("a=rtpmap:9 G722/8000\r\n");
        }

        sb.append("a=rtpmap:101 telephone-event/8000\r\n");
        sb.append("a=fmtp:101 0-15\r\n");
        sb.append("a=sendrecv\r\n");
        sb.append("a=ptime:20\r\n");
        return sb.toString();
    }

    private String buildSdpHold(String ip, int rtpPort, int pt) {
        StringBuilder sb = new StringBuilder();
        sb.append("v=0\r\n");
        sb.append("o=SIPSimulator 90008 90008 IN IP4 ").append(ip).append("\r\n");
        sb.append("s=SIP Call\r\n");
        sb.append("c=IN IP4 ").append(ip).append("\r\n");
        sb.append("t=0 0\r\n");

        if (pt == 9) {
            sb.append("m=audio ").append(rtpPort).append(" RTP/AVP 9 8 0 101\r\n");
            sb.append("a=rtpmap:9 G722/8000\r\n");
        } else if (pt == 0) {
            sb.append("m=audio ").append(rtpPort).append(" RTP/AVP 0 8 9 101\r\n");
            sb.append("a=rtpmap:0 PCMU/8000\r\n");
        } else {
            sb.append("m=audio ").append(rtpPort).append(" RTP/AVP 8 0 9 101\r\n");
            sb.append("a=rtpmap:8 PCMA/8000\r\n");
        }

        sb.append("a=rtpmap:101 telephone-event/8000\r\n");
        sb.append("a=fmtp:101 0-15\r\n");
        sb.append("a=sendonly\r\n");
        sb.append("a=ptime:20\r\n");
        return sb.toString();
    }

    private static String extractUserFromHeader(String header) {
        if (header == null) return "unknown";
        int sipIdx = header.indexOf("sip:");
        if (sipIdx != -1) {
            int atIdx = header.indexOf('@', sipIdx);
            if (atIdx != -1) {
                return header.substring(sipIdx + 4, atIdx).trim();
            }
        }
        return "unknown";
    }
}
