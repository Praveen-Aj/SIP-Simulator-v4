package sipinspector.Socket;

import sipinspector.Utils.TLSDebugLogger;
import sipinspector.Utils.TransportDebugLogger;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Centralized SIP Transport Listener and Network Resource Manager.
 *
 * Coordinates socket binding, collision detection, and listener lifecycle
 * across UDP (RFC 3261), TCP (RFC 3261), and TLS (RFC 5630).
 * Prevents port collision when multiple UI clients or headless tasks
 * interact with the simulator.
 */
public class SipTransportManager {

    private static final SipTransportManager INSTANCE = new SipTransportManager();

    public enum ListenerState {
        STOPPED,
        STARTING,
        RUNNING,
        FAILED
    }

    public static class TransportStatus {
        public final String transport;
        public final String localIp;
        public final int localPort;
        public final ListenerState state;
        public final String lastError;

        public TransportStatus(String transport, String localIp, int localPort, ListenerState state, String lastError) {
            this.transport = transport;
            this.localIp = localIp;
            this.localPort = localPort;
            this.state = state;
            this.lastError = lastError;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("transport", transport);
            m.put("localIp", localIp);
            m.put("localPort", localPort);
            m.put("state", state.name());
            m.put("lastError", lastError);
            return m;
        }
    }

    public interface PacketListener {
        void onPacketReceived(byte[] data, int length, InetAddress remoteAddr, int remotePort, String transport);
    }

    // Active sockets registry
    private final Map<String, MySocketAbstract> activeSockets = new ConcurrentHashMap<>();
    private final Map<String, TransportStatus> transportStatuses = new ConcurrentHashMap<>();
    private final List<PacketListener> packetListeners = new CopyOnWriteArrayList<>();

    private SipTransportManager() {}

    public static SipTransportManager getInstance() {
        return INSTANCE;
    }

    public synchronized void registerPacketListener(PacketListener listener) {
        if (listener != null && !packetListeners.contains(listener)) {
            packetListeners.add(listener);
        }
    }

    public synchronized void unregisterPacketListener(PacketListener listener) {
        packetListeners.remove(listener);
    }

    public void notifyPacketReceived(byte[] data, int length, InetAddress remoteAddr, int remotePort, String transport) {
        for (PacketListener pl : packetListeners) {
            try {
                pl.onPacketReceived(data, length, remoteAddr, remotePort, transport);
            } catch (Exception e) {
                TLSDebugLogger.log("[TRANSPORT MGR] PacketListener error (" + transport + "): " + e.getMessage());
            }
        }
    }

    /**
     * Check if a transport port can be cleanly bound.
     */
    public PortCheckResult checkPort(String ip, int port, String transport) {
        boolean isUdp = "UDP".equalsIgnoreCase(transport);
        if (isUdp) {
            try (DatagramSocket ds = new DatagramSocket(new InetSocketAddress(InetAddress.getByName(ip), port))) {
                ds.setReuseAddress(true);
                return new PortCheckResult(true, "UDP port " + port + " is available on " + ip);
            } catch (Exception e) {
                return new PortCheckResult(false, "UDP port " + port + " is unavailable on " + ip + ": " + e.getMessage());
            }
        } else {
            try (ServerSocket ss = new ServerSocket(port, 50, InetAddress.getByName(ip))) {
                ss.setReuseAddress(true);
                return new PortCheckResult(true, transport + " port " + port + " is available on " + ip);
            } catch (Exception e) {
                return new PortCheckResult(false, transport + " port " + port + " is unavailable on " + ip + ": " + e.getMessage());
            }
        }
    }

    public static class PortCheckResult {
        public final boolean available;
        public final String message;

        public PortCheckResult(boolean available, String message) {
            this.available = available;
            this.message = message;
        }
    }

    public synchronized void registerSocket(String key, MySocketAbstract socket, String transport, String ip, int port) {
        if (activeSockets.containsKey(key)) {
            closeSocket(key);
        }
        activeSockets.put(key, socket);
        transportStatuses.put(key, new TransportStatus(transport, ip, port, ListenerState.RUNNING, null));
    }

    public synchronized MySocketAbstract getSocket(String key) {
        return activeSockets.get(key);
    }

    public synchronized MySocketAbstract getOrCreateTransport(String transport, String localIp, int localPort) throws IOException {
        String key = transport.toUpperCase() + ":" + localIp + ":" + localPort;
        MySocketAbstract existing = activeSockets.get(key);
        if (existing != null && existing.isOpen()) {
            return existing;
        }
        MySocketAbstract sock;
        if ("TCP".equalsIgnoreCase(transport)) {
            sock = new TCPClientSocket(InetAddress.getByName(localIp), localPort);
        } else if ("TLS".equalsIgnoreCase(transport)) {
            sock = new TLSClientSocket_V6(InetAddress.getByName(localIp), localPort);
        } else {
            sock = new UDPClientSocket(InetAddress.getByName(localIp), localPort);
        }
        registerSocket(key, sock, transport, localIp, localPort);
        return sock;
    }

    public synchronized void closeSocket(String key) {
        MySocketAbstract sock = activeSockets.remove(key);
        if (sock != null) {
            try {
                sock.close();
            } catch (Exception ignored) {}
            TransportStatus cur = transportStatuses.get(key);
            if (cur != null) {
                transportStatuses.put(key, new TransportStatus(cur.transport, cur.localIp, cur.localPort, ListenerState.STOPPED, null));
            }
        }
    }

    public synchronized void closeAll() {
        for (String k : new ArrayList<>(activeSockets.keySet())) {
            closeSocket(k);
        }
        activeSockets.clear();
    }

    public List<TransportStatus> getStatuses() {
        return new ArrayList<>(transportStatuses.values());
    }

    public boolean isAnyListenerRunning() {
        for (TransportStatus s : transportStatuses.values()) {
            if (s.state == ListenerState.RUNNING) return true;
        }
        return false;
    }
}
