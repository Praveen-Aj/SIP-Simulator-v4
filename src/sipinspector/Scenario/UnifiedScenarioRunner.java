/*
 * SIP Simulator v4 - Phase 9
 * UnifiedScenarioRunner: Headless, Non-GUI SIP Scenario Execution Engine
 *
 * Consolidates scenario parsing, parameter substitution, network transmission,
 * message validation, assertions, and execution telemetry without requiring
 * any Swing JFrame or modal dialogs.
 */
package sipinspector.Scenario;

import sipinspector.MyPacket;
import sipinspector.Socket.MySocketAbstract;
import sipinspector.Socket.SipTransportManager;
import sipinspector.Socket.UDPClientSocket;
import sipinspector.Utils.TLSDebugLogger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UnifiedScenarioRunner {

    public interface ScenarioExecutionListener {
        void onStepStarted(int stepIndex, String stepType, String description);
        void onStepCompleted(int stepIndex, String stepType, boolean success, String details);
        void onScenarioFinished(boolean overallSuccess, long totalDurationMs, String summary);
        void onLog(String message);
    }

    public enum StepType {
        SEND,
        RECEIVE,
        PAUSE,
        ASSERTION
    }

    public static class ScenarioStep {
        public final int index;
        public final StepType type;
        public final String rawContent;
        public int pauseMs = 0;
        public int timeoutMs = 5000;
        public String expectedResponseCode = null;
        public String expectedMethod = null;
        public final List<String> assertions = new ArrayList<>();

        public ScenarioStep(int index, StepType type, String rawContent) {
            this.index = index;
            this.type = type;
            this.rawContent = rawContent;
        }
    }

    public static class ExecutionResult {
        public boolean success = false;
        public int totalSteps = 0;
        public int passedSteps = 0;
        public int failedSteps = 0;
        public long durationMs = 0;
        public String failureReason = null;
        public final List<String> executionLogs = new ArrayList<>();
        public final Map<String, String> capturedVariables = new ConcurrentHashMap<>();

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("success", success);
            m.put("totalSteps", totalSteps);
            m.put("passedSteps", passedSteps);
            m.put("failedSteps", failedSteps);
            m.put("durationMs", durationMs);
            m.put("failureReason", failureReason);
            m.put("capturedVariables", capturedVariables);
            m.put("logs", executionLogs);
            return m;
        }
    }

    private final List<ScenarioStep> steps = new ArrayList<>();
    private final Map<String, String> variables = new ConcurrentHashMap<>();
    private final List<ScenarioExecutionListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    private String localIp = "127.0.0.1";
    private int localPort = 5060;
    private String remoteIp = "192.168.0.20";
    private int remotePort = 5060;
    private String transport = "UDP";

    public UnifiedScenarioRunner() {}

    public void addListener(ScenarioExecutionListener l) {
        if (l != null && !listeners.contains(l)) listeners.add(l);
    }

    public void setNetworkConfig(String localIp, int localPort, String remoteIp, int remotePort, String transport) {
        this.localIp = localIp;
        this.localPort = localPort;
        this.remoteIp = remoteIp;
        this.remotePort = remotePort;
        this.transport = transport;
    }

    public void setVariables(Map<String, String> vars) {
        if (vars != null) {
            this.variables.putAll(vars);
        }
    }

    public void setVariable(String key, String value) {
        if (key != null && value != null) {
            this.variables.put(key, value);
        }
    }

    /**
     * Parses a scenario script file (text format) and extracts send/receive/pause steps.
     */
    public boolean loadScenarioFile(File scenarioFile) {
        if (scenarioFile == null || !scenarioFile.exists()) {
            log("Scenario file does not exist: " + scenarioFile);
            return false;
        }
        steps.clear();
        try (BufferedReader br = new BufferedReader(new FileReader(scenarioFile))) {
            String line;
            StringBuilder currentMsg = new StringBuilder();
            StepType currentType = null;
            int stepIndex = 0;

            while ((line = br.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.startsWith("<!-- SEND") || trimmed.startsWith("# SEND") || trimmed.equalsIgnoreCase("[SEND]")) {
                    if (currentType != null && currentMsg.length() > 0) {
                        steps.add(createStep(stepIndex++, currentType, currentMsg.toString()));
                        currentMsg.setLength(0);
                    }
                    currentType = StepType.SEND;
                } else if (trimmed.startsWith("<!-- RECEIVE") || trimmed.startsWith("# RECEIVE") || trimmed.equalsIgnoreCase("[RECEIVE]")) {
                    if (currentType != null && currentMsg.length() > 0) {
                        steps.add(createStep(stepIndex++, currentType, currentMsg.toString()));
                        currentMsg.setLength(0);
                    }
                    currentType = StepType.RECEIVE;
                } else if (trimmed.startsWith("<!-- PAUSE") || trimmed.startsWith("# PAUSE") || trimmed.startsWith("PAUSE ")) {
                    if (currentType != null && currentMsg.length() > 0) {
                        steps.add(createStep(stepIndex++, currentType, currentMsg.toString()));
                        currentMsg.setLength(0);
                    }
                    ScenarioStep pStep = new ScenarioStep(stepIndex++, StepType.PAUSE, trimmed);
                    pStep.pauseMs = parsePauseMs(trimmed);
                    steps.add(pStep);
                    currentType = null;
                } else if (currentType != null) {
                    currentMsg.append(line).append("\r\n");
                }
            }

            if (currentType != null && currentMsg.length() > 0) {
                steps.add(createStep(stepIndex++, currentType, currentMsg.toString()));
            }

            log("Loaded " + steps.size() + " steps from " + scenarioFile.getName());
            return true;
        } catch (Exception e) {
            log("Failed to load scenario file: " + e.getMessage());
            return false;
        }
    }

    private ScenarioStep createStep(int index, StepType type, String rawContent) {
        ScenarioStep step = new ScenarioStep(index, type, rawContent);
        if (type == StepType.RECEIVE) {
            Matcher m = Pattern.compile("^SIP/2.0\\s+(\\d{3})", Pattern.MULTILINE).matcher(rawContent);
            if (m.find()) {
                step.expectedResponseCode = m.group(1);
            }
            Matcher m2 = Pattern.compile("^([A-Z]+)\\s+sip:", Pattern.MULTILINE).matcher(rawContent);
            if (m2.find()) {
                step.expectedMethod = m2.group(1);
            }
        }
        return step;
    }

    private int parsePauseMs(String line) {
        Matcher m = Pattern.compile("(\\d+)", Pattern.CASE_INSENSITIVE).matcher(line);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); } catch (Exception ignored) {}
        }
        return 500;
    }

    /**
     * Executes the scenario synchronously and returns an ExecutionResult.
     */
    public ExecutionResult execute() {
        ExecutionResult res = new ExecutionResult();
        res.totalSteps = steps.size();
        long startTime = System.currentTimeMillis();
        isRunning.set(true);

        log("Starting unified scenario execution (" + steps.size() + " steps) on " + localIp + ":" + localPort + " -> " + remoteIp + ":" + remotePort);

        MySocketAbstract socket = null;
        try {
            socket = SipTransportManager.getInstance().getOrCreateTransport(transport, localIp, localPort);

            for (ScenarioStep step : steps) {
                if (!isRunning.get()) {
                    res.failureReason = "EXECUTION_ABORTED";
                    break;
                }

                notifyStepStarted(step.index, step.type.name(), "Step " + step.index);

                boolean stepOk = executeStep(step, socket, res);
                if (stepOk) {
                    res.passedSteps++;
                    notifyStepCompleted(step.index, step.type.name(), true, "OK");
                } else {
                    res.failedSteps++;
                    res.failureReason = "Failed at step " + step.index + " (" + step.type + ")";
                    notifyStepCompleted(step.index, step.type.name(), false, res.failureReason);
                    break;
                }
            }

            res.success = (res.failedSteps == 0 && res.passedSteps == res.totalSteps);
        } catch (Exception e) {
            res.success = false;
            res.failureReason = "Exception: " + e.getMessage();
            log("Execution error: " + e.getMessage());
        } finally {
            isRunning.set(false);
            res.durationMs = System.currentTimeMillis() - startTime;
            notifyScenarioFinished(res.success, res.durationMs, res.failureReason != null ? res.failureReason : "ALL_STEPS_COMPLETED");
        }

        return res;
    }

    private boolean executeStep(ScenarioStep step, MySocketAbstract socket, ExecutionResult res) throws Exception {
        switch (step.type) {
            case SEND: {
                String resolvedMsg = substituteVariables(step.rawContent);
                MyPacket pkt = new MyPacket(resolvedMsg);
                pkt.setAddress(InetAddress.getByName(remoteIp));
                pkt.setPort(remotePort);
                socket.send(pkt);
                log("[STEP " + step.index + " SEND] Sent message:\n" + resolvedMsg.split("\r?\n")[0]);
                return true;
            }
            case PAUSE: {
                log("[STEP " + step.index + " PAUSE] Pausing for " + step.pauseMs + " ms");
                Thread.sleep(step.pauseMs);
                return true;
            }
            case RECEIVE: {
                log("[STEP " + step.index + " RECEIVE] Waiting for response (timeout=" + step.timeoutMs + "ms)...");
                long deadline = System.currentTimeMillis() + step.timeoutMs;
                while (System.currentTimeMillis() < deadline) {
                    MyPacket inPkt = socket.receive();
                    if (inPkt != null && inPkt.getLength() > 0) {
                        String rcvMsg = new String(inPkt.getBytes(), 0, inPkt.getLength(), StandardCharsets.UTF_8);
                        log("[STEP " + step.index + " RECEIVED]:\n" + rcvMsg.split("\r?\n")[0]);

                        // Extract variables from received message (e.g. Call-ID, To tag)
                        extractVariablesFromMessage(rcvMsg, res);

                        if (step.expectedResponseCode != null) {
                            if (rcvMsg.contains("SIP/2.0 " + step.expectedResponseCode)) {
                                return true;
                            }
                        } else if (step.expectedMethod != null) {
                            if (rcvMsg.startsWith(step.expectedMethod + " ")) {
                                return true;
                            }
                        } else {
                            return true; // Any received message satisfies
                        }
                    }
                    Thread.sleep(50);
                }
                log("[STEP " + step.index + " RECEIVE TIMEOUT] Expected response not received within " + step.timeoutMs + " ms");
                return false;
            }
            default:
                return true;
        }
    }

    private String substituteVariables(String raw) {
        String result = raw;
        for (Map.Entry<String, String> e : variables.entrySet()) {
            result = result.replace("[" + e.getKey() + "]", e.getValue());
            result = result.replace("$" + e.getKey(), e.getValue());
        }
        // Common defaults
        result = result.replace("[local_ip]", localIp);
        result = result.replace("[local_port]", String.valueOf(localPort));
        result = result.replace("[remote_ip]", remoteIp);
        result = result.replace("[remote_port]", String.valueOf(remotePort));
        return result;
    }

    private void extractVariablesFromMessage(String msg, ExecutionResult res) {
        // Extract To tag
        Pattern pTo = Pattern.compile("(?i)^To\\s*:.*tag=([a-zA-Z0-9_-]+)", Pattern.MULTILINE);
        Matcher mTo = pTo.matcher(msg);
        if (mTo.find()) {
            String toTag = mTo.group(1);
            variables.put("to_tag", toTag);
            res.capturedVariables.put("to_tag", toTag);
        }

        // Extract Call-ID
        Pattern pCid = Pattern.compile("(?i)^Call-ID\\s*:\\s*(.*)$", Pattern.MULTILINE);
        Matcher mCid = pCid.matcher(msg);
        if (mCid.find()) {
            String cid = mCid.group(1).trim();
            variables.put("call_id", cid);
            res.capturedVariables.put("call_id", cid);
        }
    }

    private void log(String msg) {
        TLSDebugLogger.log("[UNIFIED RUNNER] " + msg);
        for (ScenarioExecutionListener l : listeners) {
            l.onLog(msg);
        }
    }

    private void notifyStepStarted(int idx, String type, String desc) {
        for (ScenarioExecutionListener l : listeners) {
            l.onStepStarted(idx, type, desc);
        }
    }

    private void notifyStepCompleted(int idx, String type, boolean ok, String details) {
        for (ScenarioExecutionListener l : listeners) {
            l.onStepCompleted(idx, type, ok, details);
        }
    }

    private void notifyScenarioFinished(boolean ok, long duration, String summary) {
        for (ScenarioExecutionListener l : listeners) {
            l.onScenarioFinished(ok, duration, summary);
        }
    }

    public void stop() {
        isRunning.set(false);
    }
}
