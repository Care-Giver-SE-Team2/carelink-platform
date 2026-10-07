package sg.nus.carelink.profile.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import sg.nus.carelink.shared.error.BusinessRuleViolation;

/** UC-MG06: only a submitted certificate can be reviewed, and each outcome records who and when. */
class CredentialReviewTest {

	private static final LocalDateTime AT = LocalDateTime.of(2026, 10, 2, 9, 15);
	private static final Long MANAGER = 7L;

	@Test
	void publishingANewCertificateRecordsTheReviewer() {
		Credential published = submitted(null).publish(null, MANAGER, AT);

		assertThat(published.status()).isEqualTo(Credential.Status.PUBLISHED);
		assertThat(published.reviewedByUserId()).isEqualTo(MANAGER);
		assertThat(published.reviewedAt()).isEqualTo(AT);
		assertThat(published.reviewNote()).isNull();
	}

	@Test
	void aRenewalPublishesOverTheSameCaregiversCertificateOfTheSameType() {
		Credential old = credential(50L, 201L, 11L, Credential.Status.PUBLISHED, null);

		assertThat(submitted(50L).publish(old, MANAGER, AT).status()).isEqualTo(Credential.Status.PUBLISHED);
	}

	@Test
	void aRenewalOfAnotherCaregiversOrAnotherTypesCertificateIsRefused() {
		Credential otherCaregiver = credential(50L, 999L, 11L, Credential.Status.PUBLISHED, null);
		Credential otherType = credential(50L, 201L, 12L, Credential.Status.PUBLISHED, null);

		assertThatThrownBy(() -> submitted(50L).publish(otherCaregiver, MANAGER, AT))
				.isInstanceOf(BusinessRuleViolation.class).hasMessageContaining("same caregiver and type");
		assertThatThrownBy(() -> submitted(50L).publish(otherType, MANAGER, AT))
				.isInstanceOf(BusinessRuleViolation.class);
		assertThatThrownBy(() -> submitted(50L).publish(null, MANAGER, AT))
				.isInstanceOf(BusinessRuleViolation.class);
	}

	@Test
	void rejectingNeedsAReason() {
		Credential rejected = submitted(null).reject(MANAGER, "Issuer is not accredited", AT);
		assertThat(rejected.status()).isEqualTo(Credential.Status.REJECTED);
		assertThat(rejected.reviewNote()).isEqualTo("Issuer is not accredited");

		assertThatThrownBy(() -> submitted(null).reject(MANAGER, "  ", AT))
				.isInstanceOf(BusinessRuleViolation.class)
				.satisfies(e -> assertThat(((BusinessRuleViolation) e).code()).isEqualTo("REJECT_REASON_REQUIRED"));
	}

	@ParameterizedTest
	@EnumSource(value = Credential.Status.class, names = "SUBMITTED", mode = EnumSource.Mode.EXCLUDE)
	void onlyASubmittedCertificateCanBeReviewed(Credential.Status status) {
		Credential reviewed = credential(60L, 201L, 11L, status, null);

		assertThatThrownBy(() -> reviewed.publish(null, MANAGER, AT))
				.isInstanceOf(BusinessRuleViolation.class)
				.satisfies(e -> assertThat(((BusinessRuleViolation) e).code()).isEqualTo("CREDENTIAL_NOT_SUBMITTED"));
		assertThatThrownBy(() -> reviewed.reject(MANAGER, "reason", AT)).isInstanceOf(BusinessRuleViolation.class);
	}

	@Test
	void aRejectionIsNeitherPublicNorHeld() {
		Credential rejected = credential(60L, 201L, 11L, Credential.Status.REJECTED, null);

		assertThat(rejected.publicStatusOn(AT.toLocalDate())).isEmpty();
		assertThat(rejected.isApproved()).isFalse();
	}

	private static Credential submitted(Long renews) {
		return credential(60L, 201L, 11L, Credential.Status.SUBMITTED, renews);
	}

	private static Credential credential(Long id, Long caregiverId, Long typeId, Credential.Status status, Long renews) {
		return new Credential(id, caregiverId, typeId, null, "SRC-FA-88412", "Singapore Red Cross",
				LocalDate.of(2026, 8, 27), LocalDate.of(2028, 8, 27), status, null, null, renews);
	}
}
