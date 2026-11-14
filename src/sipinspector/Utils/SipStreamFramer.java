package sipinspector.Utils;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Robust, deterministic TCP byte-stream framer for SIP messages.
 *
 * Adheres strictly to RFC 3261 Section 18.1.1 (TCP framing):
 * 1. Accumulates incoming raw bytes into an internal stream buffer.
 * 2. Scans for the double-CRLF ("\r\n\r\n" or "\n\n") header terminator boundary.
 * 3. Before the header terminator is reached, no message is emitted (never prematurely completes).
 * 4. Parses "Content-Length" (and compact "l:") from the headers to determine the exact payload byte length.
 * 5. Uses raw byte count (not Java String character length) for body length extraction.
 * 6. Emits fully assembled SIP messages one by one in exact arrival order.
 * 7. Correctly retains unparsed residue/trailing bytes in the buffer for subsequent reads.
 * 8. Thread-safe and Java 8 compatible.
 */
public class SipStreamFramer {

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private final Queue<byte[]> completedMessages = new ArrayDeque<>();

    public SipStreamFramer() {
    }

    /**
     * Feeds incoming raw TCP bytes into the framer buffer and extracts all completed SIP messages.
     *
     * @param data Raw byte chunk read from TCP channel/socket
     * @param offset Starting offset
     * @param length Number of bytes
     */
    public synchronized void feed(byte[] data, int offset, int length) {
        if (data == null || length <= 0) {
            return;
        }
        buffer.write(data, offset, length);
        extractMessages();
    }

    /**
     * Polls the next complete SIP message from the buffer, or returns null if incomplete.
     */
    public synchronized byte[] pollNextMessage() {
        return completedMessages.poll();
    }

    /**
     * Checks if at least one complete message is ready for extraction.
     */
    public synchronized boolean hasNextMessage() {
        return !completedMessages.isEmpty();
    }

    /**
     * Returns the number of unparsed bytes currently remaining in the framer buffer.
     */
    public synchronized int getRemainingBufferSize() {
        return buffer.size();
    }

    /**
     * Clears all buffered data and pending messages (e.g. on socket reset/close).
     */
    public synchronized void reset() {
        buffer.reset();
        completedMessages.clear();
    }

    /**
     * Internal stateful extractor scanning for header boundaries and Content-Length bodies.
     */
    private void extractMessages() {
        byte[] raw = buffer.toByteArray();
        int cursor = 0;
        int total = raw.length;

        while (cursor < total) {
            // Find CRLF CRLF (\r\n\r\n) or LF LF (\n\n)
            int headerEnd = -1;
            int headerSepLen = 0;

            for (int i = cursor; i < total - 1; i++) {
                if (raw[i] == '\r' && raw[i + 1] == '\n') {
                    if (i + 3 < total && raw[i + 2] == '\r' && raw[i + 3] == '\n') {
                        headerEnd = i;
                        headerSepLen = 4;
                        break;
                    }
                } else if (raw[i] == '\n' && raw[i + 1] == '\n') {
                    headerEnd = i;
                    headerSepLen = 2;
                    break;
                }
            }

            if (headerEnd == -1) {
                // Incomplete headers - must wait for more bytes from the stream
                break;
            }

            // Extract headers as UTF-8 string to parse Content-Length
            int headerBytesLen = (headerEnd + headerSepLen) - cursor;
            String headerStr = new String(raw, cursor, headerBytesLen, StandardCharsets.UTF_8);

            int contentLength = extractContentLength(headerStr);
            int fullMessageLen = headerBytesLen + contentLength;

            if (cursor + fullMessageLen > total) {
                // Incomplete body - must wait for more bytes from the stream
                break;
            }

            // Exactly one complete SIP message found
            byte[] msgBytes = new byte[fullMessageLen];
            System.arraycopy(raw, cursor, msgBytes, 0, fullMessageLen);
            completedMessages.add(msgBytes);

            cursor += fullMessageLen;
        }

        // Retain unparsed leftover bytes in the buffer
        buffer.reset();
        if (cursor < total) {
            buffer.write(raw, cursor, total - cursor);
        }
    }

    /**
     * Case-insensitive, whitespace-tolerant extraction of Content-Length or compact 'l' header.
     */
    public static int extractContentLength(String headers) {
        if (headers == null || headers.isEmpty()) {
            return 0;
        }

        // Check standard Content-Length: or compact l:
        String[] lines = headers.split("\r?\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            int colonIdx = trimmed.indexOf(':');
            if (colonIdx > 0) {
                String hdrName = trimmed.substring(0, colonIdx).trim();
                if (hdrName.equalsIgnoreCase("Content-Length") || hdrName.equalsIgnoreCase("l")) {
                    String val = trimmed.substring(colonIdx + 1).trim();
                    try {
                        int parsed = Integer.parseInt(val);
                        return Math.max(0, parsed);
                    } catch (NumberFormatException ignore) {
                        return 0;
                    }
                }
            }
        }
        return 0;
    }
}
