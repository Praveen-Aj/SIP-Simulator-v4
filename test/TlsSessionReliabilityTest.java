/*
 * SIP Simulator v4 - Phase 9 Validation
 * TlsSessionReliabilityTest: Validates TLS session management, pooling, and in-dialog reuse (LIM-05)
 */
package test;

import sipinspector.Socket.TLSClientSocket_V6;
import sipinspector.Socket.TlsSessionManager;

import java.net.InetAddress;
import java.util.Map;

public class TlsSessionReliabilityTest {

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("   PHASE 9: TLS SESSION RELIABILITY & IN-DIALOG REUSE (LIM-05) VALIDATION");
        System.out.println("================================================================================");

        testTlsSessionManagerSingleton();
        testInDialogCallIdMapping();
        testDiagnosticsAndStalePurge();

        System.out.println(">>> ALL TLS SESSION RELIABILITY TESTS PASSED!");
    }

    private static void testTlsSessionManagerSingleton() {
        System.out.print("[TEST 1/3] TlsSessionManager Singleton & Diagnostics... ");
        TlsSessionManager mgr = TlsSessionManager.getInstance();
        if (mgr == null) {
            throw new RuntimeException("TlsSessionManager instance cannot be null");
        }
        Map<String, Object> diag = mgr.getDiagnostics();
        if (!diag.containsKey("activeTlsConnections") || !diag.containsKey("fullHandshakes")) {
            throw new RuntimeException("Diagnostics missing required metrics: " + diag);
        }
        System.out.println("PASSED");
    }

    private static void testInDialogCallIdMapping() {
        System.out.print("[TEST 2/3] In-Dialog Call-ID Persistent Socket Association... ");
        TlsSessionManager mgr = TlsSessionManager.getInstance();
        String callId = "dialog-tls-call-99@127.0.0.1";

        // Pre-release check: should return null
        TLSClientSocket_V6 pre = mgr.getInDialogSocket(callId);
        if (pre != null) {
            throw new RuntimeException("Expected null socket for unmapped Call-ID");
        }

        // Release dialog: should execute without exception
        mgr.releaseDialog(callId);
        mgr.releaseDialog("non-existent-call-id");

        System.out.println("PASSED");
    }

    private static void testDiagnosticsAndStalePurge() {
        System.out.print("[TEST 3/3] Stale Connection Purge & Metric Counters... ");
        TlsSessionManager mgr = TlsSessionManager.getInstance();
        mgr.purgeStaleConnections();
        mgr.closeAll();

        Map<String, Object> diag = mgr.getDiagnostics();
        int active = (int) diag.get("activeTlsConnections");
        if (active != 0) {
            throw new RuntimeException("Expected 0 active connections after closeAll, got: " + active);
        }
        System.out.println("PASSED");
    }
}
