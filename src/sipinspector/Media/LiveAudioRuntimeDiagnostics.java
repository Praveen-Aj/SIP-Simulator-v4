package sipinspector.Media;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import sipinspector.Utils.TLSDebugLogger;

/**
 * Lightweight runtime diagnostics for Live Audio validation/soak runs.
 */
public final class LiveAudioRuntimeDiagnostics {

    private static final AtomicInteger activeSenders = new AtomicInteger(0);
    private static final AtomicInteger activeReceivers = new AtomicInteger(0);
    private static final AtomicInteger openSockets = new AtomicInteger(0);
    private static final AtomicInteger openMicLines = new AtomicInteger(0);
    private static final AtomicInteger openSpeakerLines = new AtomicInteger(0);
    private static final AtomicLong controllerStartCount = new AtomicLong(0);
    private static final AtomicLong controllerStopCount = new AtomicLong(0);

    private static volatile long lastSnapshotMs = 0L;
    private static final long SNAPSHOT_INTERVAL_MS = 5000L;

    private LiveAudioRuntimeDiagnostics() {}

    public static boolean enabled() {
        if (TLSDebugLogger.isEnabled()) {
            return true;
        }
        String prop = System.getProperty("liveaudio.validation");
        return "true".equalsIgnoreCase(prop);
    }

    public static void onControllerStart() {
        controllerStartCount.incrementAndGet();
        maybeLogSnapshot("controller_start");
    }

    public static void onControllerStop() {
        controllerStopCount.incrementAndGet();
        maybeLogSnapshot("controller_stop");
    }

    public static void onSocketOpened() {
        openSockets.incrementAndGet();
        maybeLogSnapshot("socket_open");
    }

    public static void onSocketClosed() {
        int v = openSockets.decrementAndGet();
        if (v < 0) {
            openSockets.set(0);
        }
        maybeLogSnapshot("socket_close");
    }

    public static void onSenderStarted() {
        activeSenders.incrementAndGet();
        maybeLogSnapshot("sender_start");
    }

    public static void onSenderStopped() {
        int v = activeSenders.decrementAndGet();
        if (v < 0) {
            activeSenders.set(0);
        }
        maybeLogSnapshot("sender_stop");
    }

    public static void onReceiverStarted() {
        activeReceivers.incrementAndGet();
        maybeLogSnapshot("receiver_start");
    }

    public static void onReceiverStopped() {
        int v = activeReceivers.decrementAndGet();
        if (v < 0) {
            activeReceivers.set(0);
        }
        maybeLogSnapshot("receiver_stop");
    }

    public static void onMicLineOpened() {
        openMicLines.incrementAndGet();
        maybeLogSnapshot("mic_open");
    }

    public static void onMicLineClosed() {
        int v = openMicLines.decrementAndGet();
        if (v < 0) {
            openMicLines.set(0);
        }
        maybeLogSnapshot("mic_close");
    }

    public static void onSpeakerLineOpened() {
        openSpeakerLines.incrementAndGet();
        maybeLogSnapshot("spk_open");
    }

    public static void onSpeakerLineClosed() {
        int v = openSpeakerLines.decrementAndGet();
        if (v < 0) {
            openSpeakerLines.set(0);
        }
        maybeLogSnapshot("spk_close");
    }

    public static int getActiveSenders() { return activeSenders.get(); }
    public static int getActiveReceivers() { return activeReceivers.get(); }
    public static int getOpenSockets() { return openSockets.get(); }
    public static int getOpenMicLines() { return openMicLines.get(); }
    public static int getOpenSpeakerLines() { return openSpeakerLines.get(); }
    public static long getControllerStartCount() { return controllerStartCount.get(); }
    public static long getControllerStopCount() { return controllerStopCount.get(); }

    public static String snapshotString() {
        return String.format(
            "activeSenders=%d activeReceivers=%d openSockets=%d openMicLines=%d openSpeakerLines=%d controllerStarts=%d controllerStops=%d",
            getActiveSenders(),
            getActiveReceivers(),
            getOpenSockets(),
            getOpenMicLines(),
            getOpenSpeakerLines(),
            getControllerStartCount(),
            getControllerStopCount()
        );
    }

    public static void maybeLogSnapshot(String reason) {
        if (!enabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastSnapshotMs < SNAPSHOT_INTERVAL_MS && !"controller_stop".equals(reason)) {
            return;
        }
        lastSnapshotMs = now;
        TLSDebugLogger.log("[LiveAudioDiag] reason=" + reason + " " + snapshotString());
    }
}

