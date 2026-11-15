package sipinspector.Call;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import sipinspector.GlobalSettings;
import sipinspector.Media.AudioDeviceManager;
import sipinspector.Media.CallState;
import sipinspector.Media.LiveAudioCodec;
import sipinspector.Media.LiveAudioRuntimeDiagnostics;
import sipinspector.Media.RtpWavRecorder;
import sipinspector.Utils.AppPaths;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Command-Line Runner and Lightweight Management Daemon for SipCallEngine.
 *
 * Provides standalone CLI entry points for automated integration testing,
 * interactive daemon mode with REST/JSON endpoints, and script execution.
 */
public class CliCallEngine {

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            return;
        }

        String command = args[0].toLowerCase();
        SipCallEngine engine = SipCallEngine.getInstance();

        try {
            switch (command) {
                case "daemon": {
                    int port = args.length > 1 ? Integer.parseInt(args[1]) : 5065;
                    runDaemon(port);
                    break;
                }

                case "devices": {
                    AudioDeviceManager.initialize();
                    System.out.println("INPUT DEVICES (Microphones):");
                    for (AudioDeviceManager.AudioDevice d : AudioDeviceManager.getInputDevices()) {
                        System.out.println(" - " + d.name);
                    }
                    System.out.println("OUTPUT DEVICES (Speakers):");
                    for (AudioDeviceManager.AudioDevice d : AudioDeviceManager.getOutputDevices()) {
                        System.out.println(" - " + d.name);
                    }
                    break;
                }

                case "register": {
                    String ext = args.length > 1 ? args[1] : "90008";
                    int exp = args.length > 2 ? Integer.parseInt(args[2]) : 1800;
                    engine.startEngine("192.168.0.9", 5060, "192.168.0.20", 5060);
                    boolean ok = engine.register(ext, exp);
                    System.out.println("REGISTER " + ext + ": " + (ok ? "SUCCESS" : "FAILED"));
                    engine.stopEngine();
                    break;
                }

                case "outbound": {
                    String caller = args.length > 1 ? args[1] : "90008";
                    String callee = args.length > 2 ? args[2] : "90001";
                    int pt = args.length > 3 ? Integer.parseInt(args[3]) : 8;
                    int durationSec = args.length > 4 ? Integer.parseInt(args[4]) : 5;

                    engine.startEngine("192.168.0.9", 5060, "192.168.0.20", 5060);
                    engine.register(caller, 1800);

                    System.out.println("Placing call: " + caller + " -> " + callee + " (PT=" + pt + ")");
                    SipCallEngine.CallSession session = engine.makeCall(caller, callee, pt);

                    // Wait for call to ring, then auto-answer on MicroSIP
                    long wait = System.currentTimeMillis();
                    while (System.currentTimeMillis() - wait < 10000 && session.state != CallState.ESTABLISHED) {
                        if (session.state == CallState.RINGING) {
                            sendMicroSipIpcCommand("call.answer", "{}");
                        }
                        Thread.sleep(100);
                    }

                    if (session.state == CallState.ESTABLISHED) {
                        System.out.println("Call established! Streaming audio for " + durationSec + "s...");
                        Thread.sleep(durationSec * 1000L);
                        System.out.println("Terminating call...");
                        engine.hangupCall(session.callId);
                        System.out.println("Call terminated successfully.");
                        System.out.println("TX WAV: " + session.txWavPath);
                        System.out.println("RX WAV: " + session.rxWavPath);
                    } else {
                        System.err.println("Failed to establish call. Final state: " + session.state);
                    }

                    engine.unregister(caller);
                    engine.stopEngine();
                    break;
                }

                case "play-wav": {
                    if (args.length < 2) {
                        System.err.println("Usage: play-wav <file.wav>");
                        return;
                    }
                    String path = args[1];
                    System.out.println("Playing audio file: " + path);
                    RtpWavRecorder.playWavFile(path);
                    System.out.println("Playback finished.");
                    break;
                }

                default:
                    System.err.println("Unknown command: " + command);
                    printUsage();
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Start the embedded HTTP management daemon for SipCallEngine.
     */
    public static void runDaemon(int port) throws Exception {
        try {
            GlobalSettings.loadSettings();
        } catch (Exception ignored) {}

        String pbxIp = GlobalSettings.getRemoteIPAddr();
        if (pbxIp == null || pbxIp.trim().isEmpty()) pbxIp = "192.168.0.20";
        String localIp = GlobalSettings.getLocalIP();
        if (localIp == null || localIp.trim().isEmpty() || "192.168.0.9".equals(localIp)) {
            localIp = SipCallEngine.resolveLocalIpTowards(pbxIp);
        }
        String ext = GlobalSettings.getUsername();
        if (ext == null || ext.trim().isEmpty()) ext = "90008";

        AudioDeviceManager.initializeFromGlobalSettings();

        final SipCallEngine engine = SipCallEngine.getInstance();
        boolean started = engine.startEngine(localIp, 5060, pbxIp, 5060);
        if (!started) {
            System.err.println("[CALL DAEMON] Failed to bind signaling socket on " + localIp + ":5060");
        } else {
            System.out.println("[CALL DAEMON] Engine started on " + localIp + ":5060 -> PBX " + pbxIp + ":5060");
            try {
                engine.register(ext, 1800);
                System.out.println("[CALL DAEMON] Registered extension " + ext + " with OXE");
            } catch (Exception e) {
                System.err.println("[CALL DAEMON] Warning: Initial registration failed: " + e.getMessage());
            }
        }

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);

        // 1. Status
        server.createContext("/api/engine/status", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                if (handleCors(exchange)) return;
                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("running", engine.isRunning());
                resp.put("localIp", engine.getLocalIp());
                resp.put("localPort", engine.getLocalSipPort());
                resp.put("pbxIp", engine.getPbxIp());
                resp.put("pbxPort", engine.getPbxSipPort());
                resp.put("registeredExtension", engine.getRegisteredExtension());
                resp.put("activeCallsCount", deduplicateSessions(engine.getActiveCalls()).size());
                sendJsonResponse(exchange, 200, toJson(resp));
            }
        });

        // 2. Register
        server.createContext("/api/engine/register", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                if (handleCors(exchange)) return;
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendJsonResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                    return;
                }
                String body = readBody(exchange);
                String extension = extractJsonField(body, "extension");
                String expStr = extractJsonField(body, "expires");
                int expires = (expStr != null && !expStr.isEmpty()) ? Integer.parseInt(expStr) : 1800;
                if (extension == null || extension.isEmpty()) extension = "90008";

                try {
                    boolean ok = engine.register(extension, expires);
                    Map<String, Object> resp = new LinkedHashMap<>();
                    resp.put("success", ok);
                    resp.put("extension", extension);
                    resp.put("expires", expires);
                    sendJsonResponse(exchange, ok ? 200 : 500, toJson(resp));
                } catch (Exception e) {
                    sendJsonResponse(exchange, 500, "{\"success\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
            }
        });

        // 3. Unregister
        server.createContext("/api/engine/unregister", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                if (handleCors(exchange)) return;
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendJsonResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                    return;
                }
                String body = readBody(exchange);
                String extension = extractJsonField(body, "extension");
                if (extension == null || extension.isEmpty()) extension = engine.getRegisteredExtension();
                try {
                    boolean ok = engine.unregister(extension != null ? extension : "90008");
                    Map<String, Object> resp = new LinkedHashMap<>();
                    resp.put("success", ok);
                    resp.put("extension", extension);
                    sendJsonResponse(exchange, 200, toJson(resp));
                } catch (Exception e) {
                    sendJsonResponse(exchange, 500, "{\"success\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
            }
        });

        // 4. Query active calls
        server.createContext("/api/call/active", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                if (handleCors(exchange)) return;
                List<Map<String, Object>> list = new ArrayList<>();
                for (SipCallEngine.CallSession s : deduplicateSessions(engine.getActiveCalls())) {
                    list.add(s.toMap());
                }
                sendJsonResponse(exchange, 200, toJson(list));
            }
        });

        // 5. Dial outbound call
        server.createContext("/api/call/dial", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                if (handleCors(exchange)) return;
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendJsonResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                    return;
                }
                String body = readBody(exchange);
                String caller = extractJsonField(body, "caller");
                String callee = extractJsonField(body, "callee");
                String ptStr = extractJsonField(body, "payloadType");
                int pt = (ptStr != null && !ptStr.isEmpty()) ? Integer.parseInt(ptStr) : 8;

                if (caller == null || caller.isEmpty()) caller = "90008";
                if (callee == null || callee.isEmpty()) callee = "90001";

                try {
                    SipCallEngine.CallSession s = engine.makeCall(caller, callee, pt);
                    Map<String, Object> resp = new LinkedHashMap<>();
                    resp.put("success", true);
                    resp.put("session", s.toMap());
                    sendJsonResponse(exchange, 200, toJson(resp));
                } catch (Exception e) {
                    sendJsonResponse(exchange, 500, "{\"success\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
            }
        });

        // 6. Answer call
        server.createContext("/api/call/answer", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                if (handleCors(exchange)) return;
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendJsonResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                    return;
                }
                String body = readBody(exchange);
                String callId = extractJsonField(body, "callId");
                if (callId == null || callId.isEmpty()) {
                    sendJsonResponse(exchange, 400, "{\"success\":false,\"error\":\"callId required\"}");
                    return;
                }
                try {
                    boolean ok = engine.answerCall(callId);
                    Map<String, Object> resp = new LinkedHashMap<>();
                    resp.put("success", ok);
                    resp.put("callId", callId);
                    sendJsonResponse(exchange, ok ? 200 : 500, toJson(resp));
                } catch (Exception e) {
                    sendJsonResponse(exchange, 500, "{\"success\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
            }
        });

        // 7. Reject call
        server.createContext("/api/call/reject", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                if (handleCors(exchange)) return;
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendJsonResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                    return;
                }
                String body = readBody(exchange);
                String callId = extractJsonField(body, "callId");
                String codeStr = extractJsonField(body, "statusCode");
                String reason = extractJsonField(body, "reason");
                int code = (codeStr != null && !codeStr.isEmpty()) ? Integer.parseInt(codeStr) : 486;
                if (reason == null || reason.isEmpty()) reason = (code == 603 ? "Decline" : "Busy Here");

                if (callId == null || callId.isEmpty()) {
                    sendJsonResponse(exchange, 400, "{\"success\":false,\"error\":\"callId required\"}");
                    return;
                }
                try {
                    boolean ok = engine.rejectCall(callId, code, reason);
                    Map<String, Object> resp = new LinkedHashMap<>();
                    resp.put("success", ok);
                    resp.put("callId", callId);
                    resp.put("statusCode", code);
                    sendJsonResponse(exchange, 200, toJson(resp));
                } catch (Exception e) {
                    sendJsonResponse(exchange, 500, "{\"success\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
            }
        });

        // 8. Hangup call
        server.createContext("/api/call/hangup", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                if (handleCors(exchange)) return;
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendJsonResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                    return;
                }
                String body = readBody(exchange);
                String callId = extractJsonField(body, "callId");
                if (callId == null || callId.isEmpty()) {
                    sendJsonResponse(exchange, 400, "{\"success\":false,\"error\":\"callId required\"}");
                    return;
                }
                try {
                    boolean ok = engine.hangupCall(callId);
                    Map<String, Object> resp = new LinkedHashMap<>();
                    resp.put("success", ok);
                    resp.put("callId", callId);
                    sendJsonResponse(exchange, 200, toJson(resp));
                } catch (Exception e) {
                    sendJsonResponse(exchange, 500, "{\"success\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
            }
        });

        // 9. Hold / Resume call
        server.createContext("/api/call/hold", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                if (handleCors(exchange)) return;
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendJsonResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                    return;
                }
                String body = readBody(exchange);
                String callId = extractJsonField(body, "callId");
                String action = extractJsonField(body, "action");
                if (callId == null || callId.isEmpty()) {
                    sendJsonResponse(exchange, 400, "{\"success\":false,\"error\":\"callId required\"}");
                    return;
                }
                try {
                    boolean ok;
                    if ("resume".equalsIgnoreCase(action)) {
                        ok = engine.resumeCall(callId);
                    } else {
                        ok = engine.holdCall(callId);
                    }
                    SipCallEngine.CallSession s = engine.getCallSession(callId);
                    Map<String, Object> resp = new LinkedHashMap<>();
                    resp.put("success", ok);
                    resp.put("callId", callId);
                    resp.put("isOnHold", s != null && s.isOnHold);
                    sendJsonResponse(exchange, ok ? 200 : 500, toJson(resp));
                } catch (Exception e) {
                    sendJsonResponse(exchange, 500, "{\"success\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
            }
        });

        // 10. Mute / Unmute microphone
        server.createContext("/api/call/mute", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                if (handleCors(exchange)) return;
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendJsonResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                    return;
                }
                String body = readBody(exchange);
                String callId = extractJsonField(body, "callId");
                String mutedStr = extractJsonField(body, "muted");
                boolean muted = "true".equalsIgnoreCase(mutedStr);
                if (callId == null || callId.isEmpty()) {
                    sendJsonResponse(exchange, 400, "{\"success\":false,\"error\":\"callId required\"}");
                    return;
                }
                try {
                    engine.setMute(callId, muted);
                    SipCallEngine.CallSession s = engine.getCallSession(callId);
                    Map<String, Object> resp = new LinkedHashMap<>();
                    resp.put("success", true);
                    resp.put("callId", callId);
                    resp.put("isMuted", s != null && s.isMuted);
                    sendJsonResponse(exchange, 200, toJson(resp));
                } catch (Exception e) {
                    sendJsonResponse(exchange, 500, "{\"success\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
            }
        });

        // 11. Audio devices enumeration and selection
        server.createContext("/api/audio/devices", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                if (handleCors(exchange)) return;
                String method = exchange.getRequestMethod().toUpperCase();
                if ("GET".equals(method)) {
                    List<String> inNames = new ArrayList<>();
                    for (AudioDeviceManager.AudioDevice d : AudioDeviceManager.getInputDevices()) {
                        inNames.add(d.name);
                    }
                    List<String> outNames = new ArrayList<>();
                    for (AudioDeviceManager.AudioDevice d : AudioDeviceManager.getOutputDevices()) {
                        outNames.add(d.name);
                    }
                    AudioDeviceManager.AudioDevice curIn = AudioDeviceManager.getInputDevice();
                    AudioDeviceManager.AudioDevice curOut = AudioDeviceManager.getOutputDevice();

                    Map<String, Object> resp = new LinkedHashMap<>();
                    resp.put("inputDevices", inNames);
                    resp.put("outputDevices", outNames);
                    resp.put("selectedInput", curIn != null ? curIn.name : "");
                    resp.put("selectedOutput", curOut != null ? curOut.name : "");
                    sendJsonResponse(exchange, 200, toJson(resp));
                } else if ("POST".equals(method)) {
                    String body = readBody(exchange);
                    String inDev = extractJsonField(body, "inputDevice");
                    String outDev = extractJsonField(body, "outputDevice");

                    boolean inOk = true;
                    if (inDev != null && !inDev.isEmpty()) {
                        inOk = AudioDeviceManager.selectInputDeviceByName(inDev);
                    }
                    boolean outOk = true;
                    if (outDev != null && !outDev.isEmpty()) {
                        outOk = AudioDeviceManager.selectOutputDeviceByName(outDev);
                    }
                    Map<String, Object> resp = new LinkedHashMap<>();
                    resp.put("success", inOk && outOk);
                    resp.put("selectedInput", AudioDeviceManager.getInputDevice() != null ? AudioDeviceManager.getInputDevice().name : "");
                    resp.put("selectedOutput", AudioDeviceManager.getOutputDevice() != null ? AudioDeviceManager.getOutputDevice().name : "");
                    sendJsonResponse(exchange, 200, toJson(resp));
                } else {
                    sendJsonResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                }
            }
        });

        // 12. Cancel call (CANCEL before 200 OK)
        server.createContext("/api/call/cancel", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                if (handleCors(exchange)) return;
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendJsonResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                    return;
                }
                String body = readBody(exchange);
                String callId = extractJsonField(body, "callId");
                if (callId == null || callId.isEmpty()) {
                    sendJsonResponse(exchange, 400, "{\"success\":false,\"error\":\"callId required\"}");
                    return;
                }
                try {
                    boolean ok = engine.cancelCall(callId);
                    Map<String, Object> resp = new LinkedHashMap<>();
                    resp.put("success", ok);
                    resp.put("callId", callId);
                    sendJsonResponse(exchange, 200, toJson(resp));
                } catch (Exception e) {
                    sendJsonResponse(exchange, 500, "{\"success\":false,\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
                }
            }
        });

        // 13. Start Performance Campaign
        server.createContext("/api/perf/start", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                if (handleCors(exchange)) return;
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendJsonResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                    return;
                }
                String body = readBody(exchange);
                PerformanceTestEngine.CampaignConfig cfg = new PerformanceTestEngine.CampaignConfig();
                String name = extractJsonField(body, "name");
                if (name != null && !name.isEmpty()) cfg.name = name;

                String mode = extractJsonField(body, "mode");
                if (mode != null && !mode.isEmpty()) {
                    try {
                        cfg.mode = PerformanceTestEngine.ExecutionMode.valueOf(mode.toUpperCase());
                    } catch (Exception ignored) {}
                }

                String caller = extractJsonField(body, "caller");
                if (caller != null && !caller.isEmpty()) cfg.caller = caller;

                String callee = extractJsonField(body, "callee");
                if (callee != null && !callee.isEmpty()) cfg.callee = callee;

                String totalCallsStr = extractJsonField(body, "totalCalls");
                if (totalCallsStr != null && !totalCallsStr.isEmpty()) {
                    try { cfg.totalCalls = Math.min(100, Math.max(1, Integer.parseInt(totalCallsStr))); } catch (Exception ignored) {}
                }

                String maxConcurrentStr = extractJsonField(body, "maxConcurrent");
                if (maxConcurrentStr != null && !maxConcurrentStr.isEmpty()) {
                    try { cfg.maxConcurrent = Math.min(10, Math.max(1, Integer.parseInt(maxConcurrentStr))); } catch (Exception ignored) {}
                }

                String cpsStr = extractJsonField(body, "callsPerSecond");
                if (cpsStr != null && !cpsStr.isEmpty()) {
                    try { cfg.callsPerSecond = Math.min(10.0, Math.max(0.1, Double.parseDouble(cpsStr))); } catch (Exception ignored) {}
                }

                String durationStr = extractJsonField(body, "durationSeconds");
                if (durationStr != null && !durationStr.isEmpty()) {
                    try { cfg.durationSeconds = Math.max(1, Integer.parseInt(durationStr)); } catch (Exception ignored) {}
                }

                String delayStr = extractJsonField(body, "delayBetweenCallsMs");
                if (delayStr != null && !delayStr.isEmpty()) {
                    try { cfg.delayBetweenCallsMs = Math.max(0, Integer.parseInt(delayStr)); } catch (Exception ignored) {}
                }

                String ptStr = extractJsonField(body, "payloadType");
                if (ptStr != null && !ptStr.isEmpty()) {
                    try { cfg.payloadType = Integer.parseInt(ptStr); } catch (Exception ignored) {}
                }

                String autoAnswerStr = extractJsonField(body, "autoAnswer");
                cfg.autoAnswer = "true".equalsIgnoreCase(autoAnswerStr);

                boolean started = PerformanceTestEngine.getInstance().startCampaign(cfg);
                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("success", started);
                resp.put("running", PerformanceTestEngine.getInstance().isRunning());
                resp.put("campaignName", cfg.name);
                resp.put("mode", cfg.mode.name());
                sendJsonResponse(exchange, started ? 200 : 409, toJson(resp));
            }
        });

        // 14. Stop Performance Campaign
        server.createContext("/api/perf/stop", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                if (handleCors(exchange)) return;
                PerformanceTestEngine.getInstance().stopCampaign();
                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("success", true);
                resp.put("running", false);
                sendJsonResponse(exchange, 200, toJson(resp));
            }
        });

        // 15. Query Performance Campaign Status
        server.createContext("/api/perf/status", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                if (handleCors(exchange)) return;
                PerformanceTestEngine enginePerf = PerformanceTestEngine.getInstance();
                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("running", enginePerf.isRunning());
                if (enginePerf.getCurrentConfig() != null) {
                    Map<String, Object> cfgMap = new LinkedHashMap<>();
                    cfgMap.put("name", enginePerf.getCurrentConfig().name);
                    cfgMap.put("mode", enginePerf.getCurrentConfig().mode.name());
                    cfgMap.put("caller", enginePerf.getCurrentConfig().caller);
                    cfgMap.put("callee", enginePerf.getCurrentConfig().callee);
                    cfgMap.put("totalCalls", enginePerf.getCurrentConfig().totalCalls);
                    cfgMap.put("maxConcurrent", enginePerf.getCurrentConfig().maxConcurrent);
                    cfgMap.put("callsPerSecond", enginePerf.getCurrentConfig().callsPerSecond);
                    cfgMap.put("durationSeconds", enginePerf.getCurrentConfig().durationSeconds);
                    resp.put("config", cfgMap);
                }
                if (enginePerf.getCurrentResult() != null) {
                    resp.put("result", enginePerf.getCurrentResult().toMap());
                }
                sendJsonResponse(exchange, 200, toJson(resp));
            }
        });

        // 16. Query Saved Performance Reports
        server.createContext("/api/perf/results", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                if (handleCors(exchange)) return;
                File dir = new File(AppPaths.getLogsDir(), "perf_campaigns");
                List<String> files = new ArrayList<>();
                if (dir.exists() && dir.isDirectory()) {
                    File[] list = dir.listFiles();
                    if (list != null) {
                        for (File f : list) {
                            if (f.getName().endsWith(".json")) {
                                files.add(f.getName());
                            }
                        }
                    }
                }
                Collections.sort(files, Collections.reverseOrder());
                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("reports", files);
                sendJsonResponse(exchange, 200, toJson(resp));
            }
        });

        // 17. Execute Controlled Failure Injection Test
        server.createContext("/api/perf/failure-test", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                if (handleCors(exchange)) return;
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendJsonResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                    return;
                }
                String body = readBody(exchange);
                String scenarioStr = extractJsonField(body, "scenario");
                String caller = extractJsonField(body, "caller");
                if (caller == null || caller.isEmpty()) caller = "90008";
                String callee = extractJsonField(body, "callee");
                if (callee == null || callee.isEmpty()) callee = "90001";

                FailureInjectionEngine.FailureScenario scenario = FailureInjectionEngine.FailureScenario.INVALID_DESTINATION_404;
                if (scenarioStr != null && !scenarioStr.isEmpty()) {
                    try {
                        scenario = FailureInjectionEngine.FailureScenario.valueOf(scenarioStr.trim().toUpperCase());
                    } catch (Exception ignored) {}
                }

                FailureInjectionEngine.FailureTestResult res = FailureInjectionEngine.runTest(scenario, caller, callee, engine.getPbxIp());
                sendJsonResponse(exchange, 200, toJson(res.toMap()));
            }
        });

        // 18. System Health and Resource Status
        server.createContext("/api/health", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                if (handleCors(exchange)) return;
                Runtime rt = Runtime.getRuntime();
                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("status", "UP");
                resp.put("engineRunning", engine.isRunning());
                resp.put("localIp", engine.getLocalIp());
                resp.put("localPort", engine.getLocalSipPort());
                resp.put("pbxIp", engine.getPbxIp());
                resp.put("pbxPort", engine.getPbxSipPort());
                resp.put("registeredExtension", engine.getRegisteredExtension());
                resp.put("activeCallsCount", deduplicateSessions(engine.getActiveCalls()).size());
                resp.put("memoryUsedMb", (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024));
                resp.put("memoryFreeMb", rt.freeMemory() / (1024 * 1024));
                resp.put("memoryMaxMb", rt.maxMemory() / (1024 * 1024));
                resp.put("activeThreads", Thread.activeCount());
                resp.put("openMediaSockets", LiveAudioRuntimeDiagnostics.getOpenSockets());
                resp.put("openMicLines", LiveAudioRuntimeDiagnostics.getOpenMicLines());
                resp.put("openSpeakerLines", LiveAudioRuntimeDiagnostics.getOpenSpeakerLines());
                resp.put("campaignRunning", PerformanceTestEngine.getInstance().isRunning());
                sendJsonResponse(exchange, 200, toJson(resp));
            }
        });

        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        System.out.println("[CALL DAEMON] Listening on http://127.0.0.1:" + port);

        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    System.out.println("[CALL DAEMON] Shutting down...");
                    server.stop(0);
                    engine.stopEngine();
                } catch (Exception ignored) {}
            }
        }));

        // Keep process running
        while (true) {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private static List<SipCallEngine.CallSession> deduplicateSessions(List<SipCallEngine.CallSession> list) {
        List<SipCallEngine.CallSession> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (SipCallEngine.CallSession s : list) {
            if (s != null && s.callId != null && seen.add(s.callId)) {
                out.add(s);
            }
        }
        return out;
    }

    private static boolean handleCors(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return true;
        }
        return false;
    }

    private static void sendJsonResponse(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes("UTF-8");
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            Scanner s = new Scanner(is, "UTF-8").useDelimiter("\\A");
            return s.hasNext() ? s.next() : "";
        }
    }

    private static String extractJsonField(String json, String field) {
        if (json == null) return null;
        Pattern p = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"?([^,\"}]+)\"?");
        Matcher m = p.matcher(json);
        if (m.find()) {
            String val = m.group(1).trim();
            if (val.startsWith("\"") && val.endsWith("\"")) {
                val = val.substring(1, val.length() - 1);
            }
            return val;
        }
        return null;
    }

    public static String toJson(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof String) return "\"" + escapeJson((String) obj) + "\"";
        if (obj instanceof Number || obj instanceof Boolean) return obj.toString();
        if (obj instanceof Map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> e : ((Map<?, ?>) obj).entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(escapeJson(String.valueOf(e.getKey()))).append("\":");
                sb.append(toJson(e.getValue()));
                first = false;
            }
            sb.append("}");
            return sb.toString();
        }
        if (obj instanceof Collection) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : (Collection<?>) obj) {
                if (!first) sb.append(",");
                sb.append(toJson(item));
                first = false;
            }
            sb.append("]");
            return sb.toString();
        }
        return "\"" + escapeJson(obj.toString()) + "\"";
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 32) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    private static String sendMicroSipIpcCommand(String method, String paramsJson) {
        try (RandomAccessFile pipe = new RandomAccessFile("\\\\.\\pipe\\microsip_ipc", "rw")) {
            String id = String.valueOf(System.currentTimeMillis() % 100000);
            String req;
            if (paramsJson != null && !paramsJson.isEmpty()) {
                req = "{\"jsonrpc\":\"2.0\",\"method\":\"" + method + "\",\"params\":" + paramsJson + ",\"id\":" + id + "}\n";
            } else {
                req = "{\"jsonrpc\":\"2.0\",\"method\":\"" + method + "\",\"id\":" + id + "}\n";
            }
            pipe.write(req.getBytes("UTF-8"));
            return pipe.readLine();
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    private static void printUsage() {
        System.out.println("SipCallEngine CLI Interface");
        System.out.println("Usage: java -cp build;lib/* sipinspector.Call.CliCallEngine <command> [args...]");
        System.out.println("Commands:");
        System.out.println("  daemon [port]                         Start lightweight management REST daemon (default 5065)");
        System.out.println("  devices                               List available audio hardware");
        System.out.println("  register [ext] [expires]              Register extension with OXE");
        System.out.println("  outbound [caller] [callee] [pt] [sec] Make call to MicroSIP and stream audio");
        System.out.println("  play-wav <file.wav>                   Play WAV file to speakers");
    }
}
