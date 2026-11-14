package sipinspector.Threads;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;

import javax.swing.JOptionPane;

import sipinspector.GlobalSettings;
import sipinspector.Utils.RemoteClient;
import sipinspector.Utils.TLSDebugLogger;

public class PurePcapRtpPlayer implements PcapRtpPlayer {

    @Override
    public void play(RTPSenderThread owner) throws Exception {
        File file = new File(owner.file_path);

        if (!file.exists() || !file.isFile()) {
            if (TLSDebugLogger.isEnabled()) {
                TLSDebugLogger.log("");
                TLSDebugLogger.log("=== PURE PCAP ENGINE – FILE NOT FOUND ===");
                TLSDebugLogger.log("Source: PurePcapRtpPlayer.play");
                TLSDebugLogger.log("File: " + owner.file_path);
                TLSDebugLogger.log("");
            }

            JOptionPane.showMessageDialog(null,
                    "Problem opening pcap file. File not found: " + owner.file_path,
                    "Play Pcap",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (TLSDebugLogger.isEnabled()) {
            TLSDebugLogger.log("");
            TLSDebugLogger.log("=== PURE PCAP ENGINE START ===");
            TLSDebugLogger.log("Source: PurePcapRtpPlayer.play");
            TLSDebugLogger.log("File: " + owner.file_path);
            TLSDebugLogger.log("Continuous RTP: " + owner.continuousRTP);
            TLSDebugLogger.log("");
        }

        if (owner.getOrOpenSocket() == null) {
            JOptionPane.showMessageDialog(null,
                    "Problem opening RTP socket for pure PCAP playback. Check local IP/media port settings.",
                    "Play Pcap",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        DataInputStream in = null;
        BufferedInputStream bin = null;
        long packetCount = 0;
        long udpPacketCount = 0;
        long cnt = 0;
        long nextPacketDueInms = 0;
        long lastTimestampMs = -1;
        long restartCount = 0;
        long numRTPPackets;
        try {
            numRTPPackets = Long.parseLong(GlobalSettings.getSetting("numRTPPackets"));
        } catch (NumberFormatException exc) {
            numRTPPackets = 1;
        }

        try {
            bin = new BufferedInputStream(new FileInputStream(file));
            in = new DataInputStream(bin);

            int magic = in.readInt();
            boolean littleEndian;

            if (magic == 0xa1b2c3d4 || magic == 0xa1b23c4d) {
                littleEndian = false;
            } else if (magic == 0xd4c3b2a1 || magic == 0x4d3cb2a1) {
                littleEndian = true;
            } else {
                if (TLSDebugLogger.isEnabled()) {
                    TLSDebugLogger.log("");
                    TLSDebugLogger.log("=== PURE PCAP ENGINE – UNSUPPORTED MAGIC ===");
                    TLSDebugLogger.log("Magic: 0x" + Integer.toHexString(magic));
                    TLSDebugLogger.log("");
                }
                JOptionPane.showMessageDialog(null,
                        "Unsupported PCAP file format (magic=0x" + Integer.toHexString(magic) + ")",
                        "Play Pcap",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            int versionMajor = readUnsignedShort(in, littleEndian);
            int versionMinor = readUnsignedShort(in, littleEndian);
            int thisZone = readInt(in, littleEndian);
            int sigfigs = readInt(in, littleEndian);
            long snaplen = readUnsignedInt(in, littleEndian);
            long network = readUnsignedInt(in, littleEndian);

            if (TLSDebugLogger.isEnabled()) {
                TLSDebugLogger.log("");
                TLSDebugLogger.log("=== PURE PCAP ENGINE – GLOBAL HEADER ===");
                TLSDebugLogger.log("magic=0x" + Integer.toHexString(magic));
                TLSDebugLogger.log("littleEndian=" + littleEndian);
                TLSDebugLogger.log("version=" + versionMajor + "." + versionMinor);
                TLSDebugLogger.log("thisZone=" + thisZone + ", sigfigs=" + sigfigs);
                TLSDebugLogger.log("snaplen=" + snaplen + ", network=" + network);
                TLSDebugLogger.log("");
            }

            if (network != 1) {
                if (TLSDebugLogger.isEnabled()) {
                    TLSDebugLogger.log("");
                    TLSDebugLogger.log("=== PURE PCAP ENGINE – UNSUPPORTED LINKTYPE ===");
                    TLSDebugLogger.log("Only Ethernet (DLT_EN10MB=1) is supported. Found: " + network);
                    TLSDebugLogger.log("");
                }
                JOptionPane.showMessageDialog(null,
                        "Unsupported PCAP link-layer type (expected Ethernet / 1, found: " + network + ")",
                        "Play Pcap",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            int markLimit = (int) Math.min(file.length(), Integer.MAX_VALUE);
            bin.mark(markLimit);

            try {
                while (true) {
                    if (owner.threadStopped) {
                        break;
                    }

                    int tsSec;
                    int tsUsec;
                    long inclLen;
                    long origLen;

                    try {
                        tsSec = readInt(in, littleEndian);
                        tsUsec = readInt(in, littleEndian);
                        inclLen = readUnsignedInt(in, littleEndian);
                        origLen = readUnsignedInt(in, littleEndian);
                    } catch (EOFException eof) {
                        if (owner.continuousRTP && !owner.threadStopped) {
                            restartCount++;
                            if (TLSDebugLogger.isEnabled()) {
                                if (restartCount <= 10 || restartCount % 1000 == 0) {
                                    TLSDebugLogger.log("");
                                    TLSDebugLogger
                                            .log("=== PURE PCAP ENGINE – EOF, CONTINUOUS RTP RESTART (count="
                                                    + restartCount
                                                    + ") ===");
                                    TLSDebugLogger.log("File: " + owner.file_path);
                                    TLSDebugLogger.log("");
                                }
                            }
                            if (bin != null) {
                                try {
                                    bin.reset();
                                } catch (IOException resetExc) {
                                    if (TLSDebugLogger.isEnabled()) {
                                        TLSDebugLogger.log("");
                                        TLSDebugLogger.log("=== PURE PCAP ENGINE – RESET FAILED ===");
                                        TLSDebugLogger.log("Error: " + resetExc.toString());
                                        TLSDebugLogger.log("");
                                    }
                                    break;
                                }
                                lastTimestampMs = -1;
                                nextPacketDueInms = 0;
                                cnt = 0;
                                continue;
                            } else {
                                break;
                            }
                        }
                        break;
                    }

                    packetCount++;

                    if (inclLen < 0 || inclLen > snaplen || inclLen > (10 * 65535L)) {
                        if (TLSDebugLogger.isEnabled()) {
                            TLSDebugLogger.log("");
                            TLSDebugLogger.log("=== PURE PCAP ENGINE – INVALID PACKET LENGTH ===");
                            TLSDebugLogger
                                    .log("Packet #: " + packetCount + ", inclLen=" + inclLen + ", snaplen=" + snaplen);
                            TLSDebugLogger.log("");
                        }
                        break;
                    }

                    byte[] data = new byte[(int) inclLen];
                    in.readFully(data);

                    if (TLSDebugLogger.isEnabled() && packetCount <= 10) {
                        TLSDebugLogger.log("PURE PCAP ENGINE – Packet " + packetCount
                                + ": ts=" + tsSec + "." + tsUsec
                                + ", inclLen=" + inclLen
                                + ", origLen=" + origLen + ")");
                    }

                    if (data.length >= 14) {
                        int etherType = ((data[12] & 0xff) << 8) | (data[13] & 0xff);
                        if (etherType == 0x0800 && data.length >= 34) {
                            int ipOffset = 14;
                            int versionIhl = data[ipOffset] & 0xff;
                            int version = (versionIhl >> 4) & 0x0f;
                            int ihl = versionIhl & 0x0f;
                            int ipHeaderLen = ihl * 4;
                            if (version == 4 && ihl >= 5 && data.length >= ipOffset + ipHeaderLen + 8) {
                                int protocolField = data[ipOffset + 9] & 0xff;
                                if (protocolField == 17) {
                                    int srcIpOffset = ipOffset + 12;
                                    int dstIpOffset = ipOffset + 16;
                                    String srcIp = (data[srcIpOffset] & 0xff) + "."
                                            + (data[srcIpOffset + 1] & 0xff) + "."
                                            + (data[srcIpOffset + 2] & 0xff) + "."
                                            + (data[srcIpOffset + 3] & 0xff);
                                    String dstIp = (data[dstIpOffset] & 0xff) + "."
                                            + (data[dstIpOffset + 1] & 0xff) + "."
                                            + (data[dstIpOffset + 2] & 0xff) + "."
                                            + (data[dstIpOffset + 3] & 0xff);

                                    int udpOffset = ipOffset + ipHeaderLen;
                                    int srcPort = ((data[udpOffset] & 0xff) << 8) | (data[udpOffset + 1] & 0xff);
                                    int dstPort = ((data[udpOffset + 2] & 0xff) << 8) | (data[udpOffset + 3] & 0xff);
                                    int udpLenField = ((data[udpOffset + 4] & 0xff) << 8)
                                            | (data[udpOffset + 5] & 0xff);
                                    int udpPayloadLen = udpLenField - 8;
                                    if (udpPayloadLen < 0) {
                                        udpPayloadLen = 0;
                                    }
                                    udpPacketCount++;

                                    if (TLSDebugLogger.isEnabled() && udpPacketCount <= 10) {
                                        TLSDebugLogger.log("PURE PCAP ENGINE – UDP " + udpPacketCount
                                                + " src=" + srcIp + ":" + srcPort
                                                + " dst=" + dstIp + ":" + dstPort
                                                + ", payloadLen=" + udpPayloadLen);
                                    }

                                    if (udpPayloadLen > 0 && data.length >= udpOffset + 8 + udpPayloadLen) {
                                        int rtpOffset = udpOffset + 8;
                                        int rtpLength = udpPayloadLen;

                                        RemoteClient[] array = owner.remoteClients.toArray(new RemoteClient[0]);
                                        boolean sentFlag = false;
                                        for (RemoteClient client : array) {
                                            if (client.getRemAddress() != null
                                                    && client.getRemPort() != 0
                                                    && !"0.0.0.0".equals(client.getRemAddress().getHostAddress())) {
                                                DatagramPacket packet = new DatagramPacket(
                                                        data,
                                                        rtpLength,
                                                        client.getRemAddress(),
                                                        client.getRemPort());
                                                packet.setData(data, rtpOffset, rtpLength);
                                                owner.socket.send(packet);
                                                owner.captureOutgoingRtp(data, rtpOffset, rtpLength,
                                                        client.getRemAddress(), client.getRemPort());
                                                sentFlag = true;
                                            }
                                        }

                                        if (sentFlag) {
                                            owner.recordSentPacket(data, rtpOffset, rtpLength);
                                            long tsMs = (tsSec & 0xffffffffL) * 1000L + (tsUsec / 1000L);
                                            if (lastTimestampMs >= 0) {
                                                long delta = tsMs - lastTimestampMs;
                                                if (delta < 0) {
                                                    delta = 0;
                                                }
                                                nextPacketDueInms += delta;
                                                cnt++;
                                            }
                                            lastTimestampMs = tsMs;

                                            if (cnt > 0 && cnt % numRTPPackets == 0) {
                                                if (nextPacketDueInms > 0) {
                                                    try {
                                                        Thread.sleep(nextPacketDueInms);
                                                    } catch (InterruptedException ie) {
                                                        // ignore
                                                    }
                                                }
                                                nextPacketDueInms = 0;
                                                cnt = 0;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (EOFException eof) {
                // Normal end-of-file
            }

            if (TLSDebugLogger.isEnabled()) {
                TLSDebugLogger.log("");
                TLSDebugLogger.log("=== PURE PCAP ENGINE – PARSE COMPLETE ===");
                TLSDebugLogger.log("File: " + owner.file_path);
                TLSDebugLogger.log("Total packets read: " + packetCount);
                TLSDebugLogger.log("Total UDP packets detected: " + udpPacketCount);
                TLSDebugLogger.log("Total continuous restarts: " + restartCount);
                TLSDebugLogger.log("NOTE: Pure engine currently replays UDP/RTP payloads based on PCAP timestamps.");
                TLSDebugLogger.log("");
            }

            // Do NOT pop a modal dialog on normal completion: this runs at the end
            // of every tone/pcap playback, on the scenario thread, and a modal
            // here stalls automated/looping campaigns (and floods dialogs at high
            // call volume) until manually dismissed. Log it instead.
            if (TLSDebugLogger.isEnabled()) {
                TLSDebugLogger.log("[PurePcap] parsed " + packetCount + " packets (UDP: " + udpPacketCount
                        + ") and sent RTP payloads using PCAP timing.");
            }

        } catch (IOException exc) {
            if (TLSDebugLogger.isEnabled()) {
                TLSDebugLogger.log("");
                TLSDebugLogger.log("=== PURE PCAP ENGINE – IO ERROR ===");
                TLSDebugLogger.log("File: " + owner.file_path);
                TLSDebugLogger.log("Error: " + exc.toString());
                TLSDebugLogger.log("");
            }
            JOptionPane.showMessageDialog(null,
                    "Problem reading pcap file. " + System.getProperty("line.separator")
                            + " - File: " + owner.file_path + System.getProperty("line.separator")
                            + " - " + exc + System.getProperty("line.separator"),
                    "Play Pcap",
                    JOptionPane.ERROR_MESSAGE);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignore) {
                    // ignore
                }
            }
            owner.closeRtpSocket();
        }
    }

    private int readUnsignedShort(DataInputStream in, boolean littleEndian) throws IOException {
        int b1 = in.readUnsignedByte();
        int b2 = in.readUnsignedByte();
        if (littleEndian) {
            return (b2 << 8) | b1;
        }
        return (b1 << 8) | b2;
    }

    private int readInt(DataInputStream in, boolean littleEndian) throws IOException {
        int b1 = in.readUnsignedByte();
        int b2 = in.readUnsignedByte();
        int b3 = in.readUnsignedByte();
        int b4 = in.readUnsignedByte();
        if (littleEndian) {
            return (b4 << 24) | (b3 << 16) | (b2 << 8) | b1;
        }
        return (b1 << 24) | (b2 << 16) | (b3 << 8) | b4;
    }

    private long readUnsignedInt(DataInputStream in, boolean littleEndian) throws IOException {
        int value = readInt(in, littleEndian);
        return value & 0xffffffffL;
    }
}
