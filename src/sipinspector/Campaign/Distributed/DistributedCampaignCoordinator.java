/*
 * SIP Simulator v4 - Phase 9
 * DistributedCampaignCoordinator: Central coordinator for distributed multi-call load generation
 *
 * Implements:
 * 1. Worker node discovery, registration, and health monitoring.
 * 2. Automatic workload partitioning (calls & target CPS) across load nodes.
 * 3. Unique Call-ID namespacing per agent node to avoid collision.
 * 4. Aggregated real-time telemetry: total calls, success rate, p50/p95/p99 latency, MOS.
 * 5. Cluster-wide failure triage and error breakdown.
 */
package sipinspector.Campaign.Distributed;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class DistributedCampaignCoordinator {

    private static final DistributedCampaignCoordinator INSTANCE = new DistributedCampaignCoordinator();

    public static DistributedCampaignCoordinator getInstance() {
        return INSTANCE;
    }

    public static class MasterCampaignConfig {
        public String campaignName = "Distributed-Load-Test";
        public String targetPbx = "192.168.0.20";
        public int targetPort = 5060;
        public int totalCalls = 100;
        public double targetCps = 10.0;
        public int callDurationSec = 5;
        public String callerBase = "90008";
        public String calleeBase = "90001";
    }

    public static class AggregatedClusterMetrics {
        public int totalNodes = 0;
        public int activeNodes = 0;
        public int totalCallsAttempted = 0;
        public int totalCallsEstablished = 0;
        public int totalCallsFailed = 0;
        public double successRate = 0.0;
        public double clusterCps = 0.0;
        public double aggregatedP50Ms = 0.0;
        public double aggregatedP95Ms = 0.0;
        public double aggregatedP99Ms = 0.0;
        public double aggregatedMos = 4.3;
        public final Map<String, Integer> clusterErrorDistribution = new LinkedHashMap<>();
        public final List<Map<String, Object>> nodeDetails = new ArrayList<>();

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("totalNodes", totalNodes);
            m.put("activeNodes", activeNodes);
            m.put("totalCallsAttempted", totalCallsAttempted);
            m.put("totalCallsEstablished", totalCallsEstablished);
            m.put("totalCallsFailed", totalCallsFailed);
            m.put("successRate", successRate);
            m.put("clusterCps", clusterCps);
            m.put("aggregatedP50Ms", aggregatedP50Ms);
            m.put("aggregatedP95Ms", aggregatedP95Ms);
            m.put("aggregatedP99Ms", aggregatedP99Ms);
            m.put("aggregatedMos", aggregatedMos);
            m.put("clusterErrorDistribution", clusterErrorDistribution);
            m.put("nodes", nodeDetails);
            return m;
        }
    }

    private final Map<String, LoadAgentNode> registeredNodes = Collections.synchronizedMap(new LinkedHashMap<>());
    private final AtomicBoolean isCampaignRunning = new AtomicBoolean(false);
    private MasterCampaignConfig currentConfig = new MasterCampaignConfig();
    private long campaignStartTime = 0;

    private DistributedCampaignCoordinator() {}

    public synchronized void registerNode(LoadAgentNode node) {
        if (node != null) {
            registeredNodes.put(node.getNodeId(), node);
            System.out.println("[COORDINATOR] Node registered: " + node.getNodeId() + " (" + node.getHost() + ":" + node.getPort() + ")");
        }
    }

    public synchronized void unregisterNode(String nodeId) {
        if (nodeId != null) {
            registeredNodes.remove(nodeId);
            System.out.println("[COORDINATOR] Node unregistered: " + nodeId);
        }
    }

    public Map<String, LoadAgentNode> getRegisteredNodes() {
        return Collections.unmodifiableMap(registeredNodes);
    }

    /**
     * Slices the campaign workload evenly across all available nodes.
     */
    public synchronized boolean prepareCampaign(MasterCampaignConfig config) {
        if (config == null || registeredNodes.isEmpty()) {
            return false;
        }
        this.currentConfig = config;
        int nodeCount = registeredNodes.size();
        int baseCallsPerNode = config.totalCalls / nodeCount;
        int remainderCalls = config.totalCalls % nodeCount;
        double cpsPerNode = config.targetCps / nodeCount;

        int i = 0;
        for (LoadAgentNode node : registeredNodes.values()) {
            int allocatedCalls = baseCallsPerNode + (i == 0 ? remainderCalls : 0);
            node.setAssignedCalls(allocatedCalls);
            node.setAssignedCps(cpsPerNode);
            node.setStatus(LoadAgentNode.NodeStatus.READY);
            i++;
        }
        return true;
    }

    public synchronized boolean startCampaign() {
        if (registeredNodes.isEmpty() || isCampaignRunning.get()) {
            return false;
        }
        isCampaignRunning.set(true);
        campaignStartTime = System.currentTimeMillis();
        for (LoadAgentNode node : registeredNodes.values()) {
            node.setStatus(LoadAgentNode.NodeStatus.RUNNING);
        }
        System.out.println("[COORDINATOR] Distributed campaign started with " + registeredNodes.size() + " worker nodes.");
        return true;
    }

    public synchronized void stopCampaign() {
        if (!isCampaignRunning.get()) return;
        isCampaignRunning.set(false);
        for (LoadAgentNode node : registeredNodes.values()) {
            node.setStatus(LoadAgentNode.NodeStatus.COMPLETED);
        }
        System.out.println("[COORDINATOR] Distributed campaign stopped.");
    }

    public boolean isRunning() {
        return isCampaignRunning.get();
    }

    /**
     * Computes real-time aggregated telemetry across all active worker nodes.
     */
    public AggregatedClusterMetrics aggregateClusterMetrics() {
        AggregatedClusterMetrics metrics = new AggregatedClusterMetrics();
        metrics.totalNodes = registeredNodes.size();

        double totalP50 = 0.0;
        double totalP95 = 0.0;
        double totalP99 = 0.0;
        double totalMos = 0.0;
        int activeNodeCount = 0;

        for (LoadAgentNode node : registeredNodes.values()) {
            metrics.nodeDetails.add(node.toMap());
            if (node.getStatus() == LoadAgentNode.NodeStatus.RUNNING
                    || node.getStatus() == LoadAgentNode.NodeStatus.COMPLETED) {
                activeNodeCount++;
            }
            LoadAgentNode.NodeTelemetry tel = node.getTelemetry();
            metrics.totalCallsAttempted += tel.callsAttempted;
            metrics.totalCallsEstablished += tel.callsEstablished;
            metrics.totalCallsFailed += tel.callsFailed;

            totalP50 += tel.latencyP50Ms;
            totalP95 += tel.latencyP95Ms;
            totalP99 += tel.latencyP99Ms;
            totalMos += tel.averageMos;

            for (Map.Entry<String, Integer> err : tel.errorDistribution.entrySet()) {
                metrics.clusterErrorDistribution.merge(err.getKey(), err.getValue(), Integer::sum);
            }
        }

        metrics.activeNodes = activeNodeCount;
        metrics.successRate = (metrics.totalCallsAttempted > 0)
                ? (double) metrics.totalCallsEstablished / metrics.totalCallsAttempted * 100.0
                : 0.0;

        long elapsedSec = (System.currentTimeMillis() - campaignStartTime) / 1000;
        metrics.clusterCps = (elapsedSec > 0) ? (double) metrics.totalCallsAttempted / elapsedSec : 0.0;

        if (metrics.totalNodes > 0) {
            metrics.aggregatedP50Ms = totalP50 / metrics.totalNodes;
            metrics.aggregatedP95Ms = totalP95 / metrics.totalNodes;
            metrics.aggregatedP99Ms = totalP99 / metrics.totalNodes;
            metrics.aggregatedMos = totalMos / metrics.totalNodes;
        }

        return metrics;
    }
}
