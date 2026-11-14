/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.formdev.flatlaf.FlatDarkLaf
 *  com.formdev.flatlaf.FlatLightLaf
 */
package sipinspector;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Vector;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;

import net.miginfocom.swing.MigLayout;
import sipinspector.ControlTabs.CommandsPanel;
import sipinspector.ControlTabs.ConditionsPanel;
import sipinspector.ControlTabs.EntryTab;
import sipinspector.ControlTabs.SDPPanel;
import sipinspector.ControlTabs.SIPPanel;
import sipinspector.ControlTabs.VariablesPanel;
import sipinspector.OperationTabs.AccountTab;
import sipinspector.OperationTabs.EditValuesTab;
import sipinspector.OperationTabs.LogsTab;
import sipinspector.OperationTabs.CampaignsTab;
import sipinspector.OperationTabs.SimulationTab;
import sipinspector.OperationTabs.RtpInspectorTab;
import sipinspector.OperationTabs.ScenarioValuesPanel;
import sipinspector.OperationTabs.SettingsTab;
import sipinspector.Socket.MySocketAbstract;
import sipinspector.Socket.TCPClientSocket;
import sipinspector.Socket.TCPServerSocket;
import sipinspector.Socket.TLSClientSocket_V6;
import sipinspector.Socket.UDPClientSocket;
import sipinspector.Socket.UDPServerSocket;
import sipinspector.Statistics.StatisticsPanel;
import sipinspector.UI.EnhancedStatusBar;
import sipinspector.UI.MessageDiffDialog;
import sipinspector.UI.MessageSearchPanel;
import sipinspector.UI.MessageTemplatesDialog;
import sipinspector.UI.KeyboardShortcutsDialog;
import sipinspector.Utils.CustomThemes;
import sipinspector.Utils.EmojiIcon;
import sipinspector.Utils.ExportUtils;
import sipinspector.Utils.PcapCapture;
import sipinspector.Utils.FloatValidator;
import sipinspector.Utils.IntegerValidator;
import sipinspector.Utils.LoadScenario;
import sipinspector.Utils.LogManager;
import sipinspector.Utils.PortValidator;
import sipinspector.Utils.TLSDebugLogger;
import sipinspector.Utils.UserSettingsHandler;
import sipinspector.Utils.ValuesLoader;
import sipinspector.Utils.WriteSettings;

public class SIPInspectorMainFrame
        extends JFrame {
    public static final int MAX_CONNECTIONS = 256;
    private static EnhancedStatusBar statusBar;

    public static String getSIPInspectorVersion() {
        return "4.1";
    }

    private JMenuItem AboutMenuItem;
    private JMenuItem startCaptureItem;
    private JMenuItem stopCaptureItem;
    private JTabbedPane ControlTabbedPane;
    private JTabbedPane mainTabbedPane;
    private ButtonGroup ConnType;
    private JMenuItem Exit;
    private JMenu FileMenu;
    private JMenu EditMenu;
    private JMenuItem FindReplaceMenuItem;
    private JMenu HelpMenu;
    private JPanel IPAddressPanel;
    private JPanel IPConnType;
    private JList IPList;
    private JPanel IPPortPanel;
    private JMenuItem LoadScenMenuItem;
    private JMenuItem LoadVariablesItem;
    private JPanel LocalInfoPanel;
    private JTextField LocalPort;
    private JMenuBar MainMenuBar;
    private JTextField MaxConcurrentCallsField;
    private JLabel MaxConcurrentCallsLabel;
    private JTextArea MessageTextArea;
    private JLabel MessageTextAreaLabel;
    private Color defaultMessageBg;
    private Color defaultMessageFg;
    private JScrollPane MsgScrollPane;
    private JScrollPane MsgTextScrollPane;
    private JTree MsgTree;
    private JButton AddButton;
    private JButton Next;
    private JMenu OptionsMenu;
    private JMenuItem NewScenMenuItem;
    private JTextField RemoteIPAddress;
    private JPanel RemoteIPAddressPanel;
    private JMenuItem RemoteRTPMenuItem;
    private JMenuItem QoSMenuItem;
    private JCheckBox RemoteServerEnabled;
    private JPanel RemoteServerInfoPanel;
    private JTextField RemoteServerPort;
    private JPanel RemoteServerPortPanel;
    private JButton RemoveButton;
    private JButton RunButton;
    private JButton statisticsButton;
    private JButton BackButton;
    private JMenuItem SDPCalculator;
    private JCheckBoxMenuItem LoggingCheckBoxMenuItem;
    private JMenuItem SaveScenMenuItem;
    private JMenuItem SaveScenAsMenuItem;
    private JLabel ScenarioScreenLabel;
    private JTabbedPane SettingsTabbedPane;
    private SettingsTab settingsTab;
    private AccountTab accountTab;
    private JPanel StartingPanel;
    private JRadioButton TCPRadioButton;
    private JRadioButton TLSRadioButton;
    private JMenu ToolsMenu;
    private JRadioButton UDPRadioButton;
    private JPanel cardPanel;
    private JLabel cpsLabel;
    private JTextField cpsField;
    private JButton downArrowButton;
    private JLabel totCalsLabel;
    private JTextField totCalsField;
    private JButton upArrowButton;
    private MySocketAbstract mySocket;
    private InetAddress localIPAddress;
    private int localPort;
    private InetAddress remoteIPAddress;
    private int remotePort;
    private InetAddress remoteRTPAddress;
    private SIPScenario sipScenario;
    private GlobalSettings settings = new GlobalSettings();
    boolean updateTreeFlag;
    private ValuesLoader valuesLoader;
    private EditValuesTab editValuesTab;
    private Process webServerProcess;

    public static EnhancedStatusBar getStatusBar() {
        return statusBar;
    }

    public SIPInspectorMainFrame() {
        File file;
        Object object;
        Object object2;
        File file2 = new File("userSettings.xml");
        UserSettingsHandler userSettingsHandler = new UserSettingsHandler(this.settings);
        SAXParserFactory sAXParserFactory = SAXParserFactory.newInstance();
        try {
            object2 = sAXParserFactory.newSAXParser();
            ((SAXParser) object2).parse(file2, (DefaultHandler) userSettingsHandler);
        } catch (ParserConfigurationException parserConfigurationException) {
        } catch (SAXException sAXException) {
        } catch (IOException iOException) {
            // empty catch block
        }
        try {
            this.GetIPs();
        } catch (SocketException socketException) {
            JOptionPane.showMessageDialog(this.StartingPanel, "Trouble discovering local IP addresses",
                    "Local IP address discovery", 2);
        }
        this.valuesLoader = new ValuesLoader();
        this.initComponents();
        SIPInspectorMainFrame.setIcon(this);
        this.IPList.setCellRenderer(new IPListRenderer());
        this.LocalPort.setInputVerifier(new PortValidator());
        this.RemoteServerPort.setInputVerifier(new PortValidator());
        this.applyStartupSettings();
        this.configureWindowBoundsAndPosition();
        this.initializeMsgTree();
        this.sipScenario = new SIPScenario();
        this.MsgTree.addTreeSelectionListener(new MyTreeSelectionListener(this));
        this.mySocket = null;
        this.SaveScenMenuItem.setEnabled(false);
        this.LoadScenMenuItem.setEnabled(true);
        this.updateTreeFlag = false;
        object2 = GlobalSettings.getSetting("scenarioPath");
        if (object2 != null && !((File) (object = new File((String) object2))).exists()) {
            object2 = null;
        }
        if (object2 == null
                && ((File) (object = new File("scenarios" + File.separatorChar + "Outgoing_basic_call.txt")))
                        .exists()) {
            object2 = ((File) object).getPath();
            GlobalSettings.setSetting("scenarioPath", (String) object2);
        }
        TLSDebugLogger.log("INIT SCENARIO PATH: " + (String) object2);
        object = new LoadScenario(this, (String) object2);
        ((SwingWorker) object).execute();
        String string = GlobalSettings.getSetting("valuesPath");
        if (string != null && !(file = new File(string)).exists()) {
            string = null;
        }
        if (string == null
                && (file = new File("values" + File.separatorChar + "Outgoing_basic_call_values.txt")).exists()) {
            string = file.getPath();
            GlobalSettings.setSetting("valuesPath", string);
        }
        TLSDebugLogger.log("INIT VALUES PATH: " + string);
        this.valuesLoader.loadValuesFromFile(string);
        GlobalSettings.setSetting("numRTPPackets", "1");
        this.remoteRTPAddress = null;

        // Shutdown hook to kill the web server when the JVM exits
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            PcapCapture.getInstance().stop();
            stopWebServer();
        }));
    }

    public void initializeMsgTree() {
        this.MsgTree.getSelectionModel().setSelectionMode(4);
        this.MsgTree.setCellRenderer(new MyTreeCellRenderer());
    }

    public void GetIPs() throws SocketException {
        SwingWorker<Vector, InetAddress> swingWorker = new SwingWorker<Vector, InetAddress>() {

            @Override
            protected Vector doInBackground() throws Exception {
                Vector<InetAddress> vector = new Vector<InetAddress>();
                NetworkInterface networkInterface = null;
                Enumeration<NetworkInterface> enumeration = NetworkInterface.getNetworkInterfaces();
                while (enumeration.hasMoreElements()) {
                    networkInterface = enumeration.nextElement();
                    InetAddress inetAddress = null;
                    Enumeration<InetAddress> enumeration2 = networkInterface.getInetAddresses();
                    while (enumeration2.hasMoreElements()) {
                        inetAddress = enumeration2.nextElement();
                        if (inetAddress.isLoopbackAddress() || inetAddress.getHostAddress().indexOf(":") != -1)
                            continue;
                        vector.add(inetAddress);
                    }
                }
                return vector;
            }

            @Override
            protected void done() {
                try {
                    Vector vector = (Vector) this.get();
                    SIPInspectorMainFrame.this.IPList.setListData(vector);
                    if (!vector.isEmpty()) {
                        int n = vector.size();
                        String string = GlobalSettings.getLocalIP();
                        SIPInspectorMainFrame.this.IPList.setSelectedIndex(0);
                        for (int i = 0; i < n; ++i) {
                            if (!((InetAddress) vector.elementAt(i)).getHostAddress().equals(string))
                                continue;
                            SIPInspectorMainFrame.this.IPList.setSelectedIndex(i);
                        }
                    }
                } catch (Exception exception) {
                    // empty catch block
                }
            }
        };
        swingWorker.execute();
    }

    public JList getIPList() {
        return this.IPList;
    }

    public String getLocalPort() {
        return this.LocalPort.getText();
    }

    public String getCPSText() {
        return this.cpsField.getText();
    }

    public String getTotCalsText() {
        return this.totCalsField.getText();
    }

    public String getMaxConcurrCalls() {
        return this.MaxConcurrentCallsField.getText();
    }

    public boolean isRemoteServerEnabled() {
        return this.RemoteServerEnabled.isSelected();
    }

    public boolean isConnectionUDP() {
        return this.UDPRadioButton.isSelected();
    }

    public boolean isConnectionTCP() {
        return this.TCPRadioButton.isSelected();
    }

    public boolean isConnectionTLS() {
        return this.TLSRadioButton.isSelected();
    }

    public InetAddress getRemoteAddress() {
        return this.remoteIPAddress;
    }

    public int getRemotePort() {
        return this.remotePort;
    }

    public void updateMessagesFromText(String string) {
        MsgScenarioUpdater msgScenarioUpdater = new MsgScenarioUpdater(this, string);
        msgScenarioUpdater.execute();
    }

    /**
     * Synchronously commit pending edits in the message editor (MessageTextArea)
     * into the in-memory scenario. Must run on the EDT.
     *
     * Previously edits were only pushed to the model by an async SwingWorker on
     * mouse-exit, and ONLY when a tree node was selected - so after loading a
     * scenario (full text shown, nothing selected) a manual header change was
     * silently discarded, and the ORIGINAL message was sent on Run. This commits
     * edits deterministically: when a node is selected it replaces those entries;
     * when nothing is selected it treats the editor as the WHOLE scenario and
     * rebuilds it (clear + parse), mirroring how a scenario is loaded. No-ops when
     * there are no pending edits.
     */
    public void commitEditorEdits() {
        if (!this.updateTreeFlag) {
            return;
        }
        String content = this.MessageTextArea.getText().replace("«\r\n", "\r\n");
        if (content == null || content.indexOf("---------") < 0) {
            this.updateTreeFlag = false;
            return;
        }
        int[] positions = this.MsgTree.isSelectionEmpty() ? null : this.MsgTree.getSelectionRows();
        if (positions != null) {
            java.util.Arrays.sort(positions);
        }
        // No selection => the editor holds the entire scenario: rebuild from scratch.
        if (positions == null) {
            this.getScenario().RemoveAllScenarioEntries(this.MsgTree);
        }

        int posEntry = 0;
        int listCount = 0;
        if (positions != null) {
            if (positions[posEntry] == 0) {
                posEntry++;
            }
            listCount = positions.length;
        }
        boolean processFlag = false;
        StringBuilder tmp = new StringBuilder(4096);
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.StringReader(content))) {
            String str;
            while ((str = reader.readLine()) != null) {
                if (!processFlag && !str.contains("---------")) {
                    continue;
                }
                if (str.startsWith("---------", 1)) {
                    if (processFlag) {
                        sipinspector.ScenarioEntries.ScenarioEntry newEntry =
                                sipinspector.ScenarioEntries.ScenarioEntry.parse(tmp.toString());
                        if (positions != null && posEntry < listCount) {
                            this.getScenario().replaceEntryAtPosition(newEntry, positions[posEntry] - 1);
                            posEntry++;
                        } else {
                            this.getScenario().appendNewEntry(newEntry, this.MsgTree);
                        }
                        tmp.setLength(0);
                        tmp.append(str).append("\r\n");
                        continue;
                    } else {
                        processFlag = true;
                    }
                }
                tmp.append(str).append("\r\n");
            }
            if (processFlag) {
                sipinspector.ScenarioEntries.ScenarioEntry newEntry =
                        sipinspector.ScenarioEntries.ScenarioEntry.parse(tmp.toString());
                if (positions != null && posEntry < listCount) {
                    this.getScenario().replaceEntryAtPosition(newEntry, positions[posEntry] - 1);
                } else {
                    this.getScenario().appendNewEntry(newEntry, this.MsgTree);
                }
            }
        } catch (java.io.IOException e) {
            // Leave scenario as-is on parse error.
        }
        this.getScenario().updateTreeFromScenario(this.MsgTree);
        this.getScenario().setEntriesIndices();
        this.updateTreeFlag = false;
    }

    public JTree getTree() {
        return this.MsgTree;
    }

    public SIPScenario getScenario() {
        return this.sipScenario;
    }

    public JTextArea getMessageTextArea() {
        return this.MessageTextArea;
    }

    private void createDefaultScenario() {
        DefaultMutableTreeNode defaultMutableTreeNode = (DefaultMutableTreeNode) this.MsgTree.getModel().getRoot();
        if (this.MsgTree.getModel().getChildCount(defaultMutableTreeNode) == 0) {
            LoadScenario loadScenario = this.RemoteServerEnabled.isSelected()
                    ? new LoadScenario(this, "scenarios" + File.separatorChar + "Call_client.txt")
                    : new LoadScenario(this, "scenarios" + File.separatorChar + "Call_server.txt");
            loadScenario.execute();
        }
    }

    private void NewScenMenuItemActionPerformed(ActionEvent actionEvent) {
        this.sipScenario.RemoveAllScenarioEntries(this.MsgTree);
        this.MessageTextArea.setText("");
        this.SaveScenMenuItem.setEnabled(true);
    }

    private void saveScenarioToFile() {
        JFileChooser chooser = new JFileChooser(System.getProperty("user.dir"));
        chooser.setDialogTitle("Save scenario file");
        if (sipinspector.Utils.FileChooserUtils.showSaveDialog(this, chooser) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (file.exists()) {
                if (GlobalSettings.isScenarioProtected) {
                    JOptionPane.showMessageDialog(this,
                            "\"" + file.getName() + "\" already exists and Scenario Protection is enabled "
                                    + "(Settings tab).\nChoose a different file name, or disable Scenario "
                                    + "Protection to overwrite.",
                            "Scenario Protected",
                            JOptionPane.WARNING_MESSAGE);
                    return;
                }
                Object[] options = new Object[] { "Yes", "No" };
                int choice = JOptionPane.showOptionDialog(this,
                        "Do you really want to overwrite an existing scenario file?",
                        "Save File",
                        JOptionPane.DEFAULT_OPTION,
                        JOptionPane.WARNING_MESSAGE,
                        null,
                        options,
                        options[0]);
                if (choice != 0) {
                    return;
                }
            }
            try (FileOutputStream fos = new FileOutputStream(file)) {
                for (int i = 0; i < SIPScenario.getEntriesCount(); ++i) {
                    fos.write(SIPScenario.getScenarioPos(i).getScenarioScreenText()
                            .replace("\u00ab\r\n", "\r\n").getBytes());
                }
                GlobalSettings.setSetting("scenarioPath", file.getAbsolutePath());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this,
                        "Failed to save scenario: " + ex.getMessage(),
                        "Save Scenario",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    @Override
    public void dispose() {
        if (this.mySocket != null) {
            this.mySocket.close();
        }
        if (statusBar != null) {
            statusBar.setConnected(false);
            statusBar.resetSession();
        }
        super.dispose();
    }

    void setTreeUpdateFlag() {
        this.updateTreeFlag = true;
    }

    MySocketAbstract getSocket() {
        return this.mySocket;
    }

    private boolean openSocket() {
        if (!this.isRemoteServerEnabled()) {
            if (this.isConnectionUDP()) {
                try {
                    this.mySocket = new UDPServerSocket(this.localIPAddress, this.localPort);
                } catch (IOException iOException) {
                    JOptionPane.showMessageDialog(this, iOException, "Socket status", 0);
                    return false;
                }
                GlobalSettings.setTransport("UDP");
            } else if (this.isConnectionTCP()) {
                this.mySocket = new TCPServerSocket(this.localIPAddress, this.localPort);
                GlobalSettings.setTransport("TCP");
            } else if (this.isConnectionTLS()) {
                this.mySocket = new TCPServerSocket(this.localIPAddress, this.localPort);
                GlobalSettings.setTransport("TLS");
            }
        } else {
            if (this.remoteIPAddress == null) {
                JOptionPane.showMessageDialog(this, "Please enter IP address", "Remote Server Address", 0);
                return false;
            }
            if (this.isConnectionUDP()) {
                try {
                    this.mySocket = new UDPClientSocket(this.localIPAddress, this.localPort);
                } catch (IOException iOException) {
                    JOptionPane.showMessageDialog(this, iOException, "Socket status", 0);
                    return false;
                }
                GlobalSettings.setTransport("UDP");
            } else if (this.isConnectionTCP()) {
                this.mySocket = new TCPClientSocket(this.localIPAddress, this.localPort);
                GlobalSettings.setTransport("TCP");
            } else if (this.isConnectionTLS()) {
                this.mySocket = new TLSClientSocket_V6(this.localIPAddress, this.localPort);
                GlobalSettings.setTransport("TLS");
            }
        }
        return this.mySocket.open(this.remoteIPAddress, this.remotePort);
    }

    public ValuesLoader getValuesLoader() {
        return this.valuesLoader;
    }

    public EditValuesTab getEditValuesTab() {
        return this.editValuesTab;
    }

    private void setRemoteIPAdddress() {
        try {
            this.remoteIPAddress = InetAddress.getByName(this.RemoteIPAddress.getText());
            GlobalSettings.setRemoteIPAddr(this.remoteIPAddress.getHostAddress());
        } catch (UnknownHostException unknownHostException) {
            JOptionPane.showMessageDialog(this, "Invalid IP or invalid host name", "Remote Server Address", 0);
        }
    }

    private void setRemotePort() {
        this.remotePort = Integer.parseInt(this.RemoteServerPort.getText());
        GlobalSettings.setRemotePort(this.RemoteServerPort.getText());
    }

    private void setLocalPort() {
        this.localPort = Integer.parseInt(this.LocalPort.getText());
        GlobalSettings.setLocalPort(this.LocalPort.getText());
    }

    private void setLocalIPAddress() {
        if (this.getIPList().isSelectionEmpty()) {
            JOptionPane.showMessageDialog(this, "You must have and pick at least one IP address",
                    "IP address selection", 0);
        }
        this.localIPAddress = (InetAddress) this.getIPList().getSelectedValue();
        GlobalSettings.setLocalIP(this.localIPAddress.getHostAddress());
    }

    public static void setIcon(Window window) {
        BufferedImage bufferedImage = null;
        URL uRL = window.getClass().getResource("/Icons/SIPSimulator.png");
        if (uRL != null) {
            try {
                bufferedImage = ImageIO.read(uRL);
                window.setIconImage(bufferedImage);
            } catch (IOException iOException) {
                // empty catch block
            }
        }
    }

    private void initComponents() {
        this.cardPanel = new JPanel();
        this.MainMenuBar = new JMenuBar();
        this.FileMenu = new JMenu();
        this.LoadScenMenuItem = new JMenuItem();
        this.LoadVariablesItem = new JMenuItem();
        this.SaveScenMenuItem = new JMenuItem();
        this.Exit = new JMenuItem();
        this.EditMenu = new JMenu();
        this.FindReplaceMenuItem = new JMenuItem();
        this.OptionsMenu = new JMenu();
        this.LoggingCheckBoxMenuItem = new JCheckBoxMenuItem();
        this.RemoteRTPMenuItem = new JMenuItem();
        this.QoSMenuItem = new JMenuItem();
        this.ToolsMenu = new JMenu();
        this.SDPCalculator = new JMenuItem();
        this.HelpMenu = new JMenu();
        this.AboutMenuItem = new JMenuItem();
        this.setDefaultCloseOperation(3);
        this.setTitle("SIP Simulator");
        this.setResizable(true);
        this.cardPanel.setLayout(new CardLayout());
        this.StartingPanel = new JPanel(new MigLayout("fill, insets 18", "[grow]", "[][grow][]"));
        this.SettingsTabbedPane = new JTabbedPane();
        this.accountTab = new AccountTab();
        this.SettingsTabbedPane.addTab("Account", this.accountTab);
        this.settingsTab = new SettingsTab(this);
        this.SettingsTabbedPane.addTab("Settings", this.settingsTab);
        this.StartingPanel.add((Component) this.SettingsTabbedPane, "growx, wrap");
        this.LocalInfoPanel = new JPanel(new MigLayout("fillx"));
        this.LocalInfoPanel.setBorder(BorderFactory.createTitledBorder("Local Info"));
        this.IPAddressPanel = new JPanel(new MigLayout("fill"));
        this.IPAddressPanel.setBorder(BorderFactory.createTitledBorder("Select local IP address"));
        this.IPAddressPanel.setToolTipText("Select local IP address");
        this.IPList = new JList();
        this.IPList.setBorder(BorderFactory.createEtchedBorder());
        this.IPList.setToolTipText("Available IP addresses");
        this.IPAddressPanel.add((Component) this.IPList, "grow, hmin 80, wmin 130");
        this.LocalInfoPanel.add((Component) this.IPAddressPanel, "w 50%!, spany 2, grow");
        this.IPPortPanel = new JPanel(new MigLayout("fillx"));
        this.IPPortPanel.setBorder(BorderFactory.createTitledBorder("Bind to port"));
        this.LocalPort = new JTextField();
        this.LocalPort.setToolTipText("Set local SIP port");
        if (GlobalSettings.getLocalPort() != null) {
            this.LocalPort.setText(GlobalSettings.getLocalPort());
        } else {
            this.LocalPort.setText("5060");
        }
        this.IPPortPanel.add((Component) this.LocalPort, "wmin 65, pref 80, growx");
        this.LocalInfoPanel.add((Component) this.IPPortPanel, "w 50%, wrap");
        this.StartingPanel.add((Component) this.LocalInfoPanel, "grow, wrap");
        this.IPConnType = new JPanel(new MigLayout(""));
        this.IPConnType.setBorder(BorderFactory.createTitledBorder("IP Connection Type"));
        this.IPConnType.setToolTipText("Pick IP connection type");
        this.ConnType = new ButtonGroup();
        this.UDPRadioButton = new JRadioButton("UDP");
        this.UDPRadioButton.setSelected(true);
        this.UDPRadioButton.setToolTipText("UDP connection");
        this.UDPRadioButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                SIPInspectorMainFrame.this.UDPRadioButtonActionPerformed(actionEvent);
            }
        });
        this.ConnType.add(this.UDPRadioButton);
        this.IPConnType.add(this.UDPRadioButton);
        this.TCPRadioButton = new JRadioButton("TCP");
        this.TCPRadioButton.setToolTipText("TCP connection");
        this.TCPRadioButton.setEnabled(true);
        this.TCPRadioButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                SIPInspectorMainFrame.this.TCPRadioButtonActionPerformed(actionEvent);
            }
        });
        this.ConnType.add(this.TCPRadioButton);
        this.IPConnType.add(this.TCPRadioButton);
        this.TLSRadioButton = new JRadioButton("TLS");
        this.TLSRadioButton.setToolTipText("TLS/SSL connection");
        this.TLSRadioButton.setEnabled(true);
        this.TLSRadioButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                SIPInspectorMainFrame.this.TLSRadioButtonActionPerformed(actionEvent);
            }
        });
        this.ConnType.add(this.TLSRadioButton);
        this.IPConnType.add(this.TLSRadioButton);
        this.LocalInfoPanel.add((Component) this.IPConnType, "w 50%");
        this.RemoteServerInfoPanel = new JPanel(new MigLayout("fillx"));
        this.RemoteServerInfoPanel.setBorder(BorderFactory.createTitledBorder("Remote Server Info"));
        this.RemoteServerInfoPanel.setToolTipText("Remote Server Information");
        this.StartingPanel.add((Component) this.RemoteServerInfoPanel, "grow, wrap");
        this.RemoteServerEnabled = new JCheckBox("Remote Server");
        this.RemoteServerEnabled.setToolTipText("Select if message will be sent to remote server");
        String string = GlobalSettings.getSetting("remoteServerFlag");
        if (string != null && string.equals("enabled")) {
            this.RemoteServerEnabled.setSelected(true);
        } else {
            this.RemoteServerEnabled.setSelected(false);
        }
        this.RemoteServerEnabled.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                SIPInspectorMainFrame.this.RemoteServerEnabledActionPerformed(actionEvent);
            }
        });
        this.RemoteServerInfoPanel.add((Component) this.RemoteServerEnabled, "spanx 2, wrap");
        this.RemoteIPAddressPanel = new JPanel(new MigLayout("fillx"));
        this.RemoteIPAddressPanel.setBorder(BorderFactory.createTitledBorder("Remote IP Address"));
        this.RemoteIPAddress = new JTextField();
        this.RemoteIPAddress.setToolTipText("Remote Server IP address");
        if (GlobalSettings.getRemoteIPAddr() != null) {
            this.RemoteIPAddress.setText(GlobalSettings.getRemoteIPAddr());
        }
        if (this.RemoteServerEnabled.isSelected()) {
            this.RemoteIPAddress.setEnabled(true);
        } else {
            this.RemoteIPAddress.setEnabled(false);
        }
        this.RemoteIPAddressPanel.add((Component) this.RemoteIPAddress, "growx, wmin 130");
        this.RemoteServerInfoPanel.add((Component) this.RemoteIPAddressPanel, "w 50%!");
        this.RemoteServerPortPanel = new JPanel(new MigLayout("fillx"));
        this.RemoteServerPortPanel.setBorder(BorderFactory.createTitledBorder("Remote Server Port"));
        this.RemoteServerPort = new JTextField();
        this.RemoteServerPort.setToolTipText("Remote Server port");
        if (GlobalSettings.getRemotePort() != null) {
            this.RemoteServerPort.setText(GlobalSettings.getRemotePort());
        } else {
            this.RemoteServerPort.setText("5060");
        }
        if (this.RemoteServerEnabled.isSelected()) {
            this.RemoteServerPort.setEnabled(true);
        } else {
            this.RemoteServerPort.setEnabled(false);
        }
        this.RemoteServerPortPanel.add((Component) this.RemoteServerPort, "wmin 65, pref 80, growx");
        this.RemoteServerInfoPanel.add((Component) this.RemoteServerPortPanel, "w 50%");
        JPanel jPanel = new JPanel(new MigLayout("fill, insets 0", "[grow][]", "[]"));
        this.statisticsButton = new JButton("Statistics", EmojiIcon.getStatsIcon());
        this.statisticsButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                CardLayout cardLayout = (CardLayout) SIPInspectorMainFrame.this.cardPanel.getLayout();
                cardLayout.show(SIPInspectorMainFrame.this.cardPanel, "statistics_card");
            }
        });
        jPanel.add((Component) this.statisticsButton, "cell 0 0, left");
        this.Next = new JButton("Next >>");
        this.Next.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                SIPInspectorMainFrame.this.NextActionPerformed(actionEvent);
            }
        });
        jPanel.add((Component) this.Next, "cell 1 0, right");
        this.StartingPanel.add((Component) jPanel, "growx");
        this.cardPanel.add((Component) this.StartingPanel, "interview_card");
        JPanel jPanel2 = new JPanel();
        jPanel2.setLayout(new MigLayout("fill, insets 10", "[grow 1] [] [grow 3] []", "[] [] [] [] [grow] []"));
        this.MsgScrollPane = new JScrollPane();
        DefaultMutableTreeNode defaultMutableTreeNode = new DefaultMutableTreeNode("SIP Messages");
        DefaultTreeModel defaultTreeModel = new DefaultTreeModel(defaultMutableTreeNode);
        this.MsgTree = new JTree(defaultTreeModel);
        this.MsgScrollPane.setViewportView(this.MsgTree);
        JPanel jPanel3 = new JPanel(new MigLayout("fillx, ins 0"));
        jPanel3.add((Component) new JLabel("Tool"), "left");
        this.ScenarioScreenLabel = new JLabel("Scenario");
        this.ScenarioScreenLabel.setHorizontalAlignment(0);
        this.ScenarioScreenLabel.setBorder(BorderFactory.createLineBorder(new Color(51, 153, 255)));
        jPanel3.add((Component) this.ScenarioScreenLabel, "center");
        jPanel3.add((Component) new JLabel("Remote"), "right");
        jPanel2.add((Component) jPanel3, "grow, wrap");
        jPanel2.add((Component) this.MsgScrollPane, "spany 2, grow, wmin 220, pref 260, hmin 120");
        this.upArrowButton = new JButton();
        this.upArrowButton.setIcon(EmojiIcon.getArrowUpIcon());
        this.upArrowButton.setBorder(null);
        this.upArrowButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                SIPInspectorMainFrame.this.upArrowButtonActionPerformed(actionEvent);
            }
        });
        jPanel2.add((Component) this.upArrowButton, "top, w 30!");
        this.mainTabbedPane = new JTabbedPane();
        this.ControlTabbedPane = new JTabbedPane();
        this.editValuesTab = new EditValuesTab(this);
        ScenarioValuesPanel scenarioValuesPanel = new ScenarioValuesPanel(this, this.editValuesTab);
        this.ControlTabbedPane.addTab("SIP", new SIPPanel());
        this.ControlTabbedPane.addTab("Condition", new ConditionsPanel(this.sipScenario));
        this.ControlTabbedPane.addTab("Commands", new CommandsPanel());
        this.ControlTabbedPane.addTab("Variables", new VariablesPanel());
        this.ControlTabbedPane.addTab("SDP", new SDPPanel());
        this.ControlTabbedPane.addTab("Tones", new sipinspector.ControlTabs.TonesPanel());
        this.ControlTabbedPane.getAccessibleContext().setAccessibleName("SIP");
        jPanel2.add((Component) this.ControlTabbedPane, "spanx, grow, wrap");
        this.downArrowButton = new JButton();
        this.downArrowButton.setIcon(EmojiIcon.getArrowDownIcon());
        this.downArrowButton.setBorder(null);
        this.downArrowButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                SIPInspectorMainFrame.this.downArrowButtonActionPerformed(actionEvent);
            }
        });
        jPanel2.add((Component) this.downArrowButton, "baseline, w 30!");
        this.AddButton = new JButton("<< Add");
        this.AddButton.setToolTipText("Add SIP message to scenario");
        this.AddButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                SIPInspectorMainFrame.this.AddButtonActionPerformed(actionEvent);
            }
        });
        jPanel2.add(this.AddButton);
        this.RemoveButton = new JButton("Del >>");
        this.RemoveButton.setToolTipText("Delete selected message from scenario");
        this.RemoveButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                SIPInspectorMainFrame.this.RemoveButtonActionPerformed(actionEvent);
            }
        });
        jPanel2.add((Component) this.RemoveButton, "wrap, right");
        this.MessageTextAreaLabel = new JLabel("SIP messages text");
        this.MessageTextAreaLabel.setBorder(BorderFactory.createLineBorder(new Color(51, 153, 255)));
        // Explicit "Apply Edits" button: commit the text-area edits into the
        // scenario on demand (edits are also committed automatically on mouse-out
        // and on Run, but this gives a clear, deliberate way to apply changes -
        // especially useful for keyboard-only editing).
        JButton applyEditsButton = new JButton("Apply Edits");
        applyEditsButton.setToolTipText("Commit your edits in the text box into the scenario now (also applied automatically when you Run)");
        applyEditsButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                // Force a commit of the current text-area content even if the
                // dirty flag was already cleared (e.g. by an earlier mouse-out).
                SIPInspectorMainFrame.this.updateTreeFlag = true;
                SIPInspectorMainFrame.this.commitEditorEdits();
                SIPInspectorMainFrame.this.MessageTextAreaLabel.setText("SIP messages text  (applied)");
            }
        });
        JPanel msgLabelBar = new JPanel(new MigLayout("fillx, insets 0", "[][grow]", "[]"));
        msgLabelBar.add((Component) applyEditsButton, "left");
        msgLabelBar.add((Component) this.MessageTextAreaLabel, "right");
        jPanel2.add((Component) msgLabelBar, "span, growx, wrap");
        this.MsgTextScrollPane = new JScrollPane();
        this.MsgTextScrollPane.setAutoscrolls(true);
        this.MessageTextArea = new JTextArea();
        this.MessageTextArea.setFont(new Font("Courier New", 0, 13));
        this.defaultMessageBg = this.MessageTextArea.getBackground();
        this.defaultMessageFg = this.MessageTextArea.getForeground();
        this.MessageTextArea.addMouseListener(new MouseAdapter() {

            @Override
            public void mouseEntered(MouseEvent mouseEvent) {
                SIPInspectorMainFrame.this.MessageTextAreaMouseEntered(mouseEvent);
            }

            @Override
            public void mouseExited(MouseEvent mouseEvent) {
                SIPInspectorMainFrame.this.MessageTextAreaMouseExited(mouseEvent);
            }
        });
        this.MessageTextArea.addKeyListener(new KeyAdapter() {

            @Override
            public void keyTyped(KeyEvent keyEvent) {
                SIPInspectorMainFrame.this.MessageTextAreaKeyTyped(keyEvent);
            }
        });
        this.MsgTextScrollPane.setViewportView(this.MessageTextArea);
        jPanel2.add((Component) this.MsgTextScrollPane, "spanx, grow, push, wrap");
        JPanel jPanel4 = new JPanel(new MigLayout("fill, insets 0"));
        this.BackButton = new JButton("<<Back");
        this.BackButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                SIPInspectorMainFrame.this.BackButtonActionPerformed(actionEvent);
            }
        });
        jPanel4.add(this.BackButton);
        this.cpsLabel = new JLabel("Calls/sec");
        jPanel4.add((Component) this.cpsLabel, "right");
        this.cpsField = new JTextField();
        if (GlobalSettings.getSetting("callsPerSec") == null) {
            GlobalSettings.setSetting("callsPerSec", "1");
        }
        this.cpsField.setText(GlobalSettings.getSetting("callsPerSec"));
        this.cpsField.setToolTipText("Calls per second (integer values only)");
        this.cpsField.setAutoscrolls(false);
        this.cpsField.setInputVerifier(new FloatValidator());
        this.cpsField.addFocusListener(new FocusAdapter() {

            @Override
            public void focusLost(FocusEvent focusEvent) {
                SIPInspectorMainFrame.this.cpsFieldFocusLost(focusEvent);
            }
        });
        jPanel4.add((Component) this.cpsField, "left, wmin 50, pref 60");
        this.MaxConcurrentCallsLabel = new JLabel("Max Concurrent");
        jPanel4.add((Component) this.MaxConcurrentCallsLabel, "right");
        this.MaxConcurrentCallsField = new JTextField();
        this.MaxConcurrentCallsField.setToolTipText("Max concurrent calls allowed (0 for unlimited)");
        this.MaxConcurrentCallsField.setInputVerifier(new IntegerValidator());
        this.MaxConcurrentCallsField.addFocusListener(new FocusAdapter() {

            @Override
            public void focusLost(FocusEvent focusEvent) {
                SIPInspectorMainFrame.this.MaxConcurrentCallsFieldFocusLost(focusEvent);
            }
        });
        if (GlobalSettings.getSetting("maxConcurrCalls") == null) {
            GlobalSettings.setSetting("maxConcurrCalls", "1");
        }
        this.MaxConcurrentCallsField.setText(GlobalSettings.getSetting("maxConcurrCalls"));
        jPanel4.add((Component) this.MaxConcurrentCallsField, "left, wmin 50, pref 60");
        this.totCalsLabel = new JLabel("Total Calls");
        jPanel4.add((Component) this.totCalsLabel, "right");
        this.totCalsField = new JTextField();
        this.totCalsField.setToolTipText("Total number of calls (0 for infinite)");
        this.totCalsField.setInputVerifier(new IntegerValidator());
        this.totCalsField.addFocusListener(new FocusAdapter() {

            @Override
            public void focusLost(FocusEvent focusEvent) {
                SIPInspectorMainFrame.this.totCalsFieldFocusLost(focusEvent);
            }
        });
        if (GlobalSettings.getSetting("totalCalls") == null) {
            GlobalSettings.setSetting("totalCalls", "1");
        }
        this.totCalsField.setText(GlobalSettings.getSetting("totalCalls"));
        jPanel4.add((Component) this.totCalsField, "left, wmin 50, pref 60");
        this.RunButton = new JButton("Run", EmojiIcon.getRunIcon());
        this.RunButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                SIPInspectorMainFrame.this.RunButtonActionPerformed(actionEvent);
            }
        });
        jPanel4.add((Component) this.RunButton, "right");
        jPanel2.add((Component) jPanel4, "grow, spanx");
        final int scenarioEditorTabIndex = this.mainTabbedPane.getTabCount();
        this.mainTabbedPane.addTab("Scenario Editor", jPanel2);
        this.mainTabbedPane.addTab("Scenario & Values", scenarioValuesPanel);
        this.mainTabbedPane.addTab("Edit Values", editValuesTab);
        this.mainTabbedPane.addTab("Logs", new LogsTab(this));
        this.mainTabbedPane.addTab("Campaigns", new CampaignsTab(this));
        this.mainTabbedPane.addTab("Simulation", new SimulationTab(this));
        this.mainTabbedPane.addTab("RTP Inspector", new RtpInspectorTab());

        // Message Search & Filter - own tab (next to RTP Inspector), acts on the
        // SIP message tree shown in the Scenario Editor tab.
        JPanel messageSearchTabPanel = new JPanel(new BorderLayout());
        JLabel messageSearchTitle = new JLabel("Search & filter the SIP messages in the current scenario");
        messageSearchTitle.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        messageSearchTitle.setFont(messageSearchTitle.getFont().deriveFont(Font.BOLD, 13f));
        messageSearchTabPanel.add(messageSearchTitle, BorderLayout.NORTH);
        MessageSearchPanel messageSearchPanel = new MessageSearchPanel(this.MsgTree);
        messageSearchPanel.setOnSearchPerformed(() -> this.mainTabbedPane.setSelectedIndex(scenarioEditorTabIndex));
        messageSearchTabPanel.add(messageSearchPanel, BorderLayout.CENTER);
        this.mainTabbedPane.addTab("Message Search", messageSearchTabPanel);

        this.cardPanel.add((Component) this.mainTabbedPane, "program_card");
        JPanel jPanel5 = new JPanel();
        jPanel5.setLayout(new MigLayout("fill, ins 0"));
        jPanel5.add((Component) StatisticsPanel.getInstance(), "grow");
        this.cardPanel.add((Component) jPanel5, "statistics_card");
        this.FileMenu.setText("File");
        this.FileMenu.setIcon(EmojiIcon.getFileIcon());
        this.NewScenMenuItem = new JMenuItem("New Scenario");
        this.NewScenMenuItem.setIcon(EmojiIcon.getFileIcon());
        this.NewScenMenuItem.setAccelerator(KeyStroke.getKeyStroke(78, 128));
        this.NewScenMenuItem.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                SIPInspectorMainFrame.this.NewScenMenuItemActionPerformed(actionEvent);
            }
        });
        this.FileMenu.add(this.NewScenMenuItem);
        this.LoadScenMenuItem.setText("Load Scenario");
        this.LoadScenMenuItem.setAccelerator(KeyStroke.getKeyStroke(79, 128));
        this.LoadScenMenuItem.setIcon(EmojiIcon.getOpenFolderIcon());
        this.LoadScenMenuItem.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                SIPInspectorMainFrame.this.LoadScenMenuItemActionPerformed(actionEvent);
            }
        });
        this.FileMenu.add(this.LoadScenMenuItem);
        this.LoadVariablesItem.setText("Load Values");
        this.LoadVariablesItem.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                SIPInspectorMainFrame.this.LoadValuesItemActionPerformed(actionEvent);
            }
        });
        this.FileMenu.add(this.LoadVariablesItem);
        this.SaveScenMenuItem.setText("Save Scenario");
        this.SaveScenMenuItem.setAccelerator(KeyStroke.getKeyStroke(83, 128));
        this.SaveScenMenuItem.setIcon(EmojiIcon.getSaveIcon());
        this.SaveScenMenuItem.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                SIPInspectorMainFrame.this.SaveScenMenuItemActionPerformed(actionEvent);
            }
        });
        this.FileMenu.add(this.SaveScenMenuItem);
        this.SaveScenAsMenuItem = new JMenuItem("Save Scenario As...");
        this.SaveScenAsMenuItem.setAccelerator(KeyStroke.getKeyStroke(83, 192));
        this.SaveScenAsMenuItem.setIcon(EmojiIcon.getSaveIcon());
        this.SaveScenAsMenuItem.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                SIPInspectorMainFrame.this.saveScenarioToFile();
            }
        });
        this.FileMenu.add(this.SaveScenAsMenuItem);
        this.Exit.setText("Exit");
        this.Exit.setAccelerator(KeyStroke.getKeyStroke(81, 128));
        this.Exit.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                SIPInspectorMainFrame.this.ExitProgram(actionEvent);
            }
        });
        this.FileMenu.add(this.Exit);
        this.MainMenuBar.add(this.FileMenu);
        this.EditMenu.setText("Edit");
        this.EditMenu.setIcon(EmojiIcon.getEditIcon());
        this.FindReplaceMenuItem.setAccelerator(KeyStroke.getKeyStroke(70, 2));
        this.FindReplaceMenuItem.setText("Find/Replace");
        this.FindReplaceMenuItem.setIcon(EmojiIcon.getSearchIcon());
        this.FindReplaceMenuItem.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                SIPInspectorMainFrame.this.FindReplaceMenuItemActionPerformed(actionEvent);
            }
        });
        this.EditMenu.add(this.FindReplaceMenuItem);
        this.MainMenuBar.add(this.EditMenu);
        this.OptionsMenu.setText("Options");
        this.OptionsMenu.setIcon(EmojiIcon.getOptionsIcon());
        this.LoggingCheckBoxMenuItem.setText("Enable Logging");
        this.LoggingCheckBoxMenuItem.setToolTipText("Toggle SIP + transport logging (Ctrl+L)");
        this.LoggingCheckBoxMenuItem.setAccelerator(KeyStroke.getKeyStroke(76, 128));
        this.LoggingCheckBoxMenuItem.setSelected(GlobalSettings.logsEnabled);
        this.LoggingCheckBoxMenuItem.addActionListener(actionEvent -> {
            if (LoggingCheckBoxMenuItem.isSelected()) {
                LogManager.startLogging(GlobalSettings.getTransport());
            } else {
                LogManager.stopLogging();
            }
        });
        this.OptionsMenu.add(this.LoggingCheckBoxMenuItem);
        this.RemoteRTPMenuItem.setText("Remote RTP...");
        this.RemoteRTPMenuItem.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                SIPInspectorMainFrame.this.RemoteRTPMenuItemActionPerformed(actionEvent);
            }
        });
        this.OptionsMenu.add(this.RemoteRTPMenuItem);
        this.QoSMenuItem.setText("QoS");
        this.QoSMenuItem.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                SIPInspectorMainFrame.this.QoSMenuItemActionPerformed(actionEvent);
            }
        });
        this.OptionsMenu.add(this.QoSMenuItem);
        this.MainMenuBar.add(this.OptionsMenu);
        this.ToolsMenu.setText("Tools");
        this.ToolsMenu.setIcon(EmojiIcon.getToolsIcon());
        this.SDPCalculator.setText("SDP Calculator");
        this.SDPCalculator.setIcon(EmojiIcon.getStatsIcon());
        this.SDPCalculator.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                SIPInspectorMainFrame.this.SDPCalculatorActionPerformed(actionEvent);
            }
        });
        this.ToolsMenu.add(this.SDPCalculator);
        JMenuItem messageTemplatesItem = new JMenuItem("Message Templates", EmojiIcon.getTemplateIcon());
        messageTemplatesItem.setToolTipText("Browse pre-built SIP message templates and copy them to the clipboard");
        messageTemplatesItem.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                SIPInspectorMainFrame.this.showMessageTemplates();
            }
        });
        this.ToolsMenu.add(messageTemplatesItem);
        JMenuItem messageDiffItem = new JMenuItem("Compare SIP Messages...", EmojiIcon.getCompareIcon());
        messageDiffItem.setToolTipText("Diff two SIP messages header-by-header (interop failure triage)");
        messageDiffItem.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                new MessageDiffDialog(SIPInspectorMainFrame.this).setVisible(true);
            }
        });
        this.ToolsMenu.add(messageDiffItem);
        JMenuItem jMenuItem7 = new JMenuItem("Export Statistics", EmojiIcon.getSaveIcon());
        jMenuItem7.setToolTipText("Export statistics to CSV");
        jMenuItem7.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                SIPInspectorMainFrame.this.exportStatistics();
            }
        });
        this.ToolsMenu.add(jMenuItem7);
        JMenuItem jMenuItem8 = new JMenuItem("Open Logs Folder...");
        jMenuItem8.setToolTipText("Open transport debug logs folder");
        jMenuItem8.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                SIPInspectorMainFrame.this.openLogsFolder();
            }
        });
        this.ToolsMenu.add(jMenuItem8);
        this.ToolsMenu.addSeparator();
        JMenuItem webDashboardItem = new JMenuItem("Launch Web Dashboard");
        webDashboardItem.setToolTipText("Start the web server (if needed) and open the dashboard in your browser");
        webDashboardItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                launchWebDashboard();
            }
        });
        this.ToolsMenu.add(webDashboardItem);
        this.ToolsMenu.addSeparator();
        startCaptureItem = new JMenuItem("Start Packet Capture...");
        startCaptureItem.setToolTipText("Capture SIP signaling to a .pcap file (open in Wireshark)");
        startCaptureItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                SIPInspectorMainFrame.this.startPacketCapture();
            }
        });
        this.ToolsMenu.add(startCaptureItem);
        stopCaptureItem = new JMenuItem("Stop Packet Capture");
        stopCaptureItem.setEnabled(false);
        stopCaptureItem.setToolTipText("Stop the active .pcap capture");
        stopCaptureItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                SIPInspectorMainFrame.this.stopPacketCapture();
            }
        });
        this.ToolsMenu.add(stopCaptureItem);
        this.MainMenuBar.add(this.ToolsMenu);
        this.HelpMenu.setText("Help");
        this.HelpMenu.setIcon(EmojiIcon.getHelpIcon());
        JMenuItem jMenuItem9 = new JMenuItem("Keyboard Shortcuts");
        jMenuItem9.setAccelerator(KeyStroke.getKeyStroke(112, 0));
        jMenuItem9.setToolTipText("View all keyboard shortcuts (F1)");
        jMenuItem9.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                SIPInspectorMainFrame.this.showKeyboardShortcuts();
            }
        });
        this.HelpMenu.add(jMenuItem9);
        JMenuItem guideItem = new JMenuItem("Feature User Guide");
        guideItem.setToolTipText("View documentation on all simulator tabs and features");
        guideItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                SIPInspectorMainFrame.this.showUserGuide();
            }
        });
        this.HelpMenu.add(guideItem);
        this.HelpMenu.addSeparator();
        this.AboutMenuItem.setText("About");
        this.AboutMenuItem.setIcon(EmojiIcon.getHelpIcon());
        this.AboutMenuItem.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                SIPInspectorMainFrame.this.AboutMenuItemActionPerformed(actionEvent);
            }
        });
        this.HelpMenu.add(this.AboutMenuItem);
        this.MainMenuBar.add(this.HelpMenu);
        this.setJMenuBar(this.MainMenuBar);
        this.getContentPane().setLayout(new MigLayout("fill, insets 0", "[grow]", "[grow][]"));
        this.getContentPane().add((Component) this.cardPanel, "cell 0 0, grow");
        statusBar = new EnhancedStatusBar();
        statusBar.setLogsEnabled(GlobalSettings.logsEnabled);
        this.getContentPane().add((Component) statusBar, "cell 0 1, growx");
        this.pack();
        this.applyCurrentThemeToTabs();
        this.applyThemeToButtons();
        this.resetMessageAreaColors();
    }

    /**
     * Dynamically configures window bounds, minimum size, position, and restoration state
     * based on available screen bounds (respecting Windows taskbar insets) and saved preferences.
     */
    private void configureWindowBoundsAndPosition() {
        this.setResizable(true);

        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        Rectangle maxBounds = ge.getMaximumWindowBounds();
        if (maxBounds == null || maxBounds.width <= 0 || maxBounds.height <= 0) {
            maxBounds = new Rectangle(0, 0, 1280, 720);
        }

        // Safe minimum size: ensure it never exceeds the usable screen size
        int minWidth = Math.min(960, Math.max(700, maxBounds.width - 40));
        int minHeight = Math.min(620, Math.max(500, maxBounds.height - 40));
        this.setMinimumSize(new Dimension(minWidth, minHeight));

        int targetWidth = -1;
        int targetHeight = -1;
        int targetX = -1;
        int targetY = -1;
        boolean isMaximized = false;

        try {
            String savedW = GlobalSettings.getSetting("window.width");
            String savedH = GlobalSettings.getSetting("window.height");
            String savedX = GlobalSettings.getSetting("window.x");
            String savedY = GlobalSettings.getSetting("window.y");
            String savedMax = GlobalSettings.getSetting("window.maximized");

            if (savedW != null && savedH != null) {
                targetWidth = Integer.parseInt(savedW.trim());
                targetHeight = Integer.parseInt(savedH.trim());
            }
            if (savedX != null && savedY != null) {
                targetX = Integer.parseInt(savedX.trim());
                targetY = Integer.parseInt(savedY.trim());
            }
            if ("true".equalsIgnoreCase(savedMax)) {
                isMaximized = true;
            }
        } catch (Exception ignore) {}

        // Validate whether saved coordinates are sensible and within the current monitor's working area
        boolean validSaved = (targetWidth >= minWidth && targetHeight >= minHeight
                && targetWidth <= maxBounds.width && targetHeight <= maxBounds.height
                && targetX >= maxBounds.x - 40 && targetY >= maxBounds.y - 40
                && targetX <= maxBounds.x + maxBounds.width - 100
                && targetY <= maxBounds.y + maxBounds.height - 100);

        if (!validSaved) {
            // Sensible responsive default:
            // Width: 82% of usable screen width, clamped between minWidth and 1380px (prevents ultrawide over-stretch)
            targetWidth = (int) Math.round(maxBounds.width * 0.82);
            if (targetWidth > 1380) {
                targetWidth = 1380;
            }
            if (targetWidth < minWidth) {
                targetWidth = minWidth;
            }

            // Height: 88% of usable screen height, clamped between minHeight and 840px
            targetHeight = (int) Math.round(maxBounds.height * 0.88);
            if (targetHeight > 840) {
                targetHeight = 840;
            }
            if (targetHeight < minHeight) {
                targetHeight = minHeight;
            }

            // Center within the maximum usable bounds (never underneath the Windows taskbar)
            targetX = maxBounds.x + (maxBounds.width - targetWidth) / 2;
            targetY = maxBounds.y + (maxBounds.height - targetHeight) / 2;
        }

        this.setBounds(targetX, targetY, targetWidth, targetHeight);

        if (isMaximized) {
            this.setExtendedState(JFrame.MAXIMIZED_BOTH);
        }

        // Listen for window close to persist window dimensions
        this.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                saveWindowState();
            }
        });
    }

    private void saveWindowState() {
        try {
            int state = this.getExtendedState();
            boolean isMax = (state & JFrame.MAXIMIZED_BOTH) == JFrame.MAXIMIZED_BOTH;
            GlobalSettings.setSetting("window.maximized", String.valueOf(isMax));
            if (!isMax) {
                Rectangle bounds = this.getBounds();
                GlobalSettings.setSetting("window.width", String.valueOf(bounds.width));
                GlobalSettings.setSetting("window.height", String.valueOf(bounds.height));
                GlobalSettings.setSetting("window.x", String.valueOf(bounds.x));
                GlobalSettings.setSetting("window.y", String.valueOf(bounds.y));
            }
            GlobalSettings.saveSettings();
        } catch (Exception ignore) {}
    }

    private void applyStartupSettings() {
        String string;
        String string2;
        GlobalSettings.loadSettings();

        if (this.settingsTab != null) {
            this.settingsTab.refreshSettingsCheckboxes();
        }
        if ((string2 = GlobalSettings.getSetting("defaultTheme")) != null) {
            this.applyThemeSilently(this.mapThemeNameToCode(string2));
        }
        if ((string = GlobalSettings.getSetting("defaultTransport")) == null) {
            string = "UDP";
        }
        if ("TCP".equals(string)) {
            this.TCPRadioButton.setSelected(true);
        } else if ("TLS".equals(string)) {
            this.TLSRadioButton.setSelected(true);
        } else {
            this.UDPRadioButton.setSelected(true);
        }
        String string3 = GlobalSettings.getSetting("defaultUdpTcpLocalPort");
        String string4 = GlobalSettings.getSetting("defaultTlsLocalPort");
        String string5 = GlobalSettings.getSetting("defaultUdpTcpRemotePort");
        String string6 = GlobalSettings.getSetting("defaultTlsRemotePort");
        if ("TLS".equals(string)) {
            this.LocalPort.setText(string4 != null ? string4 : "5061");
            this.RemoteServerPort.setText(string6 != null ? string6 : "5061");
        } else {
            this.LocalPort.setText(string3 != null ? string3 : "5060");
            this.RemoteServerPort.setText(string5 != null ? string5 : "5060");
        }
        if (GlobalSettings.logsEnabled) {
            LogManager.startLogging(GlobalSettings.getTransport());
        } else {
            LogManager.stopLogging();
        }
    }

    private void applyCurrentThemeToTabs() {
        Color color = UIManager.getColor("TabbedPane.background");
        Color color2 = UIManager.getColor("TabbedPane.selectedBackground");
        Color color3 = UIManager.getColor("TabbedPane.foreground");
        Color color4 = UIManager.getColor("TabbedPane.selectedForeground");
        this.applyTabPaletteToAll(color, color2, color3, color4);
    }

    private void applyTabPaletteToAll(Color color, Color color2, Color color3, Color color4) {
        this.styleTabbedPane(this.SettingsTabbedPane, color, color2, color3, color4);
        this.styleTabbedPane(this.ControlTabbedPane, color, color2, color3, color4);
        this.styleTabbedPane(this.mainTabbedPane, color, color2, color3, color4);
    }

    private void styleTabbedPane(JTabbedPane jTabbedPane, Color color, Color color2, Color color3, Color color4) {
        if (jTabbedPane == null) {
            return;
        }
        Color color5 = this.blendColors(color2, color, 0.25f);
        jTabbedPane.putClientProperty("JTabbedPane.tabAreaBackground", color);
        jTabbedPane.putClientProperty("JTabbedPane.selectedBackground", color5);
        if (color4 != null) {
            jTabbedPane.putClientProperty("JTabbedPane.selectedForeground", color4);
        }
        if (color != null) {
            jTabbedPane.setBackground(color);
            for (int n = 0; n < jTabbedPane.getTabCount(); ++n) {
                jTabbedPane.setBackgroundAt(n, color);
            }
        }
        if (color3 != null) {
            jTabbedPane.setForeground(color3);
            for (int n = 0; n < jTabbedPane.getTabCount(); ++n) {
                jTabbedPane.setForegroundAt(n, color3);
            }
        }
        jTabbedPane.revalidate();
        jTabbedPane.repaint();
    }

    private Color blendColors(Color color, Color color2, float f) {
        if (color == null && color2 == null) {
            return null;
        }
        if (color == null) {
            return color2;
        }
        if (color2 == null) {
            return color;
        }
        float f2 = 1.0f - f;
        int n = Math.round((float) color.getRed() * f + (float) color2.getRed() * f2);
        int n2 = Math.round((float) color.getGreen() * f + (float) color2.getGreen() * f2);
        int n3 = Math.round((float) color.getBlue() * f + (float) color2.getBlue() * f2);
        return new Color(n, n2, n3);
    }

    private void applyThemeToButtons() {
        Color color = UIManager.getColor("Button.background");
        Color color2 = UIManager.getColor("Button.foreground");
        this.styleButton(this.AddButton, color, color2);
        this.styleButton(this.RemoveButton, color, color2);
        this.styleButton(this.Next, color, color2);
        this.styleButton(this.BackButton, color, color2);
        this.styleButton(this.RunButton, color, color2);
        this.styleButton(this.statisticsButton, color, color2);
        this.styleButton(this.upArrowButton, color, color2);
        this.styleButton(this.downArrowButton, color, color2);
    }

    private void styleButton(AbstractButton abstractButton, Color color, Color color2) {
        if (abstractButton == null) {
            return;
        }
        if (color != null) {
            abstractButton.setBackground(color);
        }
        if (color2 != null) {
            abstractButton.setForeground(color2);
        }
        abstractButton.setOpaque(true);
        abstractButton.setContentAreaFilled(true);
    }

    private void resetMessageAreaColors() {
        if (this.MessageTextArea == null) {
            return;
        }
        if (this.defaultMessageBg != null) {
            this.MessageTextArea.setBackground(this.defaultMessageBg);
        }
        if (this.defaultMessageFg != null) {
            this.MessageTextArea.setForeground(this.defaultMessageFg);
        }
    }

    private void NextActionPerformed(ActionEvent actionEvent) {
        if (this.accountTab != null) {
            this.accountTab.saveSettings();
        }
        boolean ok = true;
        this.setLocalIPAddress();
        this.setLocalPort();
        this.setRemoteIPAdddress();
        this.setRemotePort();
        if (this.isConnectionTLS() && this.isRemoteServerEnabled() && this.remotePort != 5061) {
            JOptionPane.showMessageDialog(this,
                    "For TLS transport, remote server port is typically 5061.\nCurrent remote port: " + this.remotePort,
                    "TLS Port Warning",
                    JOptionPane.WARNING_MESSAGE);
        }
        this.createDefaultScenario();
        if (this.mySocket == null) {
            ok = this.openSocket();
        } else {
            this.mySocket.close();
            ok = this.openSocket();
        }
        if (ok) {
            if (statusBar != null) {
                statusBar.setConnected(true);
                statusBar.setTransport(GlobalSettings.getTransport());
                statusBar.resetSession();
            }
            CardLayout cardLayout = (CardLayout) this.cardPanel.getLayout();
            cardLayout.show(this.cardPanel, "program_card");
            if (!this.mySocket.isDaemon()) {
                this.cpsField.setVisible(true);
                this.cpsLabel.setVisible(true);
                this.totCalsField.setVisible(true);
                this.totCalsLabel.setVisible(true);
                this.MaxConcurrentCallsField.setVisible(true);
                this.MaxConcurrentCallsLabel.setVisible(true);
            } else {
                this.cpsField.setVisible(false);
                this.cpsLabel.setVisible(false);
                this.totCalsField.setVisible(false);
                this.totCalsLabel.setVisible(false);
                this.MaxConcurrentCallsField.setVisible(false);
                this.MaxConcurrentCallsLabel.setVisible(false);
            }
            this.SaveScenMenuItem.setEnabled(true);
        }
    }

    private void RemoteServerEnabledActionPerformed(ActionEvent actionEvent) {
        if (this.RemoteServerEnabled.isSelected()) {
            this.RemoteIPAddress.setEditable(true);
            this.RemoteIPAddress.setEnabled(true);
            this.RemoteServerPort.setEditable(true);
            this.RemoteServerPort.setEnabled(true);
            GlobalSettings.setSetting("remoteServerFlag", "enabled");
        } else {
            this.RemoteIPAddress.setEnabled(false);
            this.RemoteServerPort.setEnabled(false);
            GlobalSettings.setSetting("remoteServerFlag", "disabled");
        }
    }

    private void ExitProgram(ActionEvent actionEvent) {
        stopWebServer();
        this.dispose();
    }

    private void BackButtonActionPerformed(ActionEvent actionEvent) {
        CardLayout cardLayout = (CardLayout) this.cardPanel.getLayout();
        cardLayout.show(this.cardPanel, "interview_card");
        this.SaveScenMenuItem.setEnabled(false);
    }

    private void AddButtonActionPerformed(ActionEvent actionEvent) {
        SwingWorker<JTree, Object> swingWorker = new SwingWorker<JTree, Object>() {

            @Override
            protected JTree doInBackground() throws Exception {
                EntryTab entryTab = (EntryTab) SIPInspectorMainFrame.this.ControlTabbedPane.getSelectedComponent();
                return entryTab.addMsgTreeEntry(SIPInspectorMainFrame.this.MsgTree,
                        SIPInspectorMainFrame.this.sipScenario);
            }

            @Override
            protected void done() {
                try {
                    JTree jTree = (JTree) this.get();
                    DefaultTreeModel model = (DefaultTreeModel) jTree.getModel();
                    model.nodeChanged((TreeNode) model.getRoot());
                } catch (Exception exception) {
                    JOptionPane.showMessageDialog(SIPInspectorMainFrame.this.ControlTabbedPane,
                            exception,
                            "Adding an entry",
                            JOptionPane.ERROR_MESSAGE);
                }
                SIPInspectorMainFrame.this.sipScenario.setEntriesIndices();
            }
        };
        swingWorker.execute();
    }

    private void RemoveButtonActionPerformed(ActionEvent actionEvent) {
        int count = this.MsgTree.getSelectionCount();
        int[] rows = this.MsgTree.getSelectionRows();
        if (rows == null) {
            return;
        }
        Arrays.sort(rows);
        for (int i = count - 1; i >= 0; --i) {
            if (rows[i] == 0) {
                continue;
            }
            this.sipScenario.removeEntry(rows[i] - 1);
        }
        this.sipScenario.updateTreeFromScenario(this.MsgTree);
        this.sipScenario.setEntriesIndices();
        this.MsgTree.clearSelection();
    }

    private void upArrowButtonActionPerformed(ActionEvent actionEvent) {
        int count = this.MsgTree.getSelectionCount();
        int[] rows = this.MsgTree.getSelectionRows();
        Arrays.sort(rows);
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) this.MsgTree.getModel().getRoot();
        DefaultTreeModel model = (DefaultTreeModel) this.MsgTree.getModel();
        for (int i = 0; i < count; ++i) {
            if (rows[i] == 0 || rows[i] == 1) {
                JOptionPane.showMessageDialog(this.upArrowButton,
                        "Can not shift up",
                        "Moving up",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
            int index = rows[i] - 1;
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) this.MsgTree.getModel().getChild(root, index);
            model.removeNodeFromParent(node);
            model.insertNodeInto(node, root, index - 1);
            rows[i] = index;
            this.sipScenario.moveEntryUp(index);
        }
        this.MsgTree.setSelectionRows(rows);
        model.nodeChanged(root);
        this.sipScenario.setEntriesIndices();
    }

    private void downArrowButtonActionPerformed(ActionEvent actionEvent) {
        int count = this.MsgTree.getSelectionCount();
        int[] rows = this.MsgTree.getSelectionRows();
        Arrays.sort(rows);
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) this.MsgTree.getModel().getRoot();
        DefaultTreeModel model = (DefaultTreeModel) this.MsgTree.getModel();
        for (int i = count - 1; i >= 0; --i) {
            if (rows[i] == 0 || rows[i] == model.getChildCount(root)) {
                JOptionPane.showMessageDialog(this.downArrowButton,
                        "Can not shift down",
                        "Moving down",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
            int index = rows[i] - 1;
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) this.MsgTree.getModel().getChild(root, index);
            model.removeNodeFromParent(node);
            model.insertNodeInto(node, root, index + 1);
            this.sipScenario.moveEntryDown(index);
            rows[i] = index + 2;
        }
        this.MsgTree.setSelectionRows(rows);
        model.nodeChanged(root);
        this.sipScenario.setEntriesIndices();
    }

    private void MessageTextAreaKeyTyped(KeyEvent keyEvent) {
        this.updateTreeFlag = true;
        // Clear any stale "(applied)" hint once the user resumes editing.
        if (this.MessageTextAreaLabel != null && !"SIP messages text".equals(this.MessageTextAreaLabel.getText())) {
            this.MessageTextAreaLabel.setText("SIP messages text");
        }
    }

    private void FindReplaceMenuItemActionPerformed(ActionEvent actionEvent) {
        FindReplaceDialog findReplaceDialog = new FindReplaceDialog(this, this.MessageTextArea);
        findReplaceDialog.setVisible(true);
        findReplaceDialog.initializeButtons();
    }

    private void RunButtonActionPerformed(ActionEvent actionEvent) {
        if (this.editValuesTab != null && !this.editValuesTab.confirmPendingEdits(this)) {
            return;
        }
        // Commit any uncommitted edits in the message editor into the scenario
        // BEFORE running, so a manually changed header is actually sent (previously
        // the original, unmodified message was used).
        this.commitEditorEdits();
        SIPScenario.initializeStaticCounters();
        this.getValuesLoader().setCounter(0);
        this.getValuesLoader().resetValuesInEachRow();
        ScenarioProgressDialog scenarioProgressDialog = new ScenarioProgressDialog(this);
        scenarioProgressDialog.setLocationRelativeTo(this);
        this.mySocket.setQos();
        
        // Disable Run button to prevent concurrent scenario progress popups
        this.RunButton.setEnabled(false);
        scenarioProgressDialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                RunButton.setEnabled(true);
            }
        });
        
        scenarioProgressDialog.setModalityType(Dialog.ModalityType.MODELESS);
        scenarioProgressDialog.spawnThreads();
        scenarioProgressDialog.setVisible(true);
        WriteSettings writeSettings = new WriteSettings("userSettings.xml");
        writeSettings.writeToFile();
    }

    private void showKeyboardShortcuts() {
        KeyboardShortcutsDialog keyboardShortcutsDialog = new KeyboardShortcutsDialog(this);
        keyboardShortcutsDialog.setVisible(true);
    }

    private void showUserGuide() {
        JDialog dialog = new JDialog(this, "SIP Simulator Feature Guide", true);
        dialog.setSize(800, 600);
        dialog.setLocationRelativeTo(this);
        
        JEditorPane editor = new JEditorPane();
        editor.setEditable(false);
        editor.setContentType("text/html");
        
        // Build user guide HTML content
        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family:Segoe UI, Arial, sans-serif; font-size:12px; margin:20px; color:#cbd5e1; background-color:#0f172a; line-height:1.6;'>");
        html.append("<h1 style='color:#38bdf8; font-size:22px; border-bottom:1px solid #334155; padding-bottom:10px; margin-top:0;'>SIP Simulator v4 User Guide & Tutorial</h1>");
        html.append("<p style='font-size:13px;'>Welcome to the Feature User Guide. This comprehensive guide outlines the detailed step-by-step instructions on how to use every feature in the SIP Simulator v4 workspace.</p>");
        
        // ------------------------------------------
        // QUICK START GUIDE
        // ------------------------------------------
        html.append("<div style='background-color:#1e293b; border:1px solid #334155; border-radius:6px; padding:15px; margin:20px 0;'>");
        html.append("<h3 style='color:#38bdf8; margin-top:0; font-size:14px; border-bottom:1px solid #334155; padding-bottom:5px;'>🚀 Quick Start: Run Your First Call</h3>");
        html.append("<ol style='margin-bottom:0; padding-left:20px;'>");
        html.append("<li style='margin-bottom:6px;'><b>Load Scenarios</b>: Go to the <b>Scenario & Values</b> tab, select <code>Reg_client.txt</code>, and click <b>Load</b>.</li>");
        html.append("<li style='margin-bottom:6px;'><b>Run the Simulator</b>: Switch back to the <b>Scenario Editor</b> tab and click the <b>Run</b> button at the top.</li>");
        html.append("<li style='margin-bottom:0;'><b>Observe Visual Flow</b>: Navigate to the <b>Logs</b> tab, select the latest UDP log file, click the <b>Visual Sequence Ladder</b> sub-tab, and click on any message arrow to inspect the raw headers.</li>");
        html.append("</ol>");
        html.append("</div>");

        // 1. Scenario Editor
        html.append("<h2 style='color:#34d399; font-size:15px; border-bottom:1px solid #334155; padding-bottom:4px; margin-top:25px;'>1. Scenario Editor (SIP Code Constructor)</h2>");
        html.append("<p>The Scenario Editor is the core workspace where you construct, edit, and organize raw SIP packets.</p>");
        html.append("<b>How to use:</b>");
        html.append("<ol style='padding-left:20px;'>");
        html.append("<li style='margin-bottom:6px;'><b>Select Message</b>: Click any SIP message (e.g. <code>INVITE</code>, <code>200 OK</code>) in the left-hand <b>Message Tree</b>. The raw content of that message will load in the editor at the bottom.</li>");
        html.append("<li style='margin-bottom:6px;'><b>Add Header Elements</b>: To insert a standard SIP header, select a header from the dropdown list on the left, and click <b>&lt;&lt; Add</b>. It will append to the selected message template.</li>");
        html.append("<li style='margin-bottom:6px;'><b>Reorder Messages</b>: Select a message block and use the <b>Up/Down Arrow Buttons</b> to move its position in the signaling call flow.</li>");
        html.append("<li style='margin-bottom:6px;'><b>Manual Modifications</b>: You can type directly in the bottom text pane to add custom headers or variables (e.g., <code>[remote_ip]</code> or custom placeholders).</li>");
        html.append("<li style='margin-bottom:0;'><b>Save Changes</b>: Click the <b>Save</b> button to save updates back to the scenario template file.</li>");
        html.append("</ol>");

        // 2. Scenario & Values Loader
        html.append("<h2 style='color:#34d399; font-size:15px; border-bottom:1px solid #334155; padding-bottom:4px; margin-top:25px;'>2. Scenario & Values Loader (Previews & Imports)</h2>");
        html.append("<p>Manage test profiles, inspect resolved outputs, and import wire traces into SIP Simulator templates.</p>");
        html.append("<b>How to use:</b>");
        html.append("<ol style='padding-left:20px;'>");
        html.append("<li style='margin-bottom:6px;'><b>Choose Template</b>: Choose a file from the <b>Scenario File</b> dropdown list (e.g., <code>Outgoing_basic_call.txt</code>).</li>");
        html.append("<li style='margin-bottom:6px;'><b>Automatic Mapping</b>: The <b>Values File</b> dropdown will auto-select the matching values profile. If you want to use a different set of variables, select it from the values dropdown.</li>");
        html.append("<li style='margin-bottom:6px;'><b>Load Data</b>: Click the <b>Load</b> button to read the templates into memory.</li>");
        html.append("<li style='margin-bottom:6px;'><b>Verify Resolved Preview</b>: On the right pane, look at the <b>Resolved SIP Message Preview</b>. This renders the message with all template placeholders (like <code>[remote_ip]</code> and <code>[local_port]</code>) fully replaced by their actual values. Ensure everything is correct before running.</li>");
        html.append("<li style='margin-bottom:0;'><b>Import PCAP Network Trace</b>:<br>");
        html.append("  - Click <b>Import PCAP File</b>. A file chooser dialog opens.<br>");
        html.append("  - Select a standard network packet capture (<code>.pcap</code> or <code>.pcapng</code>) containing SIP signaling.<br>");
        html.append("  - The engine parses the PCAP, identifies direction flows, and automatically generates matching scenario files and values files saved directly to the respective folders.</li>");
        html.append("</ol>");

        // 3. Edit Values
        html.append("<h2 style='color:#34d399; font-size:15px; border-bottom:1px solid #334155; padding-bottom:4px; margin-top:25px;'>3. Edit Values (Grid Customizer & Telecom Presets)</h2>");
        html.append("<p>Directly customize bound IP addresses, UDP ports, user accounts, and test advanced enterprise PBX features (such as Alcatel-Lucent OmniPCX Enterprise).</p>");
        html.append("<b>How to use:</b>");
        html.append("<ol style='padding-left:20px;'>");
        html.append("<li style='margin-bottom:6px;'><b>Edit Variables</b>: Click on any row/cell in the grid. Double-click the value cell to enter edit mode, type your new value, and press <b>Enter</b> to apply.</li>");
        html.append("<li style='margin-bottom:6px;'><b>Inject Telecom Header Presets</b>: Select a row in the variables grid, then click one of the preset buttons:<br>");
        html.append("  - <b>Inject UUI Header</b>: Inserts a hex-encoded User-to-User Information header (e.g., <code>User-to-User: 04C801C002C4083030303030303030;encoding=hex\\r\\n</code>) for PBX call routing context.<br>");
        html.append("  - <b>Inject Call Diversion</b>: Adds a <code>Diversion</code> header (e.g., <code>Diversion: &lt;sip:70200@[local_ip]&gt;;reason=unconditional;screen=no;privacy=off\\r\\n</code>) to simulate call-forwarding.<br>");
        html.append("  - <b>Inject P-Asserted-Identity</b>: Inserts a <code>P-Asserted-Identity</code> header to test Caller ID (CLI) presentation override.<br>");
        html.append("  - <b>Custom Injection</b>: Prompts you to enter a custom header tag and value (e.g., <code>X-Header: CustomVal</code>).</li>");
        html.append("<li style='margin-bottom:0;'><b>Apply Changes</b>: Edits in the grid are automatically updated in the active values session.</li>");
        html.append("</ol>");

        // 4. Logs
        html.append("<h2 style='color:#34d399; font-size:15px; border-bottom:1px solid #334155; padding-bottom:4px; margin-top:25px;'>4. Logs (Visual Sequence Timeline)</h2>");
        html.append("<p>Review standard text logs or visualize your call flows using the interactive SIP ladder diagram.</p>");
        html.append("<b>How to use:</b>");
        html.append("<ol style='padding-left:20px;'>");
        html.append("<li style='margin-bottom:6px;'><b>Select Transport Folder</b>: Select the target transport folder (<b>UDP</b>, <b>TCP</b>, <b>TLS</b>, or <b>SIP</b>) from the sidebar dropdown.</li>");
        html.append("<li style='margin-bottom:6px;'><b>Locate Log File</b>: Scroll through the list of log files (sorted latest first) or type a filename in the search bar. Click the file name to load it.</li>");
        html.append("<li style='margin-bottom:6px;'><b>Raw Text Review</b>: Under the <b>Raw Log Text</b> sub-tab, read standard chronological logs of the run.</li>");
        html.append("<li style='margin-bottom:6px;'><b>Visual Sequence Ladder</b>: Switch to the <b>Visual Sequence Ladder</b> sub-tab to see the call flow diagram. Host timelines (Local IP and Remote PBX) are plotted vertically. Message arrows are color-coded (cyan for outgoing, green for incoming).</li>");
        html.append("<li style='margin-bottom:0;'><b>Inspect Message Details</b>: Click on any message arrow inside the ladder diagram. The bottom panel will instantly display the exact raw SIP headers and bodies for that packet.</li>");
        html.append("</ol>");

        // 5. Campaigns
        html.append("<h2 style='color:#34d399; font-size:15px; border-bottom:1px solid #334155; padding-bottom:4px; margin-top:25px;'>5. Campaigns (Batch Test Automation)</h2>");
        html.append("<p>Automate testing of regression suites by running scenarios sequentially and verifying output outcomes.</p>");
        html.append("<b>How to use:</b>");
        html.append("<ol style='padding-left:20px;'>");
        html.append("<li style='margin-bottom:6px;'><b>Add Test Case</b>: Click <b>Add Test Case</b>. In the dialog, select a scenario, choose the corresponding values file (auto-suggested based on selection), and set the <b>Expected Status</b> (<code>PASS</code> or <code>FAIL</code>). For a two-party or attended-transfer (3-way) test, also pick the <b>Type</b> and the target scenario/values so the opposite leg(s) of the call are fully defined. Click <b>Save</b> to add it to the suite.</li>");
        html.append("<li style='margin-bottom:6px;'><b>Execute Campaign</b>: Click the <b>Execute Campaign Batch</b> button. For each test case, the runner automatically spawns background headless instances of SIP Simulator to act as the opposite side of the call (the callee, and for 3-way/attended-transfer cases, the transfer target too), each registering through the real PBX using the identities in its own values file, so the whole call flow runs end-to-end without you manually starting a second copy of the app.</li>");
        html.append("<li style='margin-bottom:6px;'><b>Monitor Console Output</b>: Look at the console pane at the bottom. It streams live CLI standard outputs and errors from every spawned leg in real-time as background tests are running.</li>");
        html.append("<li style='margin-bottom:6px;'><b>Verify Status</b>: The runner checks that each expected SIP sequence (e.g. INVITE followed by a 200 OK) actually occurred in order and compares results to the expected outcomes. The test cases in the table will update with a green <b>PASS</b> or a red <b>FAIL</b>.</li>");
        html.append("<li style='margin-bottom:0;'><b>Export Results</b>: Use the <b>Export CSV</b> / <b>Export HTML Report</b> buttons to save a shareable summary of the whole campaign run.</li>");
        html.append("</ol>");

        // 6. Simulation
        html.append("<h2 style='color:#34d399; font-size:15px; border-bottom:1px solid #334155; padding-bottom:4px; margin-top:25px;'>6. Simulation (Network Anomalies & State Tracking)</h2>");
        html.append("<p>Simulate poor network conditions and protocol-level anomalies to stress-test your system.</p>");
        html.append("<b>How to use:</b>");
        html.append("<ol style='padding-left:20px;'>");
        html.append("<li style='margin-bottom:6px;'><b>Latency Sliders</b>: Drag the <b>Latency (Delay)</b> slider to add delays (in milliseconds) to all packet transmissions.</li>");
        html.append("<li style='margin-bottom:6px;'><b>Packet Loss Sliders</b>: Adjust the <b>Packet Loss</b> slider to drop a certain percentage of packets randomly to test retransmission logic.</li>");
        html.append("<li style='margin-bottom:6px;'><b>Protocol Anomalies</b>: Select a value from the <b>Anomaly Injection</b> dropdown (e.g. *Corrupt CSeq*, *Tamper Call-ID*, *Corrupt Expires*, *Tamper Via Branch*). The simulator will modify these parameters in outgoing packets.</li>");
        html.append("<li style='margin-bottom:0;'><b>Monitor Session States</b>: The <b>Active Sessions & Registration States</b> panel displays re-registration timers, expires countdowns, and active dialogue counters in real-time.</li>");
        html.append("</ol>");

        // 7. RTP Inspector
        html.append("<h2 style='color:#34d399; font-size:15px; border-bottom:1px solid #334155; padding-bottom:4px; margin-top:25px;'>7. RTP Inspector (Media & TLS Security Audits)</h2>");
        html.append("<p>Inspect negotiated audio streams and secure TLS handshake details.</p>");
        html.append("<b>How to use:</b>");
        html.append("<ol style='padding-left:20px;'>");
        html.append("<li style='margin-bottom:6px;'><b>Verify Media</b>: Start a call scenario that negotiates RTP (e.g. <code>Outgoing_basic_call.txt</code>).</li>");
        html.append("<li style='margin-bottom:6px;'><b>RTP Metrics</b>: The grid displays active SSRC channels, timestamp drift counters, and RFC 3550 jitter calculation depth.</li>");
        html.append("<li style='margin-bottom:0;'><b>TLS Handshake Validation</b>: If utilizing TLS or mutual TLS (mTLS), the <b>TLS Security Details</b> table displays active certificate Common Names (CNs), ciphers, TLS versions (e.g. TLSv1.3), and expiration validation dates. Use this to catch expired or misconfigured certificates.</li>");
        html.append("</ol>");

        // 8. Web Dashboard
        html.append("<h2 style='color:#34d399; font-size:15px; border-bottom:1px solid #334155; padding-bottom:4px; margin-top:25px;'>8. React Web Dashboard Integration</h2>");
        html.append("<p>Access the unified browser-based interface, fully synchronized with your Java backend configurations.</p>");
        html.append("<b>How to use:</b>");
        html.append("<ol style='padding-left:20px;'>");
        html.append("<li style='margin-bottom:6px;'><b>Launch Dashboard</b>: Go to the menu <b>Tools -> Launch Web Dashboard</b>. The application will start the Express server on port `3001` in the background and open the browser.</li>");
        html.append("<li style='margin-bottom:6px;'><b>Search Scenarios/Values</b>: Navigate options using the custom searchable dropdown menus (highly readable in dark mode).</li>");
        html.append("<li style='margin-bottom:6px;'><b>Telecom Customizer</b>: Edit templates in the browser editor and click preset buttons to inject UUI, diversion, or custom headers.</li>");
        html.append("<li style='margin-bottom:0;'><b>Sequence Diagram SVG Exports</b>: When campaigns are executed, you can export reports containing inline vertical SVG sequence ladder flow diagrams.</li>");
        html.append("</ol>");

        // 9. Tones
        html.append("<h2 style='color:#34d399; font-size:15px; border-bottom:1px solid #334155; padding-bottom:4px; margin-top:25px;'>9. Tones (Ringback, Busy, Reorder, Call-End Audio)</h2>");
        html.append("<p>Insert standard call-progress tones (Ringback/RBT, Busy, Reorder/Fast-Busy, Call-End) at any point in a scenario script — including before the call is answered.</p>");
        html.append("<b>How to use:</b>");
        html.append("<ol style='padding-left:20px;'>");
        html.append("<li style='margin-bottom:6px;'><b>Open the Tones Tab</b>: In the Scenario Editor's control tabs, the <b>Tones</b> tab sits next to <b>SDP</b>.</li>");
        html.append("<li style='margin-bottom:6px;'><b>Choose a Tone</b>: Pick one of the built-in presets (Dial, Busy, Reorder, Call-End) or point at a custom audio/PCAP file.</li>");
        html.append("<li style='margin-bottom:6px;'><b>Continuous Playback</b>: Check <b>Continuous</b> to loop the tone until the next scenario entry plays or the call ends.</li>");
        html.append("<li style='margin-bottom:0;'><b>Insert into the Script</b>: Position the message tree cursor where you want the tone to fire and click <b>&lt;&lt; Add</b>. As the callee/UAS, this can be placed immediately after the incoming INVITE — i.e. before you send back a 180/183/200 OK — so ringback/greeting audio plays as soon as the remote RTP target is known. As the caller/UAC, a tone can only play once an SDP answer (early media or 200 OK) has been received, which is a SIP/RTP constraint rather than a limitation of this tool.</li>");
        html.append("</ol>");

        // 10. Packet Capture (PCAP)
        html.append("<h2 style='color:#34d399; font-size:15px; border-bottom:1px solid #334155; padding-bottom:4px; margin-top:25px;'>10. Packet Capture (SIP + RTP to .pcap)</h2>");
        html.append("<p>Capture live SIP signaling and RTP media into a standard .pcap file you can open directly in Wireshark.</p>");
        html.append("<b>How to use:</b>");
        html.append("<ol style='padding-left:20px;'>");
        html.append("<li style='margin-bottom:6px;'><b>Start Capture</b>: Go to <b>Tools -> Start Packet Capture</b>, choose where to save the .pcap file, and confirm.</li>");
        html.append("<li style='margin-bottom:6px;'><b>Run Your Scenario</b>: All outgoing/incoming SIP messages (UDP/TCP/TLS, cleartext) and RTP audio packets are written to the capture as they occur.</li>");
        html.append("<li style='margin-bottom:0;'><b>Stop Capture</b>: Go to <b>Tools -> Stop Packet Capture</b>. A summary shows the packet count and file location; open it in Wireshark to inspect the trace.</li>");
        html.append("</ol>");

        // 11. Message Diff / Compare
        html.append("<h2 style='color:#34d399; font-size:15px; border-bottom:1px solid #334155; padding-bottom:4px; margin-top:25px;'>11. Compare SIP Messages (Diff Tool)</h2>");
        html.append("<p>Pinpoint interop differences between two SIP messages (e.g. a sent INVITE vs. a PBX response, or two captured traces) header-by-header.</p>");
        html.append("<b>How to use:</b>");
        html.append("<ol style='padding-left:20px;'>");
        html.append("<li style='margin-bottom:6px;'><b>Open the Tool</b>: Go to <b>Tools -> Compare SIP Messages</b>.</li>");
        html.append("<li style='margin-bottom:6px;'><b>Paste Messages</b>: Paste the raw text of Message A (left) and Message B (right), then click <b>Compare</b>.</li>");
        html.append("<li style='margin-bottom:0;'><b>Read the Result</b>: Each header (and the start line and body) is listed with a <b>SAME</b>, <b>DIFFERENT</b>, <b>ONLY IN A</b>, or <b>ONLY IN B</b> verdict, color-coded for quick scanning. Use <b>Swap</b> to flip A/B, or <b>Clear</b> to start over.</li>");
        html.append("</ol>");

        // 12. Message Templates & Search
        html.append("<h2 style='color:#34d399; font-size:15px; border-bottom:1px solid #334155; padding-bottom:4px; margin-top:25px;'>12. Message Templates &amp; Message Search</h2>");
        html.append("<p>Quickly insert common SIP message skeletons, or search across your logs and scenarios.</p>");
        html.append("<b>How to use:</b>");
        html.append("<ol style='padding-left:20px;'>");
        html.append("<li style='margin-bottom:6px;'><b>Message Templates</b>: Go to <b>Tools -> Message Templates</b> to browse a library of ready-made SIP request/response templates, preview one, and copy it to the clipboard for pasting into the Scenario Editor.</li>");
        html.append("<li style='margin-bottom:0;'><b>Message Search</b>: Use the <b>Message Search</b> tab to filter and locate specific messages across loaded logs by method, status code, or free text.</li>");
        html.append("</ol>");

        // 13. Save Values
        html.append("<h2 style='color:#34d399; font-size:15px; border-bottom:1px solid #334155; padding-bottom:4px; margin-top:25px;'>13. Saving Edited Values</h2>");
        html.append("<p>Persist changes made in the Edit Values grid back to disk instead of losing them when you load a different profile.</p>");
        html.append("<b>How to use:</b>");
        html.append("<ol style='padding-left:20px;'>");
        html.append("<li style='margin-bottom:6px;'><b>Save</b>: After editing cells in the <b>Edit Values</b> grid, click <b>Save</b> to write the changes back to the currently loaded values file (the original <code>//</code>/<code>#</code> comment header is preserved).</li>");
        html.append("<li style='margin-bottom:0;'><b>Save As</b>: Click <b>Save As</b> to write the edited values to a new file instead. If <b>Settings -> Protect Values Files</b> is enabled, overwriting a protected file is blocked with a clear message so accidental edits to shared regression profiles can't slip through.</li>");
        html.append("</ol>");
        html.append("</body></html>");
        
        editor.setText(html.toString());
        editor.setCaretPosition(0);
        
        JScrollPane scroll = new JScrollPane(editor);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        
        dialog.add(scroll);
        dialog.setVisible(true);
    }

    private void exportStatistics() {
        ExportUtils.showExportDialog(this,
                "Export Statistics",
                "SIP_Simulator_statistics",
                ".csv",
                file -> sipinspector.Statistics.StatisticsPanel.getInstance().writeStatisticsCsv(file));
    }

    private void showMessageTemplates() {
        MessageTemplatesDialog dialog = new MessageTemplatesDialog(this);
        dialog.setVisible(true);
    }

    private void startPacketCapture() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Start Packet Capture");
        String desktop = System.getProperty("user.home") + java.io.File.separator + "Desktop";
        java.io.File dir = new java.io.File(desktop);
        if (dir.exists()) {
            chooser.setCurrentDirectory(dir);
        }
        String ts = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
        chooser.setSelectedFile(new java.io.File("sip_capture_" + ts + ".pcap"));
        if (sipinspector.Utils.FileChooserUtils.showSaveDialog(this, chooser) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        java.io.File out = chooser.getSelectedFile();
        if (!out.getName().toLowerCase().endsWith(".pcap")) {
            out = new java.io.File(out.getAbsolutePath() + ".pcap");
        }
        try {
            PcapCapture.getInstance().start(out);
            startCaptureItem.setEnabled(false);
            stopCaptureItem.setEnabled(true);
            JOptionPane.showMessageDialog(this,
                    "Packet capture started:\n" + out.getAbsolutePath()
                        + "\n\nAll SIP signaling (UDP/TCP/TLS, cleartext) will be written.\nOpen the .pcap in Wireshark when you stop.",
                    "Capture Started", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Failed to start capture: " + ex.getMessage(),
                    "Capture Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void stopPacketCapture() {
        PcapCapture cap = PcapCapture.getInstance();
        if (!cap.isCapturing()) {
            startCaptureItem.setEnabled(true);
            stopCaptureItem.setEnabled(false);
            return;
        }
        String path = cap.getCurrentFile();
        int count = cap.getPacketCount();
        cap.stop();
        startCaptureItem.setEnabled(true);
        stopCaptureItem.setEnabled(false);
        JOptionPane.showMessageDialog(this,
                "Packet capture stopped.\n\nPackets captured: " + count + "\nFile: " + path,
                "Capture Stopped", JOptionPane.INFORMATION_MESSAGE);
    }

    public void openLogsFolder() {
        try {
            LogManager.openLogsFolder(null);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Failed to open logs folder: " + ex.getMessage(),
                    "Open Logs Folder",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Resolve the Node.js executable without requiring it on PATH.
     * Priority: explicit setting -> bundled runtime -> common install
     * locations -> bare "node" (PATH fallback). Keeps the dashboard working
     * on no-admin PCs and packaged installs.
     */
    private static String nodeBin() {
        try {
            String override = GlobalSettings.getSetting("NodeExePath");
            if (override != null && !override.trim().isEmpty()) {
                File f = new File(override.trim());
                if (f.exists()) {
                    return f.getAbsolutePath();
                }
            }
        } catch (Exception ignore) {
        }
        java.util.List<String> candidates = new java.util.ArrayList<>();
        candidates.add("node" + File.separator + "node.exe");                  // bundled (installer)
        candidates.add("web-app" + File.separator + "node" + File.separator + "node.exe");
        String pf = System.getenv("ProgramFiles");
        if (pf != null) {
            candidates.add(pf + File.separator + "nodejs" + File.separator + "node.exe");
        }
        String pf86 = System.getenv("ProgramFiles(x86)");
        if (pf86 != null) {
            candidates.add(pf86 + File.separator + "nodejs" + File.separator + "node.exe");
        }
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null) {
            candidates.add(localAppData + File.separator + "Programs" + File.separator + "nodejs" + File.separator + "node.exe");
        }
        for (String c : candidates) {
            File f = new File(c);
            if (f.exists()) {
                return f.getAbsolutePath();
            }
        }
        // Last resort: search PATH directories explicitly rather than just
        // returning the bare "node" and hoping the OS resolves it - this way
        // a missing/misconfigured PATH is caught and logged here, instead of
        // surfacing 8 seconds later as an unexplained "didn't start in time".
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(File.pathSeparator)) {
                if (dir.trim().isEmpty()) continue;
                File f = new File(dir.trim(), "node.exe");
                if (f.exists()) {
                    return f.getAbsolutePath();
                }
            }
        }
        System.err.println("[Dashboard] Could not find node.exe in any known install location or on PATH.");
        return "node"; // let the OS make one final attempt; will surface as a clear error if it also fails
    }

    private void launchWebDashboard() {
        final String dashboardUrl = "http://localhost:3001";

        // If the server is already running, just open the browser
        if (isWebServerRunning()) {
            openBrowser(dashboardUrl);
            return;
        }

        // Start the web server in the background
        try {
            File webAppDir = new File("web-app");
            if (!webAppDir.exists() || !new File(webAppDir, "server.js").exists()) {
                JOptionPane.showMessageDialog(this,
                        "Web app not found. Expected 'web-app/server.js' in the application directory.",
                        "Launch Dashboard",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            String resolvedNodeBin = nodeBin();
            System.out.println("[Dashboard] Using node binary: " + resolvedNodeBin);

            ProcessBuilder pb = new ProcessBuilder(resolvedNodeBin, "server.js");
            pb.directory(webAppDir);
            pb.redirectErrorStream(true);
            webServerProcess = pb.start();

            System.out.println("[Dashboard] Starting web server...");

            // Capture (never discard) the server's stdout/stderr - if it fails to
            // start, this is the only way to see WHY (bad node path, missing
            // node_modules, port already in use, etc.) instead of a generic
            // "didn't start in time" with no diagnostic information at all.
            final java.util.List<String> serverOutput = Collections.synchronizedList(new ArrayList<>());
            Process serverProc = webServerProcess;
            Thread outputGobbler = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(serverProc.getInputStream(), "UTF-8"))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        serverOutput.add(line);
                        System.out.println("[Dashboard] " + line);
                    }
                } catch (IOException ignore) {
                }
            }, "web-dashboard-output");
            outputGobbler.setDaemon(true);
            outputGobbler.start();

            // Wait for the server to become available (up to 8 seconds)
            new SwingWorker<Boolean, Void>() {
                @Override
                protected Boolean doInBackground() {
                    for (int i = 0; i < 16; i++) {
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            return false;
                        }
                        if (isWebServerRunning()) {
                            return true;
                        }
                        if (!serverProc.isAlive()) {
                            // Exited early - no point waiting out the rest of the timeout.
                            break;
                        }
                    }
                    return false;
                }

                @Override
                protected void done() {
                    try {
                        if (get()) {
                            System.out.println("[Dashboard] Web server is running on port 3001");
                            openBrowser(dashboardUrl);
                        } else {
                            String detail;
                            synchronized (serverOutput) {
                                int from = Math.max(0, serverOutput.size() - 10);
                                detail = String.join("\n", serverOutput.subList(from, serverOutput.size()));
                            }
                            System.err.println("[Dashboard] Web server failed to start");
                            String message = "Web server did not start in time (node binary used: " + resolvedNodeBin + ").";
                            if (!detail.isEmpty()) {
                                message += "\n\nLast server output:\n" + detail;
                            } else if (!serverProc.isAlive()) {
                                message += "\n\nThe process exited immediately with no output - check that Node.js is installed and working.";
                            }
                            JOptionPane.showMessageDialog(SIPInspectorMainFrame.this,
                                    message,
                                    "Launch Dashboard",
                                    JOptionPane.ERROR_MESSAGE);
                        }
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(SIPInspectorMainFrame.this,
                                "Error waiting for web server: " + ex.getMessage(),
                                "Launch Dashboard",
                                JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();

        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Could not start web server: " + ex.getMessage() +
                    "\nNode.js was not found. Install Node.js (no admin needed for the user installer)," +
                    "\nplace a node runtime in the app's 'node' folder," +
                    "\nor set 'NodeExePath' in config/settings.properties to the full path of node.exe.",
                    "Launch Dashboard",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private boolean isWebServerRunning() {
        try (Socket socket = new Socket("localhost", 3001)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void openBrowser(String url) {
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
            } else {
                Runtime.getRuntime().exec("rundll32 url.dll,FileProtocolHandler " + url);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Could not launch browser: " + ex.getMessage(),
                    "Launch Dashboard",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void stopWebServer() {
        if (webServerProcess != null && webServerProcess.isAlive()) {
            webServerProcess.destroyForcibly();
            webServerProcess = null;
        }
    }

    /** Apply a theme immediately by its Settings-tab display name (e.g. "Ocean Blue"). */
    public void applyThemeByDisplayName(String displayName) {
        applyThemeSilently(mapThemeNameToCode(displayName));
    }

    private String mapThemeNameToCode(String string) {
        if ("Classic Blue".equals(string)) {
            return "light";
        }
        if ("Ocean Blue".equals(string)) {
            return "ocean";
        }
        if ("Sunset Orange".equals(string)) {
            return "sunset";
        }
        if ("Forest Green".equals(string)) {
            return "forest";
        }
        if ("Purple Dream".equals(string)) {
            return "purple";
        }
        if ("Midnight Blue".equals(string)) {
            return "midnight";
        }
        return "ocean";
    }

    private void applyThemeSilently(String string) {
        try {
            if ("light".equals(string)) {
                FlatLightLaf.setup();
            } else if ("dark".equals(string)) {
                FlatDarkLaf.setup();
            } else if ("ocean".equals(string)) {
                CustomThemes.applyOceanTheme();
            } else if ("sunset".equals(string)) {
                CustomThemes.applySunsetTheme();
            } else if ("forest".equals(string)) {
                CustomThemes.applyForestTheme();
            } else if ("purple".equals(string)) {
                CustomThemes.applyPurpleTheme();
            } else if ("midnight".equals(string)) {
                CustomThemes.applyMidnightTheme();
            }
            CustomThemes.applyStandardFonts();
            SwingUtilities.invokeLater(new Runnable() {

                @Override
                public void run() {
                    for (Window window : Window.getWindows()) {
                        SwingUtilities.updateComponentTreeUI(window);
                        window.repaint();
                    }
                    SIPInspectorMainFrame.this.applyCurrentThemeToTabs();
                    SIPInspectorMainFrame.this.applyThemeToButtons();
                    SIPInspectorMainFrame.this.resetMessageAreaColors();
                }
            });
        } catch (Exception exception) {
            System.err.println("Failed to apply theme on startup: " + exception.getMessage());
        }
    }

    private void switchTheme(String string) {
        try {
            String string2 = "";
            if ("light".equals(string)) {
                FlatLightLaf.setup();
                string2 = "Light";
            } else if ("dark".equals(string)) {
                FlatDarkLaf.setup();
                string2 = "Dark";
            } else if ("ocean".equals(string)) {
                CustomThemes.applyOceanTheme();
                string2 = "Ocean Blue";
            } else if ("sunset".equals(string)) {
                CustomThemes.applySunsetTheme();
                string2 = "Sunset Orange";
            } else if ("forest".equals(string)) {
                CustomThemes.applyForestTheme();
                string2 = "Forest Green";
            } else if ("purple".equals(string)) {
                CustomThemes.applyPurpleTheme();
                string2 = "Purple Dream";
            } else if ("midnight".equals(string)) {
                CustomThemes.applyMidnightTheme();
                string2 = "Midnight Blue";
            }
            CustomThemes.applyStandardFonts();
            SwingUtilities.invokeLater(new Runnable() {

                @Override
                public void run() {
                    for (Window window : Window.getWindows()) {
                        SwingUtilities.updateComponentTreeUI(window);
                        window.repaint();
                    }
                    SIPInspectorMainFrame.this.applyCurrentThemeToTabs();
                    SIPInspectorMainFrame.this.applyThemeToButtons();
                    SIPInspectorMainFrame.this.resetMessageAreaColors();
                }
            });
            try {
                GlobalSettings.setSetting("theme", string);
            } catch (Exception exception) {
                System.err.println("Could not save theme preference: " + exception.getMessage());
            }
            JOptionPane.showMessageDialog(this, "Theme changed to: " + string2, "Theme Switcher", 1);
        } catch (Exception exception) {
            JOptionPane.showMessageDialog(this, "Failed to change theme:\n" + exception.getMessage(), "Theme Error", 0);
        }
    }

    private void AboutMenuItemActionPerformed(ActionEvent actionEvent) {
        AboutDialog aboutDialog = new AboutDialog(this);
        aboutDialog.setLocationRelativeTo(this);
        aboutDialog.setVisible(true);
    }

    private void LoadScenMenuItemActionPerformed(ActionEvent actionEvent) {
        File file = new File("scenarios");
        JFileChooser jFileChooser = file.exists() && file.isDirectory() ? new JFileChooser(file)
                : new JFileChooser(System.getProperty("user.dir"));
        jFileChooser.setDialogTitle("Select a valid scenario file");
        if (sipinspector.Utils.FileChooserUtils.showOpenDialog(this, jFileChooser) == 0) {
            String string = jFileChooser.getSelectedFile().getPath();
            GlobalSettings.setSetting("scenarioPath", string);
            LoadScenario loadScenario = new LoadScenario(this, GlobalSettings.getSetting("scenarioPath"));
            loadScenario.execute();
        }
    }

    private void SaveScenMenuItemActionPerformed(ActionEvent actionEvent) {
        int entriesCount = SIPScenario.getEntriesCount();
        JFileChooser chooser = new JFileChooser(System.getProperty("user.dir"));
        chooser.setDialogTitle("Save scenario file");
        if (sipinspector.Utils.FileChooserUtils.showSaveDialog(this, chooser) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (file.exists()) {
                if (GlobalSettings.isScenarioProtected) {
                    JOptionPane.showMessageDialog(this,
                            "\"" + file.getName() + "\" already exists and Scenario Protection is enabled "
                                    + "(Settings tab).\nChoose a different file name, or disable Scenario "
                                    + "Protection to overwrite.",
                            "Scenario Protected",
                            JOptionPane.WARNING_MESSAGE);
                    return;
                }
                Object[] options = new Object[] { "Yes", "No" };
                int choice = JOptionPane.showOptionDialog(this,
                        "Do you really want to overwrite an existing scenario file?",
                        "Save File",
                        JOptionPane.DEFAULT_OPTION,
                        JOptionPane.WARNING_MESSAGE,
                        null,
                        options,
                        options[0]);
                if (choice != 0) {
                    return;
                }
            }
            try (FileOutputStream fos = new FileOutputStream(file)) {
                for (int i = 0; i < entriesCount; ++i) {
                    fos.write(SIPScenario.getScenarioPos(i).getScenarioScreenText()
                            .replace("\u00ab\r\n", "\r\n").getBytes());
                }
                GlobalSettings.setSetting("scenarioPath", file.getName());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this,
                        "Failed to save scenario: " + ex.getMessage(),
                        "Save Scenario",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void MessageTextAreaMouseExited(MouseEvent mouseEvent) {
        // Commit edits deterministically (selection-aware; rebuilds the whole
        // scenario when nothing is selected). Replaces the old guard that dropped
        // edits made to the full-scenario text when no tree node was selected.
        this.commitEditorEdits();
        this.MessageTextArea.getCaret().setVisible(false);
        this.MessageTextArea.setEditable(false);
    }

    private void MessageTextAreaMouseEntered(MouseEvent mouseEvent) {
        this.MessageTextArea.setEditable(true);
        this.MessageTextArea.getCaret().setVisible(true);
    }

    private void UDPRadioButtonActionPerformed(ActionEvent actionEvent) {
        String string = GlobalSettings.getSetting("defaultUdpTcpLocalPort");
        String string2 = GlobalSettings.getSetting("defaultUdpTcpRemotePort");
        if (string != null) {
            this.LocalPort.setText(string);
        } else {
            this.LocalPort.setText("5060");
        }
        if (string2 != null) {
            this.RemoteServerPort.setText(string2);
        } else {
            this.RemoteServerPort.setText("5060");
        }
    }

    private void TLSRadioButtonActionPerformed(ActionEvent actionEvent) {
        if (this.TLSRadioButton.isSelected()) {
            String string = GlobalSettings.getSetting("defaultTlsLocalPort");
            String string2 = GlobalSettings.getSetting("defaultTlsRemotePort");
            if (string != null) {
                this.LocalPort.setText(string);
            } else {
                this.LocalPort.setText("5061");
            }
            if (string2 != null) {
                this.RemoteServerPort.setText(string2);
            } else {
                this.RemoteServerPort.setText("5061");
            }
            JOptionPane.showMessageDialog(this,
                    "TLS is selected as transport.\nIf your server expects SRTP, please open the SDP tab and enable 'Use SRTP (RTP/SAVP)'.",
                    "TLS/SRTP reminder", 1);
        }
    }

    private void TCPRadioButtonActionPerformed(ActionEvent actionEvent) {
        String string = GlobalSettings.getSetting("defaultUdpTcpLocalPort");
        String string2 = GlobalSettings.getSetting("defaultUdpTcpRemotePort");
        if (string != null) {
            this.LocalPort.setText(string);
        } else {
            this.LocalPort.setText("5060");
        }
        if (string2 != null) {
            this.RemoteServerPort.setText(string2);
        } else {
            this.RemoteServerPort.setText("5060");
        }
    }

    private void totCalsFieldFocusLost(FocusEvent focusEvent) {
        GlobalSettings.setSetting("totalCalls", this.totCalsField.getText());
    }

    private void cpsFieldFocusLost(FocusEvent focusEvent) {
        GlobalSettings.setSetting("callsPerSec", this.cpsField.getText());
    }

    private void LoadValuesItemActionPerformed(ActionEvent actionEvent) {
        File file = new File("values");
        JFileChooser jFileChooser = file.exists() && file.isDirectory() ? new JFileChooser(file)
                : new JFileChooser(System.getProperty("user.dir"));
        jFileChooser.setDialogTitle("Select a values file");
        if (sipinspector.Utils.FileChooserUtils.showOpenDialog(this, jFileChooser) == 0) {
            String string = jFileChooser.getSelectedFile().getPath();
            GlobalSettings.setSetting("valuesPath", string);
            this.valuesLoader.loadValuesFromFile(string);
        }
    }

    private void MaxConcurrentCallsFieldFocusLost(FocusEvent focusEvent) {
        GlobalSettings.setSetting("maxConcurrCalls", this.MaxConcurrentCallsField.getText());
    }

    private void RemoteRTPMenuItemActionPerformed(ActionEvent actionEvent) {
        RemoteRTP remoteRTP = new RemoteRTP(this);
        remoteRTP.setLocationRelativeTo(this);
        remoteRTP.setVisible(true);
    }

    private void QoSMenuItemActionPerformed(ActionEvent actionEvent) {
        QoS qoS = new QoS(this);
        qoS.setLocationRelativeTo(this);
        qoS.setVisible(true);
    }

    private void SDPCalculatorActionPerformed(ActionEvent actionEvent) {
        SDPBodyCalc calc = new SDPBodyCalc(this);
        calc.setLocationRelativeTo(this);
        calc.setVisible(true);
    }

    public static enum SOCKET_TYPE {
        NONE,
        UDP_CLIENT,
        UDP_SERVER,
        TCP_CLIENT,
        TCP_SERVER;

    }
}