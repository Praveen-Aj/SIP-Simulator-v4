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
package sipinspector.ScenarioEntries;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;

import javax.swing.JOptionPane;

import sipinspector.SIPCall;
import sipinspector.ScenarioProgressDialog;
import sipinspector.Threads.RTPSenderThread;
import sipinspector.Utils.TLSDebugLogger;

/**
 *
 * @author Zarko Coklin
 */
public class CommandPlayPcapEntry extends ScenarioEntry {

    public CommandPlayPcapEntry(String content) {
        super(TYPE.COMMAND_PLAYPCAP, DIRECTION.NONE);
        String str;
        path = "";
        filename = "";
        continuousRTP = false;

        BufferedReader reader = new BufferedReader(new StringReader(content));
        try {
            while ((str = reader.readLine()) != null) {
                if (str.startsWith("//") == true) {
                    continue;
                }
                if (str.startsWith("cmd_PlayPcap=") == true) {
                    setValues(str);
                    break;
                }
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null,
                    e,
                    "Update Play Pcap",
                    JOptionPane.ERROR_MESSAGE);
        }

        if (TLSDebugLogger.isEnabled()) {
            TLSDebugLogger.log("");
            TLSDebugLogger.log("=== PCAP COMMAND PARSED ===");
            TLSDebugLogger.log("Source: CommandPlayPcapEntry.<init>");
            TLSDebugLogger.log("Path: " + path);
            TLSDebugLogger.log("Filename: " + filename);
            TLSDebugLogger.log("Continuous RTP: " + continuousRTP);
            TLSDebugLogger.log("");
        }
    }

    private void setValues(String command) {
        String[] arguments = command.split(";");

        int pos = arguments[0].indexOf('=');
        if (pos == -1) {
            return;
        } else {
            path = arguments[0].substring(pos + 1);
            filename = getFilename(path);
        }

        if (arguments.length == 2) {
            if (arguments[1].equals("continuous=false") == true) {
                continuousRTP = false;
                return;
            }
        }
        continuousRTP = true;
    }

    static public String getFilename(String filePath) {
        int posSeparator;
        posSeparator = filePath.lastIndexOf(File.separatorChar);

        if (posSeparator == -1) {
            return "";
        }

        return filePath.substring(posSeparator + 1);
    }

    @Override
    public boolean processEntry(SIPCall call,
            ScenarioProgressDialog dialog) throws ScenarioException {
        if (TLSDebugLogger.isEnabled()) {
            TLSDebugLogger.log("");
            TLSDebugLogger.log("=== PCAP PLAYBACK ENTRY ===");
            TLSDebugLogger.log("Source: CommandPlayPcapEntry.processEntry");
            TLSDebugLogger.log("PCAP path: " + path);
            TLSDebugLogger.log("Continuous RTP: " + continuousRTP);
            TLSDebugLogger.log("");
        }

        this.increaseExpectedCnt();
        dialog.updateStats(this);
        if (dialog.getRTPThread().getActivatedFlag() == false) {
            dialog.getRTPThread().addRemoteClient(call.getRemoteClient());
            if (dialog.getRTPThread().getRunningFlag() == false) {
                dialog.startRTPThread(path, continuousRTP);
            }
        } else {
            dialog.restartRTPThread(path, continuousRTP, call);
        }

        call.nextEntry();
        return true;
    }

    @Override
    public String getTreeText() {
        if (continuousRTP == true) {
            return (" ---- Play pcap=" + filename + ";cont=true");
        } else {
            return (" ---- Play pcap=" + filename);
        }
    }

    @Override
    public String getScenarioScreenText() {
        StringBuilder txt = new StringBuilder(System.getProperty("line.separator")
                + "----------" + System.getProperty("line.separator")
                + "cmd_PlayPcap=" + path);
        if (continuousRTP == true) {
            txt.append(";continuous=true");
        } else {
            txt.append(";continuous=false");
        }
        txt.append(System.getProperty("line.separator"));
        return txt.toString();
    }

    @Override
    public String getShortCode() {
        if (continuousRTP == true) {
            return "Play pcap=" + filename + ";cont=true";
        } else {
            return "Play pcap=" + filename + ";cont=false";
        }
    }

    @Override
    public String getScenarioProgressScreenText() {
        return " ---- " + getShortCode();
    }

    @Override
    public void stop() {
        rtpThread.interrupt();
    }

    private String path;

    private String filename;

    RTPSenderThread rtpThread;

    boolean continuousRTP;
}
