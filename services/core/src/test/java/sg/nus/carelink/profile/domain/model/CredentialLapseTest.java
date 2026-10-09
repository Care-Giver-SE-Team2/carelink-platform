package sg.nus.carelink.profile.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/** SYS01: the status the daily scan stores, by days until expiry, with a 30-day window. */
class CredentialLapseTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 4);

	@ParameterizedTest
	@CsvSource({
			"PUBLISHED,31,", "PUBLISHED,30,EXPIRING", "PUBLISHED,0,EXPIRING", "PUBLISHED,-1,EXPIRED",
			"EXPIRING,30,", "EXPIRING,0,", "EXPIRING,-1,EXPIRED", "EXPIRING,-400,EXPIRED"
	})
	void movesForwardAsTheDateComesUp(Credential.Status stored, int daysUntilExpiry, Credential.Status expected) {
		var credential = credential(stored, TODAY.plusDays(daysUntilExpiry));

		var lapsed = credential.lapseOn(TODAY, 30);

		if (expected == null) {
			assertThat(lapsed).isEmpty();
		} else {
			assertThat(lapsed).hasValueSatisfying(c -> {
				assertThat(c.status()).isEqualTo(expected);
				assertThat(c.expiryDate()).isEqualTo(credential.expiryDate());
				assertThat(c.reviewedByUserId()).isEqualTo(credential.reviewedByUserId());
			});
		}
	}

	@ParameterizedTest
	@EnumSource(value = Credential.Status.class, names = { "SUBMITTED", "REJECTED", "REVOKED", "EXPIRED" })
	void leavesAnythingNotCurrentlyApprovedAlone(Credential.Status stored) {
		assertThat(credential(stored, TODAY.minusDays(5)).lapseOn(TODAY, 30)).isEmpty();
	}

	@ParameterizedTest
	@EnumSource(value = Credential.Status.class, names = { "PUBLISHED", "EXPIRING" })
	void aPermanentCertificateNeverLapses(Credential.Status stored) {
		assertThat(credential(stored, Credential.PERMANENT).lapseOn(LocalDate.of(9999, 12, 1), 30)).isEmpty();
	}

	private static Credential credential(Credential.Status status, LocalDate expiry) {
		return new Credential(1L, 201L, 11L, 7L, "FA-1", "Singapore Red Cross", null, expiry, status, null, null, null);
	}
}
