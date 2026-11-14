/*
 *  SIP Inspector - tool for VoIP engineers to create, duplicate and 
 *  troubleshoot SIP signaling. 
 * 
 *  Copyright (C) 2008  Zarko Coklin (zarko_coklin at yahoo dot com)
 * 
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 * 
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 * 
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package sipinspector.Threads;

import java.io.IOException;
import java.math.BigInteger;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Vector;

import javax.swing.JOptionPane;

import jpcap.JpcapCaptor;
import jpcap.packet.Packet;
import jpcap.packet.UDPPacket;
import sipinspector.GlobalSettings;
import sipinspector.Utils.PcapCapture;
import sipinspector.Utils.RemoteClient;
import sipinspector.Utils.TLSDebugLogger;

/**
 *
 * @author Zarko Coklin
 */
public class RTPSenderThread extends Thread {

    private static volatile RTPSenderThread activeInstance = null;

    public static RTPSenderThread getActiveInstance() {
        return activeInstance;
    }

    private volatile long packetsSent = 0;
    private volatile long bytesSent = 0;
    private volatile int ssrc = 0;
    private volatile int payloadType = 0;

    public void recordSentPacket(byte[] data, int offset, int length) {
        packetsSent++;
        bytesSent += length;
        if (length >= 12) {
            payloadType = data[offset + 1] & 0x7F;
            ssrc = ((data[offset + 8] & 0xFF) << 24) |
                   ((data[offset + 9] & 0xFF) << 16) |
                   ((data[offset + 10] & 0xFF) << 8)  |
                   (data[offset + 11] & 0xFF);
        }
    }

    /**
     * If a .pcap capture is active, write this outgoing RTP packet to it
     * using the RTP socket's local address/media port as the source.
     */
    void captureOutgoingRtp(byte[] data, int offset, int length, InetAddress dst, int dstPort) {
        try {
            PcapCapture cap = PcapCapture.getInstance();
            if (!cap.isCapturing() || socket == null || dst == null) {
                return;
            }
            cap.writePacket(socket.getLocalAddress(), socket.getLocalPort(), dst, dstPort,
                    PcapCapture.UDP, data, offset, length);
        } catch (Exception ignore) {
        }
    }

    public long getPacketsSent() { return packetsSent; }
    public long getBytesSent() { return bytesSent; }
    public int getSsrc() { return ssrc; }
    public int getPayloadType() { return payloadType; }
    public String getFilePath() { return file_path; }

    public boolean isPlaying() {
        return threadActivated && !threadStopped;
    }

    public RTPSenderThread() {
        threadStopped = false;
        remoteClients = new Vector();
        threadActivated = false;
        setPriority(MAX_PRIORITY);
    }

    @Override
    @SuppressWarnings("SleepWhileHoldingLock")
    public void run() {
        activeInstance = this;
        PcapRtpPlayer player;
        String engine = GlobalSettings.getPcapEngine();
        if ("pure".equalsIgnoreCase(engine)) {
            player = new PurePcapRtpPlayer();
        } else {
            player = new JpcapRtpPlayer();
        }
        threadActivated = true;
        sipinspector.Utils.LogManager.logRtpEvent("RTP/tone playback STARTED"
                + System.lineSeparator() + "File: " + file_path
                + System.lineSeparator() + "Continuous: " + continuousRTP
                + System.lineSeparator() + "Engine: " + engine);
        try {
            player.play(this);
        } catch (Exception exc) {
            if (TLSDebugLogger.isEnabled()) {
                TLSDebugLogger.logException("RTPSenderThread.run exception", exc);
            }
        } finally {
            sipinspector.Utils.LogManager.logRtpEvent("RTP/tone playback STOPPED"
                    + System.lineSeparator() + "File: " + file_path
                    + System.lineSeparator() + "Packets sent: " + packetsSent
                    + System.lineSeparator() + "Bytes sent: " + bytesSent
                    + System.lineSeparator() + "Payload type: " + payloadType
                    + System.lineSeparator() + "SSRC: 0x" + Integer.toHexString(ssrc));
            activeInstance = null;
            threadActivated = false;
        }
    }

    private static class JpcapRtpPlayer implements PcapRtpPlayer {

        @Override
        public void play(RTPSenderThread owner) throws Exception {
            JpcapCaptor captor = null;
            long nextPacketDueInms = 0;
            Packet currentPacket;
            Packet nextPacket;
            boolean sentFlag = false;
            long cnt = 0;
            long numRTPPackets;

            numRTPPackets = Integer.parseInt(GlobalSettings.getSetting("numRTPPackets"));

            if (TLSDebugLogger.isEnabled()) {
                TLSDebugLogger.log("");
                TLSDebugLogger.log("=== PCAP RTP THREAD START ===");
                TLSDebugLogger.log("Source: RTPSenderThread.run");
                TLSDebugLogger.log("File: " + owner.file_path);
                TLSDebugLogger.log("Continuous RTP: " + owner.continuousRTP);
                TLSDebugLogger.log("numRTPPackets: " + numRTPPackets);
                TLSDebugLogger.log("");
            }

            owner.threadActivated = true;
            if ((owner.socket = owner.openRTPSocket()) == null) {
                return;
            }

            if ((captor = owner.loadCaptureFile()) == null) {
                owner.closeSocket();
                return;
            }

            currentPacket = captor.getPacket();
            if (currentPacket == null || currentPacket == UDPPacket.EOF) {
                owner.closeSocket();
                owner.captorClose(captor);
                return;
            }

            try {
                while (owner.threadStopped == false) {
                    /*
                     * Get all remote IP addresses and ports and send the packet to all of them
                     */
                    sentFlag = false;
                    RemoteClient[] array = owner.remoteClients.toArray(new RemoteClient[0]);
                    for (RemoteClient client : array) {
                        /* if put on hold do not send packets */
                        if (client.getRemAddress().getHostAddress().equals("0.0.0.0") == false) {
                            DatagramPacket packet = new DatagramPacket(currentPacket.data, currentPacket.data.length,
                                    client.getRemAddress(), client.getRemPort());
                            owner.socket.send(packet);
                            owner.captureOutgoingRtp(currentPacket.data, 0, currentPacket.data.length,
                                    client.getRemAddress(), client.getRemPort());
                            /* Detect the case when there is at least one remote client */
                            sentFlag = true;
                        }
                    }

                    /* If packet was sent, read another one from the file */
                    if (sentFlag == true) {
                        owner.recordSentPacket(currentPacket.data, 0, currentPacket.data.length);
                        /*
                         * read a next packet from the capture file and prepare it for sending
                         */
                        nextPacket = captor.getPacket();
                        if (nextPacket == null || nextPacket == UDPPacket.EOF) {
                            if (TLSDebugLogger.isEnabled()) {
                                TLSDebugLogger.log("");
                                TLSDebugLogger.log("=== PCAP EOF REACHED ===");
                                TLSDebugLogger.log("Source: RTPSenderThread.run");
                                TLSDebugLogger.log("Continuous RTP: " + owner.continuousRTP);
                                TLSDebugLogger.log("");
                            }
                            /*
                             * once the end of the file is reached, open the file again and keep looping
                             * through packets until SIP scenario says it is enough
                             */
                            owner.captorClose(captor);
                            /* If a continuous RTP play command, quit */
                            if (owner.continuousRTP == false) {
                                owner.threadPaused = false;
                                while (owner.threadPaused == false && owner.threadStopped == false) {
                                    Thread.sleep(20);
                                }
                            }

                            /* If continuous RTP, reload the file again */
                            if ((captor = owner.loadCaptureFile()) == null) {
                                owner.closeSocket();
                                return;
                            }
                            nextPacket = captor.getPacket();
                        } else {
                            ++cnt;
                            nextPacketDueInms += (nextPacket.sec - currentPacket.sec) * 1000
                                    + (nextPacket.usec - currentPacket.usec) / 1000;
                        }
                        currentPacket = nextPacket;

                    }

                    if (cnt % numRTPPackets == 0) {
                        Thread.sleep(nextPacketDueInms);
                        nextPacketDueInms = 0;
                        cnt = 0;
                    }
                }
            } catch (Exception exc) {
                if (TLSDebugLogger.isEnabled()) {
                    TLSDebugLogger.logException("RTPSenderThread.run exception", exc);
                }
                owner.closeSocket();
                owner.captorClose(captor);
                return;
            }
            owner.closeSocket();
            owner.captorClose(captor);
        }
    }

    private JpcapCaptor loadCaptureFile() {
        JpcapCaptor captor = null;

        try {
            captor = JpcapCaptor.openFile(file_path);
            if (TLSDebugLogger.isEnabled()) {
                TLSDebugLogger.log("");
                TLSDebugLogger.log("=== PCAP FILE OPENED SUCCESSFULLY ===");
                TLSDebugLogger.log("File: " + file_path);
                TLSDebugLogger.log("");
            }
        } catch (IOException exc) {
            if (TLSDebugLogger.isEnabled()) {
                TLSDebugLogger.log("");
                TLSDebugLogger.log("=== PCAP LOAD FAILED (IOEXCEPTION) ===");
                TLSDebugLogger.log("File: " + file_path);
                TLSDebugLogger.log("Error: " + exc.toString());
                TLSDebugLogger.log("");
            }
            JOptionPane.showMessageDialog(null,
                    "Problem opening pcap file. " + System.getProperty("line.separator") + " - File: " + file_path
                            + System.getProperty("line.separator") + " - " + exc + System.getProperty("line.separator"),
                    "Play Pcap", JOptionPane.ERROR_MESSAGE);
            closeSocket();
            return null;
        } catch (UnsatisfiedLinkError exc) {
            if (TLSDebugLogger.isEnabled()) {
                TLSDebugLogger.log("");
                TLSDebugLogger.log("=== PCAP LOAD FAILED (JPcap missing) ===");
                TLSDebugLogger.log("File: " + file_path);
                TLSDebugLogger.log("Error: " + exc.toString());
                TLSDebugLogger.log("OS arch: " + System.getProperty("os.arch") + ", data.model: "
                        + System.getProperty("sun.arch.data.model"));
                TLSDebugLogger.log("user.dir: " + System.getProperty("user.dir"));
                TLSDebugLogger.log("java.library.path: " + System.getProperty("java.library.path"));
                TLSDebugLogger.log("jpcap.jar location: "
                        + String.valueOf(JpcapCaptor.class.getProtectionDomain().getCodeSource().getLocation()));
                TLSDebugLogger.log("");
            }
            JOptionPane.showMessageDialog(null,
                    "Problem opening pcap file. " + System.getProperty("line.separator")
                            + "- Native JPcap libraries are missing. " + System.getProperty("line.separator")
                            + "- Please install Npcap and enable 'WinPcap API-compatible mode' during installation.",
                    "Play Pcap", JOptionPane.ERROR_MESSAGE);
            closeSocket();
            return null;
        }
        return captor;
    }

    private DatagramSocket openRTPSocket() {
        DatagramSocket tmpSocket;

        try {
            InetAddress localAddress = InetAddress.getByName(GlobalSettings.getLocalIP());
            int localPort;
            try {
                localPort = Integer.parseInt(GlobalSettings.getMediaPort());
            } catch (NumberFormatException exc) {
                localPort = 6000;
            }
            tmpSocket = new DatagramSocket(localPort, localAddress);
            BigInteger bi = new BigInteger(GlobalSettings.getSetting("QoS"), 16);
            tmpSocket.setTrafficClass(bi.intValue());
            tmpSocket.setReuseAddress(true);
        } catch (SocketException ex) {
            JOptionPane.showMessageDialog(null, ex, "RTP sender1", JOptionPane.WARNING_MESSAGE);
            tmpSocket = null;
        } catch (UnknownHostException exc) {
            JOptionPane.showMessageDialog(null, exc, "RTP sender2", JOptionPane.WARNING_MESSAGE);
            tmpSocket = null;
        }

        return tmpSocket;
    }

    DatagramSocket getOrOpenSocket() {
        if (socket == null || socket.isClosed()) {
            socket = openRTPSocket();
        }
        return socket;
    }

    public void setFilePath(String path) {
        file_path = path;

        if (TLSDebugLogger.isEnabled()) {
            TLSDebugLogger.log("");
            TLSDebugLogger.log("=== PCAP FILE PATH SET ===");
            TLSDebugLogger.log("Source: RTPSenderThread.setFilePath");
            TLSDebugLogger.log("File: " + file_path);
            TLSDebugLogger.log("");
        }
    }

    public void stopThread() {
        threadStopped = true;
        threadPaused = false;
    }

    public void shutdown() {
        stopThread();
        closeSocket();
    }

    public void addRemoteClient(RemoteClient client) {
        if (client.getRemAddress() != null && client.getRemPort() != 0) {
            remoteClients.add(client);
        } else {
            JOptionPane.showMessageDialog(null,
                    "RTP destination has an invalid IP address or port. See Options/Remote RTP...!",
                    "Remote RTP destination...", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void delRemoteClient(RemoteClient client) {
        remoteClients.remove(client);
    }

    private void closeSocket() {
        if (socket != null) {
            socket.close();
        }
        threadPaused = false;
    }

    void closeRtpSocket() {
        closeSocket();
    }

    public void setRunningFlag(boolean value) {
        threadPaused = value;
    }

    public void setContinuousRTPFlag(boolean value) {
        continuousRTP = value;
    }

    public boolean getRunningFlag() {
        return threadPaused;
    }

    public boolean getActivatedFlag() {
        return threadActivated;
    }

    private void captorClose(JpcapCaptor captor) {
        if (captor != null) {
            captor.close();
        }
    }

    DatagramSocket socket = null;

    String file_path = null;

    Vector<RemoteClient> remoteClients;

    boolean threadStopped = false;

    boolean threadPaused = false;

    boolean threadActivated;

    boolean continuousRTP;
}
