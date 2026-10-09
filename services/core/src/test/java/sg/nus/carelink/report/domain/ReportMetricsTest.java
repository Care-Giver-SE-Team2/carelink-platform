package sg.nus.carelink.report.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import sg.nus.carelink.report.domain.model.ConfirmationFact;
import sg.nus.carelink.report.domain.model.ElderProfile;
import sg.nus.carelink.report.domain.model.ReportFacts;
import sg.nus.carelink.report.domain.model.ReportMetrics;
import sg.nus.carelink.report.domain.model.VisitFact;
import sg.nus.carelink.report.support.ReportFixtures;

/**
 * The period's numbers: what a basis stores in columns and every overview states. Counted from
 * the facts in one place, so the columns and the text cannot disagree.
 */
class ReportMetricsTest {

	@Test
	void theWeeksNumbers() {
		ReportMetrics metrics = ReportMetrics.of(ReportFixtures.week());

		assertThat(metrics.visitsPlanned()).isEqualTo(3);
		assertThat(metrics.visitsCompleted()).isEqualTo(2);
		assertThat(metrics.fulfilmentRate()).isEqualTo(new BigDecimal("66.67"));
		assertThat(metrics.vitalsOutOfRange()).isEqualTo(1);
		assertThat(metrics.incidentCount()).isEqualTo(1);
		assertThat(metrics.averageElderRating()).isEqualTo(new BigDecimal("3.50"));
		assertThat(metrics.ratingCount()).isEqualTo(2);
		assertThat(metrics.dataComplete()).isFalse();
	}

	@Test
	void aQuietWeekHasNoRateAndNoAverageRatherThanZeros() {
		assertThat(ReportMetrics.of(ReportFixtures.quietWeek()))
				.isEqualTo(new ReportMetrics(0, 0, null, 0, 0, null, 0, true));
	}

	@Test
	void aCancelledVisitIsNotPlannedAndAnExceptionIsPlannedButNotCarriedOut() {
		ReportFacts facts = withVisits(
				visit(1L, VisitFact.Status.COMPLETED),
				visit(2L, VisitFact.Status.AUTO_CLOSED),
				visit(3L, VisitFact.Status.EXCEPTION),
				visit(4L, VisitFact.Status.CANCELLED));

		ReportMetrics metrics = ReportMetrics.of(facts);

		assertThat(metrics.visitsPlanned()).isEqualTo(3);
		assertThat(metrics.visitsCompleted()).isEqualTo(2);
		assertThat(metrics.fulfilmentRate()).isEqualTo(new BigDecimal("66.67"));
		assertThat(metrics.dataComplete()).as("COMPLETED still waits for the elder").isFalse();
	}

	@Test
	void everyVisitCarriedOutIsAFulfilmentOfAHundred() {
		ReportMetrics metrics = ReportMetrics.of(withVisits(visit(1L, VisitFact.Status.VERIFIED)));

		assertThat(metrics.fulfilmentRate()).isEqualByComparingTo("100");
		assertThat(metrics.dataComplete()).isTrue();
	}

	@Test
	void theAverageIsTakenOverTheAnswersThatCameWithARatingToTwoPlaces() {
		ReportFacts week = ReportFixtures.quietWeek();
		ReportFacts rated = new ReportFacts(week.elderId(), week.period(), List.of(), List.of(), List.of(), List.of(),
				ElderProfile.UNKNOWN,
				new ReportFacts.Quality(List.of(
						answer(1L, 5), answer(2L, 4), answer(3L, 4), answer(4L, null)), List.of(), List.of()),
				ReportFacts.Changes.NONE);

		ReportMetrics metrics = ReportMetrics.of(rated);

		assertThat(metrics.averageElderRating()).isEqualTo(new BigDecimal("4.33"));
		assertThat(metrics.ratingCount()).isEqualTo(3);
	}

	@ParameterizedTest
	@EnumSource(VisitFact.Status.class)
	void aVisitIsPlannedUnlessCancelledAndCarriedOutOnceCheckedOut(VisitFact.Status status) {
		VisitFact visit = visit(1L, status);

		assertThat(visit.isPlanned()).isEqualTo(status != VisitFact.Status.CANCELLED);
		assertThat(visit.isCompleted()).isEqualTo(status == VisitFact.Status.COMPLETED
				|| status == VisitFact.Status.VERIFIED
				|| status == VisitFact.Status.AUTO_CLOSED);
	}

	private static ReportFacts withVisits(VisitFact... visits) {
		ReportFacts week = ReportFixtures.quietWeek();
		return new ReportFacts(week.elderId(), week.period(), List.of(visits), List.of(), List.of(), List.of());
	}

	private static VisitFact visit(Long id, VisitFact.Status status) {
		return new VisitFact(id, 3L, "Daniel Goh", "Personal care", LocalDateTime.of(2026, 9, 15, 9, 0), status, 0, 0);
	}

	private static ConfirmationFact answer(Long visitId, Integer rating) {
		return new ConfirmationFact(visitId, false, rating, null, LocalDateTime.of(2026, 9, 15, 11, 0));
	}
}
