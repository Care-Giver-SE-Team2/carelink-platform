package sg.nus.carelink.profile.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Checks visibility and inclusive expiry dates without changing stored credential states.
 *
 * @author Wang Zhili
 */
class CredentialPublicStatusTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 9, 28);

	@ParameterizedTest
	@EnumSource(value = Credential.Status.class, names = { "SUBMITTED", "REJECTED" })
	void unpublishedCredentialsAreHiddenEvenWhenTheirDatesAreCurrent(Credential.Status status) {
		assertThat(credential(status, TODAY.minusDays(1), TODAY.plusYears(1)).publicStatusOn(TODAY)).isEmpty();
	}

	@ParameterizedTest
	@CsvSource({
			"PUBLISHED,-1,EXPIRED", "PUBLISHED,0,PUBLISHED", "PUBLISHED,1,PUBLISHED", "PUBLISHED,30,PUBLISHED",
			"EXPIRING,-1,EXPIRED", "EXPIRING,0,EXPIRING", "EXPIRING,1,EXPIRING", "EXPIRING,100,EXPIRING",
			"REVOKED,-1,REVOKED", "REVOKED,0,REVOKED", "REVOKED,100,REVOKED",
			"EXPIRED,-1,EXPIRED", "EXPIRED,0,EXPIRED", "EXPIRED,100,EXPIRED"
	})
	void publicStatesRespectExpiryAndNeverReviveRevokedOrExpiredCredentials(
			Credential.Status storedStatus, int daysUntilExpiry, Credential.Status expected) {
		var credential = credential(storedStatus, null, TODAY.plusDays(daysUntilExpiry));

		assertThat(credential.publicStatusOn(TODAY)).contains(expected);
		assertThat(credential.status()).isEqualTo(storedStatus);
	}

	@ParameterizedTest
	@EnumSource(value = Credential.Status.class, names = { "PUBLISHED", "EXPIRING", "EXPIRED", "REVOKED" })
	void permanentDatesDoNotOverrideExistingReviewStates(Credential.Status status) {
		assertThat(credential(status, null, LocalDate.of(9999, 12, 31)).publicStatusOn(TODAY)).contains(status);
	}

	@Test
	void futureStartDateRemainsAvailableWithoutInventingANewStatus() {
		var credential = credential(Credential.Status.PUBLISHED, TODAY.plusDays(2), TODAY.plusYears(1));

		assertThat(credential.publicStatusOn(TODAY)).contains(Credential.Status.PUBLISHED);
		assertThat(credential.validFrom()).isAfter(TODAY);
	}

	@Test
	void aFutureStartDateDoesNotHideAnAlreadyElapsedExpiry() {
		var credential = credential(Credential.Status.PUBLISHED, TODAY.plusDays(1), TODAY.minusDays(1));

		assertThat(credential.publicStatusOn(TODAY)).contains(Credential.Status.EXPIRED);
	}

	private static Credential credential(Credential.Status status, LocalDate validFrom, LocalDate expiryDate) {
		return new Credential(401L, 201L, 11L, 5L, "private-certificate", "Training Centre",
				validFrom, expiryDate, status, null, null, null);
	}
}
