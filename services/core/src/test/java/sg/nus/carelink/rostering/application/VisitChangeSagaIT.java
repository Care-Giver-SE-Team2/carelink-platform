package sg.nus.carelink.rostering.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import sg.nus.carelink.rostering.domain.model.AbsenceReport;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.ResourceNotFound;
import sg.nus.carelink.testsupport.SharedMySql;
import sg.nus.carelink.testsupport.VisitStandIn;
import sg.nus.carelink.testsupport.VisitStandIn.Fault;
import sg.nus.carelink.testsupport.VisitStandIn.Held;

/**
 * The re-rostering saga against real MySQL, with visit failing on purpose: unreachable, answering 503,
 * making a change and losing the answer, refusing, or missing the visit.
 *
 * <p>Each test is a clean comparison. The same institution is laid out twice - the same people, visits,
 * absence and decisions, with the clock at 09:00 in Singapore each time - once with visit well and once
 * with the one fault; after the faulty run has caught up (the retry sweep driven by hand, the clock wound
 * on to each next try), its end state is compared with the first run's: who has each visit and in what
 * state, each roster change's status, outcome and decider, who was told what (as a list, not in order),
 * how many incidents, and every change visit made. Only the fault differs.
 */
@SpringBootTest
@Import(AbsenceReRosteringIT.MovableClockConfig.class)
@TestPropertySource(properties = {
		// The sweeps are driven by hand here; keep the real schedulers out of the way.
		"carelink.rerostering.scan-initial-delay=PT1H",
		"carelink.escalation.scan-initial-delay=PT1H",
		"carelink.roster.uncovered-scan-initial-delay=PT1H",
		// Its incidents for visits on leave would count against the runs' incidents
		"carelink.roster.leave-reminder-initial-delay=PT1H"
})
class VisitChangeSagaIT {

	private static final Instant NINE_SGT = Instant.parse("2026-10-07T01:00:00Z");
	private static final ZoneId SINGAPORE = ZoneId.of("Asia/Singapore");
	private static final LocalDate TODAY = LocalDate.of(2026, 10, 7);
	private static final LocalDate TOMORROW = TODAY.plusDays(1);
	private static final LocalDateTime MOVED_TO = TOMORROW.plusDays(1).atTime(10, 0);

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		SharedMySql.register(registry, VisitChangeSagaIT.class, null);
	}

	@Autowired
	private AbsenceService absences;
	@Autowired
	private AbsenceReRosteringService reRostering;
	@Autowired
	private VisitChangeStepScanService retries;
	@Autowired
	private RosterChangeScanService defaultPlans;
	@Autowired
	private AbsenceReRosteringIT.MovableClock clock;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private VisitStandIn visits;

	// ------------------------------------------------------------------ T1: visit down mid-run ---

	/**
	 * T1: a re-roster leaves three visits uncovered, and visit cannot be reached for the k-th. The request
	 * answers as usual; the first k-1 are done and their people told; the k-th and after wait, with
	 * nobody told; once visit is back and the sweep has run, it is as if nothing had failed.
	 */
	@ParameterizedTest(name = "visit {0} on change {1} of 3")
	@CsvSource({"UNAVAILABLE, 1", "UNAVAILABLE, 2", "SERVER_ERROR, 3"})
	void aRerosterVisitCannotReachIsCaughtUpAsIfNothingHadFailed(Fault fault, int k) {
		Outcome control = nobodyFree(this::rerosterNow);

		Outcome caughtUp = nobodyFree(world -> {
			visits.failChanges(fault, k, 1);

			AbsenceReRosteringService.ReRosterOutcome outcome = rerosterNow(world);

			assertThat(outcome.uncovered()).as("the request answers as usual").isEqualTo(3);
			assertThat(world.visitStates()).containsExactly(firstUncovered(k, 0), firstUncovered(k, 1),
					firstUncovered(k, 2));
			assertThat(count("select count(*) from roster_change where absence_id = ?", world.absence))
					.isEqualTo(k - 1);
			assertThat(count("select count(*) from incident where elder_id = ?", world.elder)).isEqualTo(k - 1);
			assertThat(notifications(world.familyAccount, "ROSTER_CHANGE_COORDINATING"))
					.as("nobody hears of a change visit has not made").isEqualTo(k - 1);
			assertThat(world.steps()).containsExactlyElementsOf(stepsAfterFailure(k));

			catchUp(world);
		});

		assertThat(control.changes()).hasSize(3).allSatisfy(change -> assertThat(change).endsWith(" UNCOVERED none"));
		assertThat(control.incidents()).isEqualTo(3);
		assertThat(control.madeByVisit()).hasSize(3);
		assertThat(control.notifications()).contains("family ROSTER_CHANGE_COORDINATING", "aisha VISIT_RELEASED");
		assertThat(caughtUp).isEqualTo(control);
	}

	/** T1 for the family's three answers: each sent while visit is down, each made once it is back. */
	@Test
	void theFamilysAnswersSentWhileVisitIsDownEndAsIfItHadBeenUp() {
		Outcome control = threeOffers(world -> {
			keep(world, 0);
			skip(world, 1);
			move(world, 2);
		});

		Outcome caughtUp = threeOffers(world -> {
			visits.failChanges(Fault.UNAVAILABLE, 1, 3);

			keep(world, 0);
			skip(world, 1);
			move(world, 2);

			assertThat(world.changeStates()).as("nothing settled while visit is down").containsOnly("AWAITING_FAMILY");
			assertThat(world.visitStates()).containsOnly("SCHEDULED aisha");
			assertThat(notifications(world.familyAccount, "ROSTER_CHANGE_SETTLED")).isZero();
			assertThat(notifications(world.userOf(world.farah), "VISIT_ASSIGNED")).isZero();
			assertThat(world.steps()).containsExactly("PENDING 1", "PENDING 1", "PENDING 1");

			catchUp(world);
		});

		assertThat(control.changes()).hasSize(4).anySatisfy(change -> assertThat(change).contains("SKIPPED FAMILY"))
				.anySatisfy(change -> assertThat(change).contains("RESCHEDULED FAMILY"))
				.anySatisfy(change -> assertThat(change).endsWith("AWAITING_FAMILY none"));
		assertThat(control.madeByVisit()).extracting(made -> made.split(" ")[1])
				.containsExactlyInAnyOrder("REASSIGN", "CALL_OFF", "MOVE");
		assertThat(control.notifications()).contains("family ROSTER_CHANGE_SETTLED", "farah VISIT_ASSIGNED",
				"elder VISIT_CHANGED");
		assertThat(caughtUp).isEqualTo(control);
		assertThat(caughtUp.visits()).as("Farah's visit last month, the three, and the one moved to").hasSize(5);
	}

	/** R5: while a change to a visit is under way, nothing else is decided for that visit. */
	@Test
	void aVisitBeingChangedTakesNoOtherDecision() {
		threeOffers(world -> {
			visits.failChanges(Fault.UNAVAILABLE, 1, 1);
			keep(world, 0);
			Long change = world.changeOf(0);

			assertThatThrownBy(() -> reRostering.decide(change, world.family, FamilyChoice.skip()))
					.isInstanceOfSatisfying(BusinessRuleViolation.class,
							refused -> assertThat(refused.code()).isEqualTo("CHANGE_IN_PROGRESS"));
			assertThat(rerosterNow(world).searched()).as("re-rostering leaves it alone").isZero();
			clock.advance(Duration.ofHours(2));
			defaultPlans.sweep();
			assertThat(world.steps()).as("one change, not a second").containsExactly("PENDING 1", "DONE 0", "DONE 0");

			catchUp(world);
			assertThat(world.changeStates()).first().isEqualTo("RESOLVED REPLACED FAMILY farah");
		});
	}

	// ------------------------------------------------------------------ T2: made, answer lost ---

	/**
	 * T2: visit makes the change and its answer is lost. The retry carries the same key, visit makes the
	 * change once - one record, one hand-over - and a move answers the same new visit.
	 */
	@Test
	void aChangeWhoseAnswerWasLostIsMadeOnceAndAMoveKeepsItsNewVisit() {
		Outcome control = threeOffers(world -> {
			keep(world, 0);
			move(world, 2);
		});

		Outcome caughtUp = threeOffers(world -> {
			visits.failChanges(Fault.TIMED_OUT_AFTER_CHANGE, 1, 2);
			Long kept = world.visitIds.get(0);
			Long moving = world.visitIds.get(2);

			keep(world, 0);
			move(world, 2);

			assertThat(visits.current(kept).caregiverId()).as("visit has made it").isEqualTo(world.farah);
			assertThat(world.changeStates()).as("core waits to hear").containsOnly("AWAITING_FAMILY");
			assertThat(notifications(world.familyAccount, "ROSTER_CHANGE_SETTLED")).isZero();

			catchUp(world);

			assertThat(visits.requestKeysOf(kept)).hasSize(2).containsOnly(visits.requestKeysOf(kept).get(0));
			assertThat(visits.askedOf(kept)).hasSize(1);
			assertThat(visits.requestKeysOf(moving)).hasSize(2).containsOnly(visits.requestKeysOf(moving).get(0));
			assertThat(visits.askedOf(moving)).singleElement().extracting(VisitStandIn.Asked::what).isEqualTo("MOVE");
			Long newVisit = jdbc.queryForObject("select rescheduled_visit_id from roster_change where visit_id = ?",
					Long.class, moving);
			assertThat(jdbc.queryForObject("select result_visit_id from visit_change_step where visit_id = ?",
					Long.class, moving)).isEqualTo(newVisit);
			assertThat(visits.ofElder(world.elder)).extracting(Held::id).containsOnlyOnce(newVisit).hasSize(5);
		});

		assertThat(control.madeByVisit()).hasSize(2);
		assertThat(caughtUp).isEqualTo(control);
	}

	// ------------------------------------------------------------------ T3: refused ---

	/**
	 * T3, answered at once: visit refuses the family's choice (409) or does not have the visit (404). The
	 * family's request gets visit's answer, nothing is settled or announced, the reason is kept, and the
	 * visit is free for their next answer - which, once visit takes it, ends as it would have.
	 */
	@Test
	void aRefusalTheFamilyIsWaitingForIsTheirAnswerAndFreesTheVisit() {
		Outcome control = threeOffers(world -> keep(world, 0));

		Outcome refusedFirst = threeOffers(world -> {
			Long change = world.changeOf(0);
			String family = world.family;
			FamilyChoice keep = FamilyChoice.keepSuggestion();
			visits.failChanges(Fault.REFUSED, 1, 1);
			assertThatThrownBy(() -> reRostering.decide(change, family, keep))
					.isInstanceOfSatisfying(BusinessRuleViolation.class,
							refused -> assertThat(refused.code()).isEqualTo("VISIT_NOT_OPEN"));
			visits.failChanges(Fault.MISSING, 1, 1);
			assertThatThrownBy(() -> reRostering.decide(change, family, keep))
					.isInstanceOf(ResourceNotFound.class);

			assertThat(world.changeStates()).containsOnly("AWAITING_FAMILY");
			assertThat(world.steps()).containsExactly("REJECTED 0", "REJECTED 0");
			assertThat(jdbc.queryForList("select last_error from visit_change_step where absence_id = ? order by id",
					String.class, world.absence)).satisfiesExactly(
							error -> assertThat(error).startsWith("VISIT_NOT_OPEN"),
							error -> assertThat(error).startsWith("ResourceNotFound"));
			assertThat(notifications(world.familyAccount, "ROSTER_CHANGE_NOT_MADE")).as("the answer told them")
					.isZero();
			assertThat(notifications(world.familyAccount, "ROSTER_CHANGE_SETTLED")).isZero();

			keep(world, 0);
		});

		assertThat(control.changes()).first().asString().endsWith("RESOLVED REPLACED FAMILY farah");
		assertThat(refusedFirst).isEqualTo(control);
	}

	/**
	 * T3, after the family has had their answer: their choice waits for visit, the visit starts meanwhile,
	 * and visit refuses the retry. It is not tried again; the visit is free again, and the family is told
	 * their choice did not go ahead.
	 */
	@Test
	void aRefusalAfterTheFamilyHasGoneIsToldToThemAndNotRetried() {
		threeOffers(world -> {
			Long started = world.visitIds.get(0);
			visits.failChanges(Fault.UNAVAILABLE, 1, 1);
			keep(world, 0);
			visits.add(visits.current(started).withStatus("IN_PROGRESS"));

			catchUp(world);
			clock.advance(Duration.ofMinutes(30));
			retries.sweep();

			assertThat(world.steps()).containsExactly("REJECTED 1");
			assertThat(visits.requestKeysOf(started)).as("tried, and tried again once").hasSize(2);
			assertThat(visits.askedOf(started)).isEmpty();
			assertThat(world.changeStates()).containsOnly("AWAITING_FAMILY");
			assertThat(notifications(world.familyAccount, "ROSTER_CHANGE_NOT_MADE")).isEqualTo(1);
			assertThat(notifications(world.familyAccount, "ROSTER_CHANGE_SETTLED")).isZero();
		});
	}

	// ------------------------------------------------------------------ T4: given up ---

	/**
	 * T4: visit stays down through every retry. The step is given up, a manager gets an incident - once
	 * visit answers again, since raising one asks visit who last visited the elder - and the absence cannot
	 * be confirmed; the manager's next re-roster sends it again with the same key, and it ends as it would
	 * have, with the extra incident.
	 */
	@Test
	void retriesRunOutAManagerTakesOverAndReRosteringSendsTheChangeAgain() {
		Outcome control = oneNobodyFree(world -> {
			rerosterNow(world);
			reRostering.confirmCoverage(world.absence, world.manager);
		});

		Outcome sentAgain = oneNobodyFree(world -> {
			Long visit = world.visitIds.get(0);
			visits.failChanges(Fault.UNAVAILABLE, 1, 1);
			rerosterNow(world);
			visits.unreachable(true);

			catchUp(world);

			assertThat(world.steps()).containsExactly("ABANDONED 6");
			assertThat(clock.instant()).as("given up 38 minutes in").isEqualTo(NINE_SGT.plus(Duration.ofMinutes(38)));
			assertThat(count("select count(*) from incident where elder_id = ?", world.elder))
					.as("visit is down, so the incident waits for it").isZero();
			visits.unreachable(false);
			retries.sweep();
			assertThat(jdbc.queryForObject("select description from incident where elder_id = ?", String.class,
					world.elder)).contains("could not be reached to leave it uncovered after 6 tries");
			assertThat(jdbc.queryForObject("select incident_id from visit_change_step where absence_id = ?",
					Long.class, world.absence)).isNotNull();
			assertThat(visits.current(visit).status()).isEqualTo("SCHEDULED");
			assertThatThrownBy(() -> reRostering.confirmCoverage(world.absence, world.manager))
					.isInstanceOfSatisfying(BusinessRuleViolation.class,
							refused -> assertThat(refused.code()).isEqualTo("CHANGE_IN_PROGRESS"));
			clock.advance(Duration.ofHours(1));
			retries.sweep();
			assertThat(visits.requestKeysOf(visit)).as("the sweep leaves a given-up step alone").hasSize(6);

			rerosterNow(world);

			assertThat(visits.requestKeysOf(visit)).hasSize(7).containsOnly(visits.requestKeysOf(visit).get(0));
			assertThat(world.steps()).containsExactly("DONE 0");
			reRostering.confirmCoverage(world.absence, world.manager);
		});

		assertThat(control.incidents()).isEqualTo(1);
		assertThat(control.changes()).singleElement().asString().endsWith("UNCOVERED none");
		assertThat(sentAgain.incidents()).isEqualTo(control.incidents() + 1);
		assertThat(sentAgain.withoutIncidents()).isEqualTo(control.withoutIncidents());
	}

	/** T4 early: a change for a visit less than an hour away is given up on its first failure. */
	@Test
	void aChangeForAVisitDueWithinTheHourIsGivenUpAtOnce() {
		World world = world();
		Long soon = world.visit(world.elder, world.aisha, TODAY.atTime(9, 30));
		world.absence = world.absent(world.aisha, TODAY);
		visits.failChanges(Fault.UNAVAILABLE, 1, 1);

		AbsenceReRosteringService.ReRosterOutcome outcome = rerosterNow(world);

		assertThat(outcome.settled()).as("too soon to ask the family: the default plan").isEqualTo(1);
		assertThat(world.steps()).containsExactly("ABANDONED 1");
		assertThat(visits.current(soon).caregiverId()).isEqualTo(world.aisha);
		assertThat(count("select count(*) from incident where elder_id = ? and visit_id = ?", world.elder, soon))
				.isEqualTo(1);
		assertThat(notifications(world.familyAccount, "ROSTER_CHANGE_SETTLED")).isZero();
	}

	// ------------------------------------------------------------------ the two runs ---

	/** Aisha is off tomorrow and has three visits; Farah and Siti are off too, so nobody is free. */
	private Outcome nobodyFree(Consumer<World> run) {
		World world = world();
		for (int hour : new int[] {9, 11, 14}) {
			world.visitIds.add(world.visit(world.elder, world.aisha, TOMORROW.atTime(hour, 0)));
		}
		world.absent(world.farah, TOMORROW);
		world.absent(world.siti, TOMORROW);
		world.absence = world.absent(world.aisha, TOMORROW);
		run.accept(world);
		return world.outcome();
	}

	/** Aisha is off tomorrow and has one visit; nobody else is free. */
	private Outcome oneNobodyFree(Consumer<World> run) {
		World world = world();
		world.visitIds.add(world.visit(world.elder, world.aisha, TOMORROW.atTime(9, 0)));
		world.absent(world.farah, TOMORROW);
		world.absent(world.siti, TOMORROW);
		world.absence = world.absent(world.aisha, TOMORROW);
		run.accept(world);
		return world.outcome();
	}

	/** Aisha is off tomorrow and has three visits; a re-roster offers each to the family. */
	private Outcome threeOffers(Consumer<World> run) {
		World world = world();
		for (int hour : new int[] {9, 11, 14}) {
			world.visitIds.add(world.visit(world.elder, world.aisha, TOMORROW.atTime(hour, 0)));
		}
		world.absence = world.absent(world.aisha, TOMORROW);
		assertThat(rerosterNow(world).offered()).isEqualTo(3);
		run.accept(world);
		return world.outcome();
	}

	private AbsenceReRosteringService.ReRosterOutcome rerosterNow(World world) {
		return reRostering.reroster(world.absence, null, world.manager);
	}

	private void keep(World world, int visit) {
		reRostering.decide(world.changeOf(visit), world.family, FamilyChoice.keepSuggestion());
	}

	private void skip(World world, int visit) {
		reRostering.decide(world.changeOf(visit), world.family, FamilyChoice.skip());
	}

	private void move(World world, int visit) {
		reRostering.decide(world.changeOf(visit), world.family, FamilyChoice.moveTo(MOVED_TO));
	}

	/** Winds the clock on to each of the run's changes' next try and sweeps, until none waits for visit. */
	private void catchUp(World world) {
		for (int sweep = 0; sweep < 10; sweep++) {
			Timestamp next = jdbc.queryForObject("select min(next_attempt_at) from visit_change_step"
					+ " where status = 'PENDING' and absence_id = ?", Timestamp.class, world.absence);
			if (next == null) {
				return;
			}
			Instant due = next.toLocalDateTime().atZone(SINGAPORE).toInstant();
			if (due.isAfter(clock.instant())) {
				clock.set(due);
			}
			retries.sweep();
		}
		throw new AssertionError("the sweep did not catch up");
	}

	/** Three visits uncovered in turn, the k-th one failing: the earlier are done, the rest wait. */
	private static String firstUncovered(int k, int visit) {
		return visit < k - 1 ? "EXCEPTION none" : "SCHEDULED aisha";
	}

	private static List<String> stepsAfterFailure(int k) {
		List<String> steps = new ArrayList<>();
		for (int visit = 0; visit < 3; visit++) {
			steps.add(visit < k - 1 ? "DONE 0" : visit == k - 1 ? "PENDING 1" : "PENDING 0");
		}
		return steps;
	}

	private long notifications(Long userId, String eventType) {
		return count("select count(*) from requested_notification where recipient_user_id = ? and event_type = ?",
				userId, eventType);
	}

	private long count(String sql, Object... args) {
		Long rows = jdbc.queryForObject(sql, Long.class, args);
		return rows == null ? 0L : rows;
	}

	private Long lastId() {
		return jdbc.queryForObject("select last_insert_id()", Long.class);
	}

	// ------------------------------------------------------------------ one run's institution ---

	/**
	 * The end state one run is compared on: each of the elder's visits (when, its state, who has it), each
	 * roster change (when, status, outcome, decider, who has it), who was told what, how many incidents the
	 * elder has, and every change visit made - people by role, so two runs compare.
	 */
	private record Outcome(List<String> visits, List<String> changes, List<String> notifications, long incidents,
			List<String> madeByVisit) {

		/** The same, leaving out incidents and what people are told about them: what re-rostering itself did. */
		Outcome withoutIncidents() {
			return new Outcome(visits, changes, notifications.stream()
					.filter(line -> line.contains(" ROSTER_CHANGE_") || line.contains(" VISIT_")).toList(), 0,
					madeByVisit);
		}
	}

	private World world() {
		visits.clear();
		clock.set(NINE_SGT);
		return new World();
	}

	/** The run's people, absence and visits; ids from both runs are named by role. */
	private final class World {

		private final Map<Long, String> caregiverRoles = new HashMap<>();
		private final Map<Long, String> userRoles = new HashMap<>();
		private final List<Long> visitIds = new ArrayList<>();
		private final Long manager;
		private final Long elder;
		private final Long familyAccount;
		private final String family;
		private final Long aisha;
		private final Long farah;
		private final Long siti;
		private Long absence;

		World() {
			// The search draws on every caregiver who has not left, so the other run's staff leave first.
			jdbc.update("update caregiver set status = 'INACTIVE'");
			String run = Long.toString(System.nanoTime(), 36);
			manager = account("mgr-" + run, "MANAGER", "manager");
			Long elderAccount = account("elder-" + run, "ELDER", "elder");
			jdbc.update("insert into elder (user_id, full_name, sector, preferred_dialects) values (?, 'Mdm Tan',"
					+ " 'Toa Payoh', 'Hokkien')", elderAccount);
			elder = lastId();
			family = "family-" + run;
			familyAccount = account(family, "FAMILY", "family");
			jdbc.update("insert into family_member (user_id, full_name) values (?, 'Alex Tan')", familyAccount);
			jdbc.update("insert into elder_family_binding (elder_id, family_member_id, relationship, access_scope,"
					+ " status, confirmed_at) values (?, ?, 'SON', 'FULL', 'ACTIVE', ?)", elder, lastId(),
					Timestamp.valueOf(LocalDateTime.of(2026, 9, 1, 9, 0)));
			aisha = caregiver("aisha-" + run, "Aisha", "Toa Payoh", "Malay", "aisha");
			farah = caregiver("farah-" + run, "Farah", "Toa Payoh", "Hokkien, English", "farah");
			siti = caregiver("siti-" + run, "Siti", "Bishan", "English", "siti");
			visits.add(Held.scheduled(elder, farah, LocalDateTime.of(2026, 9, 30, 9, 0),
					LocalDateTime.of(2026, 9, 30, 9, 45)).withStatus("COMPLETED"));
		}

		Long visit(Long elderId, Long caregiverId, LocalDateTime start) {
			return visits.add(Held.scheduled(elderId, caregiverId, start, start.plusMinutes(45)));
		}

		Long absent(Long caregiverId, LocalDate day) {
			return absences.recordForCaregiver(caregiverId, AbsenceReport.Type.SICK, day, day, "flu", manager).id();
		}

		Long changeOf(int visit) {
			return jdbc.queryForObject("select id from roster_change where visit_id = ?", Long.class,
					visitIds.get(visit));
		}

		Long userOf(Long caregiverId) {
			return jdbc.queryForObject("select user_id from caregiver where id = ?", Long.class, caregiverId);
		}

		List<String> visitStates() {
			return visitIds.stream().map(id -> visits.current(id).status() + " " + role(visits.current(id).caregiverId()))
					.toList();
		}

		List<String> changeStates() {
			return jdbc.query("select status, outcome, decided_by, assigned_caregiver_id from roster_change"
					+ " where absence_id = ? order by visit_start, id", (row, number) -> String.join(" ",
							nonNull(row.getString("status"), row.getString("outcome"), row.getString("decided_by"),
									row.getString("outcome") == null ? null
											: role(row.getObject("assigned_caregiver_id", Long.class)))),
					absence);
		}

		List<String> steps() {
			return jdbc.query("select status, attempts from visit_change_step where absence_id = ? order by id",
					(row, number) -> row.getString("status") + " " + row.getInt("attempts"), absence);
		}

		Outcome outcome() {
			List<String> visitLines = visits.ofElder(elder).stream()
					.map(visit -> visit.start() + " " + visit.status() + " " + role(visit.caregiverId()))
					.toList();
			List<String> changeLines = jdbc.query("select visit_start, status, outcome, decided_by,"
					+ " assigned_caregiver_id from roster_change where absence_id = ? order by visit_start, id",
					(row, number) -> row.getTimestamp("visit_start").toLocalDateTime() + " " + String.join(" ",
							nonNull(row.getString("status"), row.getString("outcome"), row.getString("decided_by"),
									role(row.getObject("assigned_caregiver_id", Long.class)))),
					absence);
			List<String> told = jdbc.query("select recipient_user_id, event_type from requested_notification",
					(row, number) -> userRoles.containsKey(row.getLong(1))
							? userRoles.get(row.getLong(1)) + " " + row.getString(2) : null)
					.stream().filter(Objects::nonNull).sorted().toList();
			List<String> made = new ArrayList<>();
			for (Held visit : visits.ofElder(elder)) {
				for (VisitStandIn.Asked asked : visits.askedOf(visit.id())) {
					made.add(visit.start() + " " + asked.what() + " " + role(asked.toCaregiverId()));
				}
			}
			return new Outcome(visitLines, changeLines, told, count("select count(*) from incident where elder_id = ?",
					elder), made.stream().sorted().toList());
		}

		private String role(Long caregiverId) {
			return caregiverId == null ? "none" : caregiverRoles.getOrDefault(caregiverId, "caregiver " + caregiverId);
		}

		private Long account(String username, String role, String name) {
			jdbc.update("insert into app_user (username, password_hash, display_name, enabled) values (?, '{noop}x', ?,"
					+ " true)", username, username);
			Long id = lastId();
			jdbc.update("insert into user_role (user_id, role) values (?, ?)", id, role);
			userRoles.put(id, name);
			return id;
		}

		private Long caregiver(String username, String name, String sector, String dialects, String roleName) {
			Long user = account(username, "CAREGIVER", roleName);
			jdbc.update("insert into caregiver (user_id, full_name, sector, dialects, status) values (?, ?, ?, ?,"
					+ " 'AVAILABLE')", user, name, sector, dialects);
			Long id = lastId();
			caregiverRoles.put(id, roleName);
			return id;
		}

		private static String[] nonNull(String... parts) {
			return Arrays.stream(parts).filter(Objects::nonNull).toArray(String[]::new);
		}
	}
}
