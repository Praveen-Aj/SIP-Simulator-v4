/*
 * SIP Simulator v4 - Phase 9
 * EngineServiceBridge: Bridge connecting Desktop Swing UI to UnifiedSipEngineService
 */
package sipinspector.Controller;

import sipinspector.Call.SipCallEngine;
import sipinspector.Core.UnifiedSipEngineService;

import java.util.List;
import java.util.Map;

public class EngineServiceBridge {

    private static final EngineServiceBridge INSTANCE = new EngineServiceBridge();

    public static EngineServiceBridge getInstance() {
        return INSTANCE;
    }

    private final UnifiedSipEngineService service = UnifiedSipEngineService.getInstance();

    private EngineServiceBridge() {}

    public boolean startEngine(String localIp, int localPort, String pbxIp, int pbxPort, String transport) {
        return service.startEngine(localIp, localPort, pbxIp, pbxPort, transport);
    }

    public void stopEngine() {
        service.stopEngine();
    }

    public boolean isEngineRunning() {
        return service.isRunning();
    }

    public boolean registerExtension(String extension, int expiresSeconds) throws Exception {
        return service.registerExtension(extension, expiresSeconds);
    }

    public boolean unregisterExtension(String extension) throws Exception {
        return service.unregisterExtension(extension);
    }

    public SipCallEngine.CallSession makeCall(String caller, String callee, int requestedPt) throws Exception {
        return service.makeCall(caller, callee, requestedPt);
    }

    public boolean hangupCall(String callId) throws Exception {
        return service.hangupCall(callId);
    }

    public boolean holdCall(String callId) throws Exception {
        return service.holdCall(callId);
    }

    public boolean resumeCall(String callId) throws Exception {
        return service.resumeCall(callId);
    }

    public boolean sendDtmf(String callId, char digit, String method) throws Exception {
        return service.sendDtmf(callId, digit, method);
    }

    public boolean transferCall(String callId, String target, String replacesCallId) throws Exception {
        return service.transferCall(callId, target, replacesCallId);
    }

    public List<SipCallEngine.CallSession> getActiveCalls() {
        return service.getActiveCalls();
    }

    public void addListener(UnifiedSipEngineService.EngineEventListener listener) {
        service.addListener(listener);
    }

    public void removeListener(UnifiedSipEngineService.EngineEventListener listener) {
        service.removeListener(listener);
    }

    public Map<String, Object> getEngineStatus() {
        return service.getStatusSummary();
    }
}
