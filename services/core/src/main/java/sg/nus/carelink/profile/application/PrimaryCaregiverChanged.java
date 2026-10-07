package sg.nus.carelink.profile.application;

/**
 * Published after a manager names an elder's primary caregiver, so visits generated while
 * the elder had none can be given to them. An event rather than a call because rostering
 * depends on profile, and profile calling rostering back would make the two a cycle.
 */
public record PrimaryCaregiverChanged(Long elderId) {
}
