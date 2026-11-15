package sipinspector.Utils;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import sipinspector.MyPacket;

/**
 * Central in-memory repository for live and captured SIP messages.
 * Provides thread-safe storage, message parsing, search/filtering,
 * and change notifications for SIP Message Explorer and diagnostic workspaces.
 *
 * Compatible with Java 8.
 *
 * @author SIP Support Team
 */
public class SipMessageRepository {

    private static final SipMessageRepository INSTANCE = new SipMessageRepository();

    public static SipMessageRepository getInstance() {
        return INSTANCE;
    }

    public static final int DEFAULT_MAX_CAPACITY = 5000;
    private final int maxCapacity;
    private final List<SipMessageRecord> messageList = new ArrayList<>();
    private final List<MessageListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    public interface MessageListener {
        void onMessageAdded(SipMessageRecord record);
        void onMessagesCleared();
    }

    public static class SipMessageRecord {
        public final long id;
        public final long timestamp;
        public final String timeFormatted;
        public final String direction; // "OUT" or "IN"
        public final String transport; // "UDP", "TCP", "TLS"
        public final String source;
        public final String destination;
        public final String startLine;
        public final String methodOrResponse;
        public final boolean isRequest;
        public final int responseCode;
        public final String callId;
        public final String cseq;
        public final String cseqMethod;
        public final long cseqNumber;
        public final String from;
        public final String to;
        public final String rawMessage;
        public final Map<String, String> headers;
        public final String sdpBody;

        public SipMessageRecord(long id, long timestamp, String direction, String transport,
                                String source, String destination, String rawMessage) {
            this.id = id;
            this.timestamp = timestamp;
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");
            this.timeFormatted = sdf.format(new Date(timestamp));
            this.direction = direction != null ? direction.toUpperCase() : "IN";
            this.transport = transport != null ? transport.toUpperCase() : "UDP";
            this.source = source != null ? source : "";
            this.destination = destination != null ? destination : "";
            this.rawMessage = rawMessage != null ? rawMessage : "";

            // Parse start line, headers, SDP
            Map<String, String> hdrMap = new LinkedHashMap<>();
            String sLine = "";
            String sdp = null;

            String text = this.rawMessage;
            int splitIdx = text.indexOf("\r\n\r\n");
            int crlfLen = 4;
            if (splitIdx < 0) {
                splitIdx = text.indexOf("\n\n");
                crlfLen = 2;
            }

            String headerPart = splitIdx >= 0 ? text.substring(0, splitIdx) : text;
            if (splitIdx >= 0 && splitIdx + crlfLen < text.length()) {
                String potentialSdp = text.substring(splitIdx + crlfLen).trim();
                if (!potentialSdp.isEmpty() && (potentialSdp.startsWith("v=") || potentialSdp.contains("m=audio") || potentialSdp.contains("c=IN"))) {
                    sdp = potentialSdp;
                }
            }

            String[] lines = headerPart.split("\\r?\\n");
            if (lines.length > 0) {
                sLine = lines[0].trim();
            }

            for (int i = 1; i < lines.length; i++) {
                String line = lines[i];
                int colon = line.indexOf(':');
                if (colon > 0) {
                    String hName = line.substring(0, colon).trim();
                    String hVal = line.substring(colon + 1).trim();
                    hdrMap.put(hName, hVal);
                }
            }

            this.startLine = sLine;
            this.headers = Collections.unmodifiableMap(hdrMap);
            this.sdpBody = sdp;

            // Analyze start line for method or response
            if (sLine.startsWith("SIP/2.0")) {
                this.isRequest = false;
                int code = 0;
                String methodResp = sLine;
                Matcher m = Pattern.compile("^SIP/2\\.0\\s+(\\d{3})(?:\\s+(.*))?$").matcher(sLine);
                if (m.find()) {
                    try {
                        code = Integer.parseInt(m.group(1));
                    } catch (Exception ignore) {}
                    String reason = m.group(2) != null ? m.group(2).trim() : "";
                    methodResp = code + (reason.isEmpty() ? "" : " " + reason);
                }
                this.responseCode = code;
                this.methodOrResponse = methodResp;
            } else {
                this.isRequest = true;
                this.responseCode = 0;
                String mName = sLine;
                int sp = sLine.indexOf(' ');
                if (sp > 0) {
                    mName = sLine.substring(0, sp).trim();
                }
                this.methodOrResponse = mName;
            }

            // Extract Call-ID
            String cid = findHeader(hdrMap, "Call-ID", "i");
            this.callId = cid != null ? cid : "";

            // Extract CSeq
            String cseqStr = findHeader(hdrMap, "CSeq");
            this.cseq = cseqStr != null ? cseqStr : "";
            long num = 0;
            String cm = "";
            if (cseqStr != null) {
                String[] parts = cseqStr.trim().split("\\s+");
                if (parts.length > 0) {
                    try { num = Long.parseLong(parts[0]); } catch (Exception ignore) {}
                }
                if (parts.length > 1) {
                    cm = parts[1].trim();
                }
            }
            this.cseqNumber = num;
            this.cseqMethod = cm;

            // Extract From / To
            String f = findHeader(hdrMap, "From", "f");
            this.from = f != null ? f : "";
            String t = findHeader(hdrMap, "To", "t");
            this.to = t != null ? t : "";
        }

        private static String findHeader(Map<String, String> map, String... names) {
            for (String n : names) {
                for (Map.Entry<String, String> entry : map.entrySet()) {
                    if (entry.getKey().equalsIgnoreCase(n)) {
                        return entry.getValue();
                    }
                }
            }
            return null;
        }

        public String getHeader(String name) {
            return findHeader(headers, name);
        }

        @Override
        public String toString() {
            return String.format("[%s] %s %-6s %-18s Call-ID: %s",
                    timeFormatted, direction.equals("OUT") ? "-->" : "<--",
                    transport, methodOrResponse, callId);
        }
    }

    public SipMessageRepository() {
        this(DEFAULT_MAX_CAPACITY);
    }

    public SipMessageRepository(int maxCapacity) {
        this.maxCapacity = Math.max(100, maxCapacity);
    }

    public void addListener(MessageListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(MessageListener listener) {
        listeners.remove(listener);
    }

    /**
     * Record a SIP packet from MyPacket and socket context.
     */
    public SipMessageRecord recordPacket(MyPacket packet, boolean isOutgoing, String transport, String localEndpoint) {
        if (packet == null) return null;
        String raw = packet.getMessageTxt();
        if (raw == null || raw.trim().isEmpty()) {
            byte[] bytes = packet.getBytes();
            if (bytes != null && packet.getLength() > 0) {
                raw = new String(bytes, 0, packet.getLength());
            }
        }
        if (raw == null || raw.trim().isEmpty()) return null;

        String remoteEndpoint = "";
        if (packet.getAddress() != null) {
            remoteEndpoint = packet.getAddress().getHostAddress() + ":" + packet.getPort();
        }

        String src = isOutgoing ? localEndpoint : remoteEndpoint;
        String dst = isOutgoing ? remoteEndpoint : localEndpoint;
        String dir = isOutgoing ? "OUT" : "IN";

        return recordMessage(dir, transport, src, dst, raw);
    }

    /**
     * Record a raw message directly with endpoint metadata.
     */
    public SipMessageRecord recordMessage(String direction, String transport, String source, String destination, String rawText) {
        if (rawText == null || rawText.trim().isEmpty()) return null;

        SipMessageRecord record = new SipMessageRecord(
                idGenerator.getAndIncrement(),
                System.currentTimeMillis(),
                direction,
                transport,
                source,
                destination,
                rawText
        );

        synchronized (messageList) {
            if (messageList.size() >= maxCapacity) {
                messageList.remove(0);
            }
            messageList.add(record);
        }

        for (MessageListener listener : listeners) {
            try {
                listener.onMessageAdded(record);
            } catch (Exception ignore) {}
        }

        return record;
    }

    public List<SipMessageRecord> getAllMessages() {
        synchronized (messageList) {
            return new ArrayList<>(messageList);
        }
    }

    public int getMessageCount() {
        synchronized (messageList) {
            return messageList.size();
        }
    }

    public long getTxMessageCount() {
        long count = 0;
        synchronized (messageList) {
            for (SipMessageRecord rec : messageList) {
                if ("OUT".equalsIgnoreCase(rec.direction)) count++;
            }
        }
        return count;
    }

    public long getRxMessageCount() {
        long count = 0;
        synchronized (messageList) {
            for (SipMessageRecord rec : messageList) {
                if ("IN".equalsIgnoreCase(rec.direction)) count++;
            }
        }
        return count;
    }

    public void clear() {
        synchronized (messageList) {
            messageList.clear();
        }
        for (MessageListener listener : listeners) {
            try {
                listener.onMessagesCleared();
            } catch (Exception ignore) {}
        }
    }

    /**
     * Filter messages by combined criteria with strict AND semantics across active filters.
     *
     * @param method SIP method (e.g. "INVITE", "OPTIONS") or null/"All Types" for any
     * @param responseCode SIP response code (e.g. "200", "488", "1xx", "2xx", "4xx") or null/"All Responses" for any
     * @param callId Call-ID substring filter or null
     * @param direction "IN", "OUT", or null/"ALL"
     * @param transport "UDP", "TCP", "TLS", or null/"ALL"
     * @param searchKeyword Free text keyword matching headers, body, endpoints, start-line, or null
     */
    public List<SipMessageRecord> filter(String method, String responseCode, String callId,
                                        String direction, String transport, String searchKeyword) {
        List<SipMessageRecord> result = new ArrayList<>();
        String mFilter = (method != null && !method.equalsIgnoreCase("All") && !method.equalsIgnoreCase("All Types"))
                ? method.trim().toLowerCase() : null;

        String rFilter = null;
        if (responseCode != null && !responseCode.equalsIgnoreCase("All") && !responseCode.equalsIgnoreCase("All Responses")) {
            String trimmed = responseCode.trim();
            if (trimmed.contains(" ")) {
                rFilter = trimmed.substring(0, trimmed.indexOf(' ')).trim().toLowerCase();
            } else {
                rFilter = trimmed.toLowerCase();
            }
        }

        String cFilter = (callId != null && !callId.trim().isEmpty()) ? callId.trim().toLowerCase() : null;
        String dFilter = (direction != null && !direction.equalsIgnoreCase("ALL") && !direction.equalsIgnoreCase("All Directions"))
                ? (direction.toUpperCase().contains("IN") ? "IN" : (direction.toUpperCase().contains("OUT") ? "OUT" : direction.trim().toUpperCase())) : null;
        String tFilter = (transport != null && !transport.equalsIgnoreCase("ALL") && !transport.equalsIgnoreCase("All Transports"))
                ? transport.trim().toUpperCase() : null;
        String kFilter = (searchKeyword != null && !searchKeyword.trim().isEmpty()) ? searchKeyword.trim().toLowerCase() : null;

        synchronized (messageList) {
            for (SipMessageRecord rec : messageList) {
                // Direction filter
                if (dFilter != null && !rec.direction.equalsIgnoreCase(dFilter)) {
                    continue;
                }
                // Transport filter
                if (tFilter != null && !rec.transport.equalsIgnoreCase(tFilter)) {
                    continue;
                }
                // Call-ID filter
                if (cFilter != null && !rec.callId.toLowerCase().contains(cFilter)) {
                    continue;
                }
                // Method filter
                if (mFilter != null) {
                    boolean methodMatches = false;
                    if (rec.isRequest) {
                        methodMatches = rec.methodOrResponse.toLowerCase().contains(mFilter);
                    } else {
                        methodMatches = (rec.cseqMethod != null && rec.cseqMethod.toLowerCase().contains(mFilter))
                                || (rec.cseq != null && rec.cseq.toLowerCase().contains(mFilter));
                    }
                    if (!methodMatches) continue;
                }
                // Response code filter
                if (rFilter != null) {
                    if (rec.isRequest) {
                        continue; // Requests do not match response code filters
                    }
                    boolean codeMatches = false;
                    if (rFilter.endsWith("xx") && rFilter.length() == 3) {
                        char category = rFilter.charAt(0);
                        codeMatches = String.valueOf(rec.responseCode).charAt(0) == category;
                    } else {
                        codeMatches = String.valueOf(rec.responseCode).startsWith(rFilter)
                                || rec.methodOrResponse.toLowerCase().contains(rFilter);
                    }
                    if (!codeMatches) continue;
                }
                // Free text keyword filter
                if (kFilter != null) {
                    boolean match = rec.rawMessage.toLowerCase().contains(kFilter)
                            || rec.callId.toLowerCase().contains(kFilter)
                            || rec.source.toLowerCase().contains(kFilter)
                            || rec.destination.toLowerCase().contains(kFilter)
                            || rec.startLine.toLowerCase().contains(kFilter);
                    if (!match) continue;
                }
                result.add(rec);
            }
        }
        return result;
    }

    /**
     * Backward-compatible 5-argument filter.
     */
    public List<SipMessageRecord> filter(String methodOrCode, String callId, String direction,
                                        String transport, String searchKeyword) {
        if (methodOrCode == null || methodOrCode.equalsIgnoreCase("All") || methodOrCode.equalsIgnoreCase("All Types") || methodOrCode.equalsIgnoreCase("All Responses")) {
            return filter(null, null, callId, direction, transport, searchKeyword);
        }
        String trimmed = methodOrCode.trim();
        if (!trimmed.isEmpty() && Character.isDigit(trimmed.charAt(0))) {
            return filter(null, trimmed, callId, direction, transport, searchKeyword);
        } else {
            return filter(trimmed, null, callId, direction, transport, searchKeyword);
        }
    }

    public List<SipMessageRecord> getMessagesByCallId(String callId) {
        return filter(null, null, callId, null, null, null);
    }
}
