package sg.nus.carelink.profile.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import sg.nus.carelink.profile.domain.model.Elder;
import sg.nus.carelink.profile.domain.model.IntakeApplication;
import sg.nus.carelink.shared.error.BusinessRuleViolation;

/** The manager's answer to an application: approve once (creating the elder), or decline with a reason. */
class IntakeApplicationReviewTest {

	private static final LocalDateTime AT = LocalDateTime.of(2026, 10, 5, 2, 0);

	@Test
	void theElderRecordCarriesTheFamilysDetailsAndTheSector() {
		Elder elder = pending(IntakeApplication.Status.SUBMITTED).toElder("S31");

		assertThat(elder.id()).isNull();
		assertThat(elder.userId()).isNull();
		assertThat(elder.fullName()).isEqualTo("Tan Bee Choo");
		assertThat(elder.address()).isEqualTo("Blk 230 Bishan St 23, #04-117");
		assertThat(elder.postalCode()).isEqualTo("570230");
		assertThat(elder.sector()).isEqualTo("S31");
		assertThat(elder.preferredDialects()).isEqualTo("Hokkien");
		assertThat(elder.mobilityLevel()).isEqualTo(Elder.MobilityLevel.ASSISTIVE_CANE);
		assertThat(elder.continuityPreference()).isEqualTo(Elder.ContinuityPreference.PREFERRED);
		assertThat(elder.medicalNotes()).isEqualTo("Hard of hearing on the left side.");
		assertThat(elder.dateOfBirth()).isNull();
	}

	@ParameterizedTest
	@EnumSource(value = IntakeApplication.Status.class, names = { "SUBMITTED", "UNDER_REVIEW" })
	void approvingRecordsTheReviewerTheTimeAndTheElder(IntakeApplication.Status status) {
		IntakeApplication approved = pending(status).approve(7L, 900L, "  Welcome  ", AT);

		assertThat(approved.status()).isEqualTo(IntakeApplication.Status.APPROVED);
		assertThat(approved.reviewedByUserId()).isEqualTo(7L);
		assertThat(approved.reviewedAt()).isEqualTo(AT);
		assertThat(approved.elderId()).isEqualTo(900L);
		assertThat(approved.reviewRemarks()).isEqualTo("Welcome");
		assertThat(approved.isPending()).isFalse();
	}

	@Test
	void anApprovalNeedsNoMessage() {
		assertThat(pending(IntakeApplication.Status.SUBMITTED).approve(7L, 900L, " ", AT).reviewRemarks()).isNull();
	}

	@Test
	void decliningKeepsTheReasonAndCreatesNothing() {
		IntakeApplication declined = pending(IntakeApplication.Status.SUBMITTED).decline(7L, " Outside our area ", AT);

		assertThat(declined.status()).isEqualTo(IntakeApplication.Status.REJECTED);
		assertThat(declined.reviewRemarks()).isEqualTo("Outside our area");
		assertThat(declined.reviewedByUserId()).isEqualTo(7L);
		assertThat(declined.reviewedAt()).isEqualTo(AT);
		assertThat(declined.elderId()).isNull();
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = { "", "   " })
	void decliningNeedsAReason(String reason) {
		assertThatThrownBy(() -> pending(IntakeApplication.Status.SUBMITTED).decline(7L, reason, AT))
				.isInstanceOfSatisfying(BusinessRuleViolation.class,
						e -> assertThat(e.code()).isEqualTo("DECLINE_REASON_REQUIRED"));
	}

	@ParameterizedTest
	@EnumSource(value = IntakeApplication.Status.class, names = { "APPROVED", "REJECTED" })
	void anAnsweredApplicationCannotBeAnsweredAgainOrMakeAnElder(IntakeApplication.Status status) {
		IntakeApplication answered = pending(status);

		assertThat(answered.isPending()).isFalse();
		for (Runnable attempt : List.<Runnable>of(() -> answered.approve(7L, 900L, null, AT),
				() -> answered.decline(7L, "No", AT), () -> answered.toElder("S31"))) {
			assertThatThrownBy(attempt::run).isInstanceOfSatisfying(BusinessRuleViolation.class,
					e -> assertThat(e.code()).isEqualTo("APPLICATION_ALREADY_ANSWERED"));
		}
	}

	private static IntakeApplication pending(IntakeApplication.Status status) {
		return new IntakeApplication(42L, 7L, "Tan Bee Choo", 83, "Blk 230 Bishan St 23, #04-117", "570230",
				IntakeApplication.MobilityLevel.ASSISTIVE_CANE, "Hokkien", List.of("BATHING"),
				"Hard of hearing on the left side.", status, null, null, LocalDateTime.of(2026, 10, 5, 1, 0), null,
				null);
	}
}
