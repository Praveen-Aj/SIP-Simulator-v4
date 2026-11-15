package sipinspector.Socket;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Random;
import sipinspector.GlobalSettings;
import sipinspector.MyPacket;
import sipinspector.Utils.TLSDebugLogger;

/**
 * SocketSimulationHelper extracts latency delay, packet loss drop,
 * jitter emulation, and header corruption injection logic.
 *
 * Implements Workstream B of Phase 8.
 * Compatible with Java 8.
 *
 * @author SIP Support Team
 */
public class SocketSimulationHelper {
    private static final Random random = new Random();

    public static boolean isImpairmentEnabled() {
        String enabled = GlobalSettings.getSetting("sim.enabled");
        if (enabled != null) {
            return "true".equalsIgnoreCase(enabled.trim());
        }
        // Fallback: enabled if loss or delay > 0 or malformed != None
        int loss = getLossPercent();
        int delay = getDelayMs();
        String mal = GlobalSettings.getSetting("sim.malformed");
        return loss > 0 || delay > 0 || (mal != null && !mal.equalsIgnoreCase("None"));
    }

    public static int getDelayMs() {
        try {
            String val = GlobalSettings.getSetting("sim.delay");
            return val != null ? Math.max(0, Integer.parseInt(val.trim())) : 0;
        } catch (Exception e) { return 0; }
    }

    public static int getLossPercent() {
        try {
            String val = GlobalSettings.getSetting("sim.loss");
            return val != null ? Math.max(0, Math.min(100, Integer.parseInt(val.trim()))) : 0;
        } catch (Exception e) { return 0; }
    }

    public static int getJitterMs() {
        try {
            String val = GlobalSettings.getSetting("sim.jitter");
            return val != null ? Math.max(0, Integer.parseInt(val.trim())) : 0;
        } catch (Exception e) { return 0; }
    }

    public static boolean isAffectMedia() {
        String val = GlobalSettings.getSetting("sim.affectMedia");
        return val != null && "true".equalsIgnoreCase(val.trim());
    }

    /**
     * Applies simulation delays and drops for incoming packets.
     * Returns true if the packet should be dropped, false otherwise.
     */
    public static boolean simulateIncoming(InetAddress sourceAddress, int sourcePort, String transportType) {
        if (!isImpairmentEnabled()) return false;

        // 1. Check loss
        int loss = getLossPercent();
        if (loss > 0) {
            if (random.nextInt(100) < loss) {
                if (TLSDebugLogger.isEnabled()) {
                    TLSDebugLogger.log("[SIMULATION] Dropped incoming " + transportType + " packet (loss=" + loss + "%) from " + sourceAddress + ":" + sourcePort);
                }
                return true; // drop
            }
        }

        // 2. Check delay & jitter
        int delay = getDelayMs();
        int jitter = getJitterMs();
        int totalDelay = delay + (jitter > 0 ? random.nextInt(jitter + 1) : 0);
        if (totalDelay > 0) {
            try {
                if (TLSDebugLogger.isEnabled()) {
                    TLSDebugLogger.log("[SIMULATION] Delaying incoming " + transportType + " packet by " + totalDelay + "ms (delay=" + delay + "ms, jitter=" + jitter + "ms)");
                }
                Thread.sleep(totalDelay);
            } catch (InterruptedException ignore) {}
        }
        return false; // do not drop
    }

    /**
     * Applies corruption to outgoing packets if needed.
     */
    public static MyPacket corruptPacketIfNeeded(MyPacket original) {
        if (!isImpairmentEnabled()) return original;

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
        } else if (malformedType.equalsIgnoreCase("Content-Length")) {
            corruptedText = text.replaceAll("(?i)Content-Length:\\s*\\d+", "Content-Length: -1");
        } else if (malformedType.equalsIgnoreCase("Max-Forwards")) {
            corruptedText = text.replaceAll("(?i)Max-Forwards:\\s*\\d+", "Max-Forwards: 0");
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
        if (!isImpairmentEnabled()) return false;

        // 1. Check delay & jitter
        int delay = getDelayMs();
        int jitter = getJitterMs();
        int totalDelay = delay + (jitter > 0 ? random.nextInt(jitter + 1) : 0);
        if (totalDelay > 0) {
            try {
                if (TLSDebugLogger.isEnabled()) {
                    TLSDebugLogger.log("[SIMULATION] Delaying outgoing " + transportType + " packet by " + totalDelay + "ms");
                }
                Thread.sleep(totalDelay);
            } catch (InterruptedException ignore) {}
        }

        // 2. Check loss
        int loss = getLossPercent();
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

    /**
     * Applies impairment to outgoing RTP media packets if media impairment is enabled.
     * Returns true if the RTP packet should be dropped, false otherwise.
     */
    public static boolean simulateMediaOutgoing(InetAddress destAddress, int destPort) {
        if (!isImpairmentEnabled() || !isAffectMedia()) return false;

        int loss = getLossPercent();
        if (loss > 0 && random.nextInt(100) < loss) {
            return true; // drop RTP packet
        }

        int delay = getDelayMs();
        int jitter = getJitterMs();
        int totalDelay = delay + (jitter > 0 ? random.nextInt(jitter + 1) : 0);
        if (totalDelay > 0) {
            try {
                Thread.sleep(Math.min(totalDelay, 100)); // cap media delay per packet to avoid audio thread stalls
            } catch (InterruptedException ignore) {}
        }
        return false;
    }
}
