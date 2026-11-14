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
import sipinspector.ScenarioEntries.SIPMessageEntry;
import sipinspector.ScenarioEntries.ScenarioEntry;
import sipinspector.Socket.MySocketAbstract;
import sipinspector.Utils.SIPParser;

/**
 *
 * @author Zarko Coklin
 */
public class CallGeneratorThread extends Thread
{
    public CallGeneratorThread(ScenarioProgressDialog scenarioProgressDialog,
        MySocketAbstract socket)
    {
        progressDialog = scenarioProgressDialog;
        callDB = scenarioProgressDialog.getCallDB();
        this.socket = socket;
        threadStopped = false;
        callDB.resetCallCounter();
    }

    @Override
    /* This thread generates calls..
     */
    public void run()
    {
        SIPCall call;
        long numOfCalls;
        int i;
        int cnt = 1;
        String callId;
        int    sleepTime = 10;
        int    cntMax = 100;   
        float cpsValue = Float.parseFloat(progressDialog.getMainFrame().getCPSText());
        float cpTimeSlot;
        
        if (cpsValue < 1)
        {
            cntMax = Math.round((1/cpsValue) * cntMax);
            cpTimeSlot = (float)1/cntMax;
        }
        else
        {
            cpTimeSlot = cpsValue / cntMax;
        }

        while (true)
        {
            /* Make sure the maximum number of allowed concurrent calls never
             * goes beyond an allowed limit
             */
            if (threadStopped == true || socket.isDaemon() == true)
            {
                return;
            }

            numOfCalls = numNewCalls(cpTimeSlot, cnt);
            for (i = 0; i < numOfCalls; i++)
            {
                call = new SIPCall(progressDialog);
                call.rewindClientScenario();
                try
                {
                    /* Call generator will process first outging message.
                     * That way CallID is generated. That is needed when
                     * storing the call in callDB
                     */
                    ScenarioEntry entry;
                    switch (call.getScenarioPos().getType())
                    {
                        case COMMAND_PLAYPCAP:
                        case SET_REMOTE_TARGET:
                        {
                            callId = call.getCallID();
                            if (callDB.addCall(call, callId, true) == false)
                            {
                                progressDialog.disableStopEnableStart();
                                return;
                            }
                            entry = call.getScenarioPos();
                            entry.processEntry(call, progressDialog);
                        }
                        break;
                        default:
                        {
                            entry = call.getScenarioPos();
                            String outgoingMsg =
                                call.procesOutgoingMsg(((SIPMessageEntry) entry).getSIPMsg());
                            callId = SIPParser.getCallID(outgoingMsg);
                            call.setCallId(callId);
                            if (callDB.addCall(call, callId, true) == false)
                            {
                                progressDialog.disableStopEnableStart();
                                return;
                            }
                            entry.increaseExpectedCnt();
                            progressDialog.updateStats(entry);
                            call.justSendMsg(outgoingMsg);
                            call.nextEntry();
                        }
                        break;
                    }
                }
                catch (Exception ex)
                {
                    continue;
                }

                progressDialog.getCallsInProgress().add(call);
                if (callDB.isMaxAllowedReached() == true)
                {
                    progressDialog.disableStopEnableStart();
                    progressDialog.updateCallsPerSecond();
                    return;
                }
            }
            try
            {
                sleep(sleepTime);
                cnt++;
                if (cnt > cntMax)
                {
                    cnt = 1;
                }
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
    }

    private long numNewCalls(float cpcs,
        int cnt)
    {
        int maxCallsRegardingSpeed =
            Math.round(cnt * cpcs) - Math.round((cnt - 1) * cpcs);
        int maxAllowedConcurrent = 0;

        if (callDB.getMaxConcurrCalls() != 0)
        {
            maxAllowedConcurrent = callDB.getMaxConcurrCalls() - callDB.getConcurrCalls();
            /* max allowed concurrent is more strict requirement */
            if (maxCallsRegardingSpeed > maxAllowedConcurrent)
            {
                return maxAllowedConcurrent;
            }
        }

        return maxCallsRegardingSpeed;
    }

    private ScenarioProgressDialog progressDialog;

    private CallDB callDB;

    private MySocketAbstract socket;

    private boolean threadStopped;
}
