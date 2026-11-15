package test;

import sipinspector.Call.SipCallEngine;
import sipinspector.Media.CallState;

public class OxeFeatureDiagnosis {
    public static void main(String[] args) {
        try {
            System.out.println("=== OXE Feature Diagnosis: Multi-Line & Inquiry ===");
            SipCallEngine engine = SipCallEngine.getInstance();
            engine.startEngine("auto", 5060, "192.168.0.20", 5060);
            
            System.out.println("1. Registering 90008...");
            boolean reg = engine.register("90008", 1800);
            System.out.println("Registration: " + reg);
            if (!reg) return;

            System.out.println("2. Making Call 1 to 90001...");
            MicroSipIpc.sendCommand("call.hangup", "{}");
            Thread.sleep(500);
            
            SipCallEngine.CallSession call1 = engine.makeCall("90008", "90001", 8);
            long start = System.currentTimeMillis();
            while (System.currentTimeMillis() - start < 4000 && call1.state != CallState.ESTABLISHED) {
                if (call1.state == CallState.RINGING) {
                    MicroSipIpc.sendCommand("call.answer", "{}");
                }
                Thread.sleep(100);
            }
            System.out.println("Call 1 State: " + call1.state);
            
            if (call1.state == CallState.ESTABLISHED) {
                System.out.println("3. Putting Call 1 on HOLD (a=sendonly)...");
                boolean holdOk = engine.holdCall(call1.callId);
                System.out.println("Call 1 Hold triggered: " + holdOk);
                Thread.sleep(1500);
                
                System.out.println("4. Attempting Call 2 to 90001 while Call 1 is ON HOLD...");
                // Note: MicroSIP might be busy with call 1 unless MicroSIP supports 2 calls or we test against another ext or same
                SipCallEngine.CallSession call2 = engine.makeCall("90008", "90001", 8);
                long start2 = System.currentTimeMillis();
                while (System.currentTimeMillis() - start2 < 4000 && call2.state != CallState.FAILED && call2.state != CallState.ESTABLISHED) {
                    Thread.sleep(100);
                }
                System.out.println("Call 2 State with Call 1 on hold: " + call2.state + ", lastCode: " + call2.lastResponseCode + ", reason: " + call2.failureReason);
                
                if (call2.lastResponseTxt != null) {
                    System.out.println("Call 2 Response:\n" + call2.lastResponseTxt);
                }
                
                engine.hangupCall(call2.callId);
                Thread.sleep(500);
                
                System.out.println("5. Resuming Call 1...");
                engine.resumeCall(call1.callId);
                Thread.sleep(1000);
                System.out.println("Call 1 State after resume: " + call1.state);
                
                engine.hangupCall(call1.callId);
            }
            
            Thread.sleep(1000);
            engine.unregister("90008");
            System.out.println("=== OXE Diagnosis Complete ===");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
