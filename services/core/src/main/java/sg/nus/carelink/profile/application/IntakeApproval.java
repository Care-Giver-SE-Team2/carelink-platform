package sg.nus.carelink.profile.application;

import sg.nus.carelink.profile.domain.model.IntakeApplication;

/**
 * The outcome of approving an application: the application (carrying the new elder's id) and the
 * elder's new login. {@code temporaryPassword} is the only plain-text copy; it is shown to the
 * manager once and never stored.
 */
public record IntakeApproval(IntakeApplication application, String username, String temporaryPassword) {
}
