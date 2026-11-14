package sipinspector.Media;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import sipinspector.GlobalSettings;
import sipinspector.SIPInspectorMainFrame;
import sipinspector.ScenarioProgressDialog;
import sipinspector.Utils.TLSDebugLogger;

public class CliSipRegisterTest {

    public static void main(String[] args) {
        System.out.println("=== SIP Simulator Cli Register Test ===");
        File tempValFile = null;
        try {
            // Enable UDP debug logging
            TLSDebugLogger.enable("UDP");
            
            // Instantiate Main Frame
            System.out.println("Initializing SIPInspectorMainFrame...");
            SIPInspectorMainFrame frame = new SIPInspectorMainFrame();
            
            // Wait for LoadScenario SwingWorker to finish loading files
            System.out.println("Waiting for scenario/values files to load...");
            Thread.sleep(2000);
            
            // Extract settings dynamically. Prefer a per-test PBX override passed
            // by the Campaigns runner via -DremoteIP / -DremotePort.
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
            String localPortStr = GlobalSettings.getLocalPort();
            int localPort = 5060;
            try {
                localPort = Integer.parseInt(localPortStr);
            } catch (Exception ex) {}
            String username = GlobalSettings.getUsername();
            
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
            
            // Load the chosen REGISTER scenario. The Campaigns runner passes the
            // test case's caller scenario via -DscenarioPath; honoring it means a
            // "register" test uses ITS configured scenario/values instead of a
            // hardcoded Reg_client.txt + a username-synthesized values file.
            String regScenarioPath = System.getProperty("scenarioPath");
            if (regScenarioPath == null || regScenarioPath.trim().isEmpty()) {
                regScenarioPath = "scenarios" + File.separator + "Reg_client.txt";
            } else {
                regScenarioPath = regScenarioPath.trim();
            }
            System.out.println("Loading REGISTER scenario: " + regScenarioPath);
            GlobalSettings.setSetting("scenarioPath", regScenarioPath);
            new sipinspector.Utils.LoadScenario(frame, regScenarioPath).execute();
            Thread.sleep(1000);

            // Values: prefer the campaign-provided values file (-DvaluesPath),
            // else synthesize a minimal one from the registering identity.
            String regValuesPath = System.getProperty("valuesPath");
            if (regValuesPath != null && !regValuesPath.trim().isEmpty()) {
                regValuesPath = regValuesPath.trim();
                System.out.println("Loading REGISTER values: " + regValuesPath);
            } else {
                regValuesPath = "values" + File.separator + "temp_reg_" + username + ".txt";
                tempValFile = new File(regValuesPath);
                try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(tempValFile))) {
                    pw.println("// Temp registration values");
                    pw.println(username + ";" + remoteIP + ";" + localPort);
                }
                System.out.println("Loading temporary dynamic values: " + regValuesPath);
            }
            GlobalSettings.setSetting("valuesPath", regValuesPath);
            frame.getValuesLoader().loadValuesFromFile(regValuesPath);
            frame.getValuesLoader().setCounter(0);
            frame.getValuesLoader().resetValuesInEachRow();
            
            // Log active settings
            System.out.println("Active Settings:");
            System.out.println("Local IP: " + GlobalSettings.getLocalIP());
            System.out.println("Local Port: " + GlobalSettings.getLocalPort());
            System.out.println("Remote IP: " + GlobalSettings.getRemoteIPAddr());
            System.out.println("Remote Port: " + GlobalSettings.getRemotePort());
            System.out.println("Transport: " + GlobalSettings.getTransport());
            System.out.println("Scenario: " + GlobalSettings.getSetting("scenarioPath"));
            System.out.println("Values: " + GlobalSettings.getSetting("valuesPath"));
            
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
            
            // Initialize progress dialog and start threads
            System.out.println("Initializing ScenarioProgressDialog...");
            ScenarioProgressDialog dialog = new ScenarioProgressDialog(frame);
            
            System.out.println("Spawning threads to execute scenario...");
            dialog.spawnThreads();
            
            // Let the registration scenario run for 10 seconds
            System.out.println("Running REGISTER scenario for 10 seconds...");
            for (int i = 1; i <= 10; i++) {
                Thread.sleep(1000);
                System.out.print(".");
                System.out.flush();
            }
            System.out.println();
            
            // Emit an explicit, machine-readable verdict marker on STDOUT for the
            // Campaigns runner. SIP signaling goes to a debug FILE, not stdout, so
            // the runner keys success off this marker rather than scanning stdout
            // for SIP text (which never appears there). Registration success is
            // tracked authoritatively by SIPStateTracker off the actual REGISTER
            // 200 OK response seen on the socket.
            String regState = sipinspector.Utils.SIPStateTracker.getRegistrationState();
            if (regState != null && regState.startsWith("Registered")) {
                System.out.println("CAMPAIGN_RESULT: PASS (registered: " + regState + ")");
            } else {
                System.out.println("CAMPAIGN_RESULT: FAIL (registration state: " + regState + ")");
            }

            // Stop threads and cleanup
            System.out.println("Stopping threads...");
            dialog.stopThreads();
            frame.dispose();

            System.out.println("Test execution completed.");
            
        } catch (Exception e) {
            System.err.println("Test encountered error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (tempValFile != null && tempValFile.exists()) {
                tempValFile.delete();
            }
            TLSDebugLogger.disable();
        }
    }
    
    private static void setPrivateField(Object obj, String fieldName, Object value) throws Exception {
        Field f = obj.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(obj, value);
    }
    
    private static Object getPrivateField(Object obj, String fieldName) throws Exception {
        Field f = obj.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        return f.get(obj);
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
                                return ip;
                            }
                        }
                    }
                }
            } catch (Exception ex) {}
        }
        try {
            return remoteIP.substring(0, remoteIP.lastIndexOf('.') + 1) + "8";
        } catch (Exception e) {
            return "192.168.0.8";
        }
    }
}
