/*
 * Mock GlobalSettings for testing
 */
public class GlobalSettings {
    public static String getSetting(String key) {
        if ("QoS".equals(key)) {
            return "0"; // Default QoS value
        }
        return null;
    }
}
