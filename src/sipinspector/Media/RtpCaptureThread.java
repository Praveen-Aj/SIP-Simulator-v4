/*
 *  SIP Inspector - RTP Capture Socket
 *  A DatagramSocket wrapper that captures incoming RTP for recording.
 *  Listens on the media port, records PCMA payloads, then forwards.
 */
package sipinspector.Media;

import java.net.*;
import java.io.IOException;

/**
 * Standalone RTP capture thread: opens its own DatagramSocket on a specified port,
 * receives RTP packets, extracts PCMA payload, and feeds them to a {@link RtpWavRecorder}.
 *
 * Run this INSTEAD of (or alongside) LiveAudioController when you want to record.
 */
public class RtpCaptureThread extends Thread {

    private final int listenPort;
    private final InetAddress bindAddress;
    private final RtpWavRecorder recorder;
    private DatagramSocket socket;
    private volatile boolean running = false;
    private long packetsReceived = 0;

    private static final int RTP_HEADER_SIZE = 12;
    private static final int BUFFER_SIZE = 4096;

    public RtpCaptureThread(InetAddress bindAddress, int listenPort, RtpWavRecorder recorder) {
        this.bindAddress = bindAddress;
        this.listenPort = listenPort;
        this.recorder = recorder;
        setName("RtpCapture-:" + listenPort);
        setDaemon(true);
    }

    @Override
    public void run() {
        try {
            socket = new DatagramSocket(listenPort, bindAddress);
            socket.setReuseAddress(true);
            socket.setSoTimeout(200);
            running = true;

            System.out.println("[RtpCapture] Listening on " + bindAddress.getHostAddress() + ":" + listenPort);

            byte[] buffer = new byte[BUFFER_SIZE];

            while (running) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    byte[] data = packet.getData();
                    int len = packet.getLength();

                    if (len < RTP_HEADER_SIZE) continue;

                    // Validate RTP version
                    int version = (data[0] & 0xC0) >> 6;
                    if (version != 2) continue;

                    // Mirror incoming RTP into an active .pcap capture (any payload type)
                    try {
                        sipinspector.Utils.PcapCapture cap = sipinspector.Utils.PcapCapture.getInstance();
                        if (cap.isCapturing()) {
                            cap.writePacket(packet.getAddress(), packet.getPort(), bindAddress, listenPort,
                                    sipinspector.Utils.PcapCapture.UDP, data, 0, len);
                        }
                    } catch (Exception ignore) {
                    }

                    // Check payload type (8 = PCMA)
                    int pt = data[1] & 0x7F;
                    if (pt != 8) continue;

                    // Extract payload
                    int payloadLen = len - RTP_HEADER_SIZE;
                    byte[] payload = new byte[payloadLen];
                    System.arraycopy(data, RTP_HEADER_SIZE, payload, 0, payloadLen);

                    recorder.feedPayload(payload);
                    packetsReceived++;

                } catch (SocketTimeoutException e) {
                    // Normal timeout, continue loop
                } catch (SocketException se) {
                    if (running) {
                        System.err.println("[RtpCapture] Socket error: " + se.getMessage());
                    }
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("[RtpCapture] Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            System.out.println("[RtpCapture] Stopped. Total packets captured: " + packetsReceived);
        }
    }

    public void stopCapture() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    public DatagramSocket getSocket() {
        return socket;
    }

    public long getPacketsReceived() {
        return packetsReceived;
    }
}
