/*
 * SIP Simulator v4 - Phase 9 Validation
 * DistributedLoadCampaignTest: Validates distributed multi-call load coordinator and nodes (LIM-06)
 */
package test;

import sipinspector.Campaign.Distributed.DistributedCampaignCoordinator;
import sipinspector.Campaign.Distributed.LoadAgentNode;

import java.util.HashMap;
import java.util.Map;

public class DistributedLoadCampaignTest {

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("   PHASE 9: DISTRIBUTED LOAD GENERATION (LIM-06) VALIDATION");
        System.out.println("================================================================================");

        testLoadAgentNode();
        testCampaignWorkloadSlicing();
        testClusterMetricsAggregationAndFailureTriage();

        System.out.println(">>> ALL DISTRIBUTED LOAD CAMPAIGN TESTS PASSED!");
    }

    private static void testLoadAgentNode() {
        System.out.print("[TEST 1/3] LoadAgentNode Initialization & Call-ID Prefixes... ");
        LoadAgentNode node1 = new LoadAgentNode("node-alpha", "192.168.0.10", 5060);
        if (!"node-alpha-call-".equals(node1.getCallIdPrefix())) {
            throw new RuntimeException("Unexpected Call-ID prefix: " + node1.getCallIdPrefix());
        }
        if (node1.getStatus() != LoadAgentNode.NodeStatus.REGISTERED) {
            throw new RuntimeException("Expected REGISTERED status, got: " + node1.getStatus());
        }

        Map<String, Integer> errors = new HashMap<>();
        errors.put("486 Busy Here", 2);
        node1.updateTelemetry(10, 8, 2, 45.0, 110.0, 150.0, 4.25, errors);

        LoadAgentNode.NodeTelemetry tel = node1.getTelemetry();
        if (tel.callsAttempted != 10 || tel.callsEstablished != 8 || tel.callsFailed != 2) {
            throw new RuntimeException("Telemetry update failed");
        }
        if (tel.errorDistribution.get("486 Busy Here") != 2) {
            throw new RuntimeException("Error distribution mismatch");
        }

        System.out.println("PASSED");
    }

    private static void testCampaignWorkloadSlicing() {
        System.out.print("[TEST 2/3] Master Campaign Workload Slicing Across Worker Cluster... ");
        DistributedCampaignCoordinator coord = DistributedCampaignCoordinator.getInstance();

        LoadAgentNode node1 = new LoadAgentNode("worker-1", "127.0.0.1", 5061);
        LoadAgentNode node2 = new LoadAgentNode("worker-2", "127.0.0.1", 5062);
        LoadAgentNode node3 = new LoadAgentNode("worker-3", "127.0.0.1", 5063);

        coord.registerNode(node1);
        coord.registerNode(node2);
        coord.registerNode(node3);

        if (coord.getRegisteredNodes().size() != 3) {
            throw new RuntimeException("Expected 3 registered nodes");
        }

        DistributedCampaignCoordinator.MasterCampaignConfig cfg = new DistributedCampaignCoordinator.MasterCampaignConfig();
        cfg.totalCalls = 100;
        cfg.targetCps = 15.0;

        boolean prepOk = coord.prepareCampaign(cfg);
        if (!prepOk) throw new RuntimeException("Failed to prepare campaign");

        // One node receives 34 calls (33 + 1 remainder), other 2 receive 33
        int totalAssigned = node1.getAssignedCalls() + node2.getAssignedCalls() + node3.getAssignedCalls();
        if (totalAssigned != 100) {
            throw new RuntimeException("Workload call slicing error: expected 100 total, got " + totalAssigned);
        }
        int count34 = 0;
        int count33 = 0;
        for (int calls : new int[]{node1.getAssignedCalls(), node2.getAssignedCalls(), node3.getAssignedCalls()}) {
            if (calls == 34) count34++;
            else if (calls == 33) count33++;
        }
        if (count34 != 1 || count33 != 2) {
            throw new RuntimeException("Unexpected partition: " + node1.getAssignedCalls() + ", " + node2.getAssignedCalls() + ", " + node3.getAssignedCalls());
        }

        // Each node should have 5.0 CPS (15.0 / 3)
        if (Math.abs(node1.getAssignedCps() - 5.0) > 0.01) {
            throw new RuntimeException("CPS partition mismatch: " + node1.getAssignedCps());
        }

        System.out.println("PASSED");
    }

    private static void testClusterMetricsAggregationAndFailureTriage() {
        System.out.print("[TEST 3/3] Real-time Cluster Metrics Aggregation & Failure Triage... ");
        DistributedCampaignCoordinator coord = DistributedCampaignCoordinator.getInstance();
        boolean startOk = coord.startCampaign();
        if (!startOk || !coord.isRunning()) {
            throw new RuntimeException("Failed to start cluster campaign");
        }

        // Simulate telemetry feedback from each node
        Map<String, Integer> err1 = new HashMap<>();
        err1.put("503 Service Unavailable", 1);
        coord.getRegisteredNodes().get("worker-1").updateTelemetry(34, 33, 1, 40.0, 90.0, 120.0, 4.35, err1);

        Map<String, Integer> err2 = new HashMap<>();
        err2.put("488 Not Acceptable Here", 2);
        coord.getRegisteredNodes().get("worker-2").updateTelemetry(33, 31, 2, 50.0, 110.0, 140.0, 4.20, err2);

        coord.getRegisteredNodes().get("worker-3").updateTelemetry(33, 33, 0, 35.0, 80.0, 100.0, 4.40, new HashMap<>());

        DistributedCampaignCoordinator.AggregatedClusterMetrics metrics = coord.aggregateClusterMetrics();
        if (metrics.totalNodes != 3 || metrics.activeNodes != 3) {
            throw new RuntimeException("Node count mismatch in cluster metrics: " + metrics.totalNodes);
        }
        if (metrics.totalCallsAttempted != 100 || metrics.totalCallsEstablished != 97 || metrics.totalCallsFailed != 3) {
            throw new RuntimeException("Cluster call total mismatch: attempted=" + metrics.totalCallsAttempted
                    + " established=" + metrics.totalCallsEstablished + " failed=" + metrics.totalCallsFailed);
        }
        if (Math.abs(metrics.successRate - 97.0) > 0.1) {
            throw new RuntimeException("Cluster success rate mismatch: " + metrics.successRate);
        }

        // Check error triage breakdown
        if (!metrics.clusterErrorDistribution.containsKey("503 Service Unavailable")
                || !metrics.clusterErrorDistribution.containsKey("488 Not Acceptable Here")) {
            throw new RuntimeException("Cluster error distribution missing expected failure keys: " + metrics.clusterErrorDistribution);
        }

        coord.stopCampaign();
        if (coord.isRunning()) {
            throw new RuntimeException("Coordinator failed to stop cleanly");
        }

        // Clean up registered nodes
        coord.unregisterNode("worker-1");
        coord.unregisterNode("worker-2");
        coord.unregisterNode("worker-3");

        System.out.println("PASSED");
    }
}
