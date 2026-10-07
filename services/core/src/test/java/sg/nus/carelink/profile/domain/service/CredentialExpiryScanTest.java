package sg.nus.carelink.profile.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import sg.nus.carelink.profile.domain.model.Credential;
import sg.nus.carelink.profile.domain.service.CredentialExpiryScan.Audience;
import sg.nus.carelink.profile.domain.service.CredentialExpiryScan.Lapse;

/** SYS01: which certificates move today, and who hears about each one. */
class CredentialExpiryScanTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 4);

	private final CredentialExpiryScan scan = new CredentialExpiryScan(30);

	@Test
	void onlyCertificatesWhoseStatusMovesAreReturned() {
		List<Lapse> lapses = scan.scan(List.of(
				credential(1L, 201L, Credential.Status.PUBLISHED, TODAY.plusDays(12), null),
				credential(2L, 201L, Credential.Status.EXPIRING, TODAY.plusDays(3), null),
				credential(3L, 202L, Credential.Status.EXPIRING, TODAY.minusDays(1), null),
				credential(4L, 202L, Credential.Status.PUBLISHED, TODAY.plusDays(90), null)), TODAY);

		assertThat(lapses).extracting(l -> l.credential().id(), l -> l.credential().status(), Lapse::expired)
				.containsExactly(
						tuple(1L, Credential.Status.EXPIRING, false),
						tuple(3L, Credential.Status.EXPIRED, true));
	}

	@Test
	void withNoRenewalTheCaregiverAndTheManagersAreTold() {
		assertThat(audienceOf(credential(1L, 201L, Credential.Status.PUBLISHED, TODAY.plusDays(12), null)))
				.isEqualTo(Audience.CAREGIVER_AND_MANAGERS);
	}

	@Test
	void aRenewalWaitingForReviewIsTheManagersBusinessOnly() {
		assertThat(audienceOf(
				credential(1L, 201L, Credential.Status.PUBLISHED, TODAY.plusDays(12), null),
				credential(2L, 201L, Credential.Status.SUBMITTED, TODAY.plusYears(2), 1L)))
				.isEqualTo(Audience.MANAGERS);
	}

	@ParameterizedTest
	@EnumSource(value = Credential.Status.class, names = { "PUBLISHED", "EXPIRING" })
	void anApprovedRenewalMeansNobodyNeedsTelling(Credential.Status renewal) {
		assertThat(audienceOf(
				credential(1L, 201L, Credential.Status.PUBLISHED, TODAY.minusDays(1), null),
				credential(2L, 201L, renewal, TODAY.plusYears(2), 1L)))
				.isEqualTo(Audience.NOBODY);
	}

	@ParameterizedTest
	@EnumSource(value = Credential.Status.class, names = { "REJECTED", "REVOKED" })
	void aRenewalThatFellThroughCountsAsNone(Credential.Status renewal) {
		assertThat(audienceOf(
				credential(1L, 201L, Credential.Status.PUBLISHED, TODAY.plusDays(12), null),
				credential(2L, 201L, renewal, TODAY.plusYears(2), 1L)))
				.isEqualTo(Audience.CAREGIVER_AND_MANAGERS);
	}

	@Test
	void anotherCaregiversRecordNamingThisOneIsNotARenewal() {
		assertThat(audienceOf(
				credential(1L, 201L, Credential.Status.PUBLISHED, TODAY.plusDays(12), null),
				credential(2L, 999L, Credential.Status.PUBLISHED, TODAY.plusYears(2), 1L)))
				.isEqualTo(Audience.CAREGIVER_AND_MANAGERS);
	}

	@Test
	void aNegativeWindowIsRefused() {
		assertThatThrownBy(() -> new CredentialExpiryScan(-1)).isInstanceOf(IllegalArgumentException.class);
	}

	/** The audience of the first credential, which is the one expected to move. */
	private Audience audienceOf(Credential... credentials) {
		return scan.scan(List.of(credentials), TODAY).stream()
				.filter(l -> l.credential().id().equals(credentials[0].id()))
				.findFirst().orElseThrow().audience();
	}

	private static Credential credential(Long id, Long caregiverId, Credential.Status status, LocalDate expiry,
			Long renews) {
		return new Credential(id, caregiverId, 11L, null, "CERT-" + id, null, null, expiry, status, null, null, renews);
	}
}
