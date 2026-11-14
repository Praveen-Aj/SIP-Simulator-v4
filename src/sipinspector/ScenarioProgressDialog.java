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
package sipinspector;

import sipinspector.Threads.ReceiverThread;
import sipinspector.Threads.CallGeneratorThread;
import sipinspector.ScenarioEntries.ScenarioEntry;
import sipinspector.Socket.MySocketAbstract;
import java.io.FileOutputStream;
import java.util.Vector;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JOptionPane;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import net.miginfocom.swing.MigLayout;
import sipinspector.Threads.CallMaintainerThread;
import sipinspector.Threads.PauseMaintainerThread;
import sipinspector.Threads.RTPSenderThread;
import sipinspector.Utils.LogManager;
import sipinspector.Media.LiveAudioController;

/**
 *
 * @author  Zarko Coklin
 */
public class ScenarioProgressDialog extends javax.swing.JDialog
{
    /** Creates new form ScenarioProgressDialog */
    public ScenarioProgressDialog(SIPInspectorMainFrame owner)
    {
        super(owner, false);
        initComponents();
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                stopThreads();
            }
        });
        SIPInspectorMainFrame.setIcon(this);
        initializeTable();
        mainFrame = owner;
        mySipScenario = owner.getScenario();
        mySocket = owner.getSocket();

        callDB = new CallDB(20, Integer.parseInt(mainFrame.getTotCalsText()),
            Integer.parseInt(mainFrame.getMaxConcurrCalls()));
        callsInProgress = new Vector();
        callGenThread = null;
        rxThread = null;
        callMaintainThread = null;
        pauseMaintainThread = null;

    }

    public void initializeTable()
    {
        int i;
        int numRows = mySipScenario.getEntriesCount();
        ScenarioEntry entry;
        TableColumn column;

        column = ScenarioProgressTable.getColumnModel().getColumn(0);
        //column.setPreferredWidth(250);

        for (i = 0; i < numRows; i++)
        {
            Object[] rowValues = new Object[3];
            entry = SIPScenario.getScenarioPos(i);
            rowValues[0] = entry.getScenarioProgressScreenText();
            rowValues[1] = entry.getExpectedCnt();
            rowValues[2] = entry.getUnexpectedCnt();
            ((DefaultTableModel) ScenarioProgressTable.getModel()).addRow(rowValues);
        }
    }

    public CallDB getCallDB()
    {
        return callDB;
    }

    public void resetButtons()
    {
        StopButton.setEnabled(true);
        StartButton.setEnabled(false);
    }

    public void disableStopEnableStart()
    {
        StopButton.setEnabled(false);
        StartButton.setEnabled(true);
    }

    public JTable getScenarioProgressTable()
    {
        return ScenarioProgressTable;
    }

    public void spawnThreads()
    {
        resetButtons();
        runTimeStamp = System.currentTimeMillis();
        rxThread = new ReceiverThread(this, mainFrame.getSocket());
        rxThread.start();

        callMaintainThread = new CallMaintainerThread(this);
        callMaintainThread.start();

        pauseMaintainThread = new PauseMaintainerThread(this);
        pauseMaintainThread.start();

        callGenThread = new CallGeneratorThread(this, mainFrame.getSocket());
        callGenThread.start();

        /* At this point just create an instance of RTP thread. Whether it
         * will ever be started depends whether scenario is supposed an RTP or
         * not
         */
        rtpThread = new RTPSenderThread();
        rtpThread.setPriority(Thread.MAX_PRIORITY);

        // Instantiate live audio controller (signaling layer owns lifecycle).
        liveAudioController = new LiveAudioController();

        // Start diagnostics file writer timer
        if (diagTimer != null) {
            diagTimer.cancel();
        }
        diagTimer = new java.util.Timer("WebRtpDiagTimer", true);
        diagTimer.scheduleAtFixedRate(new java.util.TimerTask() {
            @Override
            public void run() {
                sipinspector.Utils.RtpDiagnosticsWriter.writeUnifiedRtpDiagnostics();
            }
        }, 0, 500);
    }

    public void startRTPThread(String path,
        boolean continuousRTP)
    {
        rtpThread.setContinuousRTPFlag(continuousRTP);
        rtpThread.setFilePath(path);
        rtpThread.setRunningFlag(true);
        if (rtpThread.getActivatedFlag() == false)
        {
            rtpThread.start();
        }
    }

    public void restartRTPThread(String path,
        boolean continuousRTP,
        SIPCall call)
    {
        if (rtpThread != null)
        {
            rtpThread.stopThread();
        }
        rtpThread = new RTPSenderThread();
        rtpThread.setPriority(Thread.MAX_PRIORITY);
        rtpThread.setContinuousRTPFlag(continuousRTP);
        rtpThread.addRemoteClient(call.getRemoteClient());
        rtpThread.setFilePath(path);
        rtpThread.setRunningFlag(true);
        rtpThread.start();
    }

    public void stopThreads()
    {
        if (diagTimer != null) {
            diagTimer.cancel();
            diagTimer = null;
        }
        sipinspector.Utils.RtpDiagnosticsWriter.writeStoppedDiagnostics();
        if (rxThread != null) {
            rxThread.stopThread();
        }
        if (callGenThread != null) {
            callGenThread.stopThread();
        }
        if (callMaintainThread != null) {
            callMaintainThread.stopThread();
        }
        if (pauseMaintainThread != null) {
            pauseMaintainThread.stopThread();
        }
        if (rtpThread != null) {
            rtpThread.stopThread();
        }

        if (liveAudioController != null) {
            liveAudioController.stopAudio();
        }

        callDB.clear();
    }

    public void updateCallsPerSecond()
    {
        float cps;
        
        runTimeStamp = (System.currentTimeMillis() - runTimeStamp) / 1000;
        if (runTimeStamp > 0)
        {
            cps = callDB.getTotalCallsNumber() / (float)runTimeStamp;
            cps = Math.round(cps*100);
            cps = cps/100;
            callsPerSec.setText("calls/sec: " + cps);
            callsPerSec.setVisible(true);
        }

    }

    public CallGeneratorThread getCallGenThread()
    {
        return callGenThread;
    }

    public ReceiverThread getRxThread()
    {
        return rxThread;
    }

    public RTPSenderThread getRTPThread()
    {
        return rtpThread;
    }

    public SIPScenario getSIPScenario()
    {
        return mySipScenario;
    }

    public MySocketAbstract getSocket()
    {
        return mySocket;
    }

    public FileOutputStream getFos()
    {
        return LogManager.getSipLogStream();
    }

    public void updateStats(ScenarioEntry entry)
    {
        ScenarioProgressTable.getModel().setValueAt(
            entry.getExpectedCnt(),
            entry.getIndex(), 1);
        ScenarioProgressTable.getModel().setValueAt(
            entry.getUnexpectedCnt(),
            entry.getIndex(), 2);
    }

    public Vector<SIPCall> getCallsInProgress()
    {
        return callsInProgress;
    }

    public SIPInspectorMainFrame getMainFrame()
    {
        return mainFrame;
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    private void initComponents()
    {
        setLayout(new MigLayout("", "[grow]", "[grow] []"));

        TableScrollPane = new javax.swing.JScrollPane();
        add(TableScrollPane, "grow, wrap");
        ScenarioProgressTable = new javax.swing.JTable();
        ScenarioProgressTable.setBackground(javax.swing.UIManager.getDefaults().getColor("EditorPane.disabledBackground"));
        ScenarioProgressTable.setFont(new java.awt.Font("Courier New", 0, 12));
        ScenarioProgressTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object[][]
            {
            },
            new String[]
            {
                "Entry", "Exp", "Unexp"
            })
        {
            Class[] types = new Class[]
            {
                java.lang.String.class, java.lang.Integer.class, java.lang.Integer.class
            };

            boolean[] canEdit = new boolean[]
            {
                false, false, false
            };

            public Class getColumnClass(int columnIndex)
            {
                return types[columnIndex];
            }

            public boolean isCellEditable(int rowIndex, int columnIndex)
            {
                return canEdit[columnIndex];
            }
        });
        ScenarioProgressTable.setShowHorizontalLines(false);
        ScenarioProgressTable.setShowVerticalLines(false);
        ScenarioProgressTable.getColumnModel().getColumn(0).setPreferredWidth(250);
        ScenarioProgressTable.getColumnModel().getColumn(1).setPreferredWidth(100);
        ScenarioProgressTable.getColumnModel().getColumn(2).setPreferredWidth(100);
        TableScrollPane.setViewportView(ScenarioProgressTable);


        StopButton = new javax.swing.JButton("Stop");
        StopButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                StopButtonActionPerformed(evt);
            }
        });

        JPanel lowerPanel = new JPanel(new MigLayout("fill, insets 0"));
        lowerPanel.add(StopButton);

        StartButton = new javax.swing.JButton("Start");
        StartButton.setEnabled(false);
        StartButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                StartButtonActionPerformed(evt);
            }
        });
        lowerPanel.add(StartButton, "left");

        liveAudioButton = new javax.swing.JButton("Live Audio");
        liveAudioButton.setEnabled(true);
        liveAudioButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                LiveAudioButtonActionPerformed(evt);
            }
        });
        lowerPanel.add(liveAudioButton, "left");


        callsPerSec = new javax.swing.JLabel();
        lowerPanel.add(callsPerSec);

        CloseButton = new javax.swing.JButton("Close");
        CloseButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                CloseButtonActionPerformed(evt);
            }
        });
        lowerPanel.add(CloseButton, "right");
        add(lowerPanel, "growx");

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("Scenario screen");
        setResizable(true);

        pack();
    }

    private void StopButtonActionPerformed(java.awt.event.ActionEvent evt)
    {
        stopThreads();
        updateCallsPerSecond();
        disableStopEnableStart();
    }

    private void CloseButtonActionPerformed(java.awt.event.ActionEvent evt)
    {

        stopThreads();
        dispose();
    }

    private void StartButtonActionPerformed(java.awt.event.ActionEvent evt)
    {
    /* If this instance is a call generator, make sure no threads survive a
         * previous run
         */
        if (getMainFrame().getEditValuesTab() != null && !getMainFrame().getEditValuesTab().confirmPendingEdits(this)) {
            return;
        }
        stopThreads();
        getMainFrame().getValuesLoader().setCounter(0);
        getMainFrame().getValuesLoader().resetValuesInEachRow();
        spawnThreads();
        callsPerSec.setVisible(false);
        resetButtons();
    }

    private void LiveAudioButtonActionPerformed(java.awt.event.ActionEvent evt)
    {
        if (liveAudioController == null) {
            liveAudioController = new LiveAudioController();
        }
        if (liveAudioController.isAudioRunning()) {
            liveAudioController.stopAudio();
            liveAudioButton.setText("Live Audio");
        } else {
            boolean started = liveAudioController.startAudio();
            if (started) {
                liveAudioButton.setText("Stop Audio");
            } else {
                String err = liveAudioController.getLastError();
                if (err == null || err.trim().length() == 0) {
                    err = "Live Audio could not start.";
                }
                JOptionPane.showMessageDialog(
                    this,
                    err,
                    "Live Audio Start Blocked",
                    JOptionPane.WARNING_MESSAGE
                );
            }
        }
    }

    private javax.swing.JButton CloseButton;

    private javax.swing.JTable ScenarioProgressTable;

    private javax.swing.JButton StartButton;

    private javax.swing.JButton StopButton;

    private javax.swing.JButton liveAudioButton;

    private javax.swing.JScrollPane TableScrollPane;

    private javax.swing.JLabel callsPerSec;

    private SIPScenario mySipScenario;

    private MySocketAbstract mySocket;

    private CallGeneratorThread callGenThread;

    private ReceiverThread rxThread;

    private CallMaintainerThread callMaintainThread;

    private PauseMaintainerThread pauseMaintainThread;

    private RTPSenderThread rtpThread = null;

    private LiveAudioController liveAudioController = null;

    private SIPInspectorMainFrame mainFrame;

    private CallDB callDB;

    private Vector<SIPCall> callsInProgress;

    long runTimeStamp;

    private java.util.Timer diagTimer = null;
}
