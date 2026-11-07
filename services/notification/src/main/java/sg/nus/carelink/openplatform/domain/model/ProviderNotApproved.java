package sg.nus.carelink.openplatform.domain.model;

import sg.nus.carelink.shared.error.BusinessRuleViolation;

/**
 * A portal user whose application is not approved asked for what only a provider has: its credentials,
 * webhook settings or deliveries. The contract answers 403 {@code NOT_APPROVED}.
 */
public class ProviderNotApproved extends BusinessRuleViolation {

	public ProviderNotApproved() {
		this("NOT_APPROVED", "The application is not approved.");
	}

	protected ProviderNotApproved(String code, String message) {
		super(code, message);
	}
}
