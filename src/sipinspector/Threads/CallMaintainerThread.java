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

import sipinspector.*;
import sipinspector.ScenarioEntries.ScenarioEntry;
import sipinspector.ScenarioEntries.ScenarioException;

/**
 *
 * @author Zarko Coklin
 */
public class CallMaintainerThread extends Thread
{

    public CallMaintainerThread(ScenarioProgressDialog scenarioProgressDialog)
    {
        progressDialog = scenarioProgressDialog;
        callDB = scenarioProgressDialog.getCallDB();
        threadStopped = false;
        waitTime = 10;

    }

    @Override
    /* This thread makes sure that scenario entries are walked through.
     * Its priority is to work on each call progressing in terms of scenario
     * entries being executed. But, it is not able to deal with pauses
     * within a call.
     */
    public void run()
    {
        boolean actionTaken;

        while (true)
        {
            /* Make sure the maximum number of allowed concurrent calls never
             * goes beyond an allowed limit
             */
            if (threadStopped == true)
            {
                return;
            }
          
            actionTaken = false;
            SIPCall[] array = progressDialog.getCallsInProgress().toArray(new SIPCall[0]);
            for (SIPCall currCall : array)
            {
                synchronized (currCall)
                {
                    if (processCurrCall(currCall) == false)
                    {
                        progressDialog.getCallsInProgress().remove(currCall);
                    }
                    else
                    {
                        actionTaken = true;
                    }
                }
            }

            if (actionTaken == false)
            {
                try
                {
                    sleep(waitTime);
                }
                catch (InterruptedException ex)
                {
                    return;
                }
            }
        }
    }

      private boolean processCurrCall(SIPCall currCall)
    {
        if (currCall == null)
        {
            return false;
        }

        if (currCall.isEndReached() == true)
        {
            callDB.deleteCall(currCall);
            currCall = null;
            return false;
        }
        try
        {
            ScenarioEntry entry = currCall.getScenarioPos();
            return entry.processEntry(currCall, progressDialog);

        }
        catch (ScenarioException exc)
        {
            callDB.deleteCall(currCall);
            currCall = null;
            return false;
        }
    }

    static public int getWaitTime()
    {
        return waitTime;
    }

    public void stopThread()
    {
        threadStopped = true;
        interrupt();
    }

    private ScenarioProgressDialog progressDialog;

    private CallDB callDB;

    private boolean threadStopped;

    private static int waitTime;

}
