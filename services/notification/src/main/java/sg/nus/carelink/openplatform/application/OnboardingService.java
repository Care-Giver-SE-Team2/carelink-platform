package sg.nus.carelink.openplatform.application;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.openplatform.domain.model.ApplicationDetails;
import sg.nus.carelink.openplatform.domain.model.ProviderApplication;
import sg.nus.carelink.openplatform.domain.repository.ProviderApplications;
import sg.nus.carelink.shared.error.BusinessRuleViolation;

/**
 * Applying to join the Open Platform (PT01, {@code POST /api/open/v1/applications}): the application, and
 * the provider's portal user with the password typed on the form, kept only as a hash. Both are kept in
 * one transaction. A manager then decides ({@link ProviderReviewService}).
 */
@Service
public class OnboardingService {

	private final ProviderApplications applications;
	private final ProviderIdentities identities;
	private final WebhookUrlRule webhookUrls;
	private final Clock clock;

	public OnboardingService(ProviderApplications applications, ProviderIdentities identities,
			WebhookUrlRule webhookUrls, Clock clock) {
		this.applications = applications;
		this.identities = identities;
		this.webhookUrls = webhookUrls;
		this.clock = clock;
	}

	/**
	 * Keeps the application, pending.
	 *
	 * @param webhookUrl as typed; must be HTTPS
	 * @throws BusinessRuleViolation {@code ACCOUNT_EXISTS} when the email has a pending or approved
	 *         application: the provider signs in instead
	 */
	@Transactional
	public ProviderApplication apply(ApplicationDetails details, String password) {
		webhookUrls.check("webhookUrl", details.webhookUrl().toString());
		if (applications.emailHeld(details.contactEmail())) {
			throw new BusinessRuleViolation("ACCOUNT_EXISTS",
					"This email already has a pending or approved application. Sign in instead.");
		}
		String portalUser = identities.createPortalUser(details.contactEmail(), password);
		return applications.create(ProviderApplication.submitted(details, portalUser, clock.instant()));
	}
}
