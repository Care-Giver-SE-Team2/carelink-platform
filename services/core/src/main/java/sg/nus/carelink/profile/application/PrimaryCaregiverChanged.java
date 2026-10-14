package sg.nus.carelink.profile.application;

/**
 * Published after a manager names, replaces or removes an elder's primary caregiver, so the
 * elder's visits can follow: visits generated while the elder had none go to the new one, and
 * the visits nobody has started come off the caregiver who no longer holds the role. An event
 * rather than a call because rostering depends on profile, and profile calling rostering back
 * would make the two a cycle.
 *
 * @param previousCaregiverId the caregiver who held the role until now, when it changed hands;
 *        null when the elder had none, or the same caregiver was named again
 */
public record PrimaryCaregiverChanged(Long elderId, Long previousCaregiverId) {
}
