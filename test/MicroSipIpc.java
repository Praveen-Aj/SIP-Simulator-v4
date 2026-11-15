package test;

import java.io.RandomAccessFile;

/**
 * Inter-Process Communication with MicroSIP over Windows Named Pipe.
 */
public class MicroSipIpc {
    private static final String PIPE_PATH = "\\\\.\\pipe\\microsip_ipc";

    public static String sendCommand(String method, String paramsJson) {
        try (RandomAccessFile pipe = new RandomAccessFile(PIPE_PATH, "rw")) {
            String id = String.valueOf(System.currentTimeMillis() % 100000);
            String req;
            if (paramsJson != null && !paramsJson.isEmpty()) {
                req = "{\"jsonrpc\":\"2.0\",\"method\":\"" + method + "\",\"params\":" + paramsJson + ",\"id\":" + id + "}\n";
            } else {
                req = "{\"jsonrpc\":\"2.0\",\"method\":\"" + method + "\",\"id\":" + id + "}\n";
            }
            pipe.write(req.getBytes("UTF-8"));
            return pipe.readLine();
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    public static void main(String[] args) {
        String method = args.length > 0 ? args[0] : "system.status";
        String params = args.length > 1 ? args[1] : null;
        System.out.println(sendCommand(method, params));
    }
}
