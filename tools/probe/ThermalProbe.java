import android.content.Context;
import android.os.Looper;
import android.os.PowerManager;
import local.px6.thermal.ThermalCompatibility;

public final class ThermalProbe {
    public static void main(String[] args) throws Exception {
        Looper.prepareMainLooper();
        Class<?> activityThread = Class.forName("android.app.ActivityThread");
        Object thread = activityThread.getMethod("systemMain").invoke(null);
        Context context = (Context) activityThread.getMethod("getSystemContext").invoke(thread);
        PowerManager manager = context.getSystemService(PowerManager.class);
        try {
            manager.addThermalStatusListener(status -> {});
            throw new AssertionError("The unpatched call unexpectedly succeeded.");
        } catch (NullPointerException expected) {
            System.out.println("BASELINE: " + expected.getMessage());
        }
        ThermalCompatibility.addThermalStatusListener(manager, status -> {});
        System.out.println("PATCHED_LISTENER: returned without crashing");
        int status = ThermalCompatibility.getCurrentThermalStatus(manager);
        if (status != -1) throw new AssertionError("Expected unavailable (-1), got " + status);
        System.out.println("PATCHED_STATUS: unavailable (-1)");
    }
}
