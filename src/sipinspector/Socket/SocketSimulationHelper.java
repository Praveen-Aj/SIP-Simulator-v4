package sipinspector.Socket;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Random;
import sipinspector.GlobalSettings;
import sipinspector.MyPacket;
import sipinspector.Utils.TLSDebugLogger;

/**
 * SocketSimulationHelper extracts latency delay, packet loss drop, 
 * and header corruption emulation logic to be shared by all socket types.
 * 
 * @author SIP Support Team
 */
public class SocketSimulationHelper {
    private static final Random random = new Random();

    /**
     * Applies simulation delays and drops for incoming packets.
     * Returns true if the packet should be dropped, false otherwise.
     */
    public static boolean simulateIncoming(InetAddress sourceAddress, int sourcePort, String transportType) {
        // 1. Check loss
        int loss = 0;
        try {
            String lossSetting = GlobalSettings.getSetting("sim.loss");
            if (lossSetting != null) {
                loss = Integer.parseInt(lossSetting.trim());
            }
        } catch (Exception e) {}
        if (loss > 0) {
            if (random.nextInt(100) < loss) {
                if (TLSDebugLogger.isEnabled()) {
                    TLSDebugLogger.log("[SIMULATION] Dropped incoming " + transportType + " packet (loss=" + loss + "%) from " + sourceAddress + ":" + sourcePort);
                }
                return true; // drop
            }
        }

        // 2. Check delay
        int delay = 0;
        try {
            String delaySetting = GlobalSettings.getSetting("sim.delay");
            if (delaySetting != null) {
                delay = Integer.parseInt(delaySetting.trim());
            }
        } catch (Exception e) {}
        if (delay > 0) {
            try {
                if (TLSDebugLogger.isEnabled()) {
                    TLSDebugLogger.log("[SIMULATION] Delaying incoming " + transportType + " packet by " + delay + "ms");
                }
                Thread.sleep(delay);
            } catch (InterruptedException ignore) {}
        }
        return false; // do not drop
    }

    /**
     * Applies corruption to outgoing packets if needed.
     */
    public static MyPacket corruptPacketIfNeeded(MyPacket original) {
        String malformedType = GlobalSettings.getSetting("sim.malformed");
        if (malformedType == null || malformedType.equalsIgnoreCase("None")) {
            return original;
        }
        
        String text = original.getMessageTxt();
        if (text == null || text.isEmpty()) {
            return original;
        }
        
        String corruptedText = text;
        if (malformedType.equalsIgnoreCase("CSeq")) {
            corruptedText = text.replaceAll("(?i)CSeq:\\s*\\d+", "CSeq: -9999");
        } else if (malformedType.equalsIgnoreCase("Call-ID")) {
            corruptedText = text.replaceAll("(?i)Call-ID:\\s*[^\\r\\n]+", "Call-ID: CORRUPTED-CALLID-12345");
        } else if (malformedType.equalsIgnoreCase("Via")) {
            corruptedText = text.replaceAll("(?i)branch=[^;\\r\\n\\s]+", "branch=z9hG4bK-CORRUPTED");
        } else if (malformedType.equalsIgnoreCase("Expires")) {
            corruptedText = text.replaceAll("(?i)Expires:\\s*\\d+", "Expires: invalid_number");
        }
        
        if (!corruptedText.equals(text)) {
            MyPacket corrupted = new MyPacket(corruptedText);
            corrupted.setAddress(original.getAddress());
            corrupted.setPort(original.getPort());
            return corrupted;
        }
        return original;
    }

    /**
     * Applies simulation delays and drops for outgoing packets.
     * Returns true if the packet should be dropped (not sent), false otherwise.
     */
    public static boolean simulateOutgoing(InetAddress destAddress, int destPort, String transportType) {
        // 1. Check delay
        int delay = 0;
        try {
            String delaySetting = GlobalSettings.getSetting("sim.delay");
            if (delaySetting != null) {
                delay = Integer.parseInt(delaySetting.trim());
            }
        } catch (Exception e) {}
        if (delay > 0) {
            try {
                if (TLSDebugLogger.isEnabled()) {
                    TLSDebugLogger.log("[SIMULATION] Delaying outgoing " + transportType + " packet by " + delay + "ms");
                }
                Thread.sleep(delay);
            } catch (InterruptedException ignore) {}
        }

        // 2. Check loss
        int loss = 0;
        try {
            String lossSetting = GlobalSettings.getSetting("sim.loss");
            if (lossSetting != null) {
                loss = Integer.parseInt(lossSetting.trim());
            }
        } catch (Exception e) {}
        if (loss > 0) {
            if (random.nextInt(100) < loss) {
                if (TLSDebugLogger.isEnabled()) {
                    TLSDebugLogger.log("[SIMULATION] Dropped outgoing " + transportType + " packet (loss=" + loss + "%) to " + destAddress + ":" + destPort);
                }
                return true; // drop
            }
        }
        return false; // do not drop
    }
}
