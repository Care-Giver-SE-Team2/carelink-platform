package sg.nus.carelink.profile.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.profile.domain.model.Elder;
import sg.nus.carelink.profile.domain.model.IntakeApplication;
import sg.nus.carelink.profile.domain.service.DuplicateElderRule;
import sg.nus.carelink.shared.error.BusinessRuleViolation;

/** One elder, one record: same name (ignoring case and spacing) at the same postcode. */
class DuplicateElderRuleTest {

	private static final Long GRACE = 7L;
	private static final Long OTHER_FAMILY = 8L;

	@Test
	void aNewPersonPasses() {
		assertThatCode(() -> DuplicateElderRule.requireNewApplication("Tan Bee Choo", "560230", GRACE,
				List.of(elder("Tan Bee Choo", "560231"), elder("Tan Bee Chew", "560230")),
				List.of(application(OTHER_FAMILY, "Tan Bee Choo", "570230", IntakeApplication.Status.SUBMITTED))))
				.doesNotThrowAnyException();
	}

	@Test
	void anElderOnRecordIsAlreadyRegisteredIgnoringCaseAndSpacing() {
		assertRefused(() -> DuplicateElderRule.requireNewApplication("  tan  bee choo ", " 560230 ", GRACE,
				List.of(elder("Tan Bee Choo", "560230")), List.of()), DuplicateElderRule.ALREADY_REGISTERED);
	}

	@Test
	void theirOwnPendingApplicationIsSaidToBeTheirs() {
		assertRefused(() -> DuplicateElderRule.requireNewApplication("Tan Bee Choo", "560230", GRACE, List.of(),
				List.of(application(GRACE, "Tan Bee Choo", "560230", IntakeApplication.Status.UNDER_REVIEW))),
				DuplicateElderRule.ALREADY_APPLIED);
	}

	@Test
	void anotherFamilysPendingApplicationReadsTheSameAsARecord() {
		assertRefused(() -> DuplicateElderRule.requireNewApplication("Tan Bee Choo", "560230", GRACE, List.of(),
				List.of(application(OTHER_FAMILY, "Tan Bee Choo", "560230", IntakeApplication.Status.SUBMITTED))),
				DuplicateElderRule.ALREADY_REGISTERED);
	}

	@Test
	void anAnsweredApplicationDoesNotBlockApplyingAgain() {
		assertThatCode(() -> DuplicateElderRule.requireNewApplication("Tan Bee Choo", "560230", GRACE, List.of(),
				List.of(application(GRACE, "Tan Bee Choo", "560230", IntakeApplication.Status.REJECTED))))
				.doesNotThrowAnyException();
	}

	@Test
	void approvalIsRefusedForSomeoneAlreadyOnRecord() {
		assertRefused(() -> DuplicateElderRule.requireNotOnRecord("Tan Bee Choo", "560230",
				List.of(elder("TAN BEE CHOO", "560230"))), DuplicateElderRule.ALREADY_REGISTERED);
		assertThatCode(() -> DuplicateElderRule.requireNotOnRecord("Tan Bee Choo", "560230", List.of()))
				.doesNotThrowAnyException();
	}

	private static void assertRefused(Runnable attempt, String code) {
		assertThatThrownBy(attempt::run).isInstanceOfSatisfying(BusinessRuleViolation.class,
				e -> assertThat(e.code()).isEqualTo(code));
	}

	private static Elder elder(String name, String postcode) {
		return new Elder(1L, null, name, null, null, null, null, postcode, "AMK", null, null, null,
				Elder.ContinuityPreference.PREFERRED, null, null, null);
	}

	private static IntakeApplication application(Long familyMemberId, String name, String postcode,
			IntakeApplication.Status status) {
		return new IntakeApplication(1L, familyMemberId, name, 83, "Blk 1", postcode,
				IntakeApplication.MobilityLevel.INDEPENDENT, null, List.of(), null, status, null, null,
				LocalDateTime.of(2026, 10, 5, 1, 0), null, null);
	}
}
