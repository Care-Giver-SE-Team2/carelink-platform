package sg.nus.carelink.rostering.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import sg.nus.carelink.rostering.domain.model.AbsenceReport;
import sg.nus.carelink.rostering.domain.model.RosterChange;
import sg.nus.carelink.testsupport.SharedMySql;

/**
 * UC-MG04 end to end on real MySQL: V12's tables and seeded rules, the lock on a change, the
 * visit and its assignment history, the notifications to all three ends, the audit of a default
 * plan, and an uncovered visit's incident - everything the in-memory test cannot see.
 *
 * <p>Aisha is off sick tomorrow. She had two visits with Mdm Tan; Farah knows Mdm Tan, speaks her
 * dialect and works in her sector; Siti does not. It is 09:00 in Singapore.
 */
@SpringBootTest
@Import(AbsenceReRosteringIT.MovableClockConfig.class)
@TestPropertySource(properties = {
		// The sweeps are driven by hand here; keep the real schedulers out of the way.
		"carelink.rerostering.scan-initial-delay=PT1H",
		"carelink.escalation.scan-initial-delay=PT1H",
		"carelink.roster.uncovered-scan-initial-delay=PT1H"
})
class AbsenceReRosteringIT {

	private static final Instant NINE_SGT = Instant.parse("2026-10-07T01:00:00Z");
	private static final LocalDate TOMORROW = LocalDate.of(2026, 10, 8);

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		SharedMySql.register(registry, AbsenceReRosteringIT.class, null);
	}

	@Autowired
	private AbsenceService absences;
	@Autowired
	private AbsenceReRosteringService reRostering;
	@Autowired
	private AbsenceQueryService queries;
	@Autowired
	private RosterChangeScanService scan;
	@Autowired
	private MovableClock clock;
	@Autowired
	private JdbcTemplate jdbc;

	private Long manager;
	private Long elder;
	private Long elderAccount;
	private Long familyAccount;
	private String familyUsername;
	private Long aisha;
	private Long farah;
	private Long siti;

	@BeforeEach
	void givenTheInstitution() {
		clock.set(NINE_SGT);
		// The search draws on every caregiver who has not left, so the other test's staff leave first.
		jdbc.update("update caregiver set status = 'INACTIVE'");
		String run = Long.toString(System.nanoTime(), 36);
		manager = account("mgr-" + run, "MANAGER");
		elderAccount = account("elder-" + run, "ELDER");
		jdbc.update("insert into elder (user_id, full_name, sector, preferred_dialects) values (?, 'Mdm Tan', 'Toa Payoh', 'Hokkien')",
				elderAccount);
		elder = lastId();
		familyUsername = "family-" + run;
		familyAccount = account(familyUsername, "FAMILY");
		jdbc.update("insert into family_member (user_id, full_name) values (?, 'Alex Tan')", familyAccount);
		jdbc.update("insert into elder_family_binding (elder_id, family_member_id, relationship, access_scope, status,"
				+ " confirmed_at) values (?, ?, 'SON', 'FULL', 'ACTIVE', ?)", elder, lastId(),
				Timestamp.valueOf(LocalDateTime.of(2026, 9, 1, 9, 0)));
		aisha = caregiver("aisha-" + run, "Aisha", "Toa Payoh", "Malay");
		farah = caregiver("farah-" + run, "Farah", "Toa Payoh", "Hokkien, English");
		siti = caregiver("siti-" + run, "Siti", "Bishan", "English");
		visit(farah, LocalDateTime.of(2026, 9, 30, 9, 0), "COMPLETED");
	}

	@Test
	void anAbsenceIsReRosteredAnsweredDefaultedAndConfirmed() {
		Long morning = visit(aisha, TOMORROW.atTime(9, 0), "SCHEDULED");
		Long afternoon = visit(aisha, TOMORROW.atTime(14, 0), "SCHEDULED");
		AbsenceReport absence = absences.recordForCaregiver(aisha, AbsenceReport.Type.SICK, TOMORROW, TOMORROW, "flu",
				manager);

		AbsenceReRosteringService.ReRosterOutcome outcome = reRostering.reroster(absence.id(), null, manager);

		assertThat(outcome.searched()).isEqualTo(2);
		assertThat(outcome.offered()).isEqualTo(2);
		assertThat(count("select count(*) from roster_change where absence_id = ? and status = 'AWAITING_FAMILY'",
				absence.id())).isEqualTo(2);
		assertThat(jdbc.queryForObject("select proposed_caregiver_id from roster_change where visit_id = ?", Long.class,
				morning)).isEqualTo(farah);
		assertThat(count("select count(*) from rostering_run where absence_id = ? and status = 'COMMITTED'"
				+ " and trigger_type = 'ABSENCE'", absence.id())).isEqualTo(1);
		assertThat(count("select count(*) from rostering_candidate where visit_id = ? and outcome = 'EXCLUDED'"
				+ " and excluded_by_code = 'NOT_ON_LEAVE'", morning)).isEqualTo(1);
		assertThat(count("select count(*) from rostering_candidate_check k join rostering_candidate c"
				+ " on c.id = k.rostering_candidate_id where c.visit_id = ?", morning)).isEqualTo(27);
		assertThat(jdbc.queryForObject("select match_reason from rostering_candidate where visit_id = ? and option_rank = 1",
				String.class, morning)).isEqualTo("Has visited this elder 1 time before");
		assertThat(notifications(familyAccount, "ROSTER_CHANGE_OFFERED")).isEqualTo(2);
		assertThat(queries.caseOf(absence.id()).changes().get(0).candidates().get(0).checks().get(0).name())
				.isEqualTo("Not on approved leave that day");

		Long morningChange = jdbc.queryForObject("select id from roster_change where visit_id = ?", Long.class, morning);
		RosterChange kept = reRostering.decide(morningChange, familyUsername, FamilyChoice.keepSuggestion());

		assertThat(kept.decidedBy()).isEqualTo(RosterChange.DecidedBy.FAMILY);
		assertThat(jdbc.queryForObject("select caregiver_id from visit where id = ?", Long.class, morning)).isEqualTo(farah);
		assertThat(jdbc.queryForObject("select absence_id from visit where id = ?", Long.class, morning))
				.isEqualTo(absence.id());
		assertThat(jdbc.queryForList("select status from visit_assignment where visit_id = ? order by id", String.class,
				morning)).containsExactly("REPLACED", "ACTIVE");
		assertThat(count("select count(*) from visit_assignment where visit_id = ? and status = 'ACTIVE'"
				+ " and rostering_candidate_id is not null", morning)).isEqualTo(1);
		assertThat(notifications(userOf(farah), "VISIT_ASSIGNED")).isEqualTo(1);
		assertThat(notifications(userOf(aisha), "VISIT_RELEASED")).isEqualTo(1);
		assertThat(notifications(elderAccount, "VISIT_CHANGED")).isEqualTo(1);
		assertThat(notifications(familyAccount, "ROSTER_CHANGE_SETTLED")).isEqualTo(1);
		assertThat(queries.forFamily(familyUsername)).hasSize(2);

		clock.advance(Duration.ofHours(2));
		assertThat(scan.sweep()).isEqualTo(1);

		assertThat(jdbc.queryForObject("select decided_by from roster_change where visit_id = ?", String.class, afternoon))
				.isEqualTo("DEFAULT_PLAN");
		assertThat(jdbc.queryForObject("select caregiver_id from visit where id = ?", Long.class, afternoon))
				.isIn(farah, siti);
		assertThat(count("select count(*) from audit_log where action = 'DEFAULT_PLAN_APPLIED' and resource_type ="
				+ " 'roster_change' and resource_id = (select id from roster_change where visit_id = ?)", afternoon))
				.isEqualTo(1);

		AbsenceReport confirmed = reRostering.confirmCoverage(absence.id(), manager);
		assertThat(confirmed.coverageConfirmedAt()).isNotNull();
		assertThat(absences.require(absence.id()).coverageConfirmedByUserId()).isEqualTo(manager);
	}

	@Test
	void nobodyFreeRaisesAnIncidentAndAFamilyMayMoveAVisit() {
		LocalDate dayAfter = TOMORROW.plusDays(1);
		Long uncoveredVisit = visit(aisha, dayAfter.atTime(10, 0), "SCHEDULED");
		Long movedVisit = visit(aisha, dayAfter.atTime(16, 0), "SCHEDULED");
		jdbc.update("insert into elder (full_name) values ('Mr Ong')");
		visit(lastId(), farah, dayAfter.atTime(10, 0), "SCHEDULED");
		AbsenceReport away = absences.recordForCaregiver(aisha, AbsenceReport.Type.EMERGENCY, dayAfter, dayAfter, null,
				manager);
		absences.recordForCaregiver(siti, AbsenceReport.Type.ANNUAL, dayAfter, dayAfter, null, manager);

		AbsenceReRosteringService.ReRosterOutcome outcome = reRostering.reroster(away.id(), null, manager);

		assertThat(outcome.uncovered()).as("Aisha and Siti are away, Farah is with Mr Ong at ten").isEqualTo(1);
		assertThat(outcome.offered()).isEqualTo(1);
		assertThat(jdbc.queryForObject("select status from visit where id = ?", String.class, uncoveredVisit))
				.isEqualTo("EXCEPTION");
		Long incident = jdbc.queryForObject("select incident_id from roster_change where visit_id = ?", Long.class,
				uncoveredVisit);
		assertThat(count("select count(*) from incident where id = ? and visit_id = ? and source = 'SYSTEM_MISSED_CHECKIN'"
				+ " and responder_user_id is not null", incident, uncoveredVisit)).isEqualTo(1);
		assertThat(notifications(familyAccount, "ROSTER_CHANGE_COORDINATING")).isEqualTo(1);

		Long change = jdbc.queryForObject("select id from roster_change where visit_id = ?", Long.class, movedVisit);
		LocalDateTime later = dayAfter.plusDays(1).atTime(9, 0);
		RosterChange moved = reRostering.decide(change, familyUsername, FamilyChoice.moveTo(later));

		assertThat(moved.outcome()).isEqualTo(RosterChange.Outcome.RESCHEDULED);
		assertThat(jdbc.queryForObject("select status from visit where id = ?", String.class, movedVisit))
				.isEqualTo("CANCELLED");
		assertThat(jdbc.queryForObject("select caregiver_id from visit where id = ?", Long.class,
				moved.rescheduledVisitId())).isEqualTo(moved.assignedCaregiverId());
		assertThat(queries.forFamily(familyUsername, change).rescheduledStart()).isEqualTo(later);

		// Back to step 3 for the new time: a change of its own, with the suggestions filed under the new visit.
		assertThat(jdbc.queryForObject("select status from roster_change where visit_id = ?", String.class,
				moved.rescheduledVisitId())).isEqualTo("AWAITING_FAMILY");
		assertThat(count("select count(*) from rostering_candidate where visit_id = ? and outcome = 'SUGGESTED'",
				moved.rescheduledVisitId())).isPositive();
		assertThat(notifications(familyAccount, "ROSTER_CHANGE_OFFERED")).isEqualTo(2);
	}

	/** Step 1 for a request the caregiver made themselves: every manager hears of it in their inbox. */
	@Test
	void aCaregiversOwnRequestReachesTheManagers() {
		String username = jdbc.queryForObject("select u.username from app_user u join caregiver c on c.user_id = u.id"
				+ " where c.id = ?", String.class, siti);

		AbsenceReport asked = absences.requestForSelf(username, AbsenceReport.Type.ANNUAL, TOMORROW.plusDays(3),
				TOMORROW.plusDays(3), "family trip");

		assertThat(count("select count(*) from notification where recipient_user_id = ? and event_type = 'ABSENCE_REQUESTED'"
				+ " and resource_type = 'ABSENCE' and resource_id = ?", manager, asked.id())).isEqualTo(1);
	}

	// ------------------------------------------------------------------ the institution ---

	private Long account(String username, String role) {
		jdbc.update("insert into app_user (username, password_hash, display_name, enabled) values (?, '{noop}x', ?, true)",
				username, username);
		Long id = lastId();
		jdbc.update("insert into user_role (user_id, role) values (?, ?)", id, role);
		return id;
	}

	private Long caregiver(String username, String name, String sector, String dialects) {
		Long user = account(username, "CAREGIVER");
		jdbc.update("insert into caregiver (user_id, full_name, sector, dialects, status) values (?, ?, ?, ?, 'AVAILABLE')",
				user, name, sector, dialects);
		return lastId();
	}

	private Long visit(Long caregiverId, LocalDateTime start, String status) {
		return visit(elder, caregiverId, start, status);
	}

	private Long visit(Long elderId, Long caregiverId, LocalDateTime start, String status) {
		jdbc.update("insert into visit (elder_id, caregiver_id, service_type, scheduled_start, scheduled_end, status)"
				+ " values (?, ?, 'Personal care', ?, ?, ?)", elderId, caregiverId, start, start.plusMinutes(45), status);
		return lastId();
	}

	private Long userOf(Long caregiverId) {
		return jdbc.queryForObject("select user_id from caregiver where id = ?", Long.class, caregiverId);
	}

	private long notifications(Long userId, String eventType) {
		return count("select count(*) from notification where recipient_user_id = ? and event_type = ?", userId, eventType);
	}

	private long count(String sql, Object... args) {
		Long rows = jdbc.queryForObject(sql, Long.class, args);
		return rows == null ? 0L : rows;
	}

	private Long lastId() {
		return jdbc.queryForObject("select last_insert_id()", Long.class);
	}

	// ------------------------------------------------------------------ the clock ---

	@TestConfiguration
	static class MovableClockConfig {

		@Bean
		MovableClock movableClock() {
			return new MovableClock(NINE_SGT, ZoneId.of("Asia/Singapore"));
		}

		@Bean
		Clock clock(MovableClock movable) {
			return movable;
		}
	}

	/** A clock the test winds forward, so the family's two hours pass without anybody waiting. */
	static final class MovableClock extends Clock {

		private Instant now;
		private final ZoneId zone;

		MovableClock(Instant start, ZoneId zone) {
			this.now = start;
			this.zone = zone;
		}

		void set(Instant moment) {
			this.now = moment;
		}

		void advance(Duration by) {
			this.now = this.now.plus(by);
		}

		@Override
		public ZoneId getZone() {
			return zone;
		}

		@Override
		public Clock withZone(ZoneId otherZone) {
			return new MovableClock(now, otherZone);
		}

		@Override
		public Instant instant() {
			return now;
		}
	}
}
