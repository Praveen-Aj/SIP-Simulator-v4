package sipinspector.OperationTabs;

import java.awt.Color;
import java.awt.event.FocusAdapter;import java.awt.event.FocusEvent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import net.miginfocom.swing.MigLayout;
import sipinspector.GlobalSettings;
import sipinspector.Utils.PortValidator;

public class AccountTab extends JPanel {

    private JTextField UsernameTextField;
    private JTextField authPassword;
    private JTextField authUsername;
    private JLabel passwordLabel;
    private JTextField rtpPortField;
    private JLabel rtpPortLabel;
    private JLabel usernameLabel;

    public AccountTab() {
        initComponents();
    }

    public void setUsername(String value) {
        UsernameTextField.setText(value);
    }

    public void setAuthUsername(String value) {
        authUsername.setText(value);
    }

    public void setAuthPassword(String value) {
        authPassword.setText(value);
    }

    private void initComponents() {
        setLayout(new MigLayout("gapx 20px, gapy 1px", "[left] [left] [grow]", ""));
        setName("Account");

        add(new JLabel("Number"));
        usernameLabel = new JLabel("Auth username");
        usernameLabel.setForeground(new Color(51, 51, 255));
        add(usernameLabel, "wrap");

        if (GlobalSettings.getUsername() == null) {
            GlobalSettings.setUsername("1000");
        }
        UsernameTextField = new JTextField(9);
        UsernameTextField.setText(GlobalSettings.getUsername());
        UsernameTextField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent evt) {
                UsernameTextFieldFocusLost(evt);
            }
        });
        add(UsernameTextField, "growx");

        if (GlobalSettings.getAuthUsername() == null) {
            GlobalSettings.setAuthUsername("dead");
        }
        authUsername = new JTextField(GlobalSettings.getAuthUsername());
        authUsername.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent evt) {
                authUsernameFocusLost(evt);
            }
        });
        add(authUsername, "growx");

        passwordLabel = new JLabel("Auth password");
        passwordLabel.setForeground(new Color(51, 51, 255));
        add(passwordLabel, "gaptop 9, cell 1 2");

        if (GlobalSettings.getAuthPassword() == null) {
            GlobalSettings.setAuthPassword("beef");
        }
        authPassword = new JTextField(GlobalSettings.getAuthPassword());
        authPassword.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent evt) {
                authPasswordFocusLost(evt);
            }
        });
        add(authPassword, "cell 1 3, growx");

        rtpPortLabel = new JLabel("RTP Port");
        rtpPortLabel.setForeground(new Color(51, 51, 255));
        add(rtpPortLabel, "gaptop 9, cell 1 4");

        if (GlobalSettings.getMediaPort() == null) {
            GlobalSettings.setMediaPort("6000");
        }
        rtpPortField = new JTextField(GlobalSettings.getMediaPort());
        rtpPortField.setInputVerifier(new PortValidator());
        rtpPortField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent evt) {
                rtpPortFieldFocusLost(evt);
            }
        });
        add(rtpPortField, "growx, cell 1 5");
    }
    public void saveSettings() {
        GlobalSettings.setUsername(UsernameTextField.getText());
        GlobalSettings.setAuthUsername(authUsername.getText());
        GlobalSettings.setAuthPassword(authPassword.getText());
        GlobalSettings.setMediaPort(rtpPortField.getText());
        GlobalSettings.saveSettings();
    }

    private void UsernameTextFieldFocusLost(FocusEvent evt) {
        saveSettings();
    }

    private void authUsernameFocusLost(FocusEvent evt) {
        saveSettings();
    }

    private void authPasswordFocusLost(FocusEvent evt) {
        saveSettings();
    }

    private void rtpPortFieldFocusLost(FocusEvent evt) {
        saveSettings();
    }
}


 

    

     
        
    