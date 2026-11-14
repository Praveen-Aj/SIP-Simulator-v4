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

/**
 * Abstraction for PCAP-based RTP playback. This allows RTPSenderThread to
 * delegate the actual PCAP reading and RTP send loop to an implementation
 * (e.g. legacy JPcap-based or a future pure-Java engine).
 */
public interface PcapRtpPlayer {

    /**
     * Execute PCAP-based RTP playback for the given RTPSenderThread instance.
     *
     * @param owner RTPSenderThread that owns socket, remote clients and flags.
     * @throws Exception if playback fails
     */
    void play(RTPSenderThread owner) throws Exception;
}
