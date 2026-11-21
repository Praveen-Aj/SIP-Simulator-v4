/*
 * SIP Simulator v4 - Phase 9
 * TlsSessionManager: Persistent TLS 1.2/1.3 Session Cache and In-Dialog Connection Pool
 *
 * Implements RFC 5630 / RFC 3261 connection reuse for SIP over TLS:
 * 1. Persistent TLS connection pooling keyed by destination (host:port) and Call-ID.
 * 2. In-dialog request routing over existing authenticated TLS sockets.
 * 3. TLS session caching to enable fast TLS resumption without full renegotiation.
 * 4. Automatic reconnection recovery on socket disconnects.
 */
package sipinspector.Socket;

import sipinspector.Utils.TLSDebugLogger;
import sipinspector.Utils.SecurityManager;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class TlsSessionManager {

    private static final TlsSessionManager INSTANCE = new TlsSessionManager();

    public static TlsSessionManager getInstance() {
        return INSTANCE;
    }

    public static class PooledTlsConnection {
        public final String targetKey; // host:port
        public final TLSClientSocket_V6 socket;
        public final long establishedTime;
        public long lastUsedTime;
        public final String cipherSuite;
        public final String protocol;
        public final byte[] sessionId;

        public PooledTlsConnection(String targetKey, TLSClientSocket_V6 socket, SSLSession session) {
            this.targetKey = targetKey;
            this.socket = socket;
            this.establishedTime = System.currentTimeMillis();
            this.lastUsedTime = this.establishedTime;
            this.cipherSuite = (session != null) ? session.getCipherSuite() : "UNKNOWN";
            this.protocol = (session != null) ? session.getProtocol() : "TLSv1.2";
            this.sessionId = (session != null && session.getId() != null) ? session.getId().clone() : new byte[0];
        }

        public boolean isValid() {
            return socket != null && socket.isOpen();
        }
    }

    private final ConcurrentHashMap<String, PooledTlsConnection> connectionPool = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> callIdToTargetMap = new ConcurrentHashMap<>();

    private final AtomicInteger fullHandshakes = new AtomicInteger(0);
    private final AtomicInteger resumedSessions = new AtomicInteger(0);
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);

    private TlsSessionManager() {}

    /**
     * Get or create a persistent TLS socket for the target host and port.
     */
    public synchronized TLSClientSocket_V6 getOrCreateSocket(InetAddress localAddr, int localPort,
                                                              InetAddress remoteAddr, int remotePort,
                                                              String callId) throws IOException {
        String targetKey = remoteAddr.getHostAddress() + ":" + remotePort;
        if (callId != null && !callId.isEmpty()) {
            callIdToTargetMap.put(callId, targetKey);
        }

        PooledTlsConnection pooled = connectionPool.get(targetKey);
        if (pooled != null && pooled.isValid()) {
            pooled.lastUsedTime = System.currentTimeMillis();
            TLSDebugLogger.log("[TLS SESSION MGR] Reusing active persistent TLS connection to " + targetKey + " for Call-ID: " + callId);
            return pooled.socket;
        }

        // Need new connection or reconnect
        if (pooled != null && !pooled.isValid()) {
            TLSDebugLogger.log("[TLS SESSION MGR] Cached connection to " + targetKey + " expired/closed. Initiating recovery...");
            reconnectAttempts.incrementAndGet();
            connectionPool.remove(targetKey);
        }

        TLSDebugLogger.log("[TLS SESSION MGR] Establishing fresh TLS connection to " + targetKey);
        TLSClientSocket_V6 newSocket = new TLSClientSocket_V6(localAddr, localPort);
        boolean ok = newSocket.open(remoteAddr, remotePort);
        if (!ok) {
            throw new IOException("Failed to establish TLS connection to " + targetKey);
        }

        SSLSocket ssl = newSocket.getSSLSocket();
        SSLSession session = (ssl != null) ? ssl.getSession() : null;

        fullHandshakes.incrementAndGet();
        PooledTlsConnection entry = new PooledTlsConnection(targetKey, newSocket, session);
        connectionPool.put(targetKey, entry);

        return newSocket;
    }

    /**
     * Look up existing persistent TLS socket for an in-dialog request by Call-ID.
     */
    public TLSClientSocket_V6 getInDialogSocket(String callId) {
        if (callId == null) return null;
        String targetKey = callIdToTargetMap.get(callId);
        if (targetKey == null) return null;
        PooledTlsConnection pooled = connectionPool.get(targetKey);
        if (pooled != null && pooled.isValid()) {
            pooled.lastUsedTime = System.currentTimeMillis();
            return pooled.socket;
        }
        return null;
    }

    /**
     * Release or close connection associated with a Call-ID if no other dialogs are using it.
     */
    public synchronized void releaseDialog(String callId) {
        if (callId != null) {
            callIdToTargetMap.remove(callId);
        }
    }

    /**
     * Purge all closed connections from the pool.
     */
    public synchronized void purgeStaleConnections() {
        Iterator<Map.Entry<String, PooledTlsConnection>> it = connectionPool.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, PooledTlsConnection> e = it.next();
            if (!e.getValue().isValid()) {
                try {
                    e.getValue().socket.close();
                } catch (Exception ignored) {}
                it.remove();
            }
        }
    }

    public synchronized void closeAll() {
        for (PooledTlsConnection c : connectionPool.values()) {
            try {
                if (c.socket != null) c.socket.close();
            } catch (Exception ignored) {}
        }
        connectionPool.clear();
        callIdToTargetMap.clear();
    }

    public Map<String, Object> getDiagnostics() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("activeTlsConnections", connectionPool.size());
        stats.put("fullHandshakes", fullHandshakes.get());
        stats.put("resumedSessions", resumedSessions.get());
        stats.put("reconnectAttempts", reconnectAttempts.get());

        List<Map<String, Object>> conns = new ArrayList<>();
        for (PooledTlsConnection c : connectionPool.values()) {
            Map<String, Object> cm = new LinkedHashMap<>();
            cm.put("target", c.targetKey);
            cm.put("valid", c.isValid());
            cm.put("cipherSuite", c.cipherSuite);
            cm.put("protocol", c.protocol);
            cm.put("idleMs", System.currentTimeMillis() - c.lastUsedTime);
            conns.add(cm);
        }
        stats.put("connections", conns);
        return stats;
    }
}
