package sg.nus.carelink.report.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.report.domain.model.ConfirmationFact;
import sg.nus.carelink.report.domain.model.ElderProfile;
import sg.nus.carelink.report.domain.model.ReportAmendment;
import sg.nus.carelink.report.domain.model.ReportBasis;
import sg.nus.carelink.report.domain.model.ReportFacts;
import sg.nus.carelink.report.domain.model.ReportFigure;
import sg.nus.carelink.report.domain.model.ReportMetrics;
import sg.nus.carelink.report.domain.model.ReportSection;
import sg.nus.carelink.report.domain.model.ReportSeries;
import sg.nus.carelink.report.domain.model.SpotCheckFact;
import sg.nus.carelink.report.support.ReportFixtures;

/** The small values a report is made of: their keys, their defaults and what each insists on. */
class ReportValuesTest {

	private static final LocalDate END_OF_WEEK = LocalDate.of(2026, 9, 20);

	// ------------------------------------------------------------------- sections ---

	@Test
	void aSectionIsKeyedByItsTitleInLowerCaseWithHyphens() {
		assertThat(ReportSection.keyOf("Service completion")).isEqualTo("service-completion");
		assertThat(ReportSection.keyOf("Ratings and spot checks")).isEqualTo("ratings-and-spot-checks");
		assertThat(ReportSection.keyOf("  Vital  signs! ")).isEqualTo("vital-signs");
		assertThat(ReportSection.keyOf("Week 38")).isEqualTo("week-38");
	}

	@Test
	void aSectionOfTextOnlyHasItsTitlesKeyAndNoFiguresOrSeries() {
		ReportSection section = new ReportSection("Vital signs", "Pulse 72 bpm");

		assertThat(section.key()).isEqualTo("vital-signs");
		assertThat(section.figures()).isEmpty();
		assertThat(section.series()).isEmpty();
		assertThat(new ReportSection("k", "Title", "", null, null).figures()).isEmpty();
		assertThatThrownBy(() -> new ReportSection(null, "body")).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new ReportSection("Title", null)).isInstanceOf(NullPointerException.class);
	}

	@Test
	void aFigureIsACountOrAShareOfATotal() {
		assertThat(ReportFigure.count("incidents", "Incidents", 1))
				.isEqualTo(new ReportFigure("incidents", "Incidents", BigDecimal.ONE, null, null));
		assertThat(ReportFigure.share("visits", "Visits carried out", 2, 3).outOf()).isEqualTo(BigDecimal.valueOf(3));
		assertThatThrownBy(() -> new ReportFigure("k", "label", null, null, null)).isInstanceOf(NullPointerException.class);
	}

	@Test
	void numbersAreKeptWithoutTrailingZerosSoTheyReadBackFromJsonAsFiled() {
		assertThat(ReportFigure.plain(new BigDecimal("100.00"))).isEqualTo(new BigDecimal("100"));
		assertThat(ReportFigure.plain(new BigDecimal("36.80"))).isEqualTo(new BigDecimal("36.8"));
		assertThat(ReportFigure.plain(new BigDecimal("66.67"))).isEqualTo(new BigDecimal("66.67"));
		assertThat(new ReportFigure("rating", "Rating", new BigDecimal("3.50"), new BigDecimal("5.0"), null))
				.isEqualTo(new ReportFigure("rating", "Rating", new BigDecimal("3.5"), new BigDecimal("5"), null));
		assertThat(new ReportSeries.Point("2026-09-14", new BigDecimal("128.00"), new BigDecimal("142.0"), false))
				.isEqualTo(new ReportSeries.Point("2026-09-14", new BigDecimal("128"), new BigDecimal("142"), false));
	}

	@Test
	void aSeriesWithoutPointsHasNoneAndAPointNeedsBothEnds() {
		assertThat(new ReportSeries("pulse", "Pulse", null, null).points()).isEmpty();
		assertThatThrownBy(() -> new ReportSeries.Point("2026-09-14", BigDecimal.ONE, null, false))
				.isInstanceOf(NullPointerException.class);
	}

	// -------------------------------------------------------------------- profile ---

	@Test
	void theAgeIsCountedInWholeYearsOnTheDayAndBandedByDecade() {
		ElderProfile born = profileBorn(LocalDate.of(1941, 9, 21));

		assertThat(born.ageOn(END_OF_WEEK)).hasValue(84);
		assertThat(born.ageOn(END_OF_WEEK.plusDays(1))).as("on the birthday").hasValue(85);
		assertThat(born.ageBandOn(END_OF_WEEK)).hasValue("80–89");
		assertThat(profileBorn(LocalDate.of(1956, 1, 1)).ageBandOn(END_OF_WEEK)).hasValue("70–79");
	}

	@Test
	void withoutADateOfBirthOrWithOneInTheFutureThereIsNoAge() {
		assertThat(ElderProfile.UNKNOWN.ageOn(END_OF_WEEK)).isEmpty();
		assertThat(ElderProfile.UNKNOWN.ageBandOn(END_OF_WEEK)).isEmpty();
		assertThat(profileBorn(END_OF_WEEK.plusDays(1)).ageOn(END_OF_WEEK)).isEmpty();
		assertThat(ElderProfile.UNKNOWN.hasPlan()).isFalse();
		assertThat(ElderProfile.UNKNOWN.hasPrimaryCaregiver()).isFalse();
	}

	// ---------------------------------------------------------------------- facts ---

	@Test
	void factsBuiltWithoutTheNewerPartsHaveNoneOfThem() {
		ReportFacts week = ReportFixtures.quietWeek();
		ReportFacts nulls = new ReportFacts(week.elderId(), week.period(), null, null, null, null, null, null, null);

		assertThat(nulls.elder()).isEqualTo(ElderProfile.UNKNOWN);
		assertThat(nulls.quality()).isEqualTo(ReportFacts.Quality.NONE);
		assertThat(nulls.quality().isEmpty()).isTrue();
		assertThat(nulls.changes()).isEqualTo(ReportFacts.Changes.NONE);
		assertThat(new ReportFacts.Quality(null, null, null).confirmations()).isEmpty();
		assertThat(new ReportFacts.Changes(null, null).valueAdded()).isEmpty();
	}

	@Test
	void anythingSaidOrFoundMakesTheQualityNonEmpty() {
		ConfirmationFact unrated = new ConfirmationFact(11L, false, null, null, null);
		SpotCheckFact unassigned = new SpotCheckFact(4L, null, null, LocalDateTime.of(2026, 9, 16, 10, 0),
				"PENDING_APPROVAL", null, null, null, null, null);

		assertThat(new ReportFacts.Quality(List.of(unrated), List.of(), List.of()).isEmpty()).isFalse();
		assertThat(new ReportFacts.Quality(List.of(), ReportFixtures.week().quality().reviews(), List.of()).isEmpty())
				.isFalse();
		assertThat(new ReportFacts.Quality(List.of(), List.of(), List.of(unassigned)).isEmpty()).isFalse();
		assertThat(unrated.isRated()).isFalse();
		assertThat(unassigned.hasCaregiver()).isFalse();
	}

	// ----------------------------------------------------------- notes and bases ---

	@Test
	void aNoteWithoutAKindIsACorrectionAndAKindIsAlwaysThere() {
		ReportAmendment note = new ReportAmendment(null, 40L, "note", 9L, LocalDateTime.of(2026, 9, 21, 9, 30));

		assertThat(note.kind()).isEqualTo(ReportAmendment.Kind.CORRECTION);
		assertThatThrownBy(() -> new ReportAmendment(null, 40L, null, "note", 9L, LocalDateTime.of(2026, 9, 21, 9, 30)))
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	void aBasisIsTheElderPeriodAndNumbersOfTheFactsItWasMadeFrom() {
		LocalDateTime now = LocalDateTime.of(2026, 9, 20, 23, 0);
		ReportBasis basis = ReportBasis.of(ReportFixtures.week(), now);

		assertThat(basis.id()).isNull();
		assertThat(basis.elderId()).isEqualTo(ReportFixtures.ELDER);
		assertThat(basis.period()).isEqualTo(ReportFixtures.WEEK);
		assertThat(basis.metrics()).isEqualTo(ReportMetrics.of(ReportFixtures.week()));
		assertThat(basis.createdAt()).isEqualTo(now);
		assertThatThrownBy(() -> ReportBasis.of(ReportFixtures.week(), null)).isInstanceOf(NullPointerException.class);
	}

	private static ElderProfile profileBorn(LocalDate dateOfBirth) {
		return new ElderProfile("Tan Ah Mei", "FEMALE", dateOfBirth, null, null, null, null, null, null, null);
	}
}
