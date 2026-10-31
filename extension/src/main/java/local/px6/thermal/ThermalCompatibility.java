package local.px6.thermal;

import android.os.PowerManager;
import android.util.Log;

/** Handles the missing Android thermal Binder service on PX6 firmware. */
public final class ThermalCompatibility {
    private ThermalCompatibility() {}

    public static void addThermalStatusListener(
            PowerManager manager, PowerManager.OnThermalStatusChangedListener listener) {
        try {
            manager.addThermalStatusListener(listener);
        } catch (NullPointerException error) {
            if (!isMissingService(error, "addThermalStatusListener", "registerThermalStatusListener")) {
                throw error;
            }
            Log.w("PX6ThermalCompat", "Thermal service is absent; temperature monitoring is unavailable.");
        }
    }

    public static int getCurrentThermalStatus(PowerManager manager) {
        try {
            return manager.getCurrentThermalStatus();
        } catch (NullPointerException error) {
            if (!isMissingService(error, "getCurrentThermalStatus", "getCurrentThermalStatus")) {
                throw error;
            }
            // Chromium's PowerMonitor already uses -1 for an unavailable PowerManager.
            return -1;
        }
    }

    static boolean isMissingService(NullPointerException error, String caller, String operation) {
        String message = error.getMessage();
        StackTraceElement[] stack = error.getStackTrace();
        return message != null
                && message.contains("android.os.IThermalService." + operation + "(")
                && message.contains("on a null object reference")
                && stack.length > 0
                && stack[0].getClassName().equals("android.os.PowerManager")
                && stack[0].getMethodName().equals(caller);
    }
}
