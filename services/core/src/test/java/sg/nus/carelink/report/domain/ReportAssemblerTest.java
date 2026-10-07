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

import sg.nus.carelink.report.domain.model.IncidentFact;
import sg.nus.carelink.report.domain.model.Report;
import sg.nus.carelink.report.domain.model.ReportContent;
import sg.nus.carelink.report.domain.model.ReportFacts;
import sg.nus.carelink.report.domain.model.ReportSection;
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

	private static final List<String> SKELETON =
			List.of("Service completion", "Vital signs", "Observations", "Incidents");

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
	void everyReaderGetsTheSameFourSectionsInTheSameOrder() {
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
					"No visits were scheduled in this period.",
					"No vital signs were recorded in this period.",
					"No observations were recorded in this period.",
					"No incidents were reported in this period.");
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
				"describeCaregiver", "vitalSigns", "observations", "incidents", "disclaimer");

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

		String incidents = ReportAssembler.forAudience(Report.Audience.FAMILY).assemble(open).sections().get(3).body();

		assertThat(incidents).isEqualTo("Thu 17 Sep 20:05 · Emergency call · still being followed up");
	}

	@Test
	void aSingleReadingIsShownAsOneValueRatherThanASpan() {
		ReportFacts week = ReportFixtures.quietWeek();
		ReportFacts oneReading = new ReportFacts(week.elderId(), week.period(), List.of(ReportFixtures.MONDAY),
				List.of(new VitalFact(11L, "pulse", new BigDecimal("72.00"), "bpm", false,
						LocalDateTime.of(2026, 9, 14, 9, 10))),
				List.of(), List.of());

		assertThat(ReportAssembler.forAudience(Report.Audience.FAMILY).assemble(oneReading).sections().get(1).body())
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
			assertThat(ReportAssembler.forAudience(audience).assemble(unassignedWeek).sections().get(0).body())
					.isEqualTo("1 visit: 1 cancelled.\n"
							+ "Sat 19 Sep 14:00 · Visit · no caregiver assigned · cancelled · no evidence");
		}
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
		String service = content.sections().get(0).body();

		assertThat(service).doesNotContain(status.name()).contains("evidence 0 of 1 verified");
		assertThat(content.dataComplete()).isEqualTo(visit.isClosed());
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
