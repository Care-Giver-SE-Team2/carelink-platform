package sg.nus.carelink.openplatform.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.shared.error.BusinessRuleViolation;

/** An application's life from pending to its decision, and its provider's from approval on. */
class ProviderTest {

	private static final Instant NOW = Instant.parse("2026-10-15T01:02:11Z");

	private static final ApplicationDetails DETAILS = new ApplicationDetails(" BrightHome Care", "Mei Ling Tan",
			" Ops@BrightHome.SG ", "+6591234567", List.of("Companionship", " Companionship ", "Hospital escort"),
			URI.create("https://hooks.brighthome.sg/carelink"));

	private final ProviderApplication pending = new ProviderApplication(417L, DETAILS, "sub-1",
			ProviderApplication.Status.PENDING, null, NOW, null, null, null);

	@Test
	void theDetailsKeepOneLowerCaseEmailAndEachServiceOnce() {
		assertThat(DETAILS.contactEmail()).isEqualTo("ops@brighthome.sg");
		assertThat(DETAILS.services()).containsExactly("Companionship", "Hospital escort");
		assertThatThrownBy(() -> new ApplicationDetails("A", "B", "c@d.sg", "1", List.of(), DETAILS.webhookUrl()))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void anApplicationIsQuotedByItsYearInSingaporeAndItsId() {
		assertThat(pending.reference()).isEqualTo("APP-2026-00417");
		ProviderApplication newYearsEve = new ProviderApplication(9L, DETAILS, "sub-1",
				ProviderApplication.Status.PENDING, null, Instant.parse("2026-12-31T16:30:00Z"), null, null, null);
		assertThat(newYearsEve.reference()).as("already 2027 in Singapore").isEqualTo("APP-2027-00009");
		assertThat(ProviderApplication.idOf("APP-2026-00417")).contains(417L);
		assertThat(ProviderApplication.idOf("APP-2026-123456")).contains(123456L);
		assertThat(ProviderApplication.idOf("JOB-1")).isEqualTo(Optional.empty());
		assertThat(ProviderApplication.idOf(null)).isEmpty();
		assertThatThrownBy(() -> ProviderApplication.submitted(DETAILS, "sub-1", NOW).reference())
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	void aDecisionIsMadeOnce() {
		ProviderApplication approved = pending.approved(417, 42, NOW);
		assertThat(approved.status()).isEqualTo(ProviderApplication.Status.APPROVED);
		assertThat(approved.holdsItsEmail()).isTrue();
		assertThatThrownBy(() -> approved.rejected("No", 42, NOW))
				.isInstanceOfSatisfying(BusinessRuleViolation.class, e -> assertThat(e.code()).isEqualTo("ALREADY_DECIDED"));

		ProviderApplication rejected = pending.rejected("Outside our area", 42, NOW);
		assertThat(rejected.holdsItsEmail()).isFalse();
		assertThatThrownBy(() -> rejected.approved(417, 42, NOW)).isInstanceOf(BusinessRuleViolation.class);
		assertThatThrownBy(() -> pending.rejected(null, 42, NOW)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void anApprovedApplicationNamesItsProviderAndNoOtherDoes() {
		assertThatThrownBy(() -> new ProviderApplication(1L, DETAILS, "sub", ProviderApplication.Status.APPROVED, null,
				NOW, NOW, 42L, null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new ProviderApplication(1L, DETAILS, "sub", ProviderApplication.Status.PENDING, null,
				NOW, null, null, 1L)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void aNewProviderHoldsItsSecretsSealedUntilItCollectsThem() {
		Provider provider = Provider.approved(pending, "client-1", "wxyz", "sealed-client", "sealed-hook", NOW);

		assertThat(provider.id()).isEqualTo(417);
		assertThat(provider.organisationName()).isEqualTo("BrightHome Care");
		assertThat(provider.collected()).isFalse();
		Provider collected = provider.collectedAt(NOW.plusSeconds(60));
		assertThat(collected.uncollectedClientSecret()).isNull();
		assertThat(collected.uncollectedWebhookSecret()).isNull();
		assertThat(collected.version()).as("report keeps nothing of it").isZero();
		assertThatThrownBy(() -> collected.collectedAt(NOW)).isInstanceOf(CredentialsAlreadyCollected.class);
		assertThatThrownBy(() -> new Provider(1, "Org", "a@b.sg", List.of(), "sub", "client", "wxyz", NOW, null, null,
				NOW, null, Provider.Status.ACTIVE, 0, NOW))
				.as("neither collected nor holding its secrets").isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rotatingNeedsTheFirstSecretsCollected() {
		Provider provider = Provider.approved(pending, "client-1", "wxyz", "sealed-client", "sealed-hook", NOW);
		assertThatThrownBy(() -> provider.withClient("client-2", "abcd", NOW))
				.isInstanceOfSatisfying(BusinessRuleViolation.class,
						e -> assertThat(e.code()).isEqualTo("CREDENTIALS_NOT_COLLECTED"));
		assertThatThrownBy(() -> provider.withWebhookSecretIssuedAt(NOW)).isInstanceOf(BusinessRuleViolation.class);

		Provider collected = provider.collectedAt(NOW);
		Instant later = NOW.plus(Duration.ofDays(2));
		Provider rotated = collected.withClient("client-2", "abcd", later);
		assertThat(rotated.clientId()).isEqualTo("client-2");
		assertThat(rotated.clientSecretHint()).isEqualTo("abcd");
		assertThat(rotated.clientSecretIssuedAt()).isEqualTo(later);
		assertThat(rotated.version()).isEqualTo(1);
		assertThat(collected.withWebhookSecretIssuedAt(later).webhookSecretIssuedAt()).isEqualTo(later);
		assertThat(collected.withWebhookSecretIssuedAt(later).version()).isZero();
	}

	@Test
	void suspendingAndReinstatingEachMoveTheVersionOn() {
		Provider provider = Provider.approved(pending, "client-1", "wxyz", "sealed-client", "sealed-hook", NOW);
		Provider suspended = provider.suspend();
		assertThat(suspended.suspended()).isTrue();
		assertThat(suspended.version()).isEqualTo(1);
		assertThat(suspended.uncollectedClientSecret()).as("still to collect").isEqualTo("sealed-client");
		assertThatThrownBy(suspended::suspend).isInstanceOf(BusinessRuleViolation.class);
		assertThat(suspended.reinstate().version()).isEqualTo(2);
		assertThatThrownBy(provider::reinstate).isInstanceOf(BusinessRuleViolation.class);
	}

	@Test
	void aHintIsTheLastFourCharacters() {
		assertThat(Provider.hintOf("whsec_abcdefgh")).isEqualTo("efgh");
		assertThat(Provider.hintOf("abc")).isEqualTo("abc");
	}
}
