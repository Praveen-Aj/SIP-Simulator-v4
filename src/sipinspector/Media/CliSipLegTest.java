package sipinspector.Media;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Date;
import sipinspector.GlobalSettings;
import sipinspector.SIPInspectorMainFrame;
import sipinspector.ScenarioProgressDialog;
import sipinspector.Utils.LoadScenario;
import sipinspector.Utils.TLSDebugLogger;

public class CliSipLegTest {

    private static final String RECORDINGS_DIR = "recordings";

    public static void main(String[] args) {
        System.out.println("=== SIP Simulator Cli Leg Test ===");
        
        if (args.length < 3) {
            System.out.println("Usage: java CliSipLegTest <mode: callee|caller> <localPort> <username> [calleeName]");
            System.exit(1);
        }
        
        String mode = args[0];
        int localPort = Integer.parseInt(args[1]);
        String username = args[2];
        String calleeName = args.length > 3 ? args[3] : "";
        
        System.out.println("Running in mode: " + mode);
        System.out.println("Local Port: " + localPort);
        System.out.println("Username: " + username);
        if (!calleeName.isEmpty()) {
            System.out.println("Callee Name: " + calleeName);
        }
        
        File tempValFile = null;
        File tempCallerValFile = null;
        RtpWavRecorder recorder = null;
        RtpCaptureThread captureThread = null;
        try {
            // Enable UDP debug logging
            TLSDebugLogger.enable("UDP");
            
            // Instantiate Main Frame
            System.out.println("Initializing SIPInspectorMainFrame...");
            SIPInspectorMainFrame frame = new SIPInspectorMainFrame();
            
            // Wait for LoadScenario SwingWorker to finish loading default files
            System.out.println("Waiting for default scenario/values files to load...");
            Thread.sleep(2000);
            
            // Extract settings dynamically. A per-test PBX override may be passed
            // by the Campaigns runner via -DremoteIP / -DremotePort; prefer it over
            // the global userSettings.xml value so each test can target its own PBX.
            String remoteIP = System.getProperty("remoteIP");
            if (remoteIP == null || remoteIP.trim().isEmpty()) {
                remoteIP = GlobalSettings.getRemoteIPAddr();
            } else {
                remoteIP = remoteIP.trim();
                System.out.println("PBX override (remoteIP): " + remoteIP);
            }
            int remotePort = 5060;
            String remotePortProp = System.getProperty("remotePort");
            try {
                remotePort = Integer.parseInt(
                    (remotePortProp != null && !remotePortProp.trim().isEmpty())
                        ? remotePortProp.trim() : GlobalSettings.getRemotePort());
            } catch (Exception ex) {}

            // Publish the (possibly overridden) remote to GlobalSettings BEFORE
            // local-IP detection, since getLocalIPAddress() routes to the remote
            // to pick the correct outbound interface.
            GlobalSettings.setRemoteIPAddr(remoteIP);
            GlobalSettings.setRemotePort(String.valueOf(remotePort));

            // Set fields directly via reflection to bypass blocking GUI popups
            System.out.println("Populating network fields...");
            String localIP = getLocalIPAddress();
            System.out.println("Detected Local IP: " + localIP);
            setPrivateField(frame, "localIPAddress", InetAddress.getByName(localIP));
            setPrivateField(frame, "localPort", localPort);
            setPrivateField(frame, "remoteIPAddress", InetAddress.getByName(remoteIP));
            setPrivateField(frame, "remotePort", remotePort);
            
            // Sync with GlobalSettings
            GlobalSettings.setLocalIP(localIP);
            GlobalSettings.setLocalPort(String.valueOf(localPort));
            GlobalSettings.setRemoteIPAddr(remoteIP);
            GlobalSettings.setRemotePort(String.valueOf(remotePort));
            GlobalSettings.setTransport("UDP");
            
            // Media RTP port is derived from the actual call scenario + values in
            // Phase 2 (see deriveMediaPort) so it matches whatever the scenario's
            // "m=audio [fieldN]" advertises. A mode-based default is set here only
            // to cover the registration phase.
            GlobalSettings.setMediaPort(String.valueOf("callee".equalsIgnoreCase(mode) ? 42000 : 42002));

            
            // --- Phase 1: Registration ---
            System.out.println("\n--- Phase 1: Registration ---");
            
            // Create a temp values file for registration
            String regValuesPath = "values" + File.separator + "temp_reg_" + username + ".txt";
            tempValFile = new File(regValuesPath);
            try (PrintWriter pw = new PrintWriter(new FileWriter(tempValFile))) {
                pw.println("// Temp registration values");
                pw.println(username + ";" + remoteIP + ";" + localPort);
            }
            
            System.out.println("Loading REGISTER scenario & values...");
            GlobalSettings.setSetting("scenarioPath", "scenarios" + File.separator + "Reg_client.txt");
            LoadScenario loadRegScen = new LoadScenario(frame, "scenarios" + File.separator + "Reg_client.txt");
            loadRegScen.execute();
            Thread.sleep(1000);

            
            GlobalSettings.setSetting("valuesPath", regValuesPath);
            frame.getValuesLoader().loadValuesFromFile(regValuesPath);
            frame.getValuesLoader().setCounter(0);
            frame.getValuesLoader().resetValuesInEachRow();
            
            // Open Socket programmatically
            System.out.println("Opening network socket...");
            Method openSocketMethod = SIPInspectorMainFrame.class.getDeclaredMethod("openSocket");
            openSocketMethod.setAccessible(true);
            boolean socketOk = (Boolean) openSocketMethod.invoke(frame);
            System.out.println("Socket open status: " + socketOk);
            
            if (!socketOk) {
                System.out.println("Failed to open socket! Exiting.");
                System.exit(1);
            }
            
            // Initialize progress dialog for REGISTER
            ScenarioProgressDialog regDialog = new ScenarioProgressDialog(frame);
            
            System.out.println("Spawning threads to REGISTER...");
            regDialog.spawnThreads();
            
            // Wait for registration exchange to complete
            System.out.println("Waiting 4 seconds for registration to complete...");
            Thread.sleep(4000);

            System.out.println("Stopping REGISTER threads...");
            regDialog.stopThreads();
            regDialog.dispose();
            
            // --- Phase 2: Call ---
            System.out.println("\n--- Phase 2: Call ---");
            
            String callScenarioPath = "";
            String callValuesPath = "";
            
            if ("callee".equalsIgnoreCase(mode)) {
                callScenarioPath = System.getProperty("scenarioPath");
                if (callScenarioPath == null || callScenarioPath.trim().isEmpty()) {
                    callScenarioPath = "scenarios" + File.separator + "Incoming_basic_call_generic.txt";
                }
                callValuesPath = System.getProperty("valuesPath");
                if (callValuesPath == null || callValuesPath.trim().isEmpty()) {
                    callValuesPath = "values" + File.separator + "callee_values.txt";
                }
            } else {
                callScenarioPath = System.getProperty("scenarioPath");
                if (callScenarioPath == null || callScenarioPath.trim().isEmpty()) {
                    callScenarioPath = "scenarios" + File.separator + "Outgoing_basic_call.txt";
                }
                callValuesPath = System.getProperty("valuesPath");
                if (callValuesPath == null || callValuesPath.trim().isEmpty()) {
                    callValuesPath = "values" + File.separator + "temp_caller_" + username + ".txt";
                    tempCallerValFile = new File(callValuesPath);
                    try (PrintWriter pw = new PrintWriter(new FileWriter(tempCallerValFile))) {
                        pw.println("// Temp caller values");
                        pw.println(username + ";" + calleeName + ";42002");
                    }
                }
            }
            
            System.out.println("Loading call scenario: " + callScenarioPath);
            System.out.println("Loading call values: " + callValuesPath);

            // Derive the RTP media port from the values field the scenario's
            // m=audio line actually references (field1 for incoming, field2 for
            // outgoing, etc.) so it matches the advertised SDP instead of a
            // hardcoded 42000/42002.
            int mediaPort = deriveMediaPort(callScenarioPath, callValuesPath, mode);
            GlobalSettings.setMediaPort(String.valueOf(mediaPort));
            System.out.println("Media RTP port set to: " + mediaPort + " (from scenario/values)");

            GlobalSettings.setSetting("scenarioPath", callScenarioPath);
            LoadScenario loadCallScen = new LoadScenario(frame, callScenarioPath);
            loadCallScen.execute();
            Thread.sleep(1000);
            
            GlobalSettings.setSetting("valuesPath", callValuesPath);
            frame.getValuesLoader().loadValuesFromFile(callValuesPath);
            frame.getValuesLoader().setCounter(0);
            frame.getValuesLoader().resetValuesInEachRow();
            
            // Initialize a new progress dialog for the CALL to avoid JTable out of bounds exceptions
            ScenarioProgressDialog callDialog = new ScenarioProgressDialog(frame);
            
            System.out.println("Spawning threads to execute CALL...");
            callDialog.spawnThreads();
            
            // Wait for call to establish and SDP negotiation
            System.out.println("Waiting for call establishment & SDP negotiation...");
            boolean established = false;
            for (int i = 0; i < 35; i++) {
                if (GlobalSettings.getSetting("RTP_Remote_Address") != null) {
                    established = true;
                    break;
                }
                Thread.sleep(1000);
            }
            if (!established) {
                System.out.println("Warning: SDP media target not negotiated within timeout.");
            }
            // Emit an explicit, machine-readable verdict marker on STDOUT for the
            // Campaigns runner. SIP signaling itself is written to a debug FILE
            // (TLSDebugLogger), never stdout, so the campaign runner cannot judge
            // success by scanning for "INVITE"/"200 OK" - it keys off this marker.
            // "established" == an SDP media target was negotiated == the call
            // reached the media-negotiation point (INVITE/200 OK/answer exchanged).
            if (established) {
                System.out.println("CAMPAIGN_RESULT: PASS (call established, media negotiated)");
            } else {
                System.out.println("CAMPAIGN_RESULT: FAIL (no SDP media negotiated within timeout)");
            }
            
            // --- Audio Recording Setup ---
            // Create recordings directory
            File recDir = new File(RECORDINGS_DIR);
            if (!recDir.exists()) recDir.mkdirs();
            
            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            String wavFileName = RECORDINGS_DIR + File.separator + "call_" + mode + "_" + timestamp + ".wav";
            
            System.out.println("[RECORDING] Setting up RTP audio recording...");
            System.out.println("[RECORDING] WAV output: " + wavFileName);
            
            recorder = new RtpWavRecorder(wavFileName);
            
            // Start Live Audio (handles actual SIP media flow)
            System.out.println("Starting Live Audio...");
            LiveAudioController audio = new LiveAudioController();
            boolean audioOk = audio.startAudio();
            System.out.println("Audio started status: " + audioOk);
            if (!audioOk) {
                System.err.println("Audio Error: " + audio.getLastError());
            }
            
            // Attach recorder to the receiver thread so it captures every received PCMA payload
            if (audioOk) {
                Field recvField = LiveAudioController.class.getDeclaredField("receiverThread");
                recvField.setAccessible(true);
                LiveAudioReceiverThread rxThread = (LiveAudioReceiverThread) recvField.get(audio);
                if (rxThread != null) {
                    rxThread.setRecorder(recorder);
                    System.out.println("[RECORDING] Recorder attached to RTP receiver - capturing incoming audio");
                }
            }
            
            // Stream audio for 10 seconds
            System.out.println("Streaming G.711 voice RTP for 10 seconds...");
            for (int i = 1; i <= 10; i++) {
                Thread.sleep(1000);
                System.out.print(".");
                System.out.flush();
                
                // Every 3 seconds, show live stats
                if (i % 3 == 0 && audioOk) {
                    java.util.Map<String, Object> stats = audio.getStats();
                    System.out.println();
                    System.out.println("[STATS] Sent=" + stats.get("packets_sent") 
                        + " Recv=" + stats.get("packets_received")
                        + " Dropped=" + stats.get("dropped_packets")
                        + " Jitter=" + stats.get("current_jitter_buffer_depth"));
                }
            }
            System.out.println();
            
            // Final stats
            if (audioOk) {
                java.util.Map<String, Object> finalStats = audio.getStats();
                System.out.println("\n[FINAL STATS] Packets Sent: " + finalStats.get("packets_sent"));
                System.out.println("[FINAL STATS] Packets Received: " + finalStats.get("packets_received"));
                System.out.println("[FINAL STATS] Auth Failures: " + finalStats.get("auth_failures"));
                System.out.println("[FINAL STATS] Dropped: " + finalStats.get("dropped_packets"));
                System.out.println("[FINAL STATS] Uptime: " + finalStats.get("uptime_seconds") + "s");
            }
            
            System.out.println("Stopping Live Audio...");
            audio.stopAudio();
            
            // Close recorder
            if (recorder != null) {
                recorder.close();
                recorder = null;
            }
            
            System.out.println("Waiting 3 seconds for call hangup...");
            Thread.sleep(3000);
            
            System.out.println("Stopping CALL threads...");
            callDialog.stopThreads();
            callDialog.dispose();

            frame.dispose();
            System.out.println("Test execution completed successfully.");

        } catch (Exception e) {
            System.err.println("Test encountered error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (tempValFile != null && tempValFile.exists()) {
                tempValFile.delete();
            }
            if (tempCallerValFile != null && tempCallerValFile.exists()) {
                tempCallerValFile.delete();
            }
            if (recorder != null) {
                try { recorder.close(); } catch (Exception e) {}
            }
            if (captureThread != null) {
                captureThread.stopCapture();
            }
            TLSDebugLogger.disable();
        }

        // This is a one-shot CLI harness, not the interactive GUI: disposing the
        // frame/dialogs above is not always enough to let the JVM exit on its own
        // (a lingering non-daemon AWT/Swing thread can keep the process alive
        // indefinitely after "Test execution completed successfully" prints).
        // All cleanup above has already run, so force a prompt, clean exit here.
        System.exit(0);
    }
    
    /**
     * Determine the RTP media port for a call: read the scenario's "m=audio &lt;port&gt;"
     * token, and if it is a "[fieldN]" placeholder, resolve field N from the
     * first data row of the values file. A literal numeric port is used as-is.
     * Falls back to the mode default (callee=42000, caller=42002) for
     * "[media_port]" or anything unresolvable.
     */
    private static int deriveMediaPort(String scenarioPath, String valuesPath, String mode) {
        int fallback = "callee".equalsIgnoreCase(mode) ? 42000 : 42002;
        try {
            String token = null;
            for (String line : java.nio.file.Files.readAllLines(new File(scenarioPath).toPath())) {
                int idx = line.toLowerCase().indexOf("m=audio ");
                if (idx >= 0) {
                    String[] parts = line.substring(idx + 8).trim().split("\\s+");
                    if (parts.length > 0) token = parts[0].trim();
                    break;
                }
            }
            if (token == null) {
                return fallback;
            }
            if (token.matches("\\d+")) {
                return Integer.parseInt(token); // literal port in the scenario
            }
            if (token.toLowerCase().startsWith("[field")) {
                int fieldIdx = Integer.parseInt(token.replaceAll("[^0-9]", ""));
                for (String line : java.nio.file.Files.readAllLines(new File(valuesPath).toPath())) {
                    String t = line.trim();
                    if (t.isEmpty() || t.startsWith("//") || t.startsWith("#")) {
                        continue;
                    }
                    String[] f = t.split(";");
                    if (fieldIdx < f.length && f[fieldIdx].trim().matches("\\d+")) {
                        return Integer.parseInt(f[fieldIdx].trim());
                    }
                    break; // only the first data row
                }
            }
            // "[media_port]" or unresolved -> keep the default (getMediaPort()).
        } catch (Exception ignore) {
        }
        return fallback;
    }

    private static void setPrivateField(Object obj, String fieldName, Object value) throws Exception {
        Field f = obj.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(obj, value);
    }

    private static String getLocalIPAddress() {
        String remoteIP = "192.168.0.70";
        int remotePort = 5060;
        try {
            String rip = GlobalSettings.getRemoteIPAddr();
            if (rip != null && !rip.trim().isEmpty()) {
                remoteIP = rip.trim();
            }
            String rport = GlobalSettings.getRemotePort();
            if (rport != null && !rport.trim().isEmpty()) {
                remotePort = Integer.parseInt(rport.trim());
            }
        } catch (Exception ignore) {}

        try {
            java.net.Socket socket = new java.net.Socket();
            socket.connect(new java.net.InetSocketAddress(remoteIP, remotePort), 1000);
            String ip = socket.getLocalAddress().getHostAddress();
            socket.close();
            return ip;
        } catch (Exception e) {
            // Could not reach the configured remote target (e.g. the real PBX under
            // test) to auto-detect the outbound-route local IP. Everything below is
            // a GUESS, not a detected address - surface that clearly so a campaign
            // run doesn't look like it addressed things correctly when it didn't.
            System.out.println("WARNING: Could not connect to configured remote " + remoteIP + ":" + remotePort
                    + " to auto-detect local IP (" + e.getMessage() + "). Falling back to a guessed local address -"
                    + " this leg's signaling may not reach the intended target.");
            try {
                String subnetPrefix = "";
                int lastDot = remoteIP.lastIndexOf('.');
                if (lastDot > 0) {
                    subnetPrefix = remoteIP.substring(0, lastDot + 1);
                }
                java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
                while (interfaces.hasMoreElements()) {
                    java.net.NetworkInterface iface = interfaces.nextElement();
                    if (iface.isLoopback() || !iface.isUp()) continue;
                    java.util.Enumeration<java.net.InetAddress> addresses = iface.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        java.net.InetAddress addr = addresses.nextElement();
                        if (addr instanceof java.net.Inet4Address) {
                            String ip = addr.getHostAddress();
                            if (subnetPrefix.isEmpty() || ip.startsWith(subnetPrefix)) {
                                System.out.println("WARNING: Using best-guess local IP " + ip + " (matched remote's /24 to a local NIC).");
                                return ip;
                            }
                        }
                    }
                }
            } catch (Exception ex) {}
        }
        String guessed;
        try {
            guessed = remoteIP.substring(0, remoteIP.lastIndexOf('.') + 1) + "8";
        } catch (Exception e) {
            guessed = "192.168.0.8";
        }
        System.out.println("WARNING: No local NIC matched the remote's subnet either - using last-resort guessed local IP "
                + guessed + ". If the configured remote (" + remoteIP + ":" + remotePort + ") is genuinely unreachable,"
                + " this leg's signaling will very likely fail or go nowhere.");
        return guessed;
    }
}
