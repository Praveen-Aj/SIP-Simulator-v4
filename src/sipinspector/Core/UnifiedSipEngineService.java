package sipinspector.Core;

import sipinspector.Call.FailureInjectionEngine;
import sipinspector.Call.PerformanceTestEngine;
import sipinspector.Call.SipCallEngine;
import sipinspector.GlobalSettings;
import sipinspector.Media.CallState;
import sipinspector.Media.LiveAudioController;
import sipinspector.Socket.RtpPortManager;
import sipinspector.Socket.SipTransportManager;
import sipinspector.Utils.SipMessageRepository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Unified SIP Engine Service (Workstream A & B).
 *
 * Provides a single shared service layer orchestrating SIP signaling,
 * registration state, dialogs, media sessions, and campaign testing.
 * Bridges both the Java Swing Desktop GUI and the Web Dashboard/REST daemon
 * so that both interfaces operate on a single shared engine instance rather
 * than conflicting independent processes.
 */
public class UnifiedSipEngineService {

    private static final UnifiedSipEngineService INSTANCE = new UnifiedSipEngineService();

    public enum ServiceState {
        STOPPED,
        STARTING,
        RUNNING,
        ERROR
    }

    public interface EngineEventListener {
        void onServiceStateChanged(ServiceState newState);
        void onRegistrationChanged(String extension, boolean registered);
        void onCallStateChanged(String callId, CallState newState, SipCallEngine.CallSession session);
    }

    private final SipCallEngine callEngine = SipCallEngine.getInstance();
    private final SipTransportManager transportManager = SipTransportManager.getInstance();
    private final RtpPortManager rtpPortManager = RtpPortManager.getInstance();
    private final SipMessageRepository messageRepository = SipMessageRepository.getInstance();

    private volatile ServiceState state = ServiceState.STOPPED;
    private String localIp = "127.0.0.1";
    private int localPort = 5060;
    private String pbxIp = "192.168.0.20";
    private int pbxPort = 5060;
    private String transport = "UDP";

    private final List<EngineEventListener> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, Object> serviceMetadata = new ConcurrentHashMap<>();

    private UnifiedSipEngineService() {
        // Wire internal call engine callback
        callEngine.setStateCallback(new SipCallEngine.CallStateCallback() {
            @Override
            public void onCallStateChanged(SipCallEngine.CallSession session) {
                if (session != null) {
                    notifyCallStateChanged(session.callId, session.state, session);
                }
            }
        });
    }

    public static UnifiedSipEngineService getInstance() {
        return INSTANCE;
    }

    public synchronized void addListener(EngineEventListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public synchronized void removeListener(EngineEventListener listener) {
        listeners.remove(listener);
    }

    private void notifyServiceState(ServiceState newState) {
        this.state = newState;
        for (EngineEventListener l : listeners) {
            try {
                l.onServiceStateChanged(newState);
            } catch (Exception ignored) {}
        }
    }

    private void notifyRegistration(String ext, boolean reg) {
        for (EngineEventListener l : listeners) {
            try {
                l.onRegistrationChanged(ext, reg);
            } catch (Exception ignored) {}
        }
    }

    private void notifyCallStateChanged(String callId, CallState newState, SipCallEngine.CallSession session) {
        for (EngineEventListener l : listeners) {
            try {
                l.onCallStateChanged(callId, newState, session);
            } catch (Exception ignored) {}
        }
    }

    /**
     * Start the unified engine with specific network parameters.
     */
    public synchronized boolean startEngine(String localIp, int localPort, String pbxIp, int pbxPort, String transport) {
        this.localIp = (localIp != null && !localIp.isEmpty()) ? localIp : SipCallEngine.resolveLocalIpTowards(pbxIp);
        this.localPort = localPort > 0 ? localPort : 5060;
        this.pbxIp = (pbxIp != null && !pbxIp.isEmpty()) ? pbxIp : "192.168.0.20";
        this.pbxPort = pbxPort > 0 ? pbxPort : 5060;
        this.transport = (transport != null && !transport.isEmpty()) ? transport.toUpperCase() : "UDP";

        notifyServiceState(ServiceState.STARTING);

        try {
            // Check port availability via transport manager
            SipTransportManager.PortCheckResult check = transportManager.checkPort(this.localIp, this.localPort, this.transport);
            if (!check.available && !transportManager.isAnyListenerRunning()) {
                notifyServiceState(ServiceState.ERROR);
                throw new IllegalStateException("Cannot bind " + this.transport + " on " + this.localIp + ":" + this.localPort + " - " + check.message);
            }

            // Sync with GlobalSettings
            GlobalSettings.setSetting("sip.localIp", this.localIp);
            GlobalSettings.setSetting("sip.localPort", String.valueOf(this.localPort));
            GlobalSettings.setSetting("sip.pbxIp", this.pbxIp);
            GlobalSettings.setSetting("sip.pbxPort", String.valueOf(this.pbxPort));
            GlobalSettings.setTransport(this.transport);

            // Start underlying SipCallEngine
            callEngine.startEngine(this.localIp, this.localPort, this.pbxIp, this.pbxPort);
            notifyServiceState(ServiceState.RUNNING);
            return true;
        } catch (Exception e) {
            notifyServiceState(ServiceState.ERROR);
            serviceMetadata.put("lastError", e.getMessage());
            return false;
        }
    }

    public synchronized void stopEngine() {
        notifyServiceState(ServiceState.STOPPED);
        try {
            callEngine.stopEngine();
        } catch (Exception ignored) {}
        transportManager.closeAll();
        rtpPortManager.reset();
    }

    public boolean isRunning() {
        return state == ServiceState.RUNNING;
    }

    public ServiceState getState() {
        return state;
    }

    // --- Registration Management ---

    public synchronized boolean register(String extension, int expiresSec) {
        if (!isRunning()) {
            startEngine(localIp, localPort, pbxIp, pbxPort, transport);
        }
        try {
            boolean ok = callEngine.register(extension, expiresSec);
            notifyRegistration(extension, ok);
            return ok;
        } catch (Exception e) {
            notifyRegistration(extension, false);
            return false;
        }
    }

    public synchronized boolean registerExtension(String extension, int expiresSec) throws Exception {
        return register(extension, expiresSec);
    }

    public synchronized boolean unregister(String extension) {
        if (!isRunning()) return true;
        try {
            boolean ok = callEngine.unregister(extension);
            notifyRegistration(extension, false);
            return ok;
        } catch (Exception e) {
            return false;
        }
    }

    public synchronized boolean unregisterExtension(String extension) throws Exception {
        return unregister(extension);
    }

    public boolean isRegistered() {
        return callEngine.isRegistered();
    }

    public String getRegisteredExtension() {
        return callEngine.getRegisteredExtension();
    }

    // --- Call Management ---

    public SipCallEngine.CallSession makeCall(String caller, String callee, int payloadType) throws Exception {
        if (!isRunning()) {
            startEngine(localIp, localPort, pbxIp, pbxPort, transport);
        }
        return callEngine.makeCall(caller, callee, payloadType);
    }

    public boolean answerCall(String callId) throws Exception {
        return callEngine.answerCall(callId);
    }

    public boolean rejectCall(String callId, int statusCode, String reason) throws Exception {
        return callEngine.rejectCall(callId, statusCode, reason);
    }

    public boolean hangupCall(String callId) throws Exception {
        return callEngine.hangupCall(callId);
    }

    public boolean holdCall(String callId) throws Exception {
        return callEngine.holdCall(callId);
    }

    public boolean resumeCall(String callId) throws Exception {
        return callEngine.resumeCall(callId);
    }

    public boolean sendDtmf(String callId, char digit, String mode) throws Exception {
        return callEngine.sendDtmf(callId, digit, mode);
    }

    public boolean transferCall(String callId, String target, String replacesCallId) throws Exception {
        return callEngine.transferCall(callId, target, replacesCallId);
    }

    public boolean blindTransfer(String callId, String targetExtension) {
        try {
            return callEngine.blindTransfer(callId, targetExtension);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean attendedTransfer(String callId, String targetExtension, String replacesCallId, String toTag, String fromTag) {
        try {
            return callEngine.attendedTransfer(callId, targetExtension, replacesCallId, toTag, fromTag);
        } catch (Exception e) {
            return false;
        }
    }

    public List<SipCallEngine.CallSession> getActiveCalls() {
        return callEngine.getActiveCalls();
    }

    public SipCallEngine.CallSession getCallSession(String callId) {
        return callEngine.getSession(callId);
    }

    public Map<String, Object> getStatusSummary() {
        return getStatusMap();
    }

    // --- Status and Observability Map ---

    public Map<String, Object> getStatusMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("serviceState", state.name());
        map.put("localIp", localIp);
        map.put("localPort", localPort);
        map.put("pbxIp", pbxIp);
        map.put("pbxPort", pbxPort);
        map.put("transport", transport);
        map.put("registered", isRegistered());
        map.put("registeredExtension", getRegisteredExtension());
        map.put("activeCallsCount", callEngine.getActiveCalls().size());
        map.put("allocatedRtpPorts", rtpPortManager.getActiveAllocationCount());
        map.put("capturedMessages", messageRepository.getAllMessages().size());
        map.put("txMessages", messageRepository.getTxMessageCount());
        map.put("rxMessages", messageRepository.getRxMessageCount());
        return map;
    }

    public SipCallEngine getCallEngine() { return callEngine; }
    public SipTransportManager getTransportManager() { return transportManager; }
    public RtpPortManager getRtpPortManager() { return rtpPortManager; }
    public SipMessageRepository getMessageRepository() { return messageRepository; }

    public String getLocalIp() { return localIp; }
    public int getLocalPort() { return localPort; }
    public String getPbxIp() { return pbxIp; }
    public int getPbxPort() { return pbxPort; }
    public String getTransport() { return transport; }
}
