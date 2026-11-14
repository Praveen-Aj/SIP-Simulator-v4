package sipinspector.ScenarioEntries;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Set;
import javax.swing.JOptionPane;
import sipinspector.SIPCall;
import sipinspector.ScenarioProgressDialog;
import sipinspector.Media.LiveAudioController;

/**
 * Scenario command to assert a Payload Type (codec) was observed.
 * Syntax: cmd_AssertPt=<pt> (or assert_pt=<pt> / assert_pt: <pt>)
 *
 * @author SIP Support Team
 */
public class AssertPtEntry extends ScenarioEntry {

    private int expectedPt = -1;

    public AssertPtEntry(String content) {
        super(TYPE.ASSERT_PT, DIRECTION.NONE);
        String str;

        BufferedReader reader = new BufferedReader(new StringReader(content));
        try {
            while ((str = reader.readLine()) != null) {
                if (str.startsWith("//")) {
                    continue;
                }
                String trimmed = str.trim();
                if (trimmed.startsWith("cmd_AssertPt=")) {
                    setExpectedPt(trimmed, "=");
                    break;
                } else if (trimmed.startsWith("assert_pt=")) {
                    setExpectedPt(trimmed, "=");
                    break;
                } else if (trimmed.startsWith("assert_pt:")) {
                    setExpectedPt(trimmed, ":");
                    break;
                }
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, e, "Update Messages", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void setExpectedPt(String command, String delimiter) {
        int pos = command.indexOf(delimiter);
        if (pos != -1) {
            try {
                expectedPt = Integer.parseInt(command.substring(pos + 1).trim());
            } catch (NumberFormatException exc) {
                expectedPt = -1;
            }
        }
    }

    @Override
    public boolean processEntry(SIPCall call, ScenarioProgressDialog dialog) throws ScenarioException {
        long start = System.currentTimeMillis();
        boolean matched = false;

        sipinspector.Utils.TLSDebugLogger.log("[PT ASSERTION] Waiting for payload type: " + expectedPt);

        while (System.currentTimeMillis() - start < 3000) {
            LiveAudioController active = LiveAudioController.getActiveInstance();
            if (active != null) {
                Set<Integer> pts = active.getObservedPayloadTypes();
                if (pts != null && pts.contains(expectedPt)) {
                    matched = true;
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
            sipinspector.Utils.TLSDebugLogger.log("[PT ASSERTION] Passed! Payload Type " + expectedPt + " observed.");
            super.increaseExpectedCnt();
            dialog.updateStats(this);
            call.nextEntry();
            return true;
        } else {
            sipinspector.Utils.TLSDebugLogger.log("[PT ASSERTION] Failed! Payload Type " + expectedPt + " was not observed within timeout.");
            super.increaseUnexpectedCnt();
            dialog.updateStats(this);
            throw new ScenarioException("Assert PT failed: Expected payload type " + expectedPt + " but was not received");
        }
    }

    @Override
    public String getTreeText() {
        return " ---- Assert PT=" + expectedPt;
    }

    @Override
    public String getScenarioScreenText() {
        return System.getProperty("line.separator") +
               "----------" + System.getProperty("line.separator") +
               "cmd_AssertPt=" + expectedPt +
               System.getProperty("line.separator");
    }

    @Override
    public String getShortCode() {
        return "AssertPt=" + expectedPt;
    }

    @Override
    public String getScenarioProgressScreenText() {
        return " ---- " + getShortCode();
    }
}
