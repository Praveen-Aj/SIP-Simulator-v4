package sipinspector.Utils;

import java.io.*;
import java.nio.*;
import java.util.*;
import sipinspector.GlobalSettings;

/**
 * Pure Java PCAP binary packet parser. Reads standard .pcap files,
 * filters UDP packets containing SIP payloads, and autogenerates
 * SIP Simulator scenarios and values files.
 * 
 * Supports VLAN tags (802.1Q) automatically.
 * 
 * @author SIP Support Team
 */
public class PcapConverter {

    public static class SipPacketInfo {
        public long timestampMs;
        public String srcIp;
        public int srcPort;
        public String destIp;
        public int destPort;
        public String rawSipMsg;
        public String callId;
        public String methodOrResponse;
    }

    /**
     * Parse standard PCAP binary file and extract SIP packets.
     */
    public static List<SipPacketInfo> parsePcap(File file) throws Exception {
        List<SipPacketInfo> sipPackets = new ArrayList<>();
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(file);
            byte[] globalHeader = new byte[24];
            if (fis.read(globalHeader) != 24) {
                throw new IOException("Invalid PCAP file: Global header truncated");
            }

            ByteBuffer ghBuf = ByteBuffer.wrap(globalHeader);
            int magic = ghBuf.getInt();
            boolean bigEndian = true;
            if (magic == 0xa1b2c3d4) {
                bigEndian = true;
            } else if (magic == 0xd4c3b2a1) {
                bigEndian = false;
            } else {
                throw new IOException("Unsupported PCAP magic number: 0x" + Integer.toHexString(magic) + ". Please convert PCAPNG to standard PCAP format.");
            }

            ghBuf.order(bigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
            int linkType = ghBuf.getInt(20);
            if (linkType != 1) { // 1 = Ethernet (LINKTYPE_ETHERNET)
                throw new IOException("Unsupported link layer type: " + linkType + ". Only standard Ethernet captures are supported.");
            }

            byte[] recordHeader = new byte[16];
            while (fis.read(recordHeader) == 16) {
                ByteBuffer rhBuf = ByteBuffer.wrap(recordHeader);
                rhBuf.order(bigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);

                int tsSec = rhBuf.getInt();
                int tsUsec = rhBuf.getInt();
                int inclLen = rhBuf.getInt();
                int origLen = rhBuf.getInt();

                if (inclLen < 0 || inclLen > 65535) {
                    // Safety check against corrupted records
                    break;
                }

                byte[] packetData = new byte[inclLen];
                if (fis.read(packetData) != inclLen) {
                    break; // Truncated packet
                }

                try {
                    SipPacketInfo sip = parseEthernetPacket(packetData, tsSec * 1000L + tsUsec / 1000L);
                    if (sip != null) {
                        sipPackets.add(sip);
                    }
                } catch (Exception e) {
                    // Skip malformed individual packets
                }
            }
        } finally {
            if (fis != null) {
                try { fis.close(); } catch (IOException ignore) {}
            }
        }
        return sipPackets;
    }

    private static SipPacketInfo parseEthernetPacket(byte[] data, long timestampMs) throws Exception {
        if (data.length < 14) return null;

        int etherType = ((data[12] & 0xFF) << 8) | (data[13] & 0xFF);
        int ipOffset = 14;

        // Support VLAN tagging (802.1Q)
        if (etherType == 0x8100) {
            if (data.length < 18) return null;
            etherType = ((data[16] & 0xFF) << 8) | (data[17] & 0xFF);
            ipOffset = 18;
        }

        // Must be IPv4
        if (etherType != 0x0800) return null;

        if (data.length < ipOffset + 20) return null;
        int versionAndIhl = data[ipOffset] & 0xFF;
        int version = (versionAndIhl & 0xF0) >> 4;
        int ihl = (versionAndIhl & 0x0F) * 4;

        if (version != 4) return null; // IPv4 only

        int protocol = data[ipOffset + 9] & 0xFF;
        if (protocol != 17) return null; // Must be UDP

        // Extract Source and Destination IPs
        String srcIp = String.format("%d.%d.%d.%d",
                data[ipOffset + 12] & 0xFF, data[ipOffset + 13] & 0xFF,
                data[ipOffset + 14] & 0xFF, data[ipOffset + 15] & 0xFF);
        String destIp = String.format("%d.%d.%d.%d",
                data[ipOffset + 16] & 0xFF, data[ipOffset + 17] & 0xFF,
                data[ipOffset + 18] & 0xFF, data[ipOffset + 19] & 0xFF);

        int udpOffset = ipOffset + ihl;
        if (data.length < udpOffset + 8) return null;

        int srcPort = ((data[udpOffset] & 0xFF) << 8) | (data[udpOffset + 1] & 0xFF);
        int destPort = ((data[udpOffset + 2] & 0xFF) << 8) | (data[udpOffset + 3] & 0xFF);
        int udpLen = ((data[udpOffset + 4] & 0xFF) << 8) | (data[udpOffset + 5] & 0xFF);

        int payloadOffset = udpOffset + 8;
        int payloadLen = data.length - payloadOffset;
        if (payloadLen <= 0) return null;

        String payloadText = new String(data, payloadOffset, payloadLen, "UTF-8");
        if (isSipPayload(payloadText)) {
            SipPacketInfo info = new SipPacketInfo();
            info.timestampMs = timestampMs;
            info.srcIp = srcIp;
            info.srcPort = srcPort;
            info.destIp = destIp;
            info.destPort = destPort;
            info.rawSipMsg = payloadText;
            info.callId = SIPParser.getCallID(payloadText);
            
            // Extract method or response code
            String firstLine = getFirstLine(payloadText);
            if (firstLine.startsWith("SIP/2.0")) {
                info.methodOrResponse = SIPParser.getResponseCode(payloadText);
            } else {
                info.methodOrResponse = SIPParser.getRequestMethod(payloadText);
            }
            return info;
        }
        return null;
    }

    private static boolean isSipPayload(String text) {
        if (text == null || text.length() < 10) return false;
        String prefix = text.substring(0, Math.min(text.length(), 20));
        return prefix.startsWith("INVITE") || prefix.startsWith("REGISTER") ||
               prefix.startsWith("SIP/2.0") || prefix.startsWith("ACK") ||
               prefix.startsWith("BYE") || prefix.startsWith("CANCEL") ||
               prefix.startsWith("OPTIONS") || prefix.startsWith("REFER") ||
               prefix.startsWith("SUBSCRIBE") || prefix.startsWith("NOTIFY") ||
               prefix.startsWith("PUBLISH") || prefix.startsWith("MESSAGE");
    }

    private static String getFirstLine(String text) {
        int idx = text.indexOf('\n');
        if (idx == -1) return text;
        return text.substring(0, idx).trim();
    }

    /**
     * Converts the captured SIP packets of a specific Call-ID into a scenario file and values file.
     * If callIdToExtract is null, converts the first Call-ID flow it finds.
     */
    public static Map<String, String> convert(File file, String callIdToExtract, String outputBasename) throws Exception {
        List<SipPacketInfo> allPackets = parsePcap(file);
        if (allPackets.isEmpty()) {
            throw new IllegalArgumentException("No SIP packets found in the PCAP file.");
        }

        // Group Call-IDs and find the target
        Map<String, List<SipPacketInfo>> callsMap = new LinkedHashMap<>();
        for (SipPacketInfo p : allPackets) {
            if (p.callId != null) {
                callsMap.computeIfAbsent(p.callId, k -> new ArrayList<>()).add(p);
            }
        }

        if (callsMap.isEmpty()) {
            throw new IllegalArgumentException("No valid SIP calls with Call-ID found.");
        }

        String targetCallId = callIdToExtract;
        if (targetCallId == null || !callsMap.containsKey(targetCallId)) {
            // Pick the call with the most packets
            int maxPackets = -1;
            for (Map.Entry<String, List<SipPacketInfo>> entry : callsMap.entrySet()) {
                if (entry.getValue().size() > maxPackets) {
                    maxPackets = entry.getValue().size();
                    targetCallId = entry.getKey();
                }
            }
        }

        List<SipPacketInfo> packets = callsMap.get(targetCallId);
        if (packets == null || packets.isEmpty()) {
            throw new IllegalArgumentException("Target Call-ID not found in PCAP.");
        }

        // Determine which side is the simulated client (local) and remote
        // Normally, the first sender is the Client (local_ip)
        String localIp = packets.get(0).srcIp;
        int localPort = packets.get(0).srcPort;
        String remoteIp = packets.get(0).destIp;
        int remotePort = packets.get(0).destPort;

        StringBuilder scenario = new StringBuilder();
        scenario.append("# Autogenerated from PCAP capture by SIP Simulator\n");
        scenario.append("# Base parameters: Local=").append(localIp).append(":").append(localPort)
                .append(", Remote=").append(remoteIp).append(":").append(remotePort).append("\n\n");

        List<String> values = new ArrayList<>();
        // Values header
        values.add("field0,field1,field2"); // basic placeholders mapping
        values.add(localIp + "," + remoteIp + "," + remotePort);

        long lastTimestamp = -1;

        for (int i = 0; i < packets.size(); i++) {
            SipPacketInfo p = packets.get(i);
            boolean isOutgoing = p.srcIp.equals(localIp);

            // Add delay instructions
            if (lastTimestamp != -1) {
                long delay = p.timestampMs - lastTimestamp;
                if (delay > 20) { // only inject delay if greater than 20ms
                    scenario.append("delay ").append(delay).append("\n\n");
                }
            }
            lastTimestamp = p.timestampMs;

            // Arrow direction representation
            String direction = isOutgoing ? "---------->" : "<----------";
            scenario.append(direction).append("\n");

            // Format SIP message text with standard placeholders
            String cleanedMsg = p.rawSipMsg;
            
            // Replace Call-ID, IP addresses, ports with placeholders to make it reusable
            cleanedMsg = cleanedMsg.replace(targetCallId, "[call_id]");
            cleanedMsg = cleanedMsg.replace(localIp, "[local_ip]");
            cleanedMsg = cleanedMsg.replace(remoteIp, "[remote_ip]");
            cleanedMsg = cleanedMsg.replace(String.valueOf(localPort), "[local_port]");
            cleanedMsg = cleanedMsg.replace(String.valueOf(remotePort), "[remote_port]");

            // Ensure proper CRLF endings
            cleanedMsg = cleanedMsg.replaceAll("\\r\\n", "\n").replaceAll("\\n", "\r\n");
            
            scenario.append(cleanedMsg);
            if (!cleanedMsg.endsWith("\r\n\r\n")) {
                scenario.append("\r\n");
            }
            scenario.append("\n"); // scenario separator
        }

        // Return both generated templates
        Map<String, String> results = new HashMap<>();
        results.put("scenario", scenario.toString());
        results.put("values", String.join("\r\n", values));
        results.put("call_id", targetCallId);
        results.put("packet_count", String.valueOf(packets.size()));
        return results;
    }

    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("Usage: java PcapConverter <pcap_file> <output_scenario_path> <output_values_path> [call_id]");
            System.exit(1);
        }
        try {
            File pcapFile = new File(args[0]);
            String outScenario = args[1];
            String outValues = args[2];
            String callId = (args.length > 3 && !args[3].trim().isEmpty()) ? args[3] : null;
            
            Map<String, String> result = convert(pcapFile, callId, null);
            
            File scenFile = new File(outScenario);
            if (scenFile.getParentFile() != null && !scenFile.getParentFile().exists()) {
                scenFile.getParentFile().mkdirs();
            }
            File valFile = new File(outValues);
            if (valFile.getParentFile() != null && !valFile.getParentFile().exists()) {
                valFile.getParentFile().mkdirs();
            }

            try (FileWriter fw = new FileWriter(scenFile)) {
                fw.write(result.get("scenario"));
            }
            try (FileWriter fw = new FileWriter(valFile)) {
                fw.write(result.get("values"));
            }
            
            System.out.println("SUCCESS: call_id=" + result.get("call_id") + " packets=" + result.get("packet_count"));
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace();
            System.exit(2);
        }
    }
}
