/*
 *  SIP Inspector - RTP Audio Recorder
 *  Records incoming and outgoing RTP audio (PCMA, PCMU, G.722) to WAV files
 *  Supports verification playback and audio energy / silence metrics
 */
package sipinspector.Media;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import javax.sound.sampled.*;

/**
 * Records raw RTP audio payloads to a standard RIFF/WAV file.
 * Supports G.711 A-law (PT=8), G.711 u-law (PT=0), and G.722 (PT=9).
 * Computes audio energy (RMS) and peak amplitude to verify voice presence.
 *
 * WAV format: 16-bit signed Little-Endian PCM, mono.
 */
public class RtpWavRecorder implements AutoCloseable {

    private final RandomAccessFile raf;
    private final String filePath;
    private final int sampleRate;
    private final int defaultPayloadType;
    private final int byteRate;
    private final int blockAlign;

    private long totalPcmBytes = 0;
    private long totalPackets = 0;
    private boolean closed = false;

    // Audio energy metrics
    private double sumSquareSamples = 0.0;
    private long totalSamplesAnalyzed = 0;
    private int peakAmplitude = 0;

    private static final int NUM_CHANNELS = 1;
    private static final int BITS_PER_SAMPLE = 16;

    public RtpWavRecorder(String filePath) throws IOException {
        this(filePath, 8000, AudioFormatUtils.RTP_PAYLOAD_TYPE_PCMA);
    }

    public RtpWavRecorder(String filePath, int sampleRate, int defaultPayloadType) throws IOException {
        this.filePath = filePath;
        this.sampleRate = (sampleRate > 0) ? sampleRate : 8000;
        this.defaultPayloadType = defaultPayloadType;
        this.byteRate = this.sampleRate * NUM_CHANNELS * (BITS_PER_SAMPLE / 8);
        this.blockAlign = NUM_CHANNELS * (BITS_PER_SAMPLE / 8);

        File f = new File(filePath);
        File parent = f.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        this.raf = new RandomAccessFile(filePath, "rw");
        this.raf.setLength(0); // Truncate if existing
        writeWavHeader();
    }

    private void writeWavHeader() throws IOException {
        ByteBuffer header = ByteBuffer.allocate(44);
        header.order(ByteOrder.LITTLE_ENDIAN);

        // RIFF chunk
        header.put("RIFF".getBytes("US-ASCII"));
        header.putInt(0); // Placeholder: file size - 8
        header.put("WAVE".getBytes("US-ASCII"));

        // fmt sub-chunk
        header.put("fmt ".getBytes("US-ASCII"));
        header.putInt(16); // Sub-chunk size
        header.putShort((short) 1); // PCM = 1
        header.putShort((short) NUM_CHANNELS);
        header.putInt(sampleRate);
        header.putInt(byteRate);
        header.putShort((short) blockAlign);
        header.putShort((short) BITS_PER_SAMPLE);

        // data sub-chunk
        header.put("data".getBytes("US-ASCII"));
        header.putInt(0); // Placeholder: data size

        raf.write(header.array());
    }

    /**
     * Feed a single RTP payload using default payload type.
     */
    public synchronized void feedPayload(byte[] payload) throws IOException {
        feedPayload(payload, defaultPayloadType);
    }

    /**
     * Feed a single RTP payload with explicit payload type (PCMA, PCMU, G.722).
     */
    public synchronized void feedPayload(byte[] payload, int pt) throws IOException {
        if (closed || payload == null || payload.length == 0) return;

        byte[] pcmData = null;

        if (pt == AudioFormatUtils.RTP_PAYLOAD_TYPE_PCMU) {
            pcmData = AudioFormatUtils.ulawToPcmArray(payload, payload.length);
        } else if (pt == AudioFormatUtils.RTP_PAYLOAD_TYPE_G722) {
            G722Codec g722 = new G722Codec();
            pcmData = g722.decode(payload, payload.length);
        } else {
            // Default: PCMA (PT=8)
            pcmData = AudioFormatUtils.alawToPcmArray(payload, payload.length);
        }

        if (pcmData == null || pcmData.length == 0) return;

        // Big-endian PCM to little-endian swap for WAV storage
        // Also compute audio energy and peak amplitude
        for (int i = 0; i + 1 < pcmData.length; i += 2) {
            byte hi = pcmData[i];
            byte lo = pcmData[i + 1];

            short sampleBig = (short) (((hi & 0xFF) << 8) | (lo & 0xFF));
            int abs = Math.abs(sampleBig);
            if (abs > peakAmplitude) {
                peakAmplitude = abs;
            }
            sumSquareSamples += (double) sampleBig * (double) sampleBig;
            totalSamplesAnalyzed++;

            // Swap to Little-Endian for WAV
            pcmData[i] = lo;
            pcmData[i + 1] = hi;
        }

        raf.write(pcmData);
        totalPcmBytes += pcmData.length;
        totalPackets++;
    }

    /**
     * Feed raw 16-bit PCM (big-endian) directly.
     */
    public synchronized void feedRawPcm(byte[] pcmBigEndian, int length) throws IOException {
        if (closed || pcmBigEndian == null || length <= 0) return;

        byte[] copy = new byte[length];
        System.arraycopy(pcmBigEndian, 0, copy, 0, length);

        for (int i = 0; i + 1 < length; i += 2) {
            byte hi = copy[i];
            byte lo = copy[i + 1];

            short sample = (short) (((hi & 0xFF) << 8) | (lo & 0xFF));
            int abs = Math.abs(sample);
            if (abs > peakAmplitude) {
                peakAmplitude = abs;
            }
            sumSquareSamples += (double) sample * (double) sample;
            totalSamplesAnalyzed++;

            // Swap to Little-Endian
            copy[i] = lo;
            copy[i + 1] = hi;
        }

        raf.write(copy);
        totalPcmBytes += length;
        totalPackets++;
    }

    /**
     * Calculate RMS (Root Mean Square) audio amplitude.
     */
    public synchronized double getRms() {
        if (totalSamplesAnalyzed == 0) return 0.0;
        return Math.sqrt(sumSquareSamples / totalSamplesAnalyzed);
    }

    public synchronized double getRmsEnergy() {
        return getRms();
    }

    public synchronized int getPeakAmplitude() {
        return peakAmplitude;
    }

    /**
     * Determine if recording contains genuine audio vs. silence.
     * Standard threshold: RMS > 100 and peak > 500 indicates active speech/tone.
     */
    public synchronized boolean isSilent(double rmsThreshold) {
        return getRms() < rmsThreshold;
    }

    public synchronized boolean isSilent() {
        return isSilent(100.0);
    }

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

    public String getWavPath() {
        return filePath;
    }

    public long getTotalPcmBytes() {
        return totalPcmBytes;
    }

    public long getTotalPackets() {
        return totalPackets;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public double getDurationSeconds() {
        return byteRate > 0 ? (double) totalPcmBytes / (double) byteRate : 0.0;
    }

    /**
     * Playback a WAV recording through physical speakers for verification.
     */
    public static void playWavFile(String wavPath) throws Exception {
        File file = new File(wavPath);
        if (!file.exists()) {
            throw new FileNotFoundException("WAV file not found: " + wavPath);
        }

        try (AudioInputStream ais = AudioSystem.getAudioInputStream(file)) {
            AudioFormat format = ais.getFormat();
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            SourceDataLine line;
            if (AudioSystem.isLineSupported(info)) {
                line = (SourceDataLine) AudioSystem.getLine(info);
            } else {
                line = AudioDeviceManager.openSpeakerOutput((int) format.getSampleRate());
            }
            line.open(format);
            line.start();

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = ais.read(buffer, 0, buffer.length)) != -1) {
                line.write(buffer, 0, bytesRead);
            }

            line.drain();
            line.stop();
            line.close();
        }
    }
}
