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
/*
 * UpdateInfoDialog.java
 *
 * Created on Apr 13, 2009, 2:41:04 PM
 */
package sipinspector.versionUpdater;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.ProgressMonitor;
import javax.swing.ProgressMonitorInputStream;

/**
 *
 * @author Zarko Coklin
 */
public class DownloaderThread extends Thread {

    public DownloaderThread(JFrame mainFrame,
            JFrame frame,
            JFileChooser fc) {
        this.mainFrame = mainFrame;
        updateChecker = frame;
        myFC = fc;
    }

    @Override
    public void run() {

        FileOutputStream fw = null;
        try {
            fw = new FileOutputStream(myFC.getSelectedFile());
            URL u = new URL(CheckUpdate.getNewVersionURL());
            URLConnection uc = u.openConnection();
            InputStream in = uc.getInputStream();

            // Chain a ProgressMonitorInputStream to the
            // URLConnection's InputStream
            ProgressMonitorInputStream pin = new ProgressMonitorInputStream(updateChecker,
                    "Saving new SIP Simulator zipped file", in);

            // Set the maximum value of the ProgressMonitor
            ProgressMonitor pm = pin.getProgressMonitor();
            pm.setMaximum(uc.getContentLength());

            // Read the data and save it to a file
            int c;
            byte[] buffer = new byte[8192];
            while ((c = pin.read(buffer)) != -1) {
                fw.write(buffer, 0, c);
            }
            pin.close();
            fw.close();
            System.exit(0);
        } catch (MalformedURLException ex) {
            mainFrame.setVisible(true);
            updateChecker.dispose();
        } catch (FileNotFoundException ex) {
            mainFrame.setVisible(true);
            updateChecker.dispose();
        } catch (IOException ex) {
            mainFrame.setVisible(true);
            updateChecker.dispose();
        }
    }

    private JFrame updateChecker;

    private JFileChooser myFC;

    private JFrame mainFrame;
}
