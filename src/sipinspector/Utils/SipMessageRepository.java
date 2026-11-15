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
     * Filter messages by given criteria.
     */
    public List<SipMessageRecord> filter(String methodOrCode, String callId, String direction,
                                        String transport, String searchKeyword) {
        List<SipMessageRecord> result = new ArrayList<>();
        String mFilter = (methodOrCode != null && !methodOrCode.equals("All") && !methodOrCode.equals("All Types") && !methodOrCode.equals("All Responses"))
                ? methodOrCode.trim().toLowerCase() : null;
        String cFilter = (callId != null && !callId.trim().isEmpty()) ? callId.trim().toLowerCase() : null;
        String dFilter = (direction != null && !direction.equalsIgnoreCase("ALL")) ? direction.trim().toUpperCase() : null;
        String tFilter = (transport != null && !transport.equalsIgnoreCase("ALL")) ? transport.trim().toUpperCase() : null;
        String kFilter = (searchKeyword != null && !searchKeyword.trim().isEmpty()) ? searchKeyword.trim().toLowerCase() : null;

        synchronized (messageList) {
            for (SipMessageRecord rec : messageList) {
                if (dFilter != null && !rec.direction.equals(dFilter)) {
                    continue;
                }
                if (tFilter != null && !rec.transport.equalsIgnoreCase(tFilter)) {
                    continue;
                }
                if (cFilter != null && !rec.callId.toLowerCase().contains(cFilter)) {
                    continue;
                }
                if (mFilter != null) {
                    boolean match = rec.methodOrResponse.toLowerCase().contains(mFilter)
                            || (rec.responseCode > 0 && String.valueOf(rec.responseCode).startsWith(mFilter));
                    if (!match) continue;
                }
                if (kFilter != null) {
                    boolean match = rec.rawMessage.toLowerCase().contains(kFilter)
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
}
