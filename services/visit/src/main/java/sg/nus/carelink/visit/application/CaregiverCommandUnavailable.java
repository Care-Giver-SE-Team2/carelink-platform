package sg.nus.carelink.visit.application;

/** Technical details stay server-side; the browser must reconcile an uncertain result. */
public class CaregiverCommandUnavailable extends RuntimeException {
    public CaregiverCommandUnavailable(Throwable cause) { super("Caregiver command storage unavailable",cause); }
}
