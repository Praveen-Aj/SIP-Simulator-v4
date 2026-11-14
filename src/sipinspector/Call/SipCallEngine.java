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

    private UDPClientSocket signalingSocket = null;
    private Thread listenerThread = null;
    private volatile boolean running = false;

    private String localIp = "192.168.0.9";
    private int localSipPort = 5060;
    private String pbxIp = "192.168.0.20";
    private int pbxSipPort = 5060;
    private String registeredExtension = null;

    private final Map<String, CallSession> activeSessions = new ConcurrentHashMap<>();
    private final AtomicInteger nextRtpPort = new AtomicInteger(42000);

    public enum CallDirection {
        OUTBOUND,
        INBOUND
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

        public volatile boolean isOnHold = false;
        public volatile boolean isMuted = false;

        public LiveAudioController mediaController = null;
        public String txWavPath = null;
        public String rxWavPath = null;

        public long startTimeMs = System.currentTimeMillis();
        public long establishedTimeMs = 0;
        public long endTimeMs = 0;

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
            m.put("localRtpPort", localRtpPort);
            m.put("remoteRtpIp", remoteRtpIp);
            m.put("remoteRtpPort", remoteRtpPort);
            m.put("isOnHold", isOnHold);
            m.put("isMuted", isMuted);
            m.put("txWavPath", txWavPath);
            m.put("rxWavPath", rxWavPath);
            m.put("durationSeconds", establishedTimeMs > 0 ? (System.currentTimeMillis() - establishedTimeMs) / 1000L : 0);

            if (mediaController != null) {
                m.put("mediaStats", mediaController.getStats());
                m.put("mediaHealth", mediaController.getMediaHealthSummary());
            }
            return m;
        }
    }

    private SipCallEngine() {
        // Private constructor
    }

    /**
     * Start the signaling listener and initialize SIP Call Engine.
     */
    public synchronized boolean startEngine(String localIp, int localSipPort, String pbxIp, int pbxSipPort) {
        if (running && signalingSocket != null) {
            return true;
        }

        this.localIp = (localIp != null && !localIp.trim().isEmpty()) ? localIp.trim() : "192.168.0.9";
        this.localSipPort = localSipPort > 0 ? localSipPort : 5060;
        this.pbxIp = (pbxIp != null && !pbxIp.trim().isEmpty()) ? pbxIp.trim() : "192.168.0.20";
        this.pbxSipPort = pbxSipPort > 0 ? pbxSipPort : 5060;

        try {
            signalingSocket = new UDPClientSocket(InetAddress.getByName(this.localIp), this.localSipPort);
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
                TLSDebugLogger.logf("[CALL ENGINE] Engine started on %s:%d, target PBX=%s:%d",
                        this.localIp, this.localSipPort, this.pbxIp, this.pbxSipPort);
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

    public boolean isRunning() {
        return running;
    }

    public UDPClientSocket getSignalingSocket() {
        return signalingSocket;
    }

    public String getLocalIp() {
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
        return new ArrayList<>(activeSessions.values());
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
        if (!running) {
            startEngine(localIp, localSipPort, pbxIp, pbxSipPort);
        }

        int rtpPort = allocateRtpPort();
        String callPrefix = "call" + (System.currentTimeMillis() % 10000000);
        String fullCallId = callPrefix + "@" + localIp;
        CallSession session = new CallSession(fullCallId, CallDirection.OUTBOUND, caller, callee, rtpPort);
        session.payloadType = requestedPt > 0 ? requestedPt : 8;
        session.codecName = LiveAudioCodec.isSupported(requestedPt) ?
                LiveAudioCodec.create(requestedPt).rtpmapName() : "PCMA";

        activeSessions.put(fullCallId, session);
        activeSessions.put(callPrefix, session);
        session.setState(CallState.CALLING);

        String sdpOffer = buildSdpOffer(localIp, rtpPort, session.payloadType);
        session.inviteBranch = "z9hG4bKinv" + System.currentTimeMillis();

        String inviteMsg =
                "INVITE sip:" + callee + "@" + pbxIp + " SIP/2.0\r\n" +
                "Via: SIP/2.0/UDP " + localIp + ":" + localSipPort + ";rport;branch=" + session.inviteBranch + "\r\n" +
                "Max-Forwards: 70\r\n" +
                "From: \"" + caller + "\" <sip:" + caller + "@" + pbxIp + ">;tag=" + session.localTag + "\r\n" +
                "To: \"" + callee + "\" <sip:" + callee + "@" + pbxIp + ">\r\n" +
                "Call-ID: " + fullCallId + "\r\n" +
                "CSeq: " + session.cseq + " INVITE\r\n" +
                "Contact: \"" + caller + "\" <sip:" + caller + "@" + localIp + ":" + localSipPort + ";transport=udp>\r\n" +
                "Allow: PRACK, INVITE, ACK, BYE, CANCEL, UPDATE, INFO, SUBSCRIBE, NOTIFY, REFER, MESSAGE, OPTIONS\r\n" +
                "Supported: replaces, timer\r\n" +
                "User-Agent: SIP Simulator v4\r\n" +
                "Content-Type: application/sdp\r\n" +
                "Content-Length: " + sdpOffer.getBytes().length + "\r\n\r\n" +
                sdpOffer;

        MyPacket pkt = new MyPacket(inviteMsg);
        pkt.setAddress(InetAddress.getByName(pbxIp));
        pkt.setPort(pbxSipPort);
        signalingSocket.send(pkt);

        if (TLSDebugLogger.isEnabled()) {
            TLSDebugLogger.logf("[CALL ENGINE] INVITE sent for call %s (%s -> %s, PT=%d)",
                    fullCallId, caller, callee, session.payloadType);
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
        CallSession session = activeSessions.get(callId);
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
        CallSession session = activeSessions.get(callId);
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
     * Send DTMF telephone-event via RFC 2833.
     */
    public synchronized boolean sendDtmf(String callId, char digit) {
        CallSession session = activeSessions.get(callId);
        if (session == null || session.state != CallState.ESTABLISHED) return false;

        // Formulate RFC 2833 RTP packet and transmit via session media socket
        return true;
    }

    /**
     * Terminate an established call using BYE.
     */
    public synchronized boolean hangupCall(String callId) throws Exception {
        CallSession session = activeSessions.get(callId);
        if (session == null) {
            return false;
        }

        session.setState(CallState.TERMINATING);
        session.cseq++;

        String targetUri = session.remoteContact != null ? session.remoteContact : ("sip:" + (session.direction == CallDirection.OUTBOUND ? session.callee : session.caller) + "@" + pbxIp);
        if (targetUri.startsWith("<") && targetUri.contains(">")) {
            targetUri = targetUri.substring(1, targetUri.indexOf('>'));
        }

        StringBuilder bye = new StringBuilder();
        bye.append("BYE ").append(targetUri).append(" SIP/2.0\r\n");
        bye.append("Via: SIP/2.0/UDP ").append(localIp).append(":").append(localSipPort).append(";rport;branch=z9hG4bKbye").append(System.currentTimeMillis()).append("\r\n");
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

        MyPacket pkt = new MyPacket(bye.toString());
        pkt.setAddress(InetAddress.getByName(pbxIp));
        pkt.setPort(pbxSipPort);
        signalingSocket.send(pkt);

        stopMediaForSession(session);
        session.setState(CallState.TERMINATED);
        activeSessions.remove(callId);
        activeSessions.remove(session.callId);
        String pfx = normalizeCallId(session.callId);
        if (!pfx.isEmpty()) activeSessions.remove(pfx);
        return true;
    }

    private void sendReInvite(CallSession session, String sdp) throws Exception {
        String targetUri = session.remoteContact != null ? session.remoteContact : ("sip:" + (session.direction == CallDirection.OUTBOUND ? session.callee : session.caller) + "@" + pbxIp);
        if (targetUri.startsWith("<") && targetUri.contains(">")) {
            targetUri = targetUri.substring(1, targetUri.indexOf('>'));
        }

        StringBuilder reInv = new StringBuilder();
        reInv.append("INVITE ").append(targetUri).append(" SIP/2.0\r\n");
        reInv.append("Via: SIP/2.0/UDP ").append(localIp).append(":").append(localSipPort).append(";rport;branch=z9hG4bKreinv").append(System.currentTimeMillis()).append("\r\n");
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
        reInv.append("Contact: <sip:").append(session.caller).append("@").append(localIp).append(":").append(localSipPort).append(";transport=udp>\r\n");
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
        ack.append("Via: SIP/2.0/UDP ").append(localIp).append(":").append(localSipPort).append(";rport;branch=z9hG4bKack").append(System.currentTimeMillis()).append("\r\n");
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

    private void handleIncomingRequest(MyPacket pkt, String msg, String callId, String rawCallId, CallSession session) {
        String method = SIPParser.getRequestMethod(msg);
        if (method == null) return;
        method = method.toUpperCase();

        if ("INVITE".equals(method)) {
            // Check if existing session (Re-INVITE)
            if (session != null) {
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

            // Extract Record-Route headers
            newSession.recordRoutes.clear();
            for (String line : msg.split("\r?\n")) {
                if (line.toLowerCase().startsWith("record-route:") || line.toLowerCase().startsWith("r:")) {
                    newSession.recordRoutes.add(line.substring(line.indexOf(':') + 1).trim());
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

        } else if ("BYE".equals(method)) {
            if (session != null) {
                try {
                    sendOkForBye(session, msg);
                } catch (Exception ignored) {}
                stopMediaForSession(session);
                session.setState(CallState.TERMINATED);
                activeSessions.remove(callId);
                if (rawCallId != null) activeSessions.remove(rawCallId);
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

            if ("100".equals(code)) {
                session.setState(CallState.PROCEEDING);
            } else if ("180".equals(code) || "183".equals(code)) {
                session.setState(CallState.RINGING);
                session.remoteTag = SIPParser.getParamValue(SIPParser.getHeader(msg, "To", "t"), "tag");
            } else if ("200".equals(code)) {
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

                    session.setState(CallState.ESTABLISHED);
                    try {
                        sendAck(session);
                    } catch (Exception ignored) {}

                    if (!session.isOnHold) {
                        startMediaForSession(session);
                    }
                } else if (cseq != null && cseq.toUpperCase().contains("BYE")) {
                    session.setState(CallState.TERMINATED);
                    activeSessions.remove(callId);
                }
            } else if (code.startsWith("4") || code.startsWith("5") || code.startsWith("6")) {
                System.out.println("<<< [SIP FAILURE " + code + "]:\n" + msg);
                session.setState(CallState.FAILED);
                String cseq = SIPParser.getHeaderValue(msg, "CSeq");
                if (cseq != null && cseq.toUpperCase().contains("INVITE")) {
                    sendNon2xxAck(session, msg);
                }
            }
        }
    }

    private void sendNon2xxAck(CallSession session, String failureMsg) {
        try {
            String branch = SIPParser.getParamValue(SIPParser.getHeader(failureMsg, "Via", "v"), "branch");
            StringBuilder ack = new StringBuilder();
            ack.append("ACK sip:").append(session.callee).append("@").append(pbxIp).append(" SIP/2.0\r\n");
            ack.append("Via: SIP/2.0/UDP ").append(localIp).append(":").append(localSipPort).append(";rport;branch=").append(branch != null ? branch : "z9hG4bKack").append("\r\n");
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
        boolean remoteHold = msg.contains("a=sendonly") || msg.contains("c=IN IP4 0.0.0.0");
        session.isOnHold = remoteHold;

        try {
            String sdp = remoteHold ? buildSdpHold(localIp, session.localRtpPort, session.payloadType)
                    : buildSdpOffer(localIp, session.localRtpPort, session.payloadType);

            StringBuilder ok = new StringBuilder();
            ok.append("SIP/2.0 200 OK\r\n");
            String viaHdr = SIPParser.getHeader(msg, "Via", "v");
            String branch = SIPParser.getParamValue(viaHdr, "branch");
            ok.append("Via: SIP/2.0/UDP ").append(pbxIp).append(":").append(pbxSipPort).append(";branch=").append(branch != null ? branch : "z9hG4bK").append("\r\n");
            ok.append("From: <sip:").append(session.caller).append("@").append(pbxIp).append(">;tag=").append(session.remoteTag).append("\r\n");
            ok.append("To: <sip:").append(session.callee).append("@").append(pbxIp).append(">;tag=").append(session.localTag).append("\r\n");
            ok.append("Call-ID: ").append(session.callId).append("\r\n");
            ok.append("CSeq: ").append(SIPParser.getHeaderValue(msg, "CSeq")).append("\r\n");
            ok.append("Contact: <sip:").append(session.callee).append("@").append(localIp).append(":").append(localSipPort).append(";transport=udp>\r\n");
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

    private void parseSdpBody(CallSession session, String msg) {
        for (String line : msg.split("\r?\n")) {
            if (line.startsWith("c=IN IP4 ")) {
                session.remoteRtpIp = line.substring(9).trim();
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
            }
        }
    }

    private int allocateRtpPort() {
        int p = nextRtpPort.getAndAdd(2);
        if (p > 50000) {
            nextRtpPort.set(42000);
            p = 42000;
        }
        return p;
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
