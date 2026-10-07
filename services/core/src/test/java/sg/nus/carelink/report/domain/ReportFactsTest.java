package sg.nus.carelink.report.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import sg.nus.carelink.report.domain.model.IncidentFact;
import sg.nus.carelink.report.domain.model.ReportFacts;
import sg.nus.carelink.report.domain.model.VisitFact;
import sg.nus.carelink.report.domain.model.VitalFact;
import sg.nus.carelink.report.domain.model.VitalRange;
import sg.nus.carelink.report.support.ReportFixtures;

/**
 * The working draft's own rules: which visits count as not closed, how a range is taken, and
 * what an incident's conclusion is. Worked out here so the source that reads the tables can
 * stay a set of plain queries.
 */
class ReportFactsTest {

	private static final LocalDateTime NINE = LocalDateTime.of(2026, 9, 14, 9, 0);

	/**
	 * UC-CG05 runs 已完成 then 已核销, and a visit can stop at 已完成 when the elder does not
	 * confirm; that is the "未核销" visit UC-MG07 2a flags. So COMPLETED is open.
	 */
	@ParameterizedTest
	@EnumSource(value = VisitFact.Status.class, names = {"SCHEDULED", "ARRIVED", "IN_PROGRESS", "COMPLETED"})
	void aVisitThatIsNotWrittenOffIsNotClosed(VisitFact.Status status) {
		assertThat(visit(status).isClosed()).isFalse();
		assertThat(facts(visit(status)).unclosedVisits()).hasSize(1);
	}

	@ParameterizedTest
	@EnumSource(value = VisitFact.Status.class, names = {"VERIFIED", "AUTO_CLOSED", "EXCEPTION", "CANCELLED"})
	void aVisitNothingMoreWillHappenToIsClosed(VisitFact.Status status) {
		assertThat(visit(status).isClosed()).isTrue();
		assertThat(facts(visit(status)).unclosedVisits()).isEmpty();
	}

	@Test
	void theGapsAreReadFromTheVisitsSoTheyCannotDisagree() {
		ReportFacts week = ReportFixtures.week();

		assertThat(week.unclosedVisits()).containsExactly(ReportFixtures.FRIDAY);
		assertThat(week.visit(12L)).contains(ReportFixtures.WEDNESDAY);
		assertThat(week.visit(99L)).isEmpty();
	}

	@Test
	void missingListsAreEmptyAndTheOnesGivenCannotBeChanged() {
		ReportFacts nothing = new ReportFacts(1L, ReportFixtures.WEEK, null, null, null, null);

		assertThat(nothing.visits()).isEmpty();
		assertThat(nothing.vitals()).isEmpty();
		assertThat(nothing.observations()).isEmpty();
		assertThat(nothing.incidents()).isEmpty();

		List<VisitFact> given = ReportFixtures.week().visits();
		assertThatThrownBy(given::clear).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void factsBelongToAnElderAndAPeriod() {
		assertThatThrownBy(() -> new ReportFacts(null, ReportFixtures.WEEK, null, null, null, null))
				.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new ReportFacts(1L, null, null, null, null, null))
				.isInstanceOf(NullPointerException.class);
	}

	// ------------------------------------------------------------------------ ranges ---

	@Test
	void aRangeRunsFromTheLowestToTheHighestReadingOfEachMetric() {
		List<VitalRange> ranges = VitalRange.summarise(ReportFixtures.week().vitals());

		assertThat(ranges).extracting(VitalRange::metric)
				.containsExactly("systolic", "diastolic", "pulse", "temperature");
		VitalRange systolic = ranges.getFirst();
		assertThat(systolic.lowest()).isEqualByComparingTo("128");
		assertThat(systolic.highest()).isEqualByComparingTo("142");
		assertThat(systolic.unit()).isEqualTo("mmHg");
		assertThat(systolic.readings()).isEqualTo(2);
		assertThat(systolic.isSingleValue()).isFalse();
	}

	@Test
	void aRangeTakesTheUnitOfTheFirstReadingThatHasOne() {
		List<VitalRange> ranges = VitalRange.summarise(List.of(
				reading("pulse", "70", null),
				reading("pulse", "70", "bpm"),
				reading("weight", "51.5", null)));

		assertThat(ranges).extracting(VitalRange::unit).containsExactly("bpm", null);
		assertThat(ranges.getFirst().isSingleValue()).isTrue();
	}

	@Test
	void noReadingsMeansNoRanges() {
		assertThat(VitalRange.summarise(List.of())).isEmpty();
	}

	// --------------------------------------------------------------------- incidents ---

	@Test
	void anIncidentsConclusionIsWhatItWasClosedWith() {
		IncidentFact fall = ReportFixtures.fall();

		assertThat(fall.isResolved()).isTrue();
		assertThat(fall.resolution()).contains(ReportFixtures.RESOLUTION);
		assertThat(fall.outcome()).contains("HANDLED_ON_SITE");
	}

	@Test
	void anOpenIncidentHasNoConclusion() {
		IncidentFact open = new IncidentFact(5L, "SOS", "HIGH", "OPEN", null, NINE, null, null);

		assertThat(open.isResolved()).isFalse();
		assertThat(open.timeline()).isEmpty();
		assertThat(open.resolution()).isEmpty();
		assertThat(open.outcome()).isEmpty();
	}

	@Test
	void aConclusionWithoutAnOutcomeCodeStillReadsButHasNoOutcome() {
		IncidentFact written = new IncidentFact(6L, "OTHER", "LOW", "RESOLVED", null, NINE, NINE.plusHours(1),
				List.of(new IncidentFact.Step("someone", "RESOLVED", "sorted out by phone", NINE.plusHours(1))));

		assertThat(written.resolution()).contains("sorted out by phone");
		assertThat(written.outcome()).isEmpty();
	}

	private static VisitFact visit(VisitFact.Status status) {
		return new VisitFact(20L, 3L, "Daniel Goh", "Personal care", NINE, status, 0, 0);
	}

	private static ReportFacts facts(VisitFact visit) {
		return new ReportFacts(1L, ReportFixtures.WEEK, List.of(visit), null, null, null);
	}

	private static VitalFact reading(String metric, String value, String unit) {
		return new VitalFact(11L, metric, new BigDecimal(value), unit, false, NINE);
	}
}
