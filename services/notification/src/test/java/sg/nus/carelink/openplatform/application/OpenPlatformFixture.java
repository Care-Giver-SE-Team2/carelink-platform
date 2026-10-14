package sg.nus.carelink.openplatform.application;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import tools.jackson.databind.json.JsonMapper;

import sg.nus.carelink.openplatform.domain.model.ApplicationDetails;
import sg.nus.carelink.openplatform.domain.model.Provider;
import sg.nus.carelink.openplatform.domain.model.ProviderApplication;

/**
 * The Open Platform's three services wired as the application wires them, over {@link OpenPlatformFakes}
 * and a clock the test moves. Webhooks are created and attempted for real, to a transport that answers
 * what the test queues.
 */
class OpenPlatformFixture {

	static final Instant NOW = Instant.parse("2026-10-15T01:02:11Z"); // 09:02:11 in Singapore

	static final long MANAGER = 42;

	final WebhookDeliveryServiceTest.MutableClock clock = new WebhookDeliveryServiceTest.MutableClock(NOW);
	final OpenPlatformFakes.Applications applications = new OpenPlatformFakes.Applications();
	final OpenPlatformFakes.ProviderStore providers = new OpenPlatformFakes.ProviderStore();
	final OpenPlatformFakes.Endpoints endpoints = new OpenPlatformFakes.Endpoints();
	final InMemoryWebhookDeliveries deliveries = new InMemoryWebhookDeliveries();
	final OpenPlatformFakes.Identities identities = new OpenPlatformFakes.Identities();
	final OpenPlatformFakes.Secrets secrets = new OpenPlatformFakes.Secrets();
	final OpenPlatformFakes.RecordedEvents events = new OpenPlatformFakes.RecordedEvents();
	final WebhookUrlRule webhookUrls = new WebhookUrlRule(false);

	final WebhookDeliveryServiceTest.RecordingTransport transport = new WebhookDeliveryServiceTest.RecordingTransport();
	final WebhookDeliveryService webhooks = new WebhookDeliveryService(endpoints, deliveries, transport, secrets,
			JsonMapper.builder().build(), clock);
	final OnboardingService onboarding = new OnboardingService(applications, identities, webhookUrls, clock);
	final ProviderReviewService review = new ProviderReviewService(applications, providers, endpoints, identities,
			secrets, new ProviderEvents(events), clock);
	final PortalService portal = new PortalService(applications, providers, endpoints, deliveries, webhooks,
			identities, secrets, new ProviderEvents(events), webhookUrls, clock);

	static ApplicationDetails details(String email) {
		return new ApplicationDetails("BrightHome Care", "Mei Ling Tan", email, "+6591234567",
				List.of("Hospital escort", "Companionship"), URI.create("https://hooks.brighthome.sg/carelink"));
	}

	ProviderApplication applied(String email) {
		return onboarding.apply(details(email), "correct horse battery");
	}

	/** An application, approved. */
	Provider approved(String email) {
		return review.approve(applied(email).id(), MANAGER);
	}

	static String portalUser(String email) {
		return "sub-" + email;
	}
}
