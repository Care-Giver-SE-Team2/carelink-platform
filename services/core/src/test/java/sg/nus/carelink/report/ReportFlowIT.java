package sg.nus.carelink.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import sg.nus.carelink.report.application.ReportService;
import sg.nus.carelink.report.domain.model.Report;
import sg.nus.carelink.report.domain.model.ReportAmendment;
import sg.nus.carelink.report.domain.model.ReportFacts;
import sg.nus.carelink.report.domain.model.ReportPage;
import sg.nus.carelink.report.domain.model.ReportPeriod;
import sg.nus.carelink.report.domain.model.ReportSection;
import sg.nus.carelink.report.domain.model.VisitFact;
import sg.nus.carelink.report.domain.repository.ReportFactsSource;
import sg.nus.carelink.testsupport.SharedMySql;

/**
 * UC-MG07 against a real MySQL, with the real Flyway migrations (V8's report_amendment
 * included), the real Spring wiring and the real JSON column.
 *
 * <p>The unit tests prove what the three reports say; this proves the parts that only a
 * database can: the SQL that reads other modules' tables, the derived queries and their
 * ordering, the content column taking and giving back the JSON the adapter writes, and -
 * the reason this test exists - where a week begins and ends.
 *
 * <p><strong>The connection is configured as production's is.</strong> Staging's JDBC URL
 * declares {@code connectionTimeZone=Asia/Singapore} while the application's JVM runs in UTC,
 * so the driver shifts every instant by eight hours on the way into the database and back.
 * With the container's default URL nothing shifts, and a query that bound its period
 * boundaries the wrong way would pass here and misplace Sunday evening on staging. So the
 * same parameter is on this URL: on a JVM in UTC, as on CI and in the deployed image, the
 * week boundary cases below fail if the boundaries and the rows do not travel the same path.
 *
 * <p>Every row it needs, it creates, for an elder of its own; nothing is cleared and nothing
 * depends on what another test left behind. Rows are written with their times as
 * {@link Timestamp} parameters - the path Hibernate takes when the visit and incident modules
 * write them - never as SQL literals, which the driver would store without the shift.
 *
 * <p>Named *IT: runs under the integration-tests job of the pipeline; needs Docker.
 */
@SpringBootTest
@Import(ReportFlowIT.FixedClockConfig.class)
@TestPropertySource(properties = {
		// Keep the escalation sweep out of the way, and drive the weekly run by hand.
		"carelink.escalation.scan-initial-delay=PT1H",
		"carelink.report.schedule-cron=-"
})
class ReportFlowIT {

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		SharedMySql.register(registry, ReportFlowIT.class, null, "connectionTimeZone=Asia/Singapore");
	}

	private static final ZoneId SINGAPORE = ZoneId.of("Asia/Singapore");

	/** Monday 14 to Sunday 20 September 2026. */
	private static final LocalDate MONDAY = LocalDate.of(2026, 9, 14);
	private static final LocalDate SUNDAY = LocalDate.of(2026, 9, 20);

	/** The institution's manager, created once for the class. */
	private static Long manager;
	private static Long caregiverUser;
	private static Long caregiver;

	private Long elder;

	@Autowired
	private ReportService reports;

	@Autowired
	private ReportFactsSource facts;

	@Autowired
	private JdbcClient jdbc;

	@BeforeEach
	void givenAManagerACaregiverAndAFreshElder() {
		if (manager == null) {
			manager = insert("insert into app_user (username, password_hash, display_name, enabled)"
					+ " values ('it-report-manager', '{noop}unused-here', 'Alice Tan', true)", Map.of());
			jdbc.sql("insert into user_role (user_id, role) values (:id, 'MANAGER')").param("id", manager).update();
			caregiverUser = insert("insert into app_user (username, password_hash, display_name, enabled)"
					+ " values ('it-report-caregiver', '{noop}unused-here', 'Daniel Goh', true)", Map.of());
			caregiver = insert("insert into caregiver (user_id, full_name, status) values (:user, 'Daniel Goh', 'AVAILABLE')",
					Map.of("user", caregiverUser));
		}
		elder = newElder("Report Test Elder");
	}

	// ------------------------------------------------------------------ A1 and A2 ---

	/**
	 * A1: two verified visits with readings and notes, and an incident that was resolved.
	 * Three reports, one per reader, all filed and complete, and each reads back from the JSON
	 * column exactly as it was generated.
	 */
	@Test
	void aWeekOfRecordsBecomesThreeFiledReports() {
		givenTheElderHadAVerifiedWeek();

		List<Report> filed = reports.generate(elder, MONDAY, SUNDAY, manager);

		assertThat(filed).extracting(Report::audience).containsExactly(
				Report.Audience.FAMILY, Report.Audience.REGULATOR, Report.Audience.INTERNAL);
		assertThat(filed).allSatisfy(report -> {
			assertThat(report.status()).isEqualTo(Report.Status.PUBLISHED);
			assertThat(report.content().dataComplete()).isTrue();
			assertThat(report.content().missingItems()).isEmpty();
			assertThat(reports.findDetail(report.id()).content()).isEqualTo(report.content());
		});

		Map<Report.Audience, Report> byReader = filed.stream()
				.collect(Collectors.toMap(Report::audience, Function.identity()));
		assertThat(section(byReader.get(Report.Audience.FAMILY), "Vital signs")).contains("Systolic 128–142 mmHg");
		assertThat(section(byReader.get(Report.Audience.FAMILY), "Observations")).contains("Walked to the void deck");
		assertThat(byReader.get(Report.Audience.FAMILY).content().disclaimer()).isNotBlank();

		assertThat(byReader.get(Report.Audience.REGULATOR).content().sections())
				.extracting(ReportSection::body)
				.noneMatch(body -> body.contains("Daniel Goh"));
		assertThat(section(byReader.get(Report.Audience.REGULATOR), "Service completion"))
				.contains("caregiver #" + caregiver);

		assertThat(section(byReader.get(Report.Audience.INTERNAL), "Vital signs")).contains("Systolic 142 mmHg · out of range");
		assertThat(section(byReader.get(Report.Audience.INTERNAL), "Incidents"))
				.contains("Ben Lim (it-ben)")
				.contains("Resolution: HANDLED_ON_SITE :: No injury.");
	}

	/** A2: a visit in the period that is not closed does not stop the reports; it is named in them. */
	@Test
	void aVisitThatIsNotClosedIsNamedAndTheReportsAreMarkedIncomplete() {
		givenTheElderHadAVerifiedWeek();
		Long friday = visit(LocalDateTime.of(2026, 9, 18, 9, 0), "SCHEDULED");

		List<Report> filed = reports.generate(elder, MONDAY, SUNDAY, manager);

		assertThat(filed).hasSize(3).allSatisfy(report -> {
			assertThat(report.content().dataComplete()).isFalse();
			assertThat(report.content().missingItems()).containsExactly("Visit " + friday + " on 2026-09-18 not closed");
		});
	}

	/** The content column holds a JSON object, not a JSON string with the object quoted inside it. */
	@Test
	void theContentIsStoredAsAJsonDocument() {
		givenTheElderHadAVerifiedWeek();
		Report family = reports.generate(elder, MONDAY, SUNDAY, manager).getFirst();

		String type = jdbc.sql("select json_type(content) from report where id = :id")
				.param("id", family.id()).query(String.class).single();
		String firstTitle = jdbc.sql("select json_unquote(json_extract(content, '$.sections[0].title')) from report where id = :id")
				.param("id", family.id()).query(String.class).single();

		assertThat(type).isEqualTo("OBJECT");
		assertThat(firstTitle).isEqualTo("Service completion");
	}

	// ------------------------------------------------------------------- the week ---

	/**
	 * Where a week begins and ends, on the clock of the people it is about: midnight in
	 * Singapore. With the connection zone set as production sets it, a period bound to the
	 * wrong path would move both edges by eight hours and this would fail at every one of them.
	 */
	@Test
	void theWeekRunsFromMidnightToMidnightSingaporeTime() {
		Long sundayBefore = visit(LocalDateTime.of(2026, 9, 13, 23, 45), "VERIFIED");
		Long mondayStart = visit(LocalDateTime.of(2026, 9, 14, 0, 15), "VERIFIED");
		Long sundayLate = visit(LocalDateTime.of(2026, 9, 20, 23, 30), "VERIFIED");
		Long mondayAfter = visit(LocalDateTime.of(2026, 9, 21, 0, 30), "VERIFIED");

		ReportFacts week = facts.gather(elder, new ReportPeriod(MONDAY, SUNDAY)).orElseThrow();

		assertThat(week.visits()).extracting(VisitFact::id)
				.as("in: Monday 00:15 and Sunday 23:30; out: the Sunday before and the Monday after")
				.containsExactly(mondayStart, sundayLate)
				.doesNotContain(sundayBefore, mondayAfter);
		assertThat(week.visits()).extracting(VisitFact::scheduledStart)
				.as("and the times read back are the ones that were written")
				.containsExactly(LocalDateTime.of(2026, 9, 14, 0, 15), LocalDateTime.of(2026, 9, 20, 23, 30));
		assertThat(facts.eldersWithVisitsIn(new ReportPeriod(MONDAY, SUNDAY))).contains(elder);
		assertThat(facts.eldersWithVisitsIn(new ReportPeriod(SUNDAY.plusDays(2), SUNDAY.plusDays(3))))
				.doesNotContain(elder);
	}

	@Test
	void anElderWithNoRecordsInThePeriodStillExistsAndAnUnknownOneDoesNot() {
		assertThat(facts.gather(elder, new ReportPeriod(MONDAY, SUNDAY))).get()
				.satisfies(quiet -> assertThat(quiet.visits()).isEmpty());
		assertThat(facts.gather(-1L, new ReportPeriod(MONDAY, SUNDAY))).isEmpty();
	}

	// ------------------------------------------------------------- the weekly run ---

	/** A9: filing the same week twice files it once. */
	@Test
	void generatingTheSameWeekAgainFilesNothingNew() {
		givenTheElderHadAVerifiedWeek();

		List<Long> first = reports.generate(elder, MONDAY, SUNDAY, manager).stream().map(Report::id).toList();
		List<Long> again = reports.generate(elder, MONDAY, SUNDAY, manager).stream().map(Report::id).toList();

		assertThat(again).containsExactlyElementsOf(first);
		assertThat(jdbc.sql("select count(*) from report where elder_id = :elder").param("elder", elder)
				.query(Long.class).single()).isEqualTo(3L);
	}

	/**
	 * A9: the run the scheduler fires. The clock stands at Sunday 23:00 in Singapore, so it
	 * reports on the week ending that day, for the elders who had a visit in it and no others.
	 */
	@Test
	void theWeeklyRunReportsOnEveryElderWithAVisitAndOnlyThem() {
		givenTheElderHadAVerifiedWeek();
		Long quietElder = newElder("Quiet Elder");

		reports.generateForLastWeek();
		reports.generateForLastWeek();

		assertThat(reportsFor(elder)).containsExactlyInAnyOrder(
				Report.Audience.FAMILY.name(), Report.Audience.REGULATOR.name(), Report.Audience.INTERNAL.name());
		assertThat(reportsFor(quietElder)).isEmpty();
		assertThat(jdbc.sql("select count(*) from report where elder_id = :elder and generated_by_user_id is null"
						+ " and period_start = :start and period_end = :end")
				.param("elder", elder).param("start", MONDAY).param("end", SUNDAY)
				.query(Long.class).single()).isEqualTo(3L);
	}

	// ------------------------------------------------------------- corrections ---

	/** A6: a correction is appended, signed and dated; the report itself is not written. */
	@Test
	void aCorrectionIsAppendedAndTheReportIsLeftAsItWas() {
		givenTheElderHadAVerifiedWeek();
		Report family = reports.generate(elder, MONDAY, SUNDAY, manager).getFirst();

		ReportAmendment stored = reports.amend(family.id(), "The Friday visit was cancelled by the family.", manager);

		Report read = reports.findDetail(family.id());
		assertThat(read.amendments()).containsExactly(stored);
		assertThat(stored.authorUserId()).isEqualTo(manager);
		assertThat(stored.createdAt()).isEqualTo(LocalDateTime.of(2026, 9, 20, 23, 0));
		assertThat(read.content()).isEqualTo(family.content());
		assertThat(read.createdAt()).isEqualTo(family.createdAt());
	}

	// -------------------------------------------------------------------- the list ---

	/** A8: latest period first, the three versions of a period together - MySQL's ENUM order. */
	@Test
	void theListShowsTheLatestPeriodFirstWithItsThreeVersionsTogether() {
		givenTheElderHadAVerifiedWeek();
		reports.generate(elder, MONDAY.minusWeeks(1), SUNDAY.minusWeeks(1), manager);
		reports.generate(elder, MONDAY, SUNDAY, manager);

		ReportPage page = reports.page(elder, null, 0, 20);

		assertThat(page.totalElements()).isEqualTo(6);
		assertThat(page.items()).extracting(report -> report.period().end()).containsExactly(
				SUNDAY, SUNDAY, SUNDAY, SUNDAY.minusWeeks(1), SUNDAY.minusWeeks(1), SUNDAY.minusWeeks(1));
		assertThat(page.items().subList(0, 3)).extracting(Report::audience).containsExactly(
				Report.Audience.FAMILY, Report.Audience.REGULATOR, Report.Audience.INTERNAL);
		assertThat(reports.page(elder, Report.Audience.REGULATOR, 0, 20).items())
				.extracting(Report::audience).containsOnly(Report.Audience.REGULATOR);
	}

	// ----------------------------------------------------------------------- rows ---

	/**
	 * The week A1 describes: Monday and Wednesday verified with four readings each - one out of
	 * range - two items of evidence and a note, and a fall on the Tuesday that was resolved.
	 */
	private void givenTheElderHadAVerifiedWeek() {
		Long monday = visit(LocalDateTime.of(2026, 9, 14, 9, 0), "VERIFIED");
		Long wednesday = visit(LocalDateTime.of(2026, 9, 16, 9, 0), "VERIFIED");

		readings(monday, LocalDateTime.of(2026, 9, 14, 9, 10), "128.00", "82.00", "72.00", "36.80", false);
		readings(wednesday, LocalDateTime.of(2026, 9, 16, 9, 10), "142.00", "88.00", "76.00", "36.60", true);
		for (Long visit : List.of(monday, wednesday)) {
			evidence(visit, "PHOTO");
			evidence(visit, "SIGNATURE");
		}
		note(monday, "Mobility", "Walked to the void deck with the cane, steady on her feet.");
		note(wednesday, "Meals", "Ate half of lunch; said she was not hungry.");

		Long fall = insert("insert into incident (elder_id, reported_by_user_id, responder_user_id, source, category,"
						+ " severity, status, description, reported_at, resolved_at)"
						+ " values (:elder, :reporter, :manager, 'CAREGIVER', 'FALL', 'MEDIUM', 'RESOLVED',"
						+ " 'Slipped getting out of the shower, no injury.', :reported, :resolved)",
				Map.of("elder", elder, "reporter", caregiverUser, "manager", manager,
						"reported", at(LocalDateTime.of(2026, 9, 15, 10, 15)),
						"resolved", at(LocalDateTime.of(2026, 9, 15, 11, 2))));
		log(fall, "caregiver:" + caregiverUser, "REPORTED", "reported by caregiver", LocalDateTime.of(2026, 9, 15, 10, 15));
		log(fall, "Ben Lim (it-ben)", "CLAIMED", "taken over; countdown stopped", LocalDateTime.of(2026, 9, 15, 10, 21));
		log(fall, "Ben Lim (it-ben)", "RESOLVED",
				"HANDLED_ON_SITE :: No injury. Bathroom grab bar to be fitted this week.", LocalDateTime.of(2026, 9, 15, 11, 2));
	}

	private Long newElder(String name) {
		return insert("insert into elder (full_name) values (:name)", Map.of("name", name));
	}

	private Long visit(LocalDateTime start, String status) {
		return insert("insert into visit (elder_id, caregiver_id, service_type, scheduled_start, status)"
						+ " values (:elder, :caregiver, 'Personal care', :start, :status)",
				Map.of("elder", elder, "caregiver", caregiver, "start", at(start), "status", status));
	}

	private void readings(Long visit, LocalDateTime at, String systolic, String diastolic, String pulse,
			String temperature, boolean systolicOutOfRange) {
		reading(visit, "systolic", systolic, "mmHg", systolicOutOfRange, at);
		reading(visit, "diastolic", diastolic, "mmHg", false, at);
		reading(visit, "pulse", pulse, "bpm", false, at);
		reading(visit, "temperature", temperature, "°C", false, at);
	}

	private void reading(Long visit, String metric, String value, String unit, boolean outOfRange, LocalDateTime at) {
		jdbc.sql("insert into vital_sign (visit_id, metric, value, unit, out_of_range, recorded_at)"
						+ " values (:visit, :metric, :value, :unit, :outOfRange, :at)")
				.param("visit", visit).param("metric", metric).param("value", new BigDecimal(value))
				.param("unit", unit).param("outOfRange", outOfRange).param("at", at(at))
				.update();
	}

	private void evidence(Long visit, String kind) {
		jdbc.sql("insert into visit_evidence (visit_id, kind, reference, verification_status)"
						+ " values (:visit, :kind, :reference, 'VERIFIED')")
				.param("visit", visit).param("kind", kind).param("reference", "it/" + visit + "/" + kind)
				.update();
	}

	private void note(Long visit, String task, String note) {
		jdbc.sql("insert into visit_task (visit_id, name, status, caregiver_note) values (:visit, :task, 'DONE', :note)")
				.param("visit", visit).param("task", task).param("note", note)
				.update();
	}

	private void log(Long incident, String actor, String action, String detail, LocalDateTime at) {
		jdbc.sql("insert into incident_log (incident_id, actor, action, detail, occurred_at)"
						+ " values (:incident, :actor, :action, :detail, :at)")
				.param("incident", incident).param("actor", actor).param("action", action)
				.param("detail", detail).param("at", at(at))
				.update();
	}

	private Long insert(String sql, Map<String, ?> params) {
		KeyHolder key = new GeneratedKeyHolder();
		jdbc.sql(sql).params(params).update(key);
		return key.getKeyAs(Number.class).longValue();
	}

	private List<String> reportsFor(Long elderId) {
		return jdbc.sql("select audience from report where elder_id = :elder and period_start = :start and period_end = :end")
				.param("elder", elderId).param("start", MONDAY).param("end", SUNDAY)
				.query(String.class).list();
	}

	/** How Hibernate hands a LocalDateTime to the driver. See the class comment. */
	private static Timestamp at(LocalDateTime moment) {
		return Timestamp.valueOf(moment);
	}

	private static String section(Report report, String title) {
		return report.content().sections().stream()
				.filter(section -> section.title().equals(title))
				.findFirst()
				.orElseThrow()
				.body();
	}

	// ----------------------------------------------------------------- the clock ---

	/** Sunday 20 September 2026, 23:00 in Singapore: when the weekly run fires by default. */
	@TestConfiguration
	static class FixedClockConfig {

		@Bean
		Clock clock() {
			return Clock.fixed(Instant.parse("2026-09-20T15:00:00Z"), SINGAPORE);
		}
	}
}
