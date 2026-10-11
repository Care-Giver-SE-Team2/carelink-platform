package sg.nus.carelink.report.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import sg.nus.carelink.events.EventHandler;
import sg.nus.carelink.events.EventMetadata;
import sg.nus.carelink.eventtypes.ElderConfirmationSubmitted;
import sg.nus.carelink.eventtypes.IncidentRaised;
import sg.nus.carelink.eventtypes.IncidentUpdated;
import sg.nus.carelink.eventtypes.RosterChangeUpdated;
import sg.nus.carelink.eventtypes.SingaporeTime;
import sg.nus.carelink.eventtypes.SpotCheckUpdated;
import sg.nus.carelink.eventtypes.VisitCancelled;
import sg.nus.carelink.eventtypes.VisitCheckedIn;
import sg.nus.carelink.eventtypes.VisitCompleted;
import sg.nus.carelink.eventtypes.VisitScheduled;
import sg.nus.carelink.eventtypes.VisitTaskRecorded;
import sg.nus.carelink.eventtypes.VitalsRecorded;
import sg.nus.carelink.report.domain.model.IncidentFact;
import sg.nus.carelink.report.domain.model.ReportFacts;
import sg.nus.carelink.report.domain.model.ReportPeriod;
import sg.nus.carelink.report.domain.model.VisitFact;
import sg.nus.carelink.report.domain.model.VitalFact;
import sg.nus.carelink.report.domain.repository.ReportFactsSource;
import sg.nus.carelink.testsupport.DemoData;
import sg.nus.carelink.testsupport.SharedMySql;
import sg.nus.carelink.testsupport.VisitDatabaseForDemoData;

/**
 * What visit and core tell report becomes the facts a report is made from: each event goes through
 * the handler report's queue gives it to, in a transaction as the library runs it, and the week is
 * read back through {@link ReportFactsSource}. Events arrive at least once and in no particular
 * order, so it also proves the ordering rules of the event catalogue, and the copy that gives report
 * the facts of demonstration data, which sends no events (core's db/demo/report-facts.sql).
 *
 * <p>The connection is configured as production's is ({@code ReportFlowIT} explains why): times
 * cross it as {@link Timestamp}s on a JVM in UTC.
 */
@SpringBootTest
class ReportFactsFromEventsIT {

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		SharedMySql.register(registry, ReportFactsFromEventsIT.class, null, "connectionTimeZone=Asia/Singapore");
	}

	/** Monday 14 to Sunday 20 September 2026. */
	private static final ReportPeriod WEEK = new ReportPeriod(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 20));
	private static final LocalDateTime TUESDAY_NINE = LocalDateTime.of(2026, 9, 15, 9, 0);
	private static final LocalDateTime WEDNESDAY_NINE = LocalDateTime.of(2026, 9, 16, 9, 0);

	/** Ids as visit and core would number their records, one series for the class. */
	private static final AtomicLong IDS = new AtomicLong(5_000);

	private static final String VISIT_DATABASE = VisitDatabaseForDemoData.NAME;

	@Autowired
	private List<EventHandler<?>> handlers;
	@Autowired
	private ReportFactsSource facts;
	@Autowired
	private JdbcClient jdbc;
	@Autowired
	private DataSource dataSource;
	@Autowired
	private PlatformTransactionManager transactionManager;

	private long sequence = 10_000;
	private Long elder;
	private Long caregiver;
	private Long replacement;

	/** core's rows, which the core stand-in answers the elder's profile and the caregivers' names from. */
	@BeforeEach
	void givenAnElderAndTwoCaregiversInCore() {
		elder = insert("insert into elder (full_name) values ('Events Elder')");
		caregiver = caregiver("Daniel Goh");
		replacement = caregiver("Mei Ling");
	}

	@Test
	void aWeekToldByVisitAndCoreBecomesTheReportsFacts() {
		long visit = IDS.incrementAndGet();
		long skipped = IDS.incrementAndGet();
		long incident = IDS.incrementAndGet();
		deliver(new VisitScheduled(visit, elder, caregiver, 21L, 40L, "Personal care", at(TUESDAY_NINE),
				at(TUESDAY_NINE.plusHours(1)), "PLAN", null, 0));
		deliver(new VisitCheckedIn(visit, elder, at(TUESDAY_NINE), caregiver, at(TUESDAY_NINE.plusMinutes(5)), false, 1));
		deliver(new VisitTaskRecorded(IDS.incrementAndGet(), visit, elder, at(TUESDAY_NINE), "Mobility", "DONE", null,
				"  Walked to the void deck.  ", at(TUESDAY_NINE.plusMinutes(20))));
		deliver(new VitalsRecorded(IDS.incrementAndGet(), visit, elder, at(TUESDAY_NINE), at(TUESDAY_NINE.plusMinutes(10)),
				"ATTENTION", "Slightly warm", List.of(new VitalsRecorded.Reading("systolic", "142.00", "mmHg", false),
						new VitalsRecorded.Reading("temperature", "37.80", "°C", false))));
		deliver(new VisitCompleted(visit, elder, at(TUESDAY_NINE), caregiver, at(TUESDAY_NINE.plusMinutes(5)),
				at(TUESDAY_NINE.plusHours(1)), 3));
		deliver(new ElderConfirmationSubmitted(visit, elder, at(TUESDAY_NINE), "DISPUTED", 2, "Left early",
				at(TUESDAY_NINE.plusHours(10)), incident));
		deliver(new IncidentRaised(incident, elder, visit, "ELDER_SERVICE_DISPUTE", "SERVICE", "MEDIUM", "OPEN",
				"Left early", at(TUESDAY_NINE.plusHours(10))));
		deliver(new IncidentUpdated(incident, elder, IDS.incrementAndGet(), "REPORTED", "system", "reported by the elder",
				at(TUESDAY_NINE.plusHours(10)), "OPEN", "MEDIUM", null));
		deliver(new IncidentUpdated(incident, elder, IDS.incrementAndGet(), "RESOLVED", "Ben Lim (it-ben)",
				"HANDLED_ON_SITE :: Spoke to the caregiver", at(TUESDAY_NINE.plusHours(11)), "RESOLVED", "MEDIUM",
				at(TUESDAY_NINE.plusHours(11))));
		// Wednesday's visit: the family chose to skip it when its caregiver was away
		deliver(new VisitScheduled(skipped, elder, caregiver, 21L, 40L, "Personal care", at(WEDNESDAY_NINE),
				at(WEDNESDAY_NINE.plusHours(1)), "PLAN", null, 0));
		deliver(new RosterChangeUpdated(IDS.incrementAndGet(), skipped, elder, 33L, at(WEDNESDAY_NINE), caregiver,
				"RESOLVED", "SKIPPED", "FAMILY", null, at(TUESDAY_NINE)));
		deliver(new VisitCancelled(skipped, elder, at(WEDNESDAY_NINE), caregiver, "ABSENCE", 33L,
				"Family skipped the visit", 1, at(TUESDAY_NINE)));
		deliver(new SpotCheckUpdated(IDS.incrementAndGet(), elder, visit, caregiver, at(TUESDAY_NINE.plusDays(2)),
				"APPROVED", "MEETS_STANDARD", "COMPLETED", "All in order", null, at(TUESDAY_NINE.plusDays(2).plusMinutes(40)),
				at(TUESDAY_NINE.plusDays(2).plusMinutes(40))));

		ReportFacts week = facts.gather(elder, WEEK).orElseThrow();

		assertThat(week.elder().fullName()).isEqualTo("Events Elder");
		assertThat(week.visits()).extracting(VisitFact::id, VisitFact::status, VisitFact::caregiverName,
				VisitFact::serviceType, VisitFact::plannedMinutes, VisitFact::workedMinutes)
				.containsExactly(
						tuple(visit, VisitFact.Status.COMPLETED, "Daniel Goh", "Personal care", 60, 55),
						tuple(skipped, VisitFact.Status.CANCELLED, "Daniel Goh", "Personal care", 60, null));
		assertThat(week.visits().get(1).cancelledByFamily()).isTrue();
		assertThat(week.vitals()).extracting(VitalFact::metric, VitalFact::value, VitalFact::recordedAt)
				.containsExactly(tuple("systolic", new BigDecimal("142.00"), TUESDAY_NINE.plusMinutes(10)),
						tuple("temperature", new BigDecimal("37.80"), TUESDAY_NINE.plusMinutes(10)));
		assertThat(week.observations()).singleElement()
				.satisfies(note -> assertThat(note.note()).isEqualTo("Walked to the void deck."));
		assertThat(week.incidents()).singleElement().satisfies(fact -> {
			assertThat(fact.status()).isEqualTo("RESOLVED");
			assertThat(fact.category()).isEqualTo("SERVICE");
			assertThat(fact.reportedAt()).isEqualTo(TUESDAY_NINE.plusHours(10));
			assertThat(fact.resolvedAt()).isEqualTo(TUESDAY_NINE.plusHours(11));
			assertThat(fact.timeline()).extracting(IncidentFact.Step::action).containsExactly("REPORTED", "RESOLVED");
		});
		assertThat(week.quality().confirmations()).singleElement().satisfies(answer -> {
			assertThat(answer.disputed()).isTrue();
			assertThat(answer.rating()).isEqualTo(2);
			assertThat(answer.confirmedAt()).isEqualTo(TUESDAY_NINE.plusHours(10));
		});
		assertThat(week.quality().spotChecks()).singleElement().satisfies(check -> {
			assertThat(check.caregiverName()).isEqualTo("Daniel Goh");
			assertThat(check.finding()).isEqualTo("All in order");
		});
		assertThat(week.changes().rosterChanges()).singleElement().satisfies(change -> {
			assertThat(change.outcome()).isEqualTo("SKIPPED");
			assertThat(change.originalCaregiverName()).isEqualTo("Daniel Goh");
		});
		assertThat(facts.eldersWithVisitsIn(WEEK)).contains(elder);
	}

	/** The newest version holds; what only VisitScheduled carries is filled in whenever it arrives. */
	@Test
	void aVisitsNewestVersionWinsWhateverOrderItsEventsArriveIn() {
		long visit = IDS.incrementAndGet();
		VisitCompleted completed = new VisitCompleted(visit, elder, at(TUESDAY_NINE), caregiver,
				at(TUESDAY_NINE.plusMinutes(5)), at(TUESDAY_NINE.plusHours(1)), 3);
		deliver(completed);
		deliver(new VisitCheckedIn(visit, elder, at(TUESDAY_NINE), caregiver, at(TUESDAY_NINE.plusMinutes(7)), false, 1));
		deliver(new VisitScheduled(visit, elder, null, 21L, 40L, "Bathing", at(TUESDAY_NINE), at(TUESDAY_NINE.plusHours(1)),
				"PLAN", null, 0));
		deliver(completed);

		assertThat(facts.gather(elder, WEEK).orElseThrow().visits()).singleElement().satisfies(fact -> {
			assertThat(fact.status()).isEqualTo(VisitFact.Status.COMPLETED);
			assertThat(fact.caregiverId()).as("not the empty caregiver of the older VisitScheduled").isEqualTo(caregiver);
			assertThat(fact.serviceType()).isEqualTo("Bathing");
			assertThat(fact.plannedMinutes()).isEqualTo(60);
			assertThat(fact.workedMinutes()).as("the check-in of version 3, not of version 1").isEqualTo(55);
		});
	}

	@Test
	void anIncidentTakesItsStateFromItsLatestEntryAndIsInNoWeekUntilItIsRaised() {
		long incident = IDS.incrementAndGet();
		long claimed = IDS.incrementAndGet();
		long resolved = IDS.incrementAndGet();
		deliver(new IncidentUpdated(incident, elder, resolved, "RESOLVED", "Ben Lim (it-ben)", "HANDLED_ON_SITE :: Fine",
				at(TUESDAY_NINE.plusHours(2)), "RESOLVED", "HIGH", at(TUESDAY_NINE.plusHours(2))));

		assertThat(facts.gather(elder, WEEK).orElseThrow().incidents()).as("not raised yet").isEmpty();

		deliver(new IncidentRaised(incident, elder, null, "CAREGIVER", "FALL", "MEDIUM", "OPEN", "Slipped",
				at(TUESDAY_NINE)));
		deliver(new IncidentUpdated(incident, elder, claimed, "CLAIMED", "Ben Lim (it-ben)", "taken over",
				at(TUESDAY_NINE.plusMinutes(10)), "IN_PROGRESS", "MEDIUM", null));

		assertThat(facts.gather(elder, WEEK).orElseThrow().incidents()).singleElement().satisfies(fact -> {
			assertThat(fact.status()).as("the latest entry's, not the raise's or the late claim's").isEqualTo("RESOLVED");
			assertThat(fact.severity()).isEqualTo("HIGH");
			assertThat(fact.description()).isEqualTo("Slipped");
			assertThat(fact.timeline()).extracting(IncidentFact.Step::action).containsExactly("CLAIMED", "RESOLVED");
		});
	}

	@Test
	void aSpotCheckAndARosterChangeKeepTheirNewestStateBySequence() {
		long spotCheck = IDS.incrementAndGet();
		long rosterChange = IDS.incrementAndGet();
		long visit = IDS.incrementAndGet();
		deliver(new SpotCheckUpdated(spotCheck, elder, null, caregiver, at(TUESDAY_NINE), "APPROVED", "NEEDS_IMPROVEMENT",
				"COMPLETED", "Gloves not changed", null, at(TUESDAY_NINE.plusMinutes(40)), at(TUESDAY_NINE)), 500);
		deliver(new SpotCheckUpdated(spotCheck, elder, null, caregiver, at(TUESDAY_NINE), "PENDING_APPROVAL", null, null,
				null, null, null, at(TUESDAY_NINE)), 400);
		RosterChangeUpdated waiting = new RosterChangeUpdated(rosterChange, visit, elder, 33L, at(WEDNESDAY_NINE),
				caregiver, "AWAITING_FAMILY", null, null, null, at(TUESDAY_NINE));
		deliver(waiting, 600);
		deliver(new RosterChangeUpdated(rosterChange, visit, elder, 33L, at(WEDNESDAY_NINE), caregiver, "RESOLVED",
				"REPLACED", "FAMILY", replacement, at(TUESDAY_NINE)), 700);
		deliver(waiting, 600);

		ReportFacts week = facts.gather(elder, WEEK).orElseThrow();
		assertThat(week.quality().spotChecks()).singleElement().satisfies(check -> {
			assertThat(check.result()).isEqualTo("NEEDS_IMPROVEMENT");
			assertThat(check.finding()).isEqualTo("Gloves not changed");
		});
		assertThat(week.changes().rosterChanges()).singleElement().satisfies(change -> {
			assertThat(change.status()).isEqualTo("RESOLVED");
			assertThat(change.assignedCaregiverName()).isEqualTo("Mei Ling");
		});
	}

	@Test
	void aSecondDeliveryOfReadingsOrOfAnAnswerAddsNothing() {
		long visit = IDS.incrementAndGet();
		VitalsRecorded vitals = new VitalsRecorded(IDS.incrementAndGet(), visit, elder, at(TUESDAY_NINE),
				at(TUESDAY_NINE.plusMinutes(10)), "NO_CONCERN", null,
				List.of(new VitalsRecorded.Reading("pulse", "72.00", "bpm", false)));
		ElderConfirmationSubmitted answer = new ElderConfirmationSubmitted(visit, elder, at(TUESDAY_NINE), "CONFIRMED", 5,
				null, at(TUESDAY_NINE.plusHours(3)), null);
		deliver(vitals);
		deliver(vitals);
		deliver(answer);
		deliver(answer);

		ReportFacts week = facts.gather(elder, WEEK).orElseThrow();
		assertThat(week.vitals()).hasSize(1);
		assertThat(week.quality().confirmations()).hasSize(1);
	}

	/**
	 * Demonstration data goes straight into the databases and sends no events, so core's
	 * db/demo/report-facts.sql copies its facts in: visit's from visit's own database on the same server,
	 * core's from core's. Running it again refreshes the copy without adding to it, and an event older
	 * than the copy changes nothing.
	 */
	@Test
	void demonstrationDataReachesReportThroughItsCopyStep() {
		VisitDatabaseForDemoData.build(new JdbcTemplate(dataSource));
		long visit = insert("insert into " + VISIT_DATABASE + ".visit (elder_id, caregiver_id, service_type, scheduled_start, scheduled_end,"
				+ " checked_in_at, checked_out_at, status, version) values (" + elder + ", " + caregiver
				+ ", 'Personal care', ?, ?, ?, ?, 'COMPLETED', 3)", ts(TUESDAY_NINE), ts(TUESDAY_NINE.plusHours(1)),
				ts(TUESDAY_NINE.plusMinutes(5)), ts(TUESDAY_NINE.plusHours(1)));
		jdbc.sql("insert into " + VISIT_DATABASE + ".visit_evidence (visit_id, kind, reference, verification_status) values"
				+ " (:visit, 'PHOTO', 'copy/1', 'VERIFIED'), (:visit, 'NOTE', 'copy/2', 'UNVERIFIED')")
				.param("visit", visit).update();
		jdbc.sql("insert into " + VISIT_DATABASE + ".visit_task (visit_id, name, status, caregiver_note, completed_at)"
				+ " values (:visit, 'Mobility', 'DONE', 'Walked with the cane.', :at)")
				.param("visit", visit).param("at", ts(TUESDAY_NINE.plusMinutes(30))).update();
		jdbc.sql("insert into " + VISIT_DATABASE + ".vital_sign (visit_id, metric, value, unit, out_of_range, recorded_at)"
				+ " values (:visit, 'systolic', 131.00, 'mmHg', false, :at), (:visit, 'pulse', 70.00, 'bpm', false, :at)")
				.param("visit", visit).param("at", ts(TUESDAY_NINE.plusMinutes(10))).update();
		jdbc.sql("insert into " + VISIT_DATABASE + ".elder_confirmation (visit_id, elder_id, confirmation_status, rating, confirmed_at)"
				+ " values (:visit, :elder, 'CONFIRMED', 4, :at)")
				.param("visit", visit).param("elder", elder).param("at", ts(TUESDAY_NINE.plusHours(2))).update();
		long incident = insert("insert into incident (elder_id, visit_id, source, category, severity, status, description,"
				+ " reported_at) values (" + elder + ", " + visit + ", 'CAREGIVER', 'FALL', 'LOW', 'OPEN', 'Tripped', ?)",
				ts(TUESDAY_NINE.plusMinutes(15)));
		jdbc.sql("insert into incident_log (incident_id, actor, action, detail, occurred_at)"
				+ " values (:incident, 'system', 'REPORTED', null, :at)")
				.param("incident", incident).param("at", ts(TUESDAY_NINE.plusMinutes(15))).update();

		copyTheDemonstrationFacts();
		copyTheDemonstrationFacts();
		// visit's event of version 1, still on its way when the copy was made
		deliver(new VisitCheckedIn(visit, elder, at(TUESDAY_NINE), caregiver, at(TUESDAY_NINE.plusMinutes(7)), false, 1));

		ReportFacts week = facts.gather(elder, WEEK).orElseThrow();
		assertThat(week.visits()).singleElement().satisfies(fact -> {
			assertThat(fact.status()).isEqualTo(VisitFact.Status.COMPLETED);
			assertThat(fact.workedMinutes()).isEqualTo(55);
			assertThat(fact.evidenceCount()).isEqualTo(2);
			assertThat(fact.verifiedEvidenceCount()).isEqualTo(1);
		});
		assertThat(week.vitals()).extracting(VitalFact::metric).containsExactly("systolic", "pulse");
		assertThat(week.observations()).singleElement().satisfies(note -> assertThat(note.note()).isEqualTo("Walked with the cane."));
		assertThat(week.quality().confirmations()).singleElement().satisfies(answer -> assertThat(answer.rating()).isEqualTo(4));
		assertThat(week.incidents()).singleElement().satisfies(fact -> {
			assertThat(fact.description()).isEqualTo("Tripped");
			assertThat(fact.timeline()).extracting(IncidentFact.Step::action).containsExactly("REPORTED");
		});
	}

	// ------------------------------------------------------------------ helpers ---

	/** How the library hands an event to its handler: in a transaction, with the envelope's metadata. */
	private void deliver(Object event) {
		deliver(event, ++sequence);
	}

	@SuppressWarnings("unchecked")
	private void deliver(Object event, long sequenceNumber) {
		EventHandler<Object> handler = (EventHandler<Object>) handlers.stream()
				.filter(candidate -> candidate.payloadType().equals(event.getClass()))
				.findFirst()
				.orElseThrow(() -> new AssertionError("report has no handler for " + event.getClass().getSimpleName()));
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> handler.handle(event,
				new EventMetadata(UUID.randomUUID().toString(), handler.type(), "visit", Instant.now(), sequenceNumber)));
	}

	/**
	 * The demonstration step as it runs on a real server, except that report's tables are this test's
	 * database rather than one called carelink_report, and core's are the copy beside them.
	 */
	private void copyTheDemonstrationFacts() {
		DemoData.load(new JdbcTemplate(dataSource), "report-facts.sql", "carelink_report");
	}

	private Long caregiver(String name) {
		Long user = insert("insert into app_user (username, password_hash, display_name, enabled) values (?,"
				+ " '{noop}unused-here', ?, true)", "it-events-" + UUID.randomUUID(), name);
		return insert("insert into caregiver (user_id, full_name, status) values (" + user + ", ?, 'AVAILABLE')", name);
	}

	private Long insert(String sql, Object... args) {
		GeneratedKeyHolder key = new GeneratedKeyHolder();
		JdbcClient.StatementSpec statement = jdbc.sql(sql);
		for (Object arg : args) {
			statement = statement.param(arg);
		}
		statement.update(key);
		return key.getKeyAs(Number.class).longValue();
	}

	/** A business time as an event carries it. */
	private static OffsetDateTime at(LocalDateTime wallClock) {
		return SingaporeTime.of(wallClock);
	}

	/** How the services hand a time to the driver when they write their own tables. */
	private static Timestamp ts(LocalDateTime wallClock) {
		return Timestamp.valueOf(wallClock);
	}

}
