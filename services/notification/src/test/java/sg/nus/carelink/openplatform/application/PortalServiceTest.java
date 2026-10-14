package sg.nus.carelink.openplatform.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import java.util.Set;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.eventtypes.ProviderUpdated;
import sg.nus.carelink.openplatform.domain.model.CredentialsAlreadyCollected;
import sg.nus.carelink.openplatform.domain.model.Delivery;
import sg.nus.carelink.openplatform.domain.model.InvalidField;
import sg.nus.carelink.openplatform.domain.model.Provider;
import sg.nus.carelink.openplatform.domain.model.ProviderNotApproved;
import sg.nus.carelink.openplatform.domain.model.WebhookEndpoint;
import sg.nus.carelink.openplatform.domain.model.WebhookEvent;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.ResourceNotFound;

/**
 * The Partner Portal's rules: secrets shown once and then not kept, rotations that report or the
 * webhooks follow, and nothing of an approved provider's for anyone not approved.
 */
class PortalServiceTest extends OpenPlatformFixture {

	private static final String EMAIL = "ops@brighthome.sg";

	private static final String ME = portalUser(EMAIL);

	@Test
	void theCredentialsAreHandedOverOnceAndThenNotKept() {
		Provider provider = approved(EMAIL);

		PortalService.IssuedCredentials issued = portal.collectCredentials(ME);

		assertThat(issued.clientId()).isEqualTo(provider.clientId());
		assertThat(issued.clientSecret()).isEqualTo(identities.secretByClient.get(provider.clientId()));
		assertThat(issued.webhookSecret()).startsWith("whsec_")
				.isEqualTo(secrets.reveal(endpoints.find(provider.id()).orElseThrow().signingSecret()));
		Provider after = providers.find(provider.id()).orElseThrow();
		assertThat(after.collected()).isTrue();
		assertThat(after.uncollectedClientSecret()).isNull();
		assertThat(after.uncollectedWebhookSecret()).isNull();

		assertThatThrownBy(() -> portal.collectCredentials(ME)).isInstanceOf(CredentialsAlreadyCollected.class);
	}

	@Test
	void aPendingProviderHasNoCredentialsToCollect() {
		applied(EMAIL);

		assertThatThrownBy(() -> portal.collectCredentials(ME))
				.isInstanceOfSatisfying(BusinessRuleViolation.class, e -> assertThat(e.code()).isEqualTo("NOT_APPROVED"))
				.isNotInstanceOf(ProviderNotApproved.class);
		assertThatThrownBy(() -> portal.credentials(ME)).isInstanceOf(ProviderNotApproved.class);
		assertThatThrownBy(() -> portal.webhookSettings(ME)).isInstanceOf(ProviderNotApproved.class);
		assertThatThrownBy(() -> portal.deliveries(ME, null, 0, 20)).isInstanceOf(ProviderNotApproved.class);
		assertThatThrownBy(() -> portal.sendTestEvent(ME)).isInstanceOf(ProviderNotApproved.class);
	}

	@Test
	void someoneWithoutAnApplicationHasNone() {
		assertThatThrownBy(() -> portal.application("sub-nobody")).isInstanceOf(ResourceNotFound.class);
	}

	@Test
	void theCredentialsSummaryShowsOnlyTheSecretsLastFourCharacters() {
		Provider provider = approved(EMAIL);

		PortalService.CredentialsSummary summary = portal.credentials(ME);

		assertThat(summary.clientId()).isEqualTo(provider.clientId());
		assertThat(summary.secretHint()).isEqualTo("wxyz");
		assertThat(summary.secretIssuedAt()).isEqualTo(NOW);
		assertThat(summary.scopes()).containsExactlyInAnyOrder("open-platform/jobs.read", "open-platform/jobs.write");
		assertThat(summary.tokenUrl()).isEqualTo("https://auth.test/oauth2/token");
	}

	@Test
	void rotatingTheClientSecretReplacesTheClientAndTellsReport() {
		Provider provider = approved(EMAIL);
		assertThatThrownBy(() -> portal.rotateClientSecret(ME))
				.as("the first secrets are collected, not rotated")
				.isInstanceOfSatisfying(BusinessRuleViolation.class,
						e -> assertThat(e.code()).isEqualTo("CREDENTIALS_NOT_COLLECTED"));
		portal.collectCredentials(ME);
		clock.advance(Duration.ofDays(3));

		ProviderIdentities.ClientCredentials rotated = portal.rotateClientSecret(ME);

		assertThat(rotated.clientId()).isNotEqualTo(provider.clientId());
		assertThat(identities.secretByClient).doesNotContainKey(provider.clientId()).containsKey(rotated.clientId());
		Provider after = providers.find(provider.id()).orElseThrow();
		assertThat(after.clientId()).isEqualTo(rotated.clientId());
		assertThat(after.clientSecretIssuedAt()).isEqualTo(NOW.plus(Duration.ofDays(3)));
		assertThat(after.version()).isEqualTo(1);
		ProviderUpdated told = (ProviderUpdated) events.published.getLast().getValue();
		assertThat(told.clientId()).isEqualTo(rotated.clientId());
		assertThat(told.version()).isEqualTo(1);
	}

	@Test
	void theWebhookSettingsShowTheEndpointAndTheSigningSecretsHint() {
		approved(EMAIL);
		String webhookSecret = portal.collectCredentials(ME).webhookSecret();

		PortalService.WebhookSettings settings = portal.webhookSettings(ME);

		assertThat(settings.url()).isEqualTo(URI.create("https://hooks.brighthome.sg/carelink"));
		assertThat(settings.events()).containsExactlyInAnyOrder(WebhookEvent.JOB_DISPATCHED, WebhookEvent.JOB_CANCELLED);
		assertThat(settings.secretHint()).isEqualTo(webhookSecret.substring(webhookSecret.length() - 4));
		assertThat(settings.secretIssuedAt()).isEqualTo(NOW);
	}

	@Test
	void changingTheWebhookSettingsMovesTheEndpointButNotTheSecret() {
		Provider provider = approved(EMAIL);
		String sealedBefore = endpoints.find(provider.id()).orElseThrow().signingSecret();

		PortalService.WebhookSettings settings = portal.updateWebhookSettings(ME, "https://new.brighthome.sg/hooks",
				Set.of(WebhookEvent.JOB_CANCELLED));

		assertThat(settings.url()).isEqualTo(URI.create("https://new.brighthome.sg/hooks"));
		assertThat(settings.events()).containsExactly(WebhookEvent.JOB_CANCELLED);
		WebhookEndpoint endpoint = endpoints.find(provider.id()).orElseThrow();
		assertThat(endpoint.url()).isEqualTo(URI.create("https://new.brighthome.sg/hooks"));
		assertThat(endpoint.signingSecret()).isEqualTo(sealedBefore);
		assertThatThrownBy(() -> portal.updateWebhookSettings(ME, "http://new.brighthome.sg/hooks", Set.of()))
				.isInstanceOfSatisfying(InvalidField.class, e -> assertThat(e.field()).isEqualTo("url"));
		assertThatThrownBy(() -> portal.updateWebhookSettings(ME, "not a url", Set.of()))
				.isInstanceOf(InvalidField.class);
	}

	@Test
	void rotatingTheSigningSecretKeepsTheOldOneSignedWithForADay() {
		Provider provider = approved(EMAIL);
		String first = portal.collectCredentials(ME).webhookSecret();
		clock.advance(Duration.ofHours(2));

		String second = portal.rotateWebhookSecret(ME);

		assertThat(second).startsWith("whsec_").isNotEqualTo(first);
		WebhookEndpoint endpoint = endpoints.find(provider.id()).orElseThrow();
		assertThat(endpoint.sealedSigningSecrets(clock.instant())).extracting(secrets::reveal)
				.containsExactly(second, first);
		assertThat(endpoint.sealedSigningSecrets(clock.instant().plus(Duration.ofHours(25))))
				.extracting(secrets::reveal).containsExactly(second);
		assertThat(providers.find(provider.id()).orElseThrow().webhookSecretIssuedAt()).isEqualTo(clock.instant());
		assertThat(portal.webhookSettings(ME).secretHint()).isEqualTo(second.substring(second.length() - 4));
		assertThat(events.published).as("report keeps nothing of the signing secret").hasSize(1);
	}

	@Test
	void aTestEventIsAPingDeliveryWithItsSignedRequest() {
		approved(EMAIL);

		PortalService.DeliveryView ping = portal.sendTestEvent(ME);

		assertThat(ping.delivery().event()).isEqualTo(WebhookEvent.PING);
		assertThat(ping.delivery().status()).isEqualTo(Delivery.Status.PENDING);
		assertThat(ping.attempts()).isEmpty();
		assertThat(ping.headers()).containsEntry("X-CareLink-Event", "ping")
				.containsEntry("X-CareLink-Delivery", ping.delivery().id())
				.containsEntry("X-CareLink-Timestamp", Long.toString(NOW.getEpochSecond()))
				.containsKey("X-CareLink-Signature");
	}

	@Test
	void theDeliveryLogPagesTheProvidersOwnDeliveriesNewestFirstByStatus() {
		approved(EMAIL);
		approved("ops@othercare.sg");
		String first = portal.sendTestEvent(ME).delivery().id();
		clock.advance(Duration.ofMinutes(1));
		String second = portal.sendTestEvent(ME).delivery().id();
		portal.sendTestEvent(portalUser("ops@othercare.sg"));
		transport.answers(204);
		transport.answers(500);
		transport.answers(204);
		webhooks.attemptDue();

		PortalService.DeliveryPage all = portal.deliveries(ME, null, 0, 20);
		assertThat(all.items()).extracting(view -> view.delivery().id()).containsExactly(second, first);
		assertThat(all.totalElements()).isEqualTo(2);
		assertThat(portal.deliveries(ME, null, 0, 1).items()).extracting(view -> view.delivery().id()).containsExactly(second);
		assertThat(portal.deliveries(ME, null, 1, 1).items()).extracting(view -> view.delivery().id()).containsExactly(first);

		PortalService.DeliveryView retrying = portal.deliveries(ME, Delivery.Status.PENDING, 0, 20).items().getFirst();
		assertThat(retrying.attempts()).hasSize(1);
		assertThat(retrying.attempts().getFirst().responseStatus()).isIn(204, 500);
	}

	@Test
	void aDeliveryOfAnotherProvidersIsNotFound() {
		approved(EMAIL);
		approved("ops@othercare.sg");
		String theirs = portal.sendTestEvent(portalUser("ops@othercare.sg")).delivery().id();

		assertThatThrownBy(() -> portal.delivery(ME, theirs)).isInstanceOf(PortalService.DeliveryNotFound.class);
		assertThatThrownBy(() -> portal.resend(ME, theirs)).isInstanceOf(PortalService.DeliveryNotFound.class);
		assertThat(portal.delivery(portalUser("ops@othercare.sg"), theirs).delivery().id()).isEqualTo(theirs);
	}

	@Test
	void onlyAFailedDeliveryIsResent() {
		approved(EMAIL);
		String id = portal.sendTestEvent(ME).delivery().id();

		assertThatThrownBy(() -> portal.resend(ME, id))
				.isInstanceOfSatisfying(BusinessRuleViolation.class, e -> assertThat(e.code()).isEqualTo("DELIVERY_NOT_FAILED"));

		for (int attempt = 0; attempt < 6; attempt++) {
			transport.answers(503);
			webhooks.attemptDue();
			clock.advance(Duration.ofHours(7));
		}
		assertThat(portal.delivery(ME, id).delivery().status()).isEqualTo(Delivery.Status.FAILED);

		PortalService.DeliveryView resent = portal.resend(ME, id);
		assertThat(resent.delivery().status()).isEqualTo(Delivery.Status.PENDING);
		assertThat(resent.attempts()).hasSize(6);
		assertThat(resent.headers().get("X-CareLink-Timestamp"))
				.as("shown as the last attempt sent it")
				.isEqualTo(Long.toString(resent.attempts().getLast().at().getEpochSecond()));
	}

	@Test
	void theHealthCountsTheLastSevenDaysDeliveriesByOutcome() {
		Provider provider = approved(EMAIL);
		portal.sendTestEvent(ME);
		transport.answers(200);
		webhooks.attemptDue();
		java.time.Instant deliveredAt = clock.instant();
		clock.advance(Duration.ofMinutes(5));
		portal.sendTestEvent(ME);
		transport.answers(500);
		webhooks.attemptDue();

		PortalService.IntegrationHealth health = portal.health(ME);
		assertThat(health.delivered()).isEqualTo(1);
		assertThat(health.retrying()).isEqualTo(1);
		assertThat(health.failed()).isZero();
		assertThat(health.lastDeliveredAt()).isEqualTo(deliveredAt);
		assertThat(health.suspended()).isFalse();

		clock.advance(Duration.ofDays(8));
		PortalService.IntegrationHealth later = portal.healthOf(provider);
		assertThat(later.delivered()).isZero();
		assertThat(later.lastDeliveredAt()).isNull();
	}
}
