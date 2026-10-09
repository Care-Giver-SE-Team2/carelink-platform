package sg.nus.carelink.rostering.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.rostering.domain.model.AbsenceReport;
import sg.nus.carelink.shared.error.BusinessRuleViolation;

/** UC-CG02 and UC-MG04 step 1: how an absence comes into being and what it blocks. */
class AbsenceReportTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 7);

	@Test
	void aCaregiversRequestWaitsForAManager() {
		AbsenceReport requested = AbsenceReport.requested(5L, AbsenceReport.Type.ANNUAL, TODAY.plusDays(3),
				TODAY.plusDays(4), "  family wedding  ", TODAY);

		assertThat(requested.status()).isEqualTo(AbsenceReport.Status.PENDING);
		assertThat(requested.reviewedByUserId()).isNull();
		assertThat(requested.reason()).isEqualTo("family wedding");
		assertThat(requested.isApproved()).isFalse();
		assertThat(requested.keepsAwayOn(TODAY.plusDays(3))).isFalse();
	}

	@Test
	void anAbsenceAManagerRecordsIsApprovedAtOnce() {
		AbsenceReport recorded = AbsenceReport.recordedByManager(5L, null, TODAY, TODAY, " ", 11L, TODAY);

		assertThat(recorded.status()).isEqualTo(AbsenceReport.Status.APPROVED);
		assertThat(recorded.reviewedByUserId()).isEqualTo(11L);
		assertThat(recorded.type()).isEqualTo(AbsenceReport.Type.OTHER);
		assertThat(recorded.reason()).isNull();
		assertThat(recorded.keepsAwayOn(TODAY)).isTrue();
	}

	@Test
	void anAbsenceCannotEndBeforeItStarts() {
		LocalDate later = TODAY.plusDays(2);
		LocalDate earlier = TODAY.plusDays(1);
		assertThatThrownBy(() -> AbsenceReport.requested(5L, AbsenceReport.Type.SICK, later, earlier, null, TODAY))
				.isInstanceOf(BusinessRuleViolation.class)
				.extracting("code").isEqualTo("ABSENCE_ENDS_BEFORE_IT_STARTS");
	}

	@Test
	void anAbsenceThatIsAlreadyOverVacatesNothing() {
		LocalDate began = TODAY.minusDays(3);
		LocalDate ended = TODAY.minusDays(1);
		assertThatThrownBy(() -> AbsenceReport.recordedByManager(5L, AbsenceReport.Type.SICK, began, ended, null, 11L,
				TODAY))
				.isInstanceOf(BusinessRuleViolation.class)
				.extracting("code").isEqualTo("ABSENCE_ALREADY_OVER");
	}

	@Test
	void aRequestIsApprovedOrRejectedOnce() {
		AbsenceReport requested = saved(AbsenceReport.requested(5L, AbsenceReport.Type.SICK, TODAY, TODAY, null, TODAY));

		AbsenceReport approved = requested.approvedBy(11L);
		assertThat(approved.isApproved()).isTrue();
		assertThat(approved.reviewedByUserId()).isEqualTo(11L);

		AbsenceReport rejected = requested.rejectedBy(12L);
		assertThat(rejected.status()).isEqualTo(AbsenceReport.Status.REJECTED);

		assertThatThrownBy(() -> approved.rejectedBy(12L))
				.isInstanceOf(BusinessRuleViolation.class)
				.extracting("code").isEqualTo("ABSENCE_ALREADY_REVIEWED");
		assertThatThrownBy(() -> rejected.approvedBy(12L)).isInstanceOf(BusinessRuleViolation.class);
	}

	@Test
	void onlyAnApprovedAbsenceHasCoverageToConfirm() {
		LocalDateTime now = TODAY.atTime(15, 0);
		AbsenceReport approved = saved(AbsenceReport.recordedByManager(5L, AbsenceReport.Type.SICK, TODAY, TODAY,
				null, 11L, TODAY));

		AbsenceReport confirmed = approved.coverageConfirmedBy(12L, now);
		assertThat(confirmed.coverageConfirmedAt()).isEqualTo(now);
		assertThat(confirmed.coverageConfirmedByUserId()).isEqualTo(12L);

		AbsenceReport pending = saved(AbsenceReport.requested(5L, AbsenceReport.Type.SICK, TODAY, TODAY, null, TODAY));
		assertThatThrownBy(() -> pending.coverageConfirmedBy(12L, now))
				.isInstanceOf(BusinessRuleViolation.class)
				.extracting("code").isEqualTo("ABSENCE_NOT_APPROVED");
	}

	@Test
	void aConfirmationCanBeWithdrawnAndGivenAgain() {
		AbsenceReport confirmed = saved(AbsenceReport.recordedByManager(5L, AbsenceReport.Type.ANNUAL, TODAY,
				TODAY.plusDays(20), null, 11L, TODAY)).coverageConfirmedBy(12L, TODAY.atTime(15, 0));
		assertThat(confirmed.isCoverageConfirmed()).isTrue();

		AbsenceReport reopened = confirmed.coverageReopened();
		assertThat(reopened.isCoverageConfirmed()).isFalse();
		assertThat(reopened.coverageConfirmedByUserId()).isNull();
		assertThat(reopened.isApproved()).as("only the confirmation goes; the leave still stands").isTrue();
		assertThat(reopened.coverageConfirmedBy(13L, TODAY.atTime(16, 0)).coverageConfirmedByUserId()).isEqualTo(13L);
	}

	@Test
	void theWindowRunsFromTheFirstMidnightToTheMidnightAfterTheLastDay() {
		AbsenceReport absence = AbsenceReport.recordedByManager(5L, AbsenceReport.Type.SICK, TODAY, TODAY.plusDays(1),
				null, 11L, TODAY);

		assertThat(absence.windowStart()).isEqualTo(TODAY.atStartOfDay());
		assertThat(absence.windowEnd()).isEqualTo(TODAY.plusDays(2).atStartOfDay());
		assertThat(absence.keepsAwayOn(TODAY.minusDays(1))).isFalse();
		assertThat(absence.keepsAwayOn(TODAY.plusDays(1))).isTrue();
		assertThat(absence.keepsAwayOn(TODAY.plusDays(2))).isFalse();
	}

	@Test
	void absencesSharingADayOverlap() {
		AbsenceReport first = AbsenceReport.requested(5L, null, TODAY, TODAY.plusDays(2), null, TODAY);

		assertThat(first.overlaps(AbsenceReport.requested(5L, null, TODAY.plusDays(2), TODAY.plusDays(5), null, TODAY)))
				.isTrue();
		assertThat(first.overlaps(AbsenceReport.requested(5L, null, TODAY.plusDays(3), TODAY.plusDays(5), null, TODAY)))
				.isFalse();
	}

	private static AbsenceReport saved(AbsenceReport absence) {
		return new AbsenceReport(1L, absence.caregiverId(), absence.reviewedByUserId(), absence.type(),
				absence.startDate(), absence.endDate(), absence.reason(), absence.status(), null, null, null, null);
	}
}
