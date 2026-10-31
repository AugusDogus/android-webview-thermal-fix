package local.px6.thermal;

import static org.junit.Assert.*;
import org.junit.Test;

public final class ThermalCompatibilityTest {
    private NullPointerException missing(String caller, String operation) {
        NullPointerException error = new NullPointerException(
                "Attempt to invoke interface method 'boolean android.os.IThermalService."
                + operation + "()' on a null object reference");
        error.setStackTrace(new StackTraceElement[] {
            new StackTraceElement("android.os.PowerManager", caller, "PowerManager.java", 1870)
        });
        return error;
    }

    @Test public void recognizesObservedFirmwareFailure() {
        assertTrue(ThermalCompatibility.isMissingService(
                missing("addThermalStatusListener", "registerThermalStatusListener"),
                "addThermalStatusListener", "registerThermalStatusListener"));
    }

    @Test public void recognizesMissingStatusService() {
        assertTrue(ThermalCompatibility.isMissingService(
                missing("getCurrentThermalStatus", "getCurrentThermalStatus"),
                "getCurrentThermalStatus", "getCurrentThermalStatus"));
    }

    @Test public void doesNotHideCallbackOrUnrelatedNullFailures() {
        NullPointerException callback = missing("addThermalStatusListener", "registerThermalStatusListener");
        callback.setStackTrace(new StackTraceElement[] {
            new StackTraceElement("example.Listener", "onThermalStatusChanged", "Listener.java", 12)
        });
        assertFalse(ThermalCompatibility.isMissingService(callback,
                "addThermalStatusListener", "registerThermalStatusListener"));
        assertFalse(ThermalCompatibility.isMissingService(new NullPointerException("Other failure"),
                "addThermalStatusListener", "registerThermalStatusListener"));
        assertFalse(ThermalCompatibility.isMissingService(new NullPointerException(),
                "addThermalStatusListener", "registerThermalStatusListener"));
    }
}
