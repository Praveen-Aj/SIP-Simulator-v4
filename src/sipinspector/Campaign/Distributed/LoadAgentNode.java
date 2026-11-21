/*
 * SIP Simulator v4 - Phase 9
 * LoadAgentNode: Worker node abstraction for distributed multi-call load generation
 */
package sipinspector.Campaign.Distributed;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class LoadAgentNode {

    public enum NodeStatus {
        REGISTERED,
        READY,
        RUNNING,
        COMPLETED,
        ERROR,
        OFFLINE
    }

    public static class NodeTelemetry {
        public int callsAttempted = 0;
        public int callsEstablished = 0;
        public int callsFailed = 0;
        public double latencyP50Ms = 0.0;
        public double latencyP95Ms = 0.0;
        public double latencyP99Ms = 0.0;
        public double averageMos = 4.3;
        public final Map<String, Integer> errorDistribution = new ConcurrentHashMap<>();
        public long lastHeartbeat = System.currentTimeMillis();

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("callsAttempted", callsAttempted);
            m.put("callsEstablished", callsEstablished);
            m.put("callsFailed", callsFailed);
            m.put("latencyP50Ms", latencyP50Ms);
            m.put("latencyP95Ms", latencyP95Ms);
            m.put("latencyP99Ms", latencyP99Ms);
            m.put("averageMos", averageMos);
            m.put("errorDistribution", new LinkedHashMap<>(errorDistribution));
            m.put("lastHeartbeat", lastHeartbeat);
            return m;
        }
    }

    private final String nodeId;
    private final String host;
    private final int port;
    private NodeStatus status = NodeStatus.REGISTERED;
    private double assignedCps = 1.0;
    private int assignedCalls = 10;
    private final String callIdPrefix;
    private final NodeTelemetry telemetry = new NodeTelemetry();

    public LoadAgentNode(String nodeId, String host, int port) {
        this.nodeId = nodeId;
        this.host = host;
        this.port = port;
        this.callIdPrefix = nodeId + "-call-";
    }

    public String getNodeId() { return nodeId; }
    public String getHost() { return host; }
    public int getPort() { return port; }
    public NodeStatus getStatus() { return status; }
    public void setStatus(NodeStatus status) { this.status = status; }
    public double getAssignedCps() { return assignedCps; }
    public void setAssignedCps(double assignedCps) { this.assignedCps = assignedCps; }
    public int getAssignedCalls() { return assignedCalls; }
    public void setAssignedCalls(int assignedCalls) { this.assignedCalls = assignedCalls; }
    public String getCallIdPrefix() { return callIdPrefix; }
    public NodeTelemetry getTelemetry() { return telemetry; }

    public void updateTelemetry(int attempted, int established, int failed,
                                double p50, double p95, double p99, double mos,
                                Map<String, Integer> errors) {
        telemetry.callsAttempted = attempted;
        telemetry.callsEstablished = established;
        telemetry.callsFailed = failed;
        telemetry.latencyP50Ms = p50;
        telemetry.latencyP95Ms = p95;
        telemetry.latencyP99Ms = p99;
        telemetry.averageMos = mos;
        telemetry.lastHeartbeat = System.currentTimeMillis();
        if (errors != null) {
            telemetry.errorDistribution.clear();
            telemetry.errorDistribution.putAll(errors);
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nodeId", nodeId);
        m.put("host", host);
        m.put("port", port);
        m.put("status", status.name());
        m.put("assignedCps", assignedCps);
        m.put("assignedCalls", assignedCalls);
        m.put("callIdPrefix", callIdPrefix);
        m.put("telemetry", telemetry.toMap());
        return m;
    }
}
