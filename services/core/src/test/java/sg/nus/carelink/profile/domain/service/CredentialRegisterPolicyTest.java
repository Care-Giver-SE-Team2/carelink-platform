package sg.nus.carelink.profile.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import sg.nus.carelink.profile.domain.model.Credential;
import sg.nus.carelink.profile.domain.service.CredentialRegisterPolicy.Entry;
import sg.nus.carelink.profile.domain.service.CredentialRegisterPolicy.State;

/** UC-MG06: which rows the register shows, their state, and which count as expiring. */
class CredentialRegisterPolicyTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 2);
	private final CredentialRegisterPolicy policy = new CredentialRegisterPolicy(30);

	@ParameterizedTest
	@CsvSource({ "-1,EXPIRED,true", "0,REMINDED,true", "30,REMINDED,true", "31,PUBLISHED,false" })
	void anApprovedCertificatesStateFollowsItsExpiry(int days, State state, boolean expiring) {
		Entry entry = only(policy.register(List.of(approved(1L, 201L, TODAY.plusDays(days))), TODAY));

		assertThat(entry.state()).isEqualTo(state);
		assertThat(entry.daysUntilExpiry()).isEqualTo(days);
		assertThat(entry.expiring()).isEqualTo(expiring);
		assertThat(entry.coveredUntil()).isEqualTo(TODAY.plusDays(days));
	}

	@Test
	void aCertificateThatNeverExpiresIsNeverDue() {
		Entry entry = only(policy.register(List.of(approved(1L, 201L, Credential.PERMANENT)), TODAY));

		assertThat(entry.state()).isEqualTo(State.PUBLISHED);
		assertThat(entry.watchedExpiry()).isNull();
		assertThat(entry.expiring()).isFalse();
		assertThat(entry.coveredUntil()).isEqualTo(Credential.PERMANENT);
	}

	@Test
	void aPendingRenewalStandsInForTheCertificateItReplacesAndRacesItsExpiry() {
		Credential old = approved(1L, 201L, TODAY.plusDays(12));
		Credential renewal = pending(2L, 201L, Credential.Status.SUBMITTED, 1L);

		Entry entry = only(policy.register(List.of(old, renewal), TODAY));

		assertThat(entry.credential()).isEqualTo(renewal);
		assertThat(entry.replaces()).isEqualTo(old);
		assertThat(entry.state()).isEqualTo(State.SUBMITTED);
		assertThat(entry.watchedExpiry()).isEqualTo(TODAY.plusDays(12));
		assertThat(entry.expiring()).isTrue();
		assertThat(entry.coveredUntil()).isEqualTo(TODAY.plusDays(12));
	}

	@Test
	void aFirstSubmissionHasNoExpiryAndCoversNobodyYet() {
		Entry entry = only(policy.register(List.of(pending(2L, 201L, Credential.Status.SUBMITTED, null)), TODAY));

		assertThat(entry.watchedExpiry()).isNull();
		assertThat(entry.expiring()).isFalse();
		assertThat(entry.coveredUntil()).isEqualTo(TODAY.minusDays(1));
	}

	@Test
	void aRejectedRenewalBringsBackTheCertificateItWouldHaveReplaced() {
		Credential old = approved(1L, 201L, TODAY.plusDays(12));
		Credential rejected = pending(2L, 201L, Credential.Status.REJECTED, 1L);

		List<Entry> rows = policy.register(List.of(old, rejected), TODAY);

		assertThat(rows).extracting(e -> e.credential().id()).containsExactly(1L, 2L);
		assertThat(rows.get(1).state()).isEqualTo(State.REJECTED);
		assertThat(rows.get(1).coveredUntil()).isNull();
	}

	@Test
	void aRejectedRenewalRacesTheReplacedExpiryButOnlyTheOldRowCountsAsExpiring() {
		Credential old = approved(1L, 201L, TODAY.plusDays(12));
		Credential rejected = pending(2L, 201L, Credential.Status.REJECTED, 1L);

		List<Entry> rows = policy.register(List.of(old, rejected), TODAY);

		assertThat(rows.get(1).watchedExpiry()).isEqualTo(TODAY.plusDays(12));
		assertThat(rows.get(1).daysUntilExpiry()).isEqualTo(12L);
		assertThat(rows.get(1).expiring()).isFalse();
		assertThat(rows.get(0).expiring()).isTrue();
	}

	@Test
	void aRejectedFirstSubmissionHasNoExpiry() {
		Entry entry = only(policy.register(List.of(pending(2L, 201L, Credential.Status.REJECTED, null)), TODAY));

		assertThat(entry.watchedExpiry()).isNull();
		assertThat(entry.expiring()).isFalse();
	}

	@Test
	void aPublishedRenewalReplacesTheOldRow() {
		Credential old = approved(1L, 201L, TODAY.plusDays(12));
		Credential renewed = new Credential(2L, 201L, 11L, 7L, "NEW", null, TODAY, TODAY.plusYears(2),
				Credential.Status.PUBLISHED, null, null, 1L);

		assertThat(policy.register(List.of(old, renewed), TODAY)).extracting(e -> e.credential().id()).containsExactly(2L);
	}

	@Test
	void anotherCaregiversSubmissionCannotHideACertificate() {
		Credential old = approved(1L, 201L, TODAY.plusDays(12));
		Credential foreign = pending(2L, 999L, Credential.Status.SUBMITTED, 1L);

		assertThat(policy.register(List.of(old, foreign), TODAY)).hasSize(2);
	}

	@Test
	void submittedRowsComeFirstThenTheSoonestToLapse() {
		List<Credential> credentials = List.of(
				approved(1L, 201L, TODAY.plusDays(29)),
				approved(2L, 202L, TODAY.plusMonths(8)),
				pending(3L, 203L, Credential.Status.SUBMITTED, null),
				approved(4L, 204L, TODAY.plusDays(21)),
				approved(5L, 205L, TODAY.plusDays(12)),
				pending(6L, 205L, Credential.Status.SUBMITTED, 5L));

		assertThat(policy.register(credentials, TODAY)).extracting(e -> e.credential().id())
				.containsExactly(6L, 3L, 4L, 1L, 2L);
	}

	@Test
	void aNegativeWarningWindowIsRefused() {
		assertThatThrownBy(() -> new CredentialRegisterPolicy(-1)).isInstanceOf(IllegalArgumentException.class);
	}

	private static Entry only(List<Entry> entries) {
		assertThat(entries).hasSize(1);
		return entries.getFirst();
	}

	private static Credential approved(Long id, Long caregiverId, LocalDate expiry) {
		return new Credential(id, caregiverId, 11L, 7L, "CERT-" + id, "Issuer", null, expiry,
				Credential.Status.PUBLISHED, null, null, null);
	}

	private static Credential pending(Long id, Long caregiverId, Credential.Status status, Long renews) {
		return new Credential(id, caregiverId, 11L, null, "CERT-" + id, "Issuer", null, TODAY.plusYears(2),
				status, null, null, renews);
	}
}
