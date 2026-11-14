package sipinspector.ScenarioEntries;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import javax.swing.JOptionPane;
import sipinspector.SIPCall;
import sipinspector.ScenarioProgressDialog;
import sipinspector.Media.LiveAudioController;

/**
 * Scenario command to assert a DTMF digit is received.
 * Syntax: cmd_AssertDtmf=<digit> (or assert_dtmf=<digit> / assert_dtmf: <digit>)
 *
 * @author SIP Support Team
 */
public class AssertDtmfEntry extends ScenarioEntry {

    private String expectedDigit = "";

    public AssertDtmfEntry(String content) {
        super(TYPE.ASSERT_DTMF, DIRECTION.NONE);
        String str;

        BufferedReader reader = new BufferedReader(new StringReader(content));
        try {
            while ((str = reader.readLine()) != null) {
                if (str.startsWith("//")) {
                    continue;
                }
                String trimmed = str.trim();
                if (trimmed.startsWith("cmd_AssertDtmf=")) {
                    setExpectedDigit(trimmed, "=");
                    break;
                } else if (trimmed.startsWith("assert_dtmf=")) {
                    setExpectedDigit(trimmed, "=");
                    break;
                } else if (trimmed.startsWith("assert_dtmf:")) {
                    setExpectedDigit(trimmed, ":");
                    break;
                }
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, e, "Update Messages", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void setExpectedDigit(String command, String delimiter) {
        int pos = command.indexOf(delimiter);
        if (pos != -1) {
            expectedDigit = command.substring(pos + 1).trim();
        }
    }

    @Override
    public boolean processEntry(SIPCall call, ScenarioProgressDialog dialog) throws ScenarioException {
        long start = System.currentTimeMillis();
        boolean matched = false;

        sipinspector.Utils.TLSDebugLogger.log("[DTMF ASSERTION] Waiting for digit: '" + expectedDigit + "'");

        while (System.currentTimeMillis() - start < 3000) {
            LiveAudioController active = LiveAudioController.getActiveInstance();
            if (active != null) {
                String dtmf = active.getLastReceivedDtmf();
                if (dtmf != null && dtmf.contains(expectedDigit)) {
                    matched = true;
                    active.consumeDtmf(expectedDigit);
                    break;
                }
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                break;
            }
        }

        if (matched) {
            sipinspector.Utils.TLSDebugLogger.log("[DTMF ASSERTION] Passed! Digit '" + expectedDigit + "' detected.");
            super.increaseExpectedCnt();
            dialog.updateStats(this);
            call.nextEntry();
            return true;
        } else {
            sipinspector.Utils.TLSDebugLogger.log("[DTMF ASSERTION] Failed! Digit '" + expectedDigit + "' was not detected within timeout.");
            super.increaseUnexpectedCnt();
            dialog.updateStats(this);
            throw new ScenarioException("Assert DTMF failed: Expected '" + expectedDigit + "' but was not received");
        }
    }

    @Override
    public String getTreeText() {
        return " ---- Assert DTMF=" + expectedDigit;
    }

    @Override
    public String getScenarioScreenText() {
        return System.getProperty("line.separator") +
               "----------" + System.getProperty("line.separator") +
               "cmd_AssertDtmf=" + expectedDigit +
               System.getProperty("line.separator");
    }

    @Override
    public String getShortCode() {
        return "AssertDtmf=" + expectedDigit;
    }

    @Override
    public String getScenarioProgressScreenText() {
        return " ---- " + getShortCode();
    }
}
