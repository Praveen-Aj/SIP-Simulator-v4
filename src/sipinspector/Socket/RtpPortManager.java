package sipinspector.Socket;

import sipinspector.GlobalSettings;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe RTP and RTCP Port Allocation and Lifetime Manager.
 *
 * Allocates even-numbered UDP ports for RTP media streams and reserves the
 * corresponding odd-numbered port (port + 1) for RTCP control telemetry.
 * Prevents port collisions in multi-call concurrent campaigns.
 */
public class RtpPortManager {

    private static final RtpPortManager INSTANCE = new RtpPortManager();

    private int minPort = 42000;
    private int maxPort = 50000;
    private final AtomicInteger nextPort = new AtomicInteger(minPort);

    // Map of Call-ID -> allocated RTP port
    private final Map<String, Integer> allocatedPorts = new ConcurrentHashMap<>();
    // Set of currently active reserved ports
    private final Set<Integer> activePorts = Collections.newSetFromMap(new ConcurrentHashMap<Integer, Boolean>());

    private RtpPortManager() {
        loadRangeFromSettings();
    }

    public static RtpPortManager getInstance() {
        return INSTANCE;
    }

    public synchronized void setPortRange(int min, int max) {
        if (min < 1024 || max > 65534 || min >= max) {
            throw new IllegalArgumentException("Invalid RTP port range: " + min + " - " + max);
        }
        if (min % 2 != 0) min++; // Ensure even start port
        this.minPort = min;
        this.maxPort = max;
        this.nextPort.set(minPort);
    }

    public void loadRangeFromSettings() {
        try {
            String sStart = GlobalSettings.getSetting("rtp.portRangeStart");
            String sEnd = GlobalSettings.getSetting("rtp.portRangeEnd");
            int start = (sStart != null && !sStart.isEmpty()) ? Integer.parseInt(sStart) : 42000;
            int end = (sEnd != null && !sEnd.isEmpty()) ? Integer.parseInt(sEnd) : 50000;
            if (start > 1024 && end > start && end <= 65534) {
                setPortRange(start, end);
            }
        } catch (Exception ignored) {
            // Use defaults
        }
    }

    /**
     * Allocate an available even-numbered RTP port without binding to a Call-ID.
     */
    public synchronized int allocateRtpPort() {
        return allocatePort(null);
    }

    /**
     * Release a specific RTP port.
     */
    public synchronized void releaseRtpPort(int port) {
        releaseExplicitPort(port);
    }

    /**
     * Allocate an available even-numbered RTP port for the specified Call-ID.
     */
    public synchronized int allocatePort(String callId) {
        if (callId != null && allocatedPorts.containsKey(callId)) {
            return allocatedPorts.get(callId);
        }

        int rangeSize = (maxPort - minPort) / 2;
        for (int i = 0; i < rangeSize; i++) {
            int port = nextPort.getAndAdd(2);
            if (port > maxPort - 2) {
                nextPort.set(minPort);
                port = minPort;
            }

            if (!activePorts.contains(port) && !activePorts.contains(port + 1) && isUdpPortAvailable(port) && isUdpPortAvailable(port + 1)) {
                activePorts.add(port);
                activePorts.add(port + 1); // RTCP port
                if (callId != null) {
                    allocatedPorts.put(callId, port);
                }
                return port;
            }
        }

        throw new IllegalStateException("RTP port pool exhausted in range " + minPort + " - " + maxPort);
    }

    /**
     * Release ports associated with a Call-ID.
     */
    public synchronized void releasePort(String callId) {
        if (callId == null) return;
        Integer port = allocatedPorts.remove(callId);
        if (port != null) {
            activePorts.remove(port);
            activePorts.remove(port + 1);
        }
    }

    /**
     * Release a specific port explicitly.
     */
    public synchronized void releaseExplicitPort(int port) {
        activePorts.remove(port);
        activePorts.remove(port + 1);
        allocatedPorts.values().remove(port);
    }

    public synchronized boolean isPortAllocated(int port) {
        return activePorts.contains(port);
    }

    public synchronized int getActiveAllocationCount() {
        return allocatedPorts.size();
    }

    public synchronized void reset() {
        allocatedPorts.clear();
        activePorts.clear();
        nextPort.set(minPort);
    }

    public static boolean isUdpPortAvailable(int port) {
        try (DatagramSocket ds = new DatagramSocket(port)) {
            ds.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean isTcpPortAvailable(int port) {
        try (ServerSocket ss = new ServerSocket(port)) {
            ss.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public int getMinPort() { return minPort; }
    public int getMaxPort() { return maxPort; }
    public int getPortRangeStart() { return minPort; }
    public int getPortRangeEnd() { return maxPort; }
    public synchronized int getActiveAllocatedCount() { return activePorts.size(); }
    public synchronized List<Integer> getAllocatedPorts() { return new ArrayList<>(activePorts); }
}
