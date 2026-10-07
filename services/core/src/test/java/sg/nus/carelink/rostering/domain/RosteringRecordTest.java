package sg.nus.carelink.rostering.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.rostering.domain.model.RosteringCandidate;
import sg.nus.carelink.rostering.domain.model.RosteringRun;

/** What a run and its candidates record (DECISION 17: a run is recorded, not only its outcome). */
class RosteringRecordTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 7, 9, 0);

	@Test
	void anAbsenceRunIsProposedThenCommittedWithItsTotals() {
		RosteringRun run = RosteringRun.forAbsence(12L, null, 11L, NOW);

		assertThat(run.triggerType()).isEqualTo(RosteringRun.TriggerType.ABSENCE);
		assertThat(run.objective()).isEqualTo(RosteringRun.Objective.CONTINUITY);
		assertThat(run.status()).isEqualTo(RosteringRun.Status.PROPOSED);
		assertThat(run.ranAt()).isEqualTo(NOW);

		RosteringRun committed = run.committed(7, 6, 4, NOW.plusMinutes(1));
		assertThat(committed.status()).isEqualTo(RosteringRun.Status.COMMITTED);
		assertThat(committed.visitsTotal()).isEqualTo(7);
		assertThat(committed.visitsCovered()).isEqualTo(6);
		assertThat(committed.continuityKept()).isEqualTo(4);
		assertThat(committed.committedAt()).isEqualTo(NOW.plusMinutes(1));
		assertThat(RosteringRun.forAbsence(12L, RosteringRun.Objective.EVEN_WORKLOAD, null, NOW).objective())
				.isEqualTo(RosteringRun.Objective.EVEN_WORKLOAD);
	}

	@Test
	void theCandidatePutOnTheVisitBecomesSelected() {
		RosteringCandidate suggested = new RosteringCandidate(1L, 2L, 30L, 9L, 1, new BigDecimal("74.00"),
				RosteringCandidate.Outcome.SUGGESTED, null, "Has visited this elder 3 times before");

		assertThat(suggested.isSuggestion()).isTrue();
		assertThat(suggested.selected().outcome()).isEqualTo(RosteringCandidate.Outcome.SELECTED);
		assertThat(suggested.selected().isSuggestion()).isTrue();

		RosteringCandidate excluded = new RosteringCandidate(2L, 2L, 30L, 8L, null, null,
				RosteringCandidate.Outcome.EXCLUDED, "NOT_ON_LEAVE", "x".repeat(200));
		assertThat(excluded.isSuggestion()).isFalse();
		assertThat(excluded.matchReason()).hasSize(RosteringCandidate.REASON_LENGTH).endsWith("...");
	}
}
