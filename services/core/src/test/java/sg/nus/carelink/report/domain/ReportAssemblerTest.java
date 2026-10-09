package sg.nus.carelink.report.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import sg.nus.carelink.report.domain.model.ConfirmationFact;
import sg.nus.carelink.report.domain.model.ElderProfile;
import sg.nus.carelink.report.domain.model.IncidentFact;
import sg.nus.carelink.report.domain.model.Report;
import sg.nus.carelink.report.domain.model.ReportContent;
import sg.nus.carelink.report.domain.model.ReportFacts;
import sg.nus.carelink.report.domain.model.ReportFigure;
import sg.nus.carelink.report.domain.model.ReportSection;
import sg.nus.carelink.report.domain.model.ReportSeries;
import sg.nus.carelink.report.domain.model.ReviewFact;
import sg.nus.carelink.report.domain.model.RosterChangeFact;
import sg.nus.carelink.report.domain.model.SpotCheckFact;
import sg.nus.carelink.report.domain.model.ValueAddedFact;
import sg.nus.carelink.report.domain.model.VisitFact;
import sg.nus.carelink.report.domain.model.VitalFact;
import sg.nus.carelink.report.domain.service.ReportAssembler;
import sg.nus.carelink.report.support.ReportFixtures;

/**
 * DP5: one set of facts, three readers, one skeleton.
 *
 * <p>The same {@link ReportFacts} instance is handed to all three assemblers, so the audience
 * is the only thing that differs between them - the clean comparison the design problem is
 * argued on. What has to hold is that the three reports agree on everything the skeleton
 * decides (the sections, their order, the completeness verdict) and disagree only where a
 * hook does.
 */
class ReportAssemblerTest {

	private static final List<String> SKELETON = List.of(
			"Overview", "Services", "Service completion", "Vital signs", "Observations", "Incidents",
			"Ratings and spot checks");

	/** What every reader's overview ends with for the week: the numbers, in the same words. */
	private static final List<String> NUMBERS = List.of(
			"Visits: 2 of 3 carried out (66.67%).",
			"Vital signs: 8 readings, 1 out of range.",
			"Incidents: 1 reported, 1 resolved.",
			"Visit ratings: average 3.5 out of 5 from 2 ratings.");

	private static final String DISCLAIMER = "This summary is prepared from care records for information only "
			+ "and does not constitute medical advice.";

	private final ReportFacts facts = ReportFixtures.week();

	private final Map<Report.Audience, ReportContent> reports = new EnumMap<>(Report.Audience.class);

	@BeforeEach
	void assembleTheSameFactsForEveryReader() {
		for (Report.Audience audience : Report.Audience.values()) {
			reports.put(audience, ReportAssembler.forAudience(audience).assemble(facts));
		}
	}

	// ------------------------------------------------------------------- the skeleton ---

	@Test
	void everyReaderGetsTheSameSectionsInTheSameOrder() {
		for (Report.Audience audience : Report.Audience.values()) {
			assertThat(titles(reports.get(audience)))
					.as("sections of the %s version", audience)
					.containsExactlyElementsOf(SKELETON);
		}
	}

	@Test
	void onlyTheContentOfTheSectionsDiffers() {
		ReportContent family = reports.get(Report.Audience.FAMILY);
		ReportContent regulator = reports.get(Report.Audience.REGULATOR);
		ReportContent internal = reports.get(Report.Audience.INTERNAL);

		assertThat(family).isNotEqualTo(regulator).isNotEqualTo(internal);
		assertThat(regulator).isNotEqualTo(internal);

		// Each section differs somewhere across the three readers: every step has a hook. Two
		// readers may still agree on one - the regulator and the institution both see every
		// reading - so this counts distinct versions rather than requiring three.
		for (int section = 0; section < SKELETON.size(); section++) {
			Set<String> bodies = new HashSet<>(List.of(
					family.sections().get(section).body(),
					regulator.sections().get(section).body(),
					internal.sections().get(section).body()));
			assertThat(bodies).as("versions of %s", SKELETON.get(section)).hasSizeGreaterThan(1);
		}
	}

	@Test
	void theCompletenessVerdictIsTheSameForEveryReader() {
		for (ReportContent content : reports.values()) {
			assertThat(content.dataComplete()).isFalse();
			assertThat(content.missingItems()).containsExactly("Visit 13 on 2026-09-18 not closed");
			assertThat(content.generatedBy()).isEqualTo(ReportContent.GeneratedBy.TEMPLATE);
		}
	}

	@Test
	void aPeriodWithEveryVisitClosedIsComplete() {
		for (Report.Audience audience : Report.Audience.values()) {
			ReportContent content = ReportAssembler.forAudience(audience).assemble(ReportFixtures.closedWeek());
			assertThat(content.dataComplete()).isTrue();
			assertThat(content.missingItems()).isEmpty();
		}
	}

	@Test
	void aQuietWeekStillHasEverySectionAndSaysThereWasNothing() {
		for (Report.Audience audience : Report.Audience.values()) {
			ReportContent content = ReportAssembler.forAudience(audience).assemble(ReportFixtures.quietWeek());

			assertThat(titles(content)).containsExactlyElementsOf(SKELETON);
			assertThat(content.sections()).extracting(ReportSection::body).containsExactly(
					"Visits: none scheduled.\nVital signs: none recorded.\nIncidents: none reported.\n"
							+ "Visit ratings: none given.",
					"No visits were scheduled in this period.",
					"No visits were scheduled in this period.",
					"No vital signs were recorded in this period.",
					"No observations were recorded in this period.",
					"No incidents were reported in this period.",
					"No ratings, reviews or spot checks were recorded in this period.");
			assertThat(content.sections()).flatExtracting(ReportSection::series).isEmpty();
			assertThat(content.dataComplete()).isTrue();
		}
	}

	/**
	 * The shape that makes this Template Method rather than three classes that happen to look
	 * alike: the skeleton is final, the hooks are abstract, and a reader's class declares
	 * nothing but hooks and its own private helpers.
	 */
	@Test
	void theSkeletonIsFinalAndTheReadersOnlyFillInTheHooks() throws NoSuchMethodException {
		Method assemble = ReportAssembler.class.getMethod("assemble", ReportFacts.class);
		assertThat(Modifier.isFinal(assemble.getModifiers())).isTrue();

		List<Method> abstractMethods = Arrays.stream(ReportAssembler.class.getDeclaredMethods())
				.filter(method -> Modifier.isAbstract(method.getModifiers()))
				.toList();
		assertThat(abstractMethods).allMatch(method -> Modifier.isProtected(method.getModifiers()));

		Set<String> hooks = abstractMethods.stream().map(Method::getName).collect(Collectors.toSet());
		assertThat(hooks).containsExactlyInAnyOrder(
				"describeCaregiver", "aboutTheElder", "serviceDetail", "vitalSigns", "vitalSeries", "observations",
				"incidents", "ratings", "disclaimer");

		for (Report.Audience audience : Report.Audience.values()) {
			Class<?> reader = ReportAssembler.forAudience(audience).getClass();
			assertThat(reader.getSuperclass()).isEqualTo(ReportAssembler.class);
			Set<String> overridden = Arrays.stream(reader.getDeclaredMethods())
					.filter(method -> !Modifier.isPrivate(method.getModifiers()) && !method.isSynthetic())
					.map(Method::getName)
					.collect(Collectors.toSet());
			assertThat(overridden).as("what %s overrides", reader.getSimpleName()).isEqualTo(hooks);
		}
	}

	@Test
	void eachReaderHasItsOwnAssembler() {
		Set<Class<?>> classes = Arrays.stream(Report.Audience.values())
				.map(audience -> ReportAssembler.forAudience(audience).getClass())
				.collect(Collectors.toSet());
		assertThat(classes).hasSize(3);
	}

	// --------------------------------------------------------------------- the family ---

	@Test
	void theFamilySeesARangePerMetricRatherThanTheReadings() {
		String vitals = section(Report.Audience.FAMILY, "Vital signs");

		assertThat(vitals.lines()).containsExactly(
				"Systolic 128–142 mmHg",
				"Diastolic 82–88 mmHg",
				"Pulse 72–76 bpm",
				"Temperature 36.6–36.8 °C");
		assertThat(vitals).doesNotContain("out of range").doesNotContain("visit 12");
	}

	@Test
	void theFamilyKnowsTheCaregiverByNameOnly() {
		String service = section(Report.Audience.FAMILY, "Service completion");

		assertThat(service).contains("Daniel Goh").doesNotContain("caregiver #3");
		assertThat(service.lines().findFirst()).contains("3 visits: 1 scheduled, 2 verified.");
		assertThat(service).contains("Mon 14 Sep 09:00 · Personal care · Daniel Goh · verified · evidence 2 of 2 verified");
	}

	@Test
	void theFamilyReadsTheCaregiversNotes() {
		assertThat(section(Report.Audience.FAMILY, "Observations").lines()).containsExactly(
				"Mon 14 Sep · Daniel Goh: " + ReportFixtures.MOBILITY_NOTE,
				"Wed 16 Sep · Daniel Goh: " + ReportFixtures.MEALS_NOTE);
	}

	@Test
	void theFamilyIsToldWhatHappenedButNotWhoOnTheStaffHandledIt() {
		String incidents = section(Report.Audience.FAMILY, "Incidents");

		assertThat(incidents)
				.isEqualTo("Tue 15 Sep 10:15 · Fall reported · "
						+ ReportFixtures.FALL_DESCRIPTION + " · resolved Tue 15 Sep 11:02")
				.doesNotContain("Ben Lim")
				.doesNotContain("CLAIMED")
				.doesNotContain("HANDLED_ON_SITE");
	}

	@Test
	void theFamilyVersionAlwaysCarriesTheFixedDisclaimer() {
		assertThat(reports.get(Report.Audience.FAMILY).disclaimer()).isEqualTo(DISCLAIMER);
		assertThat(ReportAssembler.forAudience(Report.Audience.FAMILY).assemble(ReportFixtures.quietWeek()).disclaimer())
				.isEqualTo(DISCLAIMER);
	}

	@Test
	void anOpenIncidentIsStillBeingFollowedUpForTheFamily() {
		ReportFacts week = ReportFixtures.week();
		ReportFacts open = new ReportFacts(week.elderId(), week.period(), week.visits(), week.vitals(),
				week.observations(), List.of(new IncidentFact(
						5L, "SOS", "HIGH", "OPEN", null, LocalDateTime.of(2026, 9, 17, 20, 5), null, List.of())));

		String incidents = body(ReportAssembler.forAudience(Report.Audience.FAMILY).assemble(open), "Incidents");

		assertThat(incidents).isEqualTo("Thu 17 Sep 20:05 · Emergency call · still being followed up");
	}

	@Test
	void aSingleReadingIsShownAsOneValueRatherThanASpan() {
		ReportFacts week = ReportFixtures.quietWeek();
		ReportFacts oneReading = new ReportFacts(week.elderId(), week.period(), List.of(ReportFixtures.MONDAY),
				List.of(new VitalFact(11L, "pulse", new BigDecimal("72.00"), "bpm", false,
						LocalDateTime.of(2026, 9, 14, 9, 10))),
				List.of(), List.of());

		assertThat(body(ReportAssembler.forAudience(Report.Audience.FAMILY).assemble(oneReading), "Vital signs"))
				.isEqualTo("Pulse 72 bpm");
	}

	// ------------------------------------------------------------------ the regulator ---

	@Test
	void theRegulatorSeesCaregiversAsNumbersAndNeverTheirNames() {
		ReportContent regulator = reports.get(Report.Audience.REGULATOR);

		assertThat(section(Report.Audience.REGULATOR, "Service completion"))
				.contains("Mon 14 Sep 09:00 · Personal care · caregiver #3 · verified");
		assertThat(regulator.sections()).extracting(ReportSection::body)
				.noneMatch(body -> body.contains("Daniel Goh"))
				.noneMatch(body -> body.contains("Ben Lim"));
	}

	@Test
	void theRegulatorSeesEveryReading() {
		assertThat(section(Report.Audience.REGULATOR, "Vital signs").lines())
				.hasSize(8)
				.contains("Wed 16 Sep 09:10 · visit 12 · Systolic 142 mmHg · out of range")
				.contains("Mon 14 Sep 09:10 · visit 11 · Temperature 36.8 °C");
	}

	@Test
	void theRegulatorIsToldHowManyNotesThereWereButNotWhatTheySay() {
		String observations = section(Report.Audience.REGULATOR, "Observations");

		assertThat(observations)
				.isEqualTo("2 observations recorded across 2 visits. The notes themselves are not included in this version.")
				.doesNotContain(ReportFixtures.MOBILITY_NOTE)
				.doesNotContain(ReportFixtures.MEALS_NOTE);
	}

	@Test
	void theRegulatorKeepsTheWholeTrailWithTheActorsReducedToRoles() {
		assertThat(section(Report.Audience.REGULATOR, "Incidents").lines()).containsExactly(
				"Tue 15 Sep 10:15 · incident 2 · FALL · severity MEDIUM · RESOLVED · resolved Tue 15 Sep 11:02"
						+ " · outcome HANDLED_ON_SITE",
				"  Tue 15 Sep 10:15 · REPORTED · caregiver (user #20)",
				"  Tue 15 Sep 10:15 · ASSIGNED · system",
				"  Tue 15 Sep 10:21 · CLAIMED · staff",
				"  Tue 15 Sep 11:02 · RESOLVED · staff");
		assertThat(reports.get(Report.Audience.REGULATOR).disclaimer()).isNull();
	}

	// ------------------------------------------------------------------ the institution ---

	@Test
	void theInstitutionSeesEveryReadingWithItsFlag() {
		List<String> lines = section(Report.Audience.INTERNAL, "Vital signs").lines().toList();

		assertThat(lines).hasSize(8);
		assertThat(lines).filteredOn(line -> line.endsWith("out of range"))
				.containsExactly("Wed 16 Sep 09:10 · visit 12 · Systolic 142 mmHg · out of range");
	}

	@Test
	void theInstitutionKnowsTheCaregiverByNameAndNumber() {
		assertThat(section(Report.Audience.INTERNAL, "Service completion"))
				.contains("Fri 18 Sep 09:00 · Personal care · Daniel Goh (caregiver #3) · scheduled · no evidence");
		assertThat(section(Report.Audience.INTERNAL, "Observations").lines()).containsExactly(
				"Mon 14 Sep 09:00 · visit 11 · Daniel Goh (caregiver #3) · Mobility: " + ReportFixtures.MOBILITY_NOTE,
				"Wed 16 Sep 09:00 · visit 12 · Daniel Goh (caregiver #3) · Meals: " + ReportFixtures.MEALS_NOTE);
	}

	@Test
	void theInstitutionSeesWhoDidWhatAndTheConclusion() {
		String incidents = section(Report.Audience.INTERNAL, "Incidents");

		assertThat(incidents.lines()).containsExactly(
				"Tue 15 Sep 10:15 · incident 2 · FALL · severity MEDIUM · RESOLVED · " + ReportFixtures.FALL_DESCRIPTION,
				"  Tue 15 Sep 10:15 · REPORTED · caregiver:20 · reported by caregiver",
				"  Tue 15 Sep 10:15 · ASSIGNED · system · responder=12 :: first responder",
				"  Tue 15 Sep 10:21 · CLAIMED · Ben Lim (demo-ben) · taken over; countdown stopped",
				"  Tue 15 Sep 11:02 · RESOLVED · Ben Lim (demo-ben) · " + ReportFixtures.RESOLUTION,
				"  Resolution: " + ReportFixtures.RESOLUTION);
		assertThat(reports.get(Report.Audience.INTERNAL).disclaimer()).isNull();
	}

	// ------------------------------------------------------------- the invariant steps ---

	@Test
	void aVisitNobodyWasAssignedToSaysSoToEveryReader() {
		VisitFact unassigned = new VisitFact(
				14L, null, null, null, LocalDateTime.of(2026, 9, 19, 14, 0), VisitFact.Status.CANCELLED, 0, 0);
		ReportFacts week = ReportFixtures.quietWeek();
		ReportFacts unassignedWeek = new ReportFacts(week.elderId(), week.period(), List.of(unassigned),
				List.of(), List.of(), List.of());

		for (Report.Audience audience : Report.Audience.values()) {
			assertThat(body(ReportAssembler.forAudience(audience).assemble(unassignedWeek), "Service completion"))
					.isEqualTo("1 visit: 1 cancelled.\n"
							+ "Sat 19 Sep 14:00 · Visit · no caregiver assigned · cancelled · no evidence");
		}
	}

	/**
	 * A visit the family chose to skip while its caregiver was away is part of the period's
	 * record, counted on its own after the other cancellations, and never reads as missed.
	 */
	@Test
	void aVisitTheFamilySkippedIsCountedOnItsOwnAndNeverAsMissed() {
		VisitFact skipped = new VisitFact(
				16L, 3L, "Daniel Goh", "Personal care", LocalDateTime.of(2026, 9, 17, 9, 0), VisitFact.Status.CANCELLED,
				0, 0, true);
		VisitFact calledOff = new VisitFact(
				17L, null, null, null, LocalDateTime.of(2026, 9, 18, 9, 0), VisitFact.Status.CANCELLED, 0, 0);
		ReportFacts week = ReportFixtures.quietWeek();
		ReportFacts withSkip = new ReportFacts(week.elderId(), week.period(), List.of(skipped, calledOff),
				List.of(), List.of(), List.of());

		for (Report.Audience audience : Report.Audience.values()) {
			ReportContent content = ReportAssembler.forAudience(audience).assemble(withSkip);
			String service = body(content, "Service completion");
			assertThat(service.lines().findFirst())
					.contains("2 visits: 1 cancelled, 1 cancelled at the family's request.");
			assertThat(service).contains(" · cancelled at the family's request · no evidence");
			assertThat(body(content, "Services")).isEqualTo("Personal care: 1 cancelled\nVisit: 1 cancelled");
			assertThat(body(content, "Overview")).startsWith("Visits: none planned; 2 cancelled.");
		}
		assertThat(skipped.isClosed()).as("it does not leave the report incomplete").isTrue();
		assertThat(new VisitFact(18L, 3L, null, null, LocalDateTime.of(2026, 9, 18, 9, 0), VisitFact.Status.VERIFIED,
				1, 1, true).cancelledByFamily()).as("only a cancelled visit can have been skipped").isFalse();
	}

	@ParameterizedTest
	@EnumSource(VisitFact.Status.class)
	void everyStateAVisitCanEndInIsDescribedInWords(VisitFact.Status status) {
		VisitFact visit = new VisitFact(
				15L, 3L, "Daniel Goh", "Personal care", LocalDateTime.of(2026, 9, 17, 9, 0), status, 1, 0);
		ReportFacts week = ReportFixtures.quietWeek();
		ReportFacts oneVisitWeek = new ReportFacts(week.elderId(), week.period(), List.of(visit),
				List.of(), List.of(), List.of());

		ReportContent content = ReportAssembler.forAudience(Report.Audience.INTERNAL).assemble(oneVisitWeek);
		String service = body(content, "Service completion");

		assertThat(service).doesNotContain(status.name()).contains("evidence 0 of 1 verified");
		assertThat(content.dataComplete()).isEqualTo(visit.isClosed());
	}

	// ------------------------------------------------------------- the new sections ---

	@Test
	void everyReaderEndsTheOverviewWithTheSameNumbersInTheSameWords() {
		for (Report.Audience audience : Report.Audience.values()) {
			List<String> lines = section(audience, "Overview").lines().toList();
			assertThat(lines.subList(lines.size() - NUMBERS.size(), lines.size()))
					.as("the numbers in the %s version", audience)
					.containsExactlyElementsOf(NUMBERS);
		}
	}

	@Test
	void theFiguresAreTheSameForEveryReader() {
		ReportContent family = reports.get(Report.Audience.FAMILY);
		for (Report.Audience audience : Report.Audience.values()) {
			assertThat(reports.get(audience).sections()).extracting(ReportSection::figures)
					.as("figures of the %s version", audience)
					.containsExactlyElementsOf(family.sections().stream().map(ReportSection::figures).toList());
		}
		assertThat(figures(Report.Audience.FAMILY, "Overview")).containsExactly(
				ReportFigure.share("visits", "Visits carried out", 2, 3),
				new ReportFigure("fulfilment", "Fulfilment", new BigDecimal("66.67"), null, "%"),
				ReportFigure.share("out-of-range", "Readings out of range", 1, 8),
				ReportFigure.count("incidents", "Incidents", 1),
				new ReportFigure("rating", "Average visit rating", new BigDecimal("3.50"), BigDecimal.valueOf(5), null));
		assertThat(figures(Report.Audience.FAMILY, "Services"))
				.containsExactly(ReportFigure.share("personal-care", "Personal care", 2, 3));
		assertThat(figures(Report.Audience.FAMILY, "Ratings and spot checks")).extracting(ReportFigure::key)
				.containsExactly("rating", "ratings", "disputed", "reviews", "spot-checks");
	}

	@Test
	void everySectionIsKeyedByItsTitle() {
		assertThat(reports.get(Report.Audience.FAMILY).sections()).extracting(ReportSection::key).containsExactly(
				"overview", "services", "service-completion", "vital-signs", "observations", "incidents",
				"ratings-and-spot-checks");
	}

	@Test
	void theFamilyOverviewGivesThePlanAndTheMainCaregiverButNotTheMedicalNotes() {
		assertThat(section(Report.Audience.FAMILY, "Overview").lines()).startsWith(
				"Care plan version 3 · 6.5 h a week.",
				"Main caregiver: Daniel Goh.");
		assertThat(section(Report.Audience.FAMILY, "Overview"))
				.doesNotContain(ReportFixtures.MEDICAL_NOTES)
				.doesNotContain("85");
	}

	@Test
	void theFamilyIsToldOfTheChangesAndRequestsItWasAskedAbout() {
		assertThat(section(Report.Audience.FAMILY, "Services").lines()).containsExactly(
				"Personal care: 2 of 3 carried out",
				"Fri 18 Sep 09:00 · Daniel Goh was away · waiting for the family's choice",
				"Hospital escort · Sat 19 Sep 10:00 · approved and booked");
	}

	@Test
	void theFamilyChartHasAPointPerDayWithThatDaysRange() {
		List<ReportSeries> series = series(Report.Audience.FAMILY);

		assertThat(series).extracting(ReportSeries::key).containsExactly("systolic", "diastolic", "pulse", "temperature");
		assertThat(series.getFirst().label()).isEqualTo("Systolic");
		assertThat(series.getFirst().unit()).isEqualTo("mmHg");
		assertThat(series.getFirst().points()).containsExactly(
				new ReportSeries.Point("2026-09-14", new BigDecimal("128.00"), new BigDecimal("128.00"), false),
				new ReportSeries.Point("2026-09-16", new BigDecimal("142.00"), new BigDecimal("142.00"), true));
	}

	@Test
	void twoReadingsOnOneDayBecomeOneFamilyPointWithBothEnds() {
		ReportFacts week = ReportFixtures.quietWeek();
		ReportFacts twice = new ReportFacts(week.elderId(), week.period(), List.of(ReportFixtures.MONDAY),
				List.of(new VitalFact(11L, "pulse", new BigDecimal("70.00"), null, false, LocalDateTime.of(2026, 9, 14, 9, 0)),
						new VitalFact(11L, "pulse", new BigDecimal("96.00"), "bpm", true, LocalDateTime.of(2026, 9, 14, 9, 30))),
				List.of(), List.of());

		ReportSeries pulse = ReportAssembler.forAudience(Report.Audience.FAMILY).assemble(twice).sections().stream()
				.filter(section -> section.key().equals("vital-signs")).findFirst().orElseThrow().series().getFirst();

		assertThat(pulse.unit()).as("the first unit on record").isEqualTo("bpm");
		assertThat(pulse.points()).containsExactly(
				new ReportSeries.Point("2026-09-14", new BigDecimal("70.00"), new BigDecimal("96.00"), true));
	}

	@Test
	void theFamilySeesHowEachSpotCheckEndedButNotWhatWasWrittenAboutTheCaregiver() {
		String ratings = section(Report.Audience.FAMILY, "Ratings and spot checks");

		assertThat(ratings.lines()).containsExactly(
				"The elder confirmed 1 visit and disputed 1.",
				"Average visit rating: 3.5 out of 5 from 2 ratings.",
				"Review of Daniel Goh · 14 Sep – 20 Sep · 4 out of 5 · keep the current caregiver",
				"Spot check · Wed 16 Sep 10:00 · needs improvement");
		assertThat(ratings).doesNotContain(ReportFixtures.FINDING).doesNotContain(ReportFixtures.CAREGIVER_RESPONSE);
	}

	@Test
	void theRegulatorCannotIdentifyTheElderFromTheOverview() {
		String overview = section(Report.Audience.REGULATOR, "Overview");

		assertThat(overview.lines()).startsWith(
				"Elder #1 · aged 80–89 · walks with a cane · lives alone",
				"Care plan version 3 · 6.5 h a week",
				"Primary caregiver: caregiver #3");
		assertThat(overview).doesNotContain("Tan Ah Mei").doesNotContain(ReportFixtures.MEDICAL_NOTES).doesNotContain("85");
	}

	@Test
	void theRegulatorCountsChangesAndAnswersWithoutQuotingAnybody() {
		assertThat(section(Report.Audience.REGULATOR, "Services").lines()).containsExactly(
				"Personal care: 2 of 3 carried out",
				"caregiver #3: 3 visits, 2 carried out · 2 h on site · 3 h planned",
				"Absence cover: 1 visit, 1 open.",
				"Value-added requests: 1, 1 approved and booked.");
		String ratings = section(Report.Audience.REGULATOR, "Ratings and spot checks");
		assertThat(ratings.lines()).containsExactly(
				"Elder's answers: 1 confirmed, 1 disputed; average rating 3.5 out of 5 from 2 ratings."
						+ " Comments are not included in this version.",
				"Periodic reviews: 1, overall 4; decisions: 1 keep the current caregiver.",
				"Spot check 4 · Wed 16 Sep 10:00 · caregiver #3 · needs improvement");
		assertThat(ratings)
				.doesNotContain(ReportFixtures.ELDER_COMMENT)
				.doesNotContain(ReportFixtures.REVIEW_NOTES)
				.doesNotContain(ReportFixtures.FINDING);
	}

	@Test
	void theRegulatorAndTheInstitutionChartEveryReading() {
		for (Report.Audience audience : List.of(Report.Audience.REGULATOR, Report.Audience.INTERNAL)) {
			List<ReportSeries> series = series(audience);
			assertThat(series).flatExtracting(ReportSeries::points).hasSize(8);
			assertThat(series.getFirst().points()).containsExactly(
					new ReportSeries.Point("2026-09-14T09:10", new BigDecimal("128.00"), new BigDecimal("128.00"), false),
					new ReportSeries.Point("2026-09-16T09:10", new BigDecimal("142.00"), new BigDecimal("142.00"), true));
		}
	}

	@Test
	void theInstitutionSeesTheWholeProfile() {
		assertThat(section(Report.Audience.INTERNAL, "Overview").lines()).startsWith(
				"Tan Ah Mei · 85 years old · female · walks with a cane · lives alone",
				"Medical notes: " + ReportFixtures.MEDICAL_NOTES,
				"Care plan version 3 · 6.5 h a week",
				"Primary caregiver: Daniel Goh (caregiver #3)");
	}

	@Test
	void theInstitutionSeesEveryonesWorkloadAndEveryChangeInFull() {
		assertThat(section(Report.Audience.INTERNAL, "Services").lines()).containsExactly(
				"Personal care: 2 of 3 carried out",
				"Daniel Goh (caregiver #3): 3 visits, 2 carried out · 2 h on site · 3 h planned",
				"Absence cover · Fri 18 Sep 09:00 · visit 13 · Daniel Goh (caregiver #3) away · awaiting the family",
				"Value-added request 7 · Hospital escort · Sat 19 Sep 10:00 · approved and booked as visit 15");
	}

	@Test
	void theInstitutionReadsEveryAnswerReviewAndFindingInItsOwnWords() {
		assertThat(section(Report.Audience.INTERNAL, "Ratings and spot checks").lines()).containsExactly(
				"Mon 14 Sep · visit 11 · Daniel Goh (caregiver #3) · confirmed, rated 5: Very patient today.",
				"Wed 16 Sep · visit 12 · Daniel Goh (caregiver #3) · disputed, rated 2: " + ReportFixtures.ELDER_COMMENT,
				"Review of Daniel Goh (caregiver #3) · 14 Sep – 20 Sep · overall 4, punctuality 5, care quality 4"
						+ " · keep the current caregiver: " + ReportFixtures.REVIEW_NOTES,
				"Spot check 4 · Wed 16 Sep 10:00 · Daniel Goh (caregiver #3) · needs improvement · finding: "
						+ ReportFixtures.FINDING + " · caregiver's response: " + ReportFixtures.CAREGIVER_RESPONSE);
	}

	@Test
	void eachWayAnAbsenceCanBeSettledIsDescribedToTheFamilyAndTheInstitution() {
		Map<RosterChangeFact, List<String>> expected = Map.of(
				change("RESOLVED", "REPLACED", "FAMILY", 5L), List.of("Mei Ling took the visit", "replaced by Mei Ling (caregiver #5) · decided by the family"),
				change("RESOLVED", "RESCHEDULED", "MANAGER", 5L), List.of("moved to another time", "rescheduled with Mei Ling (caregiver #5) · decided by a manager"),
				change("RESOLVED", "RESCHEDULED", "MANAGER", null), List.of("moved to another time", "rescheduled · decided by a manager"),
				change("RESOLVED", "SKIPPED", "FAMILY", null), List.of("skipped at the family's request", "skipped · decided by the family"),
				change("RESOLVED", "SKIPPED", "DEFAULT_PLAN", null), List.of("skipped", "skipped · decided by the default plan"),
				change("RESOLVED", "WITHDRAWN", "MANAGER", null), List.of("called off", "withdrawn · decided by a manager"),
				change("UNCOVERED", null, null, null), List.of("no replacement found yet", "uncovered"));

		expected.forEach((change, words) -> {
			ReportFacts facts = withChanges(new ReportFacts.Changes(List.of(change), List.of()));
			assertThat(body(ReportAssembler.forAudience(Report.Audience.FAMILY).assemble(facts), "Services"))
					.endsWith(words.get(0));
			assertThat(body(ReportAssembler.forAudience(Report.Audience.INTERNAL).assemble(facts), "Services"))
					.endsWith(words.get(1));
		});
	}

	@Test
	void eachStateOfAValueAddedRequestIsWorded() {
		Map<String, String> words = Map.of(
				"PENDING_APPROVAL", "awaiting the family's approval", "APPROVED", "approved", "REJECTED", "declined",
				"DISPATCHED", "approved and booked", "COMPLETED", "done", "CANCELLED", "cancelled", "LATER", "later");

		words.forEach((status, word) -> {
			ReportFacts facts = withChanges(new ReportFacts.Changes(List.of(), List.of(
					new ValueAddedFact(8L, "Companionship", LocalDateTime.of(2026, 9, 17, 15, 0), status, null))));
			assertThat(body(ReportAssembler.forAudience(Report.Audience.FAMILY).assemble(facts), "Services"))
					.endsWith("Companionship · Thu 17 Sep 15:00 · " + word);
		});
	}

	@Test
	void eachWayASpotCheckCanStandIsWorded() {
		Map<SpotCheckFact, String> words = Map.of(
				check("PENDING_APPROVAL", null, null), "awaiting the family's consent",
				check("REJECTED", null, null), "declined by the family",
				check("APPROVED", null, null), "planned",
				check("APPROVED", "MEETS_STANDARD", "COMPLETED"), "meets the standard",
				check("APPROVED", "NEEDS_IMPROVEMENT", "COMPLETED"), "needs improvement",
				check("APPROVED", null, "CAREGIVER_NO_SHOW"), "the caregiver did not turn up",
				check("APPROVED", null, "WITHDRAWN"), "withdrawn");

		words.forEach((check, word) -> {
			ReportFacts facts = withQuality(new ReportFacts.Quality(List.of(), List.of(), List.of(check)));
			assertThat(body(ReportAssembler.forAudience(Report.Audience.FAMILY).assemble(facts), "Ratings and spot checks"))
					.isEqualTo("Spot check · Thu 17 Sep 10:00 · " + word);
		});
	}

	@Test
	void eachRenewalDecisionIsWorded() {
		Map<String, String> words = Map.of(
				"RENEW_CURRENT", "keep the current caregiver", "REQUEST_CHANGE", "change the caregiver",
				"CANCEL_SERVICE", "cancel the service");

		words.forEach((decision, word) -> {
			ReportFacts facts = withQuality(new ReportFacts.Quality(List.of(), List.of(new ReviewFact(
					3L, null, ReportFixtures.WEEK.start(), ReportFixtures.WEEK.end(), 3, null, null, null, decision)), List.of()));
			assertThat(body(ReportAssembler.forAudience(Report.Audience.FAMILY).assemble(facts), "Ratings and spot checks"))
					.endsWith(word);
			assertThat(body(ReportAssembler.forAudience(Report.Audience.INTERNAL).assemble(facts), "Ratings and spot checks"))
					.isEqualTo("Review of unnamed (caregiver #3) · 14 Sep – 20 Sep · overall 3 · " + word);
		});
	}

	@Test
	void anElderWithoutAPlanOrAPrimaryCaregiverIsSaidToHaveNeither() {
		ElderProfile alone = new ElderProfile(null, null, null, "WHEELCHAIR_BEDBOUND", false, " ", null, null, null, null);
		ReportFacts facts = withElder(alone);

		assertThat(body(ReportAssembler.forAudience(Report.Audience.INTERNAL).assemble(facts), "Overview").lines())
				.startsWith("wheelchair or bed-bound · lives with others", "No care plan in force", "No primary caregiver assigned");
		assertThat(body(ReportAssembler.forAudience(Report.Audience.REGULATOR).assemble(facts), "Overview").lines())
				.startsWith("Elder #1 · wheelchair or bed-bound · lives with others", "No care plan in force",
						"No primary caregiver assigned");
		assertThat(body(ReportAssembler.forAudience(Report.Audience.FAMILY).assemble(facts), "Overview").lines())
				.startsWith("No care plan in force.")
				.noneMatch(line -> line.startsWith("Main caregiver"));
	}

	@Test
	void aPlanWithoutHoursAndAMobilityLevelOutsideTheListStillRead() {
		ElderProfile elder = new ElderProfile("Lim", "MALE", null, "CRUTCHES", null, null, 3L, null, 2, null);
		ReportFacts facts = withElder(elder);

		assertThat(body(ReportAssembler.forAudience(Report.Audience.INTERNAL).assemble(facts), "Overview").lines())
				.startsWith("Lim · male · crutches", "Care plan version 2", "Primary caregiver: unnamed (caregiver #3)");
		assertThat(body(ReportAssembler.forAudience(Report.Audience.FAMILY).assemble(facts), "Overview").lines())
				.startsWith("Care plan version 2.", "Main caregiver: a caregiver.");
	}

	@Test
	void anIndependentElderAndAnUnratedAnswerAreWordedPlainly() {
		ElderProfile elder = new ElderProfile(null, null, null, "INDEPENDENT", null, null, null, null, null, null);
		ReportFacts week = ReportFixtures.week();
		ReportFacts facts = new ReportFacts(week.elderId(), week.period(), week.visits(), List.of(), List.of(), List.of(),
				elder, new ReportFacts.Quality(List.of(new ConfirmationFact(
						11L, false, null, null, null)), List.of(), List.of()), ReportFacts.Changes.NONE);

		assertThat(body(ReportAssembler.forAudience(Report.Audience.INTERNAL).assemble(facts), "Overview"))
				.startsWith("moves independently");
		assertThat(body(ReportAssembler.forAudience(Report.Audience.INTERNAL).assemble(facts), "Ratings and spot checks"))
				.isEqualTo("Mon 14 Sep · visit 11 · Daniel Goh (caregiver #3) · confirmed");
		assertThat(body(ReportAssembler.forAudience(Report.Audience.FAMILY).assemble(facts), "Ratings and spot checks"))
				.isEqualTo("The elder confirmed 1 visit.");
		assertThat(body(ReportAssembler.forAudience(Report.Audience.FAMILY).assemble(facts), "Overview"))
				.endsWith("Visit ratings: none given.");
	}

	private static ReportFacts withChanges(ReportFacts.Changes changes) {
		ReportFacts week = ReportFixtures.quietWeek();
		return new ReportFacts(week.elderId(), week.period(), List.of(), List.of(), List.of(), List.of(),
				ElderProfile.UNKNOWN, ReportFacts.Quality.NONE, changes);
	}

	private static ReportFacts withQuality(ReportFacts.Quality quality) {
		ReportFacts week = ReportFixtures.quietWeek();
		return new ReportFacts(week.elderId(), week.period(), List.of(), List.of(), List.of(), List.of(),
				ElderProfile.UNKNOWN, quality, ReportFacts.Changes.NONE);
	}

	private static ReportFacts withElder(ElderProfile elder) {
		ReportFacts week = ReportFixtures.quietWeek();
		return new ReportFacts(week.elderId(), week.period(), List.of(), List.of(), List.of(), List.of(),
				elder, ReportFacts.Quality.NONE, ReportFacts.Changes.NONE);
	}

	private static RosterChangeFact change(String status, String outcome, String decidedBy, Long assigned) {
		return new RosterChangeFact(16L, LocalDateTime.of(2026, 9, 17, 9, 0), 3L, "Daniel Goh", status, outcome,
				decidedBy, assigned, assigned == null ? null : "Mei Ling");
	}

	private static SpotCheckFact check(String approval, String result, String outcome) {
		return new SpotCheckFact(9L, 3L, "Daniel Goh", LocalDateTime.of(2026, 9, 17, 10, 0), approval, result, outcome,
				null, null, null);
	}

	private List<ReportFigure> figures(Report.Audience audience, String title) {
		return reports.get(audience).sections().stream()
				.filter(section -> section.title().equals(title))
				.findFirst()
				.orElseThrow()
				.figures();
	}

	private List<ReportSeries> series(Report.Audience audience) {
		return reports.get(audience).sections().stream()
				.filter(section -> section.title().equals("Vital signs"))
				.findFirst()
				.orElseThrow()
				.series();
	}

	private static String body(ReportContent content, String title) {
		return content.sections().stream()
				.filter(section -> section.title().equals(title))
				.findFirst()
				.orElseThrow()
				.body();
	}

	private String section(Report.Audience audience, String title) {
		return reports.get(audience).sections().stream()
				.filter(section -> section.title().equals(title))
				.findFirst()
				.orElseThrow()
				.body();
	}

	private static List<String> titles(ReportContent content) {
		return content.sections().stream().map(ReportSection::title).toList();
	}
}
