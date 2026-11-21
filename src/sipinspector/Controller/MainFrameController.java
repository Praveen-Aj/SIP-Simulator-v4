/*
 * SIP Simulator v4 - Phase 9
 * MainFrameController: Controller separating UI presentation from application business logic
 */
package sipinspector.Controller;

import sipinspector.GlobalSettings;
import sipinspector.Scenario.UnifiedScenarioRunner;
import sipinspector.Socket.RtpPortManager;
import sipinspector.Socket.SipTransportManager;

import java.io.File;
import java.util.Map;

public class MainFrameController {

    private final EngineServiceBridge bridge = EngineServiceBridge.getInstance();
    private UnifiedScenarioRunner activeScenarioRunner;

    public MainFrameController() {}

    public void initializeEnvironment() {
        try {
            GlobalSettings.loadSettings();
        } catch (Exception ignored) {}

        // Synchronize RTP port manager
        RtpPortManager.getInstance().loadRangeFromSettings();
    }

    public boolean applyNetworkSettings(String localIp, int localPort, String remoteIp, int remotePort, String transport) {
        GlobalSettings.setLocalIP(localIp);
        GlobalSettings.setLocalPort(String.valueOf(localPort));
        GlobalSettings.setRemoteIPAddr(remoteIp);
        GlobalSettings.setRemotePort(String.valueOf(remotePort));
        GlobalSettings.setTransport(transport);
        try {
            GlobalSettings.saveSettings();
        } catch (Exception ignored) {}

        return bridge.startEngine(localIp, localPort, remoteIp, remotePort, transport);
    }

    public UnifiedScenarioRunner.ExecutionResult runScenarioHeadless(File scenarioFile, Map<String, String> variables) {
        activeScenarioRunner = new UnifiedScenarioRunner();
        String localIp = GlobalSettings.getLocalIP();
        int localPort = 5060;
        try { localPort = Integer.parseInt(GlobalSettings.getLocalPort()); } catch (Exception ignored) {}
        String remoteIp = GlobalSettings.getRemoteIPAddr();
        int remotePort = 5060;
        try { remotePort = Integer.parseInt(GlobalSettings.getRemotePort()); } catch (Exception ignored) {}
        String transport = GlobalSettings.getTransport();

        activeScenarioRunner.setNetworkConfig(localIp, localPort, remoteIp, remotePort, transport);
        if (variables != null) {
            activeScenarioRunner.setVariables(variables);
        }
        boolean loaded = activeScenarioRunner.loadScenarioFile(scenarioFile);
        if (!loaded) {
            UnifiedScenarioRunner.ExecutionResult fail = new UnifiedScenarioRunner.ExecutionResult();
            fail.failureReason = "Failed to load scenario file: " + scenarioFile;
            return fail;
        }
        return activeScenarioRunner.execute();
    }

    public void stopActiveScenario() {
        if (activeScenarioRunner != null) {
            activeScenarioRunner.stop();
        }
    }

    public EngineServiceBridge getBridge() {
        return bridge;
    }
}
