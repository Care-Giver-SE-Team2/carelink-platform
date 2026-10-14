package sg.nus.carelink.openplatform.domain.model;

import sg.nus.carelink.shared.error.BusinessRuleViolation;

/**
 * The provider asked for its credentials again: CareLink shows them once. The contract answers 410
 * {@code CREDENTIALS_COLLECTED}, not 409, since what was asked for is gone rather than forbidden.
 */
public class CredentialsAlreadyCollected extends BusinessRuleViolation {

	public CredentialsAlreadyCollected() {
		super("CREDENTIALS_COLLECTED", "The credentials were already shown. Rotate the secrets to get new ones.");
	}
}
