/*
 *  SIP Inspector - RTP Audio Recorder
 *  Records incoming RTP PCMA audio to WAV file
 */
package sipinspector.Media;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Records raw PCMA (G.711 A-law) RTP payloads to a WAV file.
 * Call {@link #feedPayload(byte[])} for each RTP packet's payload (without RTP header).
 * Call {@link #close()} when done to finalize the WAV header.
 *
 * WAV format: 8000 Hz, 16-bit signed LE, mono (decoded from A-law)
 */
public class RtpWavRecorder implements AutoCloseable {

    private final RandomAccessFile raf;
    private final String filePath;
    private long totalPcmBytes = 0;
    private boolean closed = false;

    // WAV constants
    private static final int SAMPLE_RATE = 8000;
    private static final int BITS_PER_SAMPLE = 16;
    private static final int NUM_CHANNELS = 1;
    private static final int BYTE_RATE = SAMPLE_RATE * NUM_CHANNELS * (BITS_PER_SAMPLE / 8);
    private static final int BLOCK_ALIGN = NUM_CHANNELS * (BITS_PER_SAMPLE / 8);

    public RtpWavRecorder(String filePath) throws IOException {
        this.filePath = filePath;
        this.raf = new RandomAccessFile(filePath, "rw");
        writeWavHeader(); // Write placeholder header
    }

    /**
     * Write WAV header (44 bytes). Sizes are placeholders, updated on close().
     */
    private void writeWavHeader() throws IOException {
        ByteBuffer header = ByteBuffer.allocate(44);
        header.order(ByteOrder.LITTLE_ENDIAN);

        // RIFF chunk
        header.put("RIFF".getBytes());
        header.putInt(0);           // Placeholder: file size - 8
        header.put("WAVE".getBytes());

        // fmt sub-chunk
        header.put("fmt ".getBytes());
        header.putInt(16);          // Sub-chunk size (PCM = 16)
        header.putShort((short) 1); // Audio format: PCM = 1
        header.putShort((short) NUM_CHANNELS);
        header.putInt(SAMPLE_RATE);
        header.putInt(BYTE_RATE);
        header.putShort((short) BLOCK_ALIGN);
        header.putShort((short) BITS_PER_SAMPLE);

        // data sub-chunk
        header.put("data".getBytes());
        header.putInt(0);           // Placeholder: data size

        raf.write(header.array());
    }

    /**
     * Feed a single RTP payload (PCMA bytes, no RTP header).
     * Decodes A-law to 16-bit PCM and appends to WAV.
     */
    public synchronized void feedPayload(byte[] alawPayload) throws IOException {
        if (closed || alawPayload == null || alawPayload.length == 0) return;

        byte[] pcmData = AudioFormatUtils.alawToPcmArray(alawPayload, alawPayload.length);
        // alawToPcmArray emits BIG-endian PCM16 (correct for the JavaSound
        // speaker line, which is opened with a big-endian AudioFormat). WAV/RIFF
        // PCM is ALWAYS little-endian, and writeWavHeader() declares PCM (format
        // tag 1). Without swapping, every recorded sample is stored high-byte
        // first and plays back as full-scale noise. Swap each 16-bit sample to
        // little-endian before writing.
        for (int i = 0; i + 1 < pcmData.length; i += 2) {
            byte hi = pcmData[i];
            pcmData[i] = pcmData[i + 1];
            pcmData[i + 1] = hi;
        }
        raf.write(pcmData);
        totalPcmBytes += pcmData.length;
    }

    /**
     * Finalize the WAV file by updating the header with correct sizes.
     */
    @Override
    public synchronized void close() throws IOException {
        if (closed) return;
        closed = true;

        // Update RIFF chunk size (file size - 8)
        raf.seek(4);
        writeIntLE((int) (36 + totalPcmBytes));

        // Update data sub-chunk size
        raf.seek(40);
        writeIntLE((int) totalPcmBytes);

        raf.close();

        System.out.println("[RtpWavRecorder] WAV file saved: " + filePath);
        System.out.println("[RtpWavRecorder] Total PCM bytes: " + totalPcmBytes);
        long durationMs = (totalPcmBytes * 1000L) / BYTE_RATE;
        System.out.println("[RtpWavRecorder] Duration: " + (durationMs / 1000.0) + " seconds");
    }

    private void writeIntLE(int value) throws IOException {
        byte[] buf = new byte[4];
        buf[0] = (byte) (value & 0xFF);
        buf[1] = (byte) ((value >> 8) & 0xFF);
        buf[2] = (byte) ((value >> 16) & 0xFF);
        buf[3] = (byte) ((value >> 24) & 0xFF);
        raf.write(buf);
    }

    public String getFilePath() {
        return filePath;
    }

    public long getTotalPcmBytes() {
        return totalPcmBytes;
    }
}
