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
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import sipinspector.GlobalSettings;

/**
 *
 * @author Zarko Coklin
 */
public class ValuesLoader extends SwingWorker {
    private boolean hasBeenEdited = false;

    public void loadValuesFromFile(String path) {
        filePath = path;
        hasBeenEdited = false;
        try {
            TLSDebugLogger.log("VALUES LOAD REQUEST: path=" + filePath);
            doInBackground();
        } catch (Exception exc) {
            JOptionPane.showMessageDialog(null,
                    exc,
                    "Loading Values File",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    protected Object doInBackground() throws Exception {
        try {
            BufferedReader bis;
            String line = null;
            if (filePath == null) {
                return null;
            }
            list = new ArrayList();
            bis = new BufferedReader(new FileReader(filePath));
            while ((line = bis.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("//") || line.startsWith("#")) {
                    continue;
                }
                String[] strValues = line.split(";");
                SIValue[] rowValues = new SIValue[strValues.length];
                int cnt = 0;
                for (String value : strValues) {
                    rowValues[cnt++] = new SIValue(value);
                }
                list.add(rowValues);
                TLSDebugLogger.logf("VALUES ROW LOADED: index=%d, fields=%d, raw='%s'",
                        (list.size() - 1), rowValues.length, line);
            }
            TLSDebugLogger.logf("VALUES LOAD COMPLETE: path=%s, rows=%d", filePath,
                    (list != null ? list.size() : 0));
            bis.close();
        } catch (FileNotFoundException ex) {
            JOptionPane.showMessageDialog(null,
                    ex,
                    "Loading Values File",
                    JOptionPane.ERROR_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(null,
                    ex,
                    "Loading Values File",
                    JOptionPane.ERROR_MESSAGE);
        }

        return null;
    }

    public String getFieldValue(SIValue[] row, String word) {
        int pos = SIValue.getIntValue(word.substring(6));
        // Expcted word are in format '[fieldXXX]' where XXX is any integer
        // number
        if (row != null && pos < row.length) {
            String val = row[pos].getValue();
            if (pos == 0 && !hasBeenEdited) {
                String accountUser = GlobalSettings.getUsername();
                if (accountUser != null && !accountUser.isEmpty()) {
                    if (list == null || list.size() <= 1 || val.equals("1000") || val.equals("70200")) {
                        return accountUser;
                    }
                }
            }
            return val;
        }
        return "null";
    }

    public SIValue[] getRowOfValues() {
        SIValue[] tmpArray = null;

        if (list != null && list.size() > 0) {
            int rowIndex = counter % list.size();
            tmpArray = list.get(rowIndex);
            for (SIValue tempValue : tmpArray) {
                if (counter != 0) {
                    tempValue.action();
                }
            }
            TLSDebugLogger.logf(
                    "VALUES ROW SELECTED: counter=%d, rowIndex=%d, fields=%d",
                    counter, rowIndex, tmpArray.length);
            counter++;
        }
        return tmpArray;
    }

    public void resetValuesInEachRow() {
        if (list != null && list.size() > 0) {
            for (SIValue[] tmpList : list) {
                for (SIValue value : tmpList) {
                    value.resetCurrentValue();
                }
            }
        }
    }

    /* This method shall be used when buttons 'Start' is pressed on Dialog */
    public void setCounter(int value) {
        counter = value;
    }

    /**
     * Update current row values (for Edit Values functionality)
     * Updates ALL rows with new values to ensure they're used in next scenario run
     * 
     * @param fieldUpdates Map of field name (e.g., "field0") to new value
     */
    public void updateCurrentRowValues(java.util.Map<String, String> fieldUpdates) {
        if (fieldUpdates != null && !fieldUpdates.isEmpty()) {
            hasBeenEdited = true;
        }
        if (list != null && list.size() > 0) {
            TLSDebugLogger.log("=== UPDATING VALUES IN VALUESLOADER ===");
            TLSDebugLogger.logf("Updates received: %s", fieldUpdates);
            // Update all rows to ensure consistency
            for (SIValue[] row : list) {
                for (java.util.Map.Entry<String, String> entry : fieldUpdates.entrySet()) {
                    try {
                        // Parse field name like "field0" to get index 0
                        String fieldName = entry.getKey();
                        if (fieldName.startsWith("field")) {
                            int fieldIndex = Integer.parseInt(fieldName.substring(5)); // Extract number from "fieldX"
                            if (fieldIndex < row.length) {
                                // Create new SIValue with updated value and reset it
                                SIValue newValue = new SIValue(entry.getValue());
                                newValue.resetCurrentValue(); // Reset to base value
                                row[fieldIndex] = newValue;
                                TLSDebugLogger.logf("VALUES UPDATED: field=%s (index=%d), value=%s", fieldName,
                                        fieldIndex, entry.getValue());
                            }
                        }
                    } catch (NumberFormatException e) {
                        TLSDebugLogger.logf("ERROR parsing field name: %s - %s", entry.getKey(), e.getMessage());
                    }
                }
            }
            TLSDebugLogger.log("=== VALUES UPDATE COMPLETE ===");
        }
    }

    /** @return the path last passed to {@link #loadValuesFromFile}, or null if nothing has been loaded. */
    public String getFilePath() {
        return filePath;
    }

    /**
     * Snapshot of every row's current field values (post edits/increments),
     * one semicolon-joined string per row, in file-line order. Used by
     * "Save Values" to write the in-memory state back to disk at the same
     * fidelity level {@link #updateCurrentRowValues} already operates at.
     */
    public java.util.List<String> getAllRowsAsLines() {
        java.util.List<String> lines = new ArrayList<>();
        if (list == null) {
            return lines;
        }
        for (SIValue[] row : list) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < row.length; i++) {
                if (i > 0) {
                    sb.append(';');
                }
                sb.append(row[i].getValue());
            }
            lines.add(sb.toString());
        }
        return lines;
    }

    private ArrayList<SIValue[]> list = null;

    private String filePath = null;

    int counter = 0;
}
