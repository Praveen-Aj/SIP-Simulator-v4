/*
 *  SIP Inspector - tool for VoIP engineers to create, duplicate and
 *  troubleshoot SIP signaling.
 *
 *  Copyright (C) 2010  Zarko Coklin (zarko_coklin at yahoo dot com)
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
package sipinspector.Utils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import javax.swing.SwingWorker;

import sipinspector.SIPInspectorMainFrame;

/**
 *
 * @author zcoklin
 */
public class LoadScenario extends SwingWorker {

    public LoadScenario(SIPInspectorMainFrame frame,
            String path) {
        filePath = path;
        mainFrame = frame;
        TLSDebugLogger.log("SCENARIO LOAD REQUEST: path=" + filePath);
    }

    @Override
    protected Object doInBackground() throws Exception {
        TLSDebugLogger.log("SCENARIO LOAD START");
        mainFrame.getScenario().RemoveAllScenarioEntries(mainFrame.getTree());
        BufferedReader bis;
        String line = null;
        StringBuilder scenario = new StringBuilder(4096);
        try {
            if (filePath != null) {

                bis = new BufferedReader(new FileReader(filePath));
                TLSDebugLogger.log("SCENARIO FILE OPENED: path=" + filePath);
                while ((line = bis.readLine()) != null) {
                    scenario.append(line).append(System.getProperty("line.separator"));
                }
                bis.close();
            }
        } catch (IOException ex) {
            return null;
        }
        TLSDebugLogger.logf("SCENARIO LOAD COMPLETE: path=%s, length=%d", filePath,
                scenario.length());
        mainFrame.updateMessagesFromText(scenario.toString());
        return null;
    }

    private String filePath;

    private SIPInspectorMainFrame mainFrame;
}
