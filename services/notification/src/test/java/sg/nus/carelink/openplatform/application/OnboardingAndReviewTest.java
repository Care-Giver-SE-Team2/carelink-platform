package sg.nus.carelink.openplatform.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.eventtypes.ProviderUpdated;
import sg.nus.carelink.openplatform.domain.model.ApplicationDetails;
import sg.nus.carelink.openplatform.domain.model.InvalidField;
import sg.nus.carelink.openplatform.domain.model.Provider;
import sg.nus.carelink.openplatform.domain.model.ProviderApplication;
import sg.nus.carelink.openplatform.domain.model.WebhookEndpoint;
import sg.nus.carelink.openplatform.domain.model.WebhookEvent;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.ResourceNotFound;

/**
 * Applying and a manager's decisions (PT01): one account per email, the password handed to the identities
 * and kept nowhere else, and an approval that makes the provider whole, its secrets sealed, report told.
 */
class OnboardingAndReviewTest extends OpenPlatformFixture {

	@Test
	void applyingKeepsAPendingApplicationAndMakesThePortalUserInThePool() {
		ProviderApplication application = onboarding.apply(details("  Ops@BrightHome.SG "), "correct horse battery");

		assertThat(application.status()).isEqualTo(ProviderApplication.Status.PENDING);
		assertThat(application.reference()).isEqualTo("APP-2026-00417");
		assertThat(application.details().contactEmail()).isEqualTo("ops@brighthome.sg");
		assertThat(application.portalUserId()).isEqualTo("sub-ops@brighthome.sg");
		assertThat(identities.passwordByEmail).containsEntry("ops@brighthome.sg", "correct horse battery");
		assertThat(events.published).as("a pending application tells report nothing").isEmpty();
	}

	@Test
	void anEmailWithAPendingOrApprovedApplicationCannotApplyAgain() {
		applied("ops@brighthome.sg");
		assertThatThrownBy(() -> applied("OPS@brighthome.sg"))
				.isInstanceOfSatisfying(BusinessRuleViolation.class, e -> assertThat(e.code()).isEqualTo("ACCOUNT_EXISTS"));

		review.approve(417, MANAGER);
		assertThatThrownBy(() -> applied("ops@brighthome.sg")).isInstanceOf(BusinessRuleViolation.class);
	}

	@Test
	void aRejectedProviderMayApplyAgainWithANewPassword() {
		ProviderApplication first = applied("ops@brighthome.sg");
		review.reject(first.id(), "Missing insurance certificate", MANAGER);

		clock.advance(java.time.Duration.ofDays(1));
		ProviderApplication second = onboarding.apply(details("ops@brighthome.sg"), "a newer passphrase");

		assertThat(second.portalUserId()).isEqualTo(first.portalUserId());
		assertThat(identities.passwordByEmail).containsEntry("ops@brighthome.sg", "a newer passphrase");
		assertThat(applications.latestOf(first.portalUserId())).contains(second);
	}

	@Test
	void aWebhookUrlMustBeHttps() {
		ApplicationDetails plainHttp = new ApplicationDetails("BrightHome Care", "Mei Ling Tan", "ops@brighthome.sg",
				"+6591234567", List.of("Companionship"), URI.create("http://hooks.brighthome.sg/carelink"));

		assertThatThrownBy(() -> onboarding.apply(plainHttp, "correct horse battery"))
				.isInstanceOfSatisfying(InvalidField.class, e -> assertThat(e.field()).isEqualTo("webhookUrl"));
		assertThat(identities.passwordByEmail).as("no user is made for a refused application").isEmpty();
	}

	@Test
	void approvingMakesTheProviderItsClientAndItsEndpointWithEverySecretSealed() {
		ProviderApplication application = applied("ops@brighthome.sg");

		Provider provider = review.approve(application.id(), MANAGER);

		assertThat(provider.id()).as("the application's id").isEqualTo(application.id());
		assertThat(provider.status()).isEqualTo(Provider.Status.ACTIVE);
		assertThat(provider.clientId()).isEqualTo("client-417-1");
		assertThat(provider.clientSecretHint()).isEqualTo("wxyz");
		assertThat(provider.uncollectedClientSecret()).isEqualTo("sealed:secret-417-1-wxyz");
		assertThat(provider.uncollectedWebhookSecret()).startsWith("sealed:whsec_").hasSize("sealed:whsec_".length() + 32);
		assertThat(provider.collected()).isFalse();

		WebhookEndpoint endpoint = endpoints.find(provider.id()).orElseThrow();
		assertThat(endpoint.url()).isEqualTo(URI.create("https://hooks.brighthome.sg/carelink"));
		assertThat(endpoint.events()).containsExactlyInAnyOrder(WebhookEvent.JOB_DISPATCHED, WebhookEvent.JOB_CANCELLED);
		assertThat(endpoint.signingSecret()).isEqualTo(provider.uncollectedWebhookSecret());
		assertThat(endpoint.enabled()).isTrue();

		ProviderApplication decided = applications.find(application.id()).orElseThrow();
		assertThat(decided.status()).isEqualTo(ProviderApplication.Status.APPROVED);
		assertThat(decided.reviewedBy()).isEqualTo(MANAGER);
		assertThat(decided.providerId()).isEqualTo(provider.id());
		assertThat(review.pending()).isEmpty();
		assertThat(review.providers()).containsExactly(provider);
	}

	@Test
	void approvingTellsReportWhoTheProviderIsAndWhatItOffers() {
		Provider provider = approved("ops@brighthome.sg");

		assertThat(events.published).hasSize(1);
		assertThat(events.published.getFirst().getKey()).isEqualTo(ProviderUpdated.TYPE);
		ProviderUpdated event = (ProviderUpdated) events.published.getFirst().getValue();
		assertThat(event.providerId()).isEqualTo(provider.id());
		assertThat(event.clientId()).isEqualTo(provider.clientId());
		assertThat(event.organisationName()).isEqualTo("BrightHome Care");
		assertThat(event.status()).isEqualTo("ACTIVE");
		assertThat(event.services()).containsExactly("Hospital escort", "Companionship");
		assertThat(event.version()).isZero();
		assertThat(event.changedAt()).hasToString("2026-10-15T09:02:11+08:00");
	}

	@Test
	void aDecidedApplicationIsNotDecidedAgain() {
		ProviderApplication application = applied("ops@brighthome.sg");
		review.reject(application.id(), "Outside our service area", MANAGER);

		assertThatThrownBy(() -> review.approve(application.id(), MANAGER))
				.isInstanceOfSatisfying(BusinessRuleViolation.class, e -> assertThat(e.code()).isEqualTo("ALREADY_DECIDED"));
		assertThatThrownBy(() -> review.reject(application.id(), "Again", MANAGER))
				.isInstanceOf(BusinessRuleViolation.class);
		assertThat(identities.secretByClient).as("no client for a rejected application").isEmpty();
	}

	@Test
	void aRejectionNeedsAReasonAndIsShownToTheProvider() {
		ProviderApplication application = applied("ops@brighthome.sg");
		assertThatThrownBy(() -> review.reject(application.id(), "  ", MANAGER)).isInstanceOf(IllegalArgumentException.class);

		ProviderApplication rejected = review.reject(application.id(), " Missing insurance certificate ", MANAGER);

		assertThat(rejected.reason()).isEqualTo("Missing insurance certificate");
		PortalService.MyApplication mine = portal.application(application.portalUserId());
		assertThat(mine.application().status()).isEqualTo(ProviderApplication.Status.REJECTED);
		assertThat(mine.application().reason()).isEqualTo("Missing insurance certificate");
		assertThat(mine.suspended()).isFalse();
	}

	@Test
	void anUnknownApplicationOrProviderIsNotFound() {
		assertThatThrownBy(() -> review.approve(999, MANAGER)).isInstanceOf(ResourceNotFound.class);
		assertThatThrownBy(() -> review.suspend(999)).isInstanceOf(ResourceNotFound.class);
	}

	@Test
	void suspendingStopsTheWebhooksAndReinstatingRestartsThemReportHearingOfBoth() {
		Provider provider = approved("ops@brighthome.sg");

		Provider suspended = review.suspend(provider.id());
		assertThat(suspended.suspended()).isTrue();
		assertThat(suspended.version()).isEqualTo(1);
		assertThat(endpoints.find(provider.id()).orElseThrow().enabled()).isFalse();
		assertThatThrownBy(() -> review.suspend(provider.id()))
				.isInstanceOfSatisfying(BusinessRuleViolation.class, e -> assertThat(e.code()).isEqualTo("ALREADY_SUSPENDED"));

		Provider reinstated = review.reinstate(provider.id());
		assertThat(reinstated.suspended()).isFalse();
		assertThat(reinstated.version()).isEqualTo(2);
		assertThat(endpoints.find(provider.id()).orElseThrow().enabled()).isTrue();
		assertThatThrownBy(() -> review.reinstate(provider.id()))
				.isInstanceOfSatisfying(BusinessRuleViolation.class, e -> assertThat(e.code()).isEqualTo("NOT_SUSPENDED"));

		assertThat(events.published).extracting(entry -> ((ProviderUpdated) entry.getValue()).status())
				.containsExactly("ACTIVE", "SUSPENDED", "ACTIVE");
		assertThat(events.published).extracting(entry -> ((ProviderUpdated) entry.getValue()).version())
				.containsExactly(0, 1, 2);
	}

	@Test
	void aSuspendedProviderSeesItInThePortal() {
		Provider provider = approved("ops@brighthome.sg");
		review.suspend(provider.id());

		assertThat(portal.application(provider.portalUserId()).suspended()).isTrue();
		assertThat(portal.health(provider.portalUserId()).suspended()).isTrue();
		assertThat(Set.copyOf(review.providers())).extracting(Provider::suspended).containsExactly(true);
	}
}
