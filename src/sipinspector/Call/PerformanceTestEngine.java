package sipinspector.Call;

import sipinspector.Media.CallState;
import sipinspector.Media.LiveAudioController;
import sipinspector.Utils.AppPaths;
import sipinspector.Utils.TLSDebugLogger;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * High-precision SIP Performance & Multi-Call Orchestration Test Engine.
 *
 * Supports:
 * - Single-call validation
 * - Sequential call campaigns
 * - Concurrent multi-dialog call execution
 * - Rate-controlled call generation (Calls Per Second - CPS)
 * - Long-duration soak/stability campaigns with periodic resource & media profiling
 * - ITU-T E-model G.107 MOS score aggregation
 * - Resource leak verification (JVM memory, active threads, media sockets)
 */
public class PerformanceTestEngine {

    public enum ExecutionMode {
        SINGLE_CALL,
        SEQUENTIAL,
        CONCURRENT,
        RATE_CONTROLLED,
        LONG_DURATION
    }

    public static class CampaignConfig {
        public String name = "Default Campaign";
        public ExecutionMode mode = ExecutionMode.SEQUENTIAL;
        public String caller = "90008";
        public String callee = "90001";
        public int totalCalls = 5;
        public int maxConcurrent = 2;
        public double callsPerSecond = 1.0;
        public int durationSeconds = 5;
        public int delayBetweenCallsMs = 500;
        public int payloadType = 8;
        public boolean autoAnswer = false;
        public String autoAnswerPipe = "\\\\.\\pipe\\microsip_ipc";
    }

    public static class CallMetric {
        public String callId;
        public long startTimeMs;
        public long establishedTimeMs;
        public long endTimeMs;
        public long setupLatencyMs;
        public long durationMs;
        public String state;
        public String failureReason;
        public long txPackets;
        public long rxPackets;
        public long lostPackets;
        public double lossRatePercent;
        public double jitterMs;
        public long duplicates;
        public long outOfOrder;
        public long seqDiscontinuities;
        public boolean oneWayAudio;
        public double mosScore;

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("callId", callId);
            m.put("setupLatencyMs", setupLatencyMs);
            m.put("durationMs", durationMs);
            m.put("state", state);
            m.put("failureReason", failureReason);
            m.put("txPackets", txPackets);
            m.put("rxPackets", rxPackets);
            m.put("lostPackets", lostPackets);
            m.put("lossRatePercent", Math.round(lossRatePercent * 100.0) / 100.0);
            m.put("jitterMs", Math.round(jitterMs * 100.0) / 100.0);
            m.put("duplicates", duplicates);
            m.put("outOfOrder", outOfOrder);
            m.put("seqDiscontinuities", seqDiscontinuities);
            m.put("oneWayAudio", oneWayAudio);
            m.put("mosScore", Math.round(mosScore * 100.0) / 100.0);
            return m;
        }
    }

    public static class SoakSnapshot {
        public long timestampMs;
        public long elapsedSeconds;
        public int activeCalls;
        public long memoryUsedMb;
        public long memoryFreeMb;
        public long memoryMaxMb;
        public int activeThreads;
        public long totalTxPackets;
        public long totalRxPackets;
        public double currentJitterMs;
        public double currentMosScore;

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("timestampMs", timestampMs);
            m.put("elapsedSeconds", elapsedSeconds);
            m.put("activeCalls", activeCalls);
            m.put("memoryUsedMb", memoryUsedMb);
            m.put("memoryFreeMb", memoryFreeMb);
            m.put("memoryMaxMb", memoryMaxMb);
            m.put("activeThreads", activeThreads);
            m.put("totalTxPackets", totalTxPackets);
            m.put("totalRxPackets", totalRxPackets);
            m.put("currentJitterMs", Math.round(currentJitterMs * 100.0) / 100.0);
            m.put("currentMosScore", Math.round(currentMosScore * 100.0) / 100.0);
            return m;
        }
    }

    public static class CampaignResult {
        public String campaignName;
        public ExecutionMode mode;
        public long startTimeMs;
        public long endTimeMs;
        public long totalDurationMs;
        public int totalAttempted;
        public int totalSuccessful;
        public int totalFailed;
        public double successRatePercent;

        public double minSetupLatencyMs = 0;
        public double avgSetupLatencyMs = 0;
        public double maxSetupLatencyMs = 0;
        public double p95SetupLatencyMs = 0;

        public double minDurationSeconds = 0;
        public double avgDurationSeconds = 0;
        public double maxDurationSeconds = 0;

        public long totalTxPackets = 0;
        public long totalRxPackets = 0;
        public long totalLostPackets = 0;
        public double overallLossRatePercent = 0.0;
        public double avgJitterMs = 0.0;
        public double maxJitterMs = 0.0;
        public long totalDuplicates = 0;
        public long totalOutOfOrder = 0;
        public long totalSeqDiscontinuities = 0;
        public int oneWayAudioCount = 0;
        public double overallMosScore = 4.5;

        public long memoryUsedMbStart;
        public long memoryUsedMbEnd;
        public int threadCountStart;
        public int threadCountEnd;

        public List<CallMetric> callMetrics = new CopyOnWriteArrayList<>();
        public List<SoakSnapshot> soakSnapshots = new CopyOnWriteArrayList<>();

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("campaignName", campaignName);
            m.put("mode", mode != null ? mode.name() : "UNKNOWN");
            m.put("totalDurationMs", totalDurationMs);
            m.put("totalAttempted", totalAttempted);
            m.put("totalSuccessful", totalSuccessful);
            m.put("totalFailed", totalFailed);
            m.put("successRatePercent", Math.round(successRatePercent * 100.0) / 100.0);

            Map<String, Object> lat = new LinkedHashMap<>();
            lat.put("minMs", Math.round(minSetupLatencyMs * 100.0) / 100.0);
            lat.put("avgMs", Math.round(avgSetupLatencyMs * 100.0) / 100.0);
            lat.put("maxMs", Math.round(maxSetupLatencyMs * 100.0) / 100.0);
            lat.put("p95Ms", Math.round(p95SetupLatencyMs * 100.0) / 100.0);
            m.put("setupLatency", lat);

            Map<String, Object> rtp = new LinkedHashMap<>();
            rtp.put("txPackets", totalTxPackets);
            rtp.put("rxPackets", totalRxPackets);
            rtp.put("lostPackets", totalLostPackets);
            rtp.put("lossRatePercent", Math.round(overallLossRatePercent * 100.0) / 100.0);
            rtp.put("avgJitterMs", Math.round(avgJitterMs * 100.0) / 100.0);
            rtp.put("maxJitterMs", Math.round(maxJitterMs * 100.0) / 100.0);
            rtp.put("duplicates", totalDuplicates);
            rtp.put("outOfOrder", totalOutOfOrder);
            rtp.put("seqDiscontinuities", totalSeqDiscontinuities);
            rtp.put("oneWayAudioCalls", oneWayAudioCount);
            rtp.put("overallMosScore", Math.round(overallMosScore * 100.0) / 100.0);
            m.put("rtpStats", rtp);

            Map<String, Object> res = new LinkedHashMap<>();
            res.put("memoryUsedMbStart", memoryUsedMbStart);
            res.put("memoryUsedMbEnd", memoryUsedMbEnd);
            res.put("threadCountStart", threadCountStart);
            res.put("threadCountEnd", threadCountEnd);
            m.put("resources", res);

            List<Map<String, Object>> calls = new ArrayList<>();
            for (CallMetric c : callMetrics) {
                calls.add(c.toMap());
            }
            m.put("callMetrics", calls);

            List<Map<String, Object>> snaps = new ArrayList<>();
            for (SoakSnapshot s : soakSnapshots) {
                snaps.add(s.toMap());
            }
            m.put("soakSnapshots", snaps);

            return m;
        }
    }

    private static volatile PerformanceTestEngine instance;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private CampaignConfig currentConfig;
    private CampaignResult currentResult;
    private ExecutorService campaignExecutor;
    private final List<String> activeCampaignCallIds = new CopyOnWriteArrayList<>();

    public static PerformanceTestEngine getInstance() {
        if (instance == null) {
            synchronized (PerformanceTestEngine.class) {
                if (instance == null) {
                    instance = new PerformanceTestEngine();
                }
            }
        }
        return instance;
    }

    private PerformanceTestEngine() {}

    public boolean isRunning() {
        return isRunning.get();
    }

    public CampaignConfig getCurrentConfig() {
        return currentConfig;
    }

    public CampaignResult getCurrentResult() {
        return currentResult;
    }

    /**
     * Start a new performance test campaign asynchronously.
     */
    public synchronized boolean startCampaign(CampaignConfig config) {
        if (isRunning.get()) {
            return false;
        }

        this.currentConfig = config != null ? config : new CampaignConfig();
        this.currentResult = new CampaignResult();
        this.currentResult.campaignName = currentConfig.name;
        this.currentResult.mode = currentConfig.mode;
        this.currentResult.startTimeMs = System.currentTimeMillis();

        Runtime rt = Runtime.getRuntime();
        this.currentResult.memoryUsedMbStart = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        this.currentResult.threadCountStart = Thread.activeCount();

        isRunning.set(true);
        activeCampaignCallIds.clear();

        int poolSize = Math.max(2, Math.min(currentConfig.maxConcurrent + 2, 10));
        campaignExecutor = Executors.newFixedThreadPool(poolSize, new ThreadFactory() {
            private final AtomicInteger count = new AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "PerfCampaignWorker-" + count.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        });

        campaignExecutor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    executeCampaign();
                } catch (Throwable t) {
                    System.err.println("Campaign execution encountered error: " + t.getMessage());
                    t.printStackTrace();
                } finally {
                    finishCampaign();
                }
            }
        });

        return true;
    }

    /**
     * Stop active campaign, hanging up all active calls.
     */
    public synchronized void stopCampaign() {
        if (!isRunning.get()) return;
        isRunning.set(false);

        SipCallEngine engine = SipCallEngine.getInstance();
        for (String id : activeCampaignCallIds) {
            try {
                engine.hangupCall(id);
            } catch (Exception ignored) {}
        }
        activeCampaignCallIds.clear();

        if (campaignExecutor != null) {
            campaignExecutor.shutdownNow();
            campaignExecutor = null;
        }

        finishCampaign();
    }

    private void executeCampaign() {
        SipCallEngine engine = SipCallEngine.getInstance();
        if (!engine.isRunning()) {
            engine.startEngine(null, 5060, null, 5060);
        }

        // Register simulator if caller is provided
        try {
            engine.register(currentConfig.caller, 1800);
        } catch (Exception e) {
            System.err.println("Warning: Caller registration failed: " + e.getMessage());
        }

        switch (currentConfig.mode) {
            case SINGLE_CALL:
                executeSingleCall();
                break;
            case SEQUENTIAL:
                executeSequentialCalls();
                break;
            case CONCURRENT:
                executeConcurrentCalls();
                break;
            case RATE_CONTROLLED:
                executeRateControlledCalls();
                break;
            case LONG_DURATION:
                executeLongDurationSoak();
                break;
        }
    }

    private void executeSingleCall() {
        runCallInstance(1, currentConfig.durationSeconds);
    }

    private void executeSequentialCalls() {
        for (int i = 1; i <= currentConfig.totalCalls; i++) {
            if (!isRunning.get()) break;
            runCallInstance(i, currentConfig.durationSeconds);
            if (i < currentConfig.totalCalls && currentConfig.delayBetweenCallsMs > 0) {
                try {
                    Thread.sleep(currentConfig.delayBetweenCallsMs);
                } catch (InterruptedException ie) {
                    break;
                }
            }
        }
    }

    private void executeConcurrentCalls() {
        int total = currentConfig.totalCalls;
        int maxConc = Math.max(1, currentConfig.maxConcurrent);
        final Semaphore semaphore = new Semaphore(maxConc);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 1; i <= total; i++) {
            if (!isRunning.get()) break;
            final int callIdx = i;

            try {
                semaphore.acquire();
            } catch (InterruptedException e) {
                break;
            }

            Future<?> f = campaignExecutor.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        runCallInstance(callIdx, currentConfig.durationSeconds);
                    } finally {
                        semaphore.release();
                    }
                }
            });
            futures.add(f);
        }

        for (Future<?> f : futures) {
            try {
                f.get(Math.max(30, currentConfig.durationSeconds + 15), TimeUnit.SECONDS);
            } catch (Exception ignored) {}
        }
    }

    private void executeRateControlledCalls() {
        int total = currentConfig.totalCalls;
        double cps = currentConfig.callsPerSecond > 0 ? currentConfig.callsPerSecond : 1.0;
        long intervalMs = (long) (1000.0 / cps);
        final Semaphore concurrencyLimiter = new Semaphore(Math.max(1, currentConfig.maxConcurrent));
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 1; i <= total; i++) {
            if (!isRunning.get()) break;
            final int callIdx = i;

            try {
                if (!concurrencyLimiter.tryAcquire(2, TimeUnit.SECONDS)) {
                    // Skip or wait if at max concurrent limit to prevent overload
                    concurrencyLimiter.acquire();
                }
            } catch (InterruptedException e) {
                break;
            }

            Future<?> f = campaignExecutor.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        runCallInstance(callIdx, currentConfig.durationSeconds);
                    } finally {
                        concurrencyLimiter.release();
                    }
                }
            });
            futures.add(f);

            try {
                Thread.sleep(intervalMs);
            } catch (InterruptedException ie) {
                break;
            }
        }

        for (Future<?> f : futures) {
            try {
                f.get(Math.max(30, currentConfig.durationSeconds + 15), TimeUnit.SECONDS);
            } catch (Exception ignored) {}
        }
    }

    private void executeLongDurationSoak() {
        int soakDurationSec = Math.max(10, currentConfig.durationSeconds);
        SipCallEngine engine = SipCallEngine.getInstance();
        CallMetric metric = new CallMetric();
        metric.startTimeMs = System.currentTimeMillis();

        try {
            SipCallEngine.CallSession session = engine.makeCall(currentConfig.caller, currentConfig.callee, currentConfig.payloadType);
            metric.callId = session.callId;
            activeCampaignCallIds.add(session.callId);

            // Wait for answer
            long waitStart = System.currentTimeMillis();
            while (System.currentTimeMillis() - waitStart < 15000 && session.state != CallState.ESTABLISHED && isRunning.get()) {
                if (currentConfig.autoAnswer && session.state == CallState.RINGING) {
                    answerMicroSip();
                }
                Thread.sleep(100);
            }

            if (session.state == CallState.ESTABLISHED) {
                metric.establishedTimeMs = System.currentTimeMillis();
                metric.setupLatencyMs = metric.establishedTimeMs - metric.startTimeMs;
                metric.state = "ESTABLISHED";

                // Monitor soak progression in 5-second intervals
                long soakStart = System.currentTimeMillis();
                long targetEnd = soakStart + (soakDurationSec * 1000L);

                while (System.currentTimeMillis() < targetEnd && isRunning.get() && session.state == CallState.ESTABLISHED) {
                    Thread.sleep(Math.min(5000, targetEnd - System.currentTimeMillis()));

                    SoakSnapshot snap = new SoakSnapshot();
                    snap.timestampMs = System.currentTimeMillis();
                    snap.elapsedSeconds = (snap.timestampMs - soakStart) / 1000L;
                    snap.activeCalls = engine.getActiveCalls().size();

                    Runtime rt = Runtime.getRuntime();
                    snap.memoryUsedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
                    snap.memoryFreeMb = rt.freeMemory() / (1024 * 1024);
                    snap.memoryMaxMb = rt.maxMemory() / (1024 * 1024);
                    snap.activeThreads = Thread.activeCount();

                    if (session.mediaController != null) {
                        Map<String, Object> stats = session.mediaController.getStats();
                        snap.totalTxPackets = stats.containsKey("packets_sent") ? ((Number) stats.get("packets_sent")).longValue() : 0L;
                        snap.totalRxPackets = stats.containsKey("packets_received") ? ((Number) stats.get("packets_received")).longValue() : 0L;
                        snap.currentJitterMs = stats.containsKey("rx_jitter_ms") ? ((Number) stats.get("rx_jitter_ms")).doubleValue() : 0.0;
                        snap.currentMosScore = stats.containsKey("mos_score") ? ((Number) stats.get("mos_score")).doubleValue() : 4.5;
                    }
                    currentResult.soakSnapshots.add(snap);
                }

                // Call termination
                engine.hangupCall(session.callId);
                metric.endTimeMs = System.currentTimeMillis();
                metric.durationMs = metric.endTimeMs - metric.establishedTimeMs;
                metric.state = "TERMINATED";
            } else {
                metric.state = session.state.name();
                metric.failureReason = session.failureReason != null ? session.failureReason : "Setup Timeout";
            }

            collectMediaMetrics(session, metric);

        } catch (Exception ex) {
            metric.state = "FAILED";
            metric.failureReason = ex.getMessage();
        } finally {
            if (metric.callId != null) activeCampaignCallIds.remove(metric.callId);
            currentResult.callMetrics.add(metric);
            currentResult.totalAttempted++;
            if ("ESTABLISHED".equals(metric.state) || "TERMINATED".equals(metric.state)) {
                currentResult.totalSuccessful++;
            } else {
                currentResult.totalFailed++;
            }
        }
    }

    private void runCallInstance(int callIndex, int durationSec) {
        SipCallEngine engine = SipCallEngine.getInstance();
        CallMetric metric = new CallMetric();
        metric.startTimeMs = System.currentTimeMillis();

        try {
            SipCallEngine.CallSession session = engine.makeCall(currentConfig.caller, currentConfig.callee, currentConfig.payloadType);
            metric.callId = session.callId;
            activeCampaignCallIds.add(session.callId);

            // Wait for answer
            long waitStart = System.currentTimeMillis();
            while (System.currentTimeMillis() - waitStart < 15000 && session.state != CallState.ESTABLISHED && isRunning.get()) {
                if (currentConfig.autoAnswer && session.state == CallState.RINGING) {
                    answerMicroSip();
                }
                Thread.sleep(100);
            }

            if (session.state == CallState.ESTABLISHED) {
                metric.establishedTimeMs = System.currentTimeMillis();
                metric.setupLatencyMs = metric.establishedTimeMs - metric.startTimeMs;
                metric.state = "ESTABLISHED";

                // Audio holding duration
                long endWait = System.currentTimeMillis() + (durationSec * 1000L);
                while (System.currentTimeMillis() < endWait && isRunning.get() && session.state == CallState.ESTABLISHED) {
                    Thread.sleep(200);
                }

                // Hangup
                engine.hangupCall(session.callId);
                metric.endTimeMs = System.currentTimeMillis();
                metric.durationMs = metric.endTimeMs - metric.establishedTimeMs;
                metric.state = "TERMINATED";
            } else {
                metric.state = session.state.name();
                metric.failureReason = session.failureReason != null ? session.failureReason : "Setup Timeout";
            }

            collectMediaMetrics(session, metric);

        } catch (Exception ex) {
            metric.state = "FAILED";
            metric.failureReason = ex.getMessage();
        } finally {
            if (metric.callId != null) activeCampaignCallIds.remove(metric.callId);
            currentResult.callMetrics.add(metric);
            currentResult.totalAttempted++;
            if ("ESTABLISHED".equals(metric.state) || "TERMINATED".equals(metric.state)) {
                currentResult.totalSuccessful++;
            } else {
                currentResult.totalFailed++;
            }
        }
    }

    private void collectMediaMetrics(SipCallEngine.CallSession session, CallMetric metric) {
        if (session != null && session.mediaController != null) {
            Map<String, Object> stats = session.mediaController.getStats();
            metric.txPackets = stats.containsKey("packets_sent") ? ((Number) stats.get("packets_sent")).longValue() : 0L;
            metric.rxPackets = stats.containsKey("packets_received") ? ((Number) stats.get("packets_received")).longValue() : 0L;
            metric.lostPackets = stats.containsKey("lost_packets") ? ((Number) stats.get("lost_packets")).longValue() : 0L;
            metric.lossRatePercent = stats.containsKey("loss_rate_percent") ? ((Number) stats.get("loss_rate_percent")).doubleValue() : 0.0;
            metric.jitterMs = stats.containsKey("rx_jitter_ms") ? ((Number) stats.get("rx_jitter_ms")).doubleValue() : 0.0;
            metric.duplicates = stats.containsKey("duplicate_packets") ? ((Number) stats.get("duplicate_packets")).longValue() : 0L;
            metric.outOfOrder = stats.containsKey("out_of_order_packets") ? ((Number) stats.get("out_of_order_packets")).longValue() : 0L;
            metric.seqDiscontinuities = stats.containsKey("sequence_discontinuities") ? ((Number) stats.get("sequence_discontinuities")).longValue() : 0L;
            metric.oneWayAudio = stats.containsKey("one_way_audio") && (Boolean) stats.get("one_way_audio");
            metric.mosScore = stats.containsKey("mos_score") ? ((Number) stats.get("mos_score")).doubleValue() : 4.5;
        } else {
            metric.mosScore = "FAILED".equals(metric.state) ? 1.0 : 4.5;
        }
    }

    private void answerMicroSip() {
        try {
            java.io.RandomAccessFile pipe = new java.io.RandomAccessFile(currentConfig.autoAnswerPipe, "rw");
            String cmd = "{\"jsonrpc\":\"2.0\",\"method\":\"call.answer\",\"params\":{},\"id\":1}\n";
            pipe.write(cmd.getBytes("UTF-8"));
            pipe.close();
        } catch (Exception ignored) {}
    }

    private void finishCampaign() {
        isRunning.set(false);
        if (currentResult == null) return;

        currentResult.endTimeMs = System.currentTimeMillis();
        currentResult.totalDurationMs = currentResult.endTimeMs - currentResult.startTimeMs;

        Runtime rt = Runtime.getRuntime();
        currentResult.memoryUsedMbEnd = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        currentResult.threadCountEnd = Thread.activeCount();

        int att = currentResult.totalAttempted;
        currentResult.successRatePercent = att > 0 ? ((double) currentResult.totalSuccessful / att) * 100.0 : 0.0;

        // Calculate statistics
        List<Long> latencies = new ArrayList<>();
        List<Long> durations = new ArrayList<>();
        long totalLost = 0;
        long totalTx = 0;
        long totalRx = 0;
        double sumJitter = 0.0;
        double maxJit = 0.0;
        double sumMos = 0.0;
        int oneWayCount = 0;

        for (CallMetric c : currentResult.callMetrics) {
            if (c.setupLatencyMs > 0) {
                latencies.add(c.setupLatencyMs);
            }
            if (c.durationMs > 0) {
                durations.add(c.durationMs);
            }
            totalTx += c.txPackets;
            totalRx += c.rxPackets;
            totalLost += c.lostPackets;
            currentResult.totalDuplicates += c.duplicates;
            currentResult.totalOutOfOrder += c.outOfOrder;
            currentResult.totalSeqDiscontinuities += c.seqDiscontinuities;
            if (c.oneWayAudio) oneWayCount++;
            sumJitter += c.jitterMs;
            if (c.jitterMs > maxJit) maxJit = c.jitterMs;
            sumMos += c.mosScore;
        }

        currentResult.totalTxPackets = totalTx;
        currentResult.totalRxPackets = totalRx;
        currentResult.totalLostPackets = totalLost;
        currentResult.overallLossRatePercent = (totalRx + totalLost) > 0 ? ((double) totalLost / (totalRx + totalLost)) * 100.0 : 0.0;
        currentResult.oneWayAudioCount = oneWayCount;
        currentResult.maxJitterMs = maxJit;

        int metricCount = currentResult.callMetrics.size();
        currentResult.avgJitterMs = metricCount > 0 ? sumJitter / metricCount : 0.0;
        currentResult.overallMosScore = metricCount > 0 ? sumMos / metricCount : 4.5;

        if (!latencies.isEmpty()) {
            Collections.sort(latencies);
            currentResult.minSetupLatencyMs = latencies.get(0);
            currentResult.maxSetupLatencyMs = latencies.get(latencies.size() - 1);
            long sum = 0;
            for (Long l : latencies) sum += l;
            currentResult.avgSetupLatencyMs = (double) sum / latencies.size();
            int p95Idx = (int) Math.ceil(latencies.size() * 0.95) - 1;
            currentResult.p95SetupLatencyMs = latencies.get(Math.max(0, Math.min(p95Idx, latencies.size() - 1)));
        }

        if (!durations.isEmpty()) {
            Collections.sort(durations);
            currentResult.minDurationSeconds = durations.get(0) / 1000.0;
            currentResult.maxDurationSeconds = durations.get(durations.size() - 1) / 1000.0;
            long sum = 0;
            for (Long d : durations) sum += d;
            currentResult.avgDurationSeconds = (sum / 1000.0) / durations.size();
        }

        saveReport(currentResult);
    }

    private void saveReport(CampaignResult res) {
        try {
            File dir = new File(AppPaths.getLogsDir(), "perf_campaigns");
            if (!dir.exists()) dir.mkdirs();

            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date(res.startTimeMs));
            File jsonFile = new File(dir, "campaign_" + timeStamp + ".json");
            PrintWriter pw = new PrintWriter(new FileWriter(jsonFile));
            pw.println(mapToJson(res.toMap(), 0));
            pw.close();

            if (TLSDebugLogger.isEnabled()) {
                TLSDebugLogger.logf("[PERF ENGINE] Campaign report saved to: %s", jsonFile.getAbsolutePath());
            }
        } catch (Exception ex) {
            System.err.println("Failed to save campaign report: " + ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static String mapToJson(Object obj, int indent) {
        if (obj == null) return "null";
        if (obj instanceof String) return "\"" + escapeJson((String) obj) + "\"";
        if (obj instanceof Number || obj instanceof Boolean) return obj.toString();

        StringBuilder sb = new StringBuilder();
        String pad = indent(indent);
        String padNext = indent(indent + 2);

        if (obj instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) obj;
            if (map.isEmpty()) return "{}";
            sb.append("{\n");
            int i = 0;
            for (Map.Entry<String, Object> e : map.entrySet()) {
                sb.append(padNext).append("\"").append(escapeJson(e.getKey())).append("\": ");
                sb.append(mapToJson(e.getValue(), indent + 2));
                if (++i < map.size()) sb.append(",");
                sb.append("\n");
            }
            sb.append(pad).append("}");
            return sb.toString();
        }

        if (obj instanceof List) {
            List<Object> list = (List<Object>) obj;
            if (list.isEmpty()) return "[]";
            sb.append("[\n");
            for (int i = 0; i < list.size(); i++) {
                sb.append(padNext).append(mapToJson(list.get(i), indent + 2));
                if (i < list.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append(pad).append("]");
            return sb.toString();
        }

        return "\"" + escapeJson(obj.toString()) + "\"";
    }

    private static String indent(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(' ');
        return sb.toString();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
