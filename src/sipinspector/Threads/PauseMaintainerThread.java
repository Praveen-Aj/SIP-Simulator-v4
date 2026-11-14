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

/**
 *
 * @author Zarko Coklin
 */
public class PauseMaintainerThread extends Thread
{

    public PauseMaintainerThread(ScenarioProgressDialog scenarioProgressDialog)
    {
        progressDialog = scenarioProgressDialog;
        callDB = scenarioProgressDialog.getCallDB();
        threadStopped = false;
        waitTime = 10;

    }

    @Override
    /* This method will make sure that calls are generated at a required pace
     * (calls/second) rate, no maximum number of concurrent calls value is
     * exceeded and that each new call is "fairly treated". A "fair treatment"
     * is a concept where if a call scenario requires 5 messages to go out,
     * the logic will make sure the 1st msg is sent out for all the calls, then
     * 2nd message is sent for all the calls, and so on...
     */
    public void run()
    {
        /* if scenario does not have pauses, this thread should not be
         * running at all
         */
        SIPScenario scenario = progressDialog.getSIPScenario();
        int len = scenario.getEntriesCount();
        int i;
        boolean ctrlFlag = false;
        for (i = 0; i < len; i++)
        {
            if (scenario.getScenarioPos(i).getType() ==
                    ScenarioEntry.TYPE.COMMAND_PAUSE)
            {
                ctrlFlag = true;
                break;
            }
        }
        while (ctrlFlag)
        {
            /* Make sure the maximum number of allowed concurrent calls never
             * goes beyond an allowed limit
             */
            if (threadStopped == true)
            {
                return;
            }
            
            SIPCall[] array = callDB.values().toArray(new SIPCall[0]);
            for (SIPCall currCall : array)
            {
                synchronized (currCall)
                {
                    if (currCall.isEndReached() == true)
                    {
                        callDB.deleteCall(currCall);
                        continue;
                    }

                    if (currCall.getScenarioPos().getType() ==
                            ScenarioEntry.TYPE.COMMAND_PAUSE &&
                            currCall.getPauseActivated() == true)
                    {
                        currCall.setPauseDuration(
                                currCall.getPauseDuration() - waitTime);
                        if (currCall.getPauseDuration() <= 0)
                        {
                            currCall.nextEntry();
                            progressDialog.getCallsInProgress().add(currCall);
                        }
                    }
                }
            }

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
