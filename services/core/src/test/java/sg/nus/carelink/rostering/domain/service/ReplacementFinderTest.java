package sg.nus.carelink.rostering.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.rostering.domain.model.AbsenceReport;
import sg.nus.carelink.rostering.domain.model.RosteringCandidateCheck;
import sg.nus.carelink.rostering.domain.model.RosteringRun;
import sg.nus.carelink.rostering.domain.model.VacatedSlot;
import sg.nus.carelink.shared.error.BusinessRuleViolation;

/**
 * UC-MG04 step 3: who can take a vacated visit, and in what order. One roster, seven caregivers,
 * each failing a different rule or not at all, so every rule is seen excluding somebody and the
 * survivors are seen ranked by each objective.
 */
class ReplacementFinderTest {

	private static final LocalDate DAY = LocalDate.of(2026, 10, 8);
	private static final LocalDateTime NINE = DAY.atTime(9, 0);
	private static final Long NURSING = 2L;

	/** Visit 30: elder 7's personal care, 09:00-09:45, vacated by caregiver 5. */
	private static final VacatedSlot SLOT = new VacatedSlot(30L, 7L, 4L, "Personal care", NINE, NINE.plusMinutes(45), 5L);

	@Test
	void everyRuleExcludesSomebodyAndTheRestAreRankedByContinuity() {
		Shortlist shortlist = finder(RosteringRun.Objective.CONTINUITY).shortlist(SLOT, roster(0));

		assertThat(shortlist.objective()).isEqualTo(RosteringRun.Objective.CONTINUITY);
		assertThat(excludedBy(shortlist)).containsExactlyInAnyOrderEntriesOf(Map.of(
				5L, ReplacementRules.NOT_ON_LEAVE,
				6L, ReplacementRules.CERTIFICATION_VALID,
				8L, ReplacementRules.NO_TIME_CLASH,
				11L, ReplacementRules.CERTIFICATION_VALID,
				12L, ReplacementRules.DAILY_VISIT_CAP,
				13L, ReplacementRules.DAILY_HOURS_CAP));
		assertThat(reason(shortlist, 5L)).isEqualTo("On leave 2026-10-07 to 2026-10-09");
		assertThat(reason(shortlist, 6L)).isEqualTo("No Nursing certificate");
		assertThat(reason(shortlist, 8L)).isEqualTo("Booked 09:30-10:30 with another elder");
		assertThat(reason(shortlist, 11L)).isEqualTo("Still onboarding: no certificate published yet");
		assertThat(reason(shortlist, 12L)).isEqualTo("9 / 8 visits");
		assertThat(reason(shortlist, 13L)).isEqualTo("8.8 / 8.0 hours");

		assertThat(shortlist.ranked()).extracting(Shortlist.Verdict::caregiverId).containsExactly(9L, 10L);
		Shortlist.Verdict best = shortlist.best().orElseThrow();
		assertThat(best.rank()).isEqualTo(1);
		assertThat(best.score()).isEqualByComparingTo("81.00");
		assertThat(best.reason()).isEqualTo("Has visited this elder 3 times before");
		assertThat(shortlist.ranked().get(1).score()).isEqualByComparingTo("40.00");
		assertThat(shortlist.ranked().get(1).reason()).isEqualTo("Free at that time");
		assertThat(shortlist.isFeasible(9L)).isTrue();
		assertThat(shortlist.isFeasible(5L)).isFalse();
		assertThat(shortlist.top(1)).hasSize(1);
		assertThat(shortlist.top(5)).hasSize(2);
		assertThat(shortlist.all()).hasSize(8);
		assertThat(shortlist.isEmpty()).isFalse();
	}

	@Test
	void everyRuleIsRunForEveryCandidateSoEveryReasonIsKept() {
		Shortlist shortlist = finder(RosteringRun.Objective.CONTINUITY).shortlist(SLOT, roster(0));

		Shortlist.Verdict onLeave = verdict(shortlist, 5L);
		assertThat(onLeave.checks()).hasSize(9);
		assertThat(onLeave.checks()).extracting(RuleCheck::code).containsExactly(ReplacementRules.NOT_ON_LEAVE,
				ReplacementRules.CERTIFICATION_VALID, ReplacementRules.NO_TIME_CLASH, ReplacementRules.DAILY_VISIT_CAP,
				ReplacementRules.DAILY_HOURS_CAP, ReplacementRules.CONTINUITY, ReplacementRules.SECTOR_BAND,
				ReplacementRules.DIALECT_MATCH, ReplacementRules.SPOT_CHECK);

		Shortlist.Verdict knowsTheElder = verdict(shortlist, 9L);
		assertThat(detail(knowsTheElder, ReplacementRules.CERTIFICATION_VALID)).isEqualTo("Nursing valid to 2027-01-01");
		assertThat(detail(knowsTheElder, ReplacementRules.NO_TIME_CLASH)).isEqualTo("Free 09:00-09:45");
		assertThat(detail(knowsTheElder, ReplacementRules.CONTINUITY)).isEqualTo("3 earlier visits to this elder");
		assertThat(detail(knowsTheElder, ReplacementRules.SECTOR_BAND)).isEqualTo("Works in Toa Payoh");
		assertThat(detail(knowsTheElder, ReplacementRules.DIALECT_MATCH)).isEqualTo("Speaks hokkien");
		assertThat(detail(knowsTheElder, ReplacementRules.SPOT_CHECK)).isEqualTo("Recent spot checks met the standard");

		Shortlist.Verdict stranger = verdict(shortlist, 10L);
		assertThat(detail(stranger, ReplacementRules.CONTINUITY)).isEqualTo("Has not visited this elder before");
		assertThat(detail(stranger, ReplacementRules.SECTOR_BAND)).isEqualTo("Works in Bishan, the elder is in Toa Payoh");
		assertThat(detail(stranger, ReplacementRules.DIALECT_MATCH)).isEqualTo("No shared dialect");
		assertThat(detail(stranger, ReplacementRules.SPOT_CHECK)).isEqualTo("1 recent spot check needed improvement");
		assertThat(detail(stranger, ReplacementRules.CERTIFICATION_VALID)).isEqualTo("Nursing does not expire");
		assertThat(detail(verdict(shortlist, 12L), ReplacementRules.SPOT_CHECK)).isEqualTo("No recent spot check");
	}

	@Test
	void theObjectiveDecidesWhoComesFirst() {
		RosterSnapshot busyDay = roster(2);

		Shortlist continuity = finder(RosteringRun.Objective.CONTINUITY).shortlist(SLOT, busyDay);
		assertThat(continuity.ranked()).extracting(Shortlist.Verdict::caregiverId).containsExactly(9L, 10L);
		assertThat(continuity.best().orElseThrow().score()).isEqualByComparingTo("77.00");

		Shortlist evenWorkload = finder(RosteringRun.Objective.EVEN_WORKLOAD).shortlist(SLOT, busyDay);
		assertThat(evenWorkload.objective()).isEqualTo(RosteringRun.Objective.EVEN_WORKLOAD);
		assertThat(evenWorkload.ranked()).extracting(Shortlist.Verdict::caregiverId).containsExactly(9L, 10L);
		assertThat(verdict(evenWorkload, 9L).score()).isEqualByComparingTo("44.00");
		assertThat(verdict(evenWorkload, 9L).reason()).isEqualTo("Lighter day: 2 other visits");
		assertThat(verdict(evenWorkload, 10L).score()).isEqualByComparingTo("40.00");
		assertThat(verdict(evenWorkload, 10L).reason()).isEqualTo("Has no other visit that day");

		Shortlist travel = finder(RosteringRun.Objective.TRAVEL_TIME).shortlist(SLOT, busyDay);
		assertThat(travel.best().orElseThrow().reason()).isEqualTo("Already works in the elder's sector");
		assertThat(verdict(travel, 9L).score()).isEqualByComparingTo("66.00");
		assertThat(verdict(travel, 10L).reason()).isEqualTo("Free at that time, from another sector");
	}

	@Test
	void withoutTheSpotCheckConcernTheLighterDayWinsAnEvenWorkloadRun() {
		RosterSnapshot snapshot = base(2, false).build();

		Shortlist evenWorkload = finder(RosteringRun.Objective.EVEN_WORKLOAD).shortlist(SLOT, snapshot);

		assertThat(evenWorkload.ranked()).extracting(Shortlist.Verdict::caregiverId).containsExactly(10L, 9L);
		assertThat(evenWorkload.best().orElseThrow().score()).isEqualByComparingTo("50.00");
	}

	@Test
	void reasonsFallBackInOrderWhenTheFirstSignalIsMissing() {
		CandidateCard speaker = CandidateCard.of(20L, "Wei Ling", "Bishan", "Hokkien", false);
		CandidateCard neighbour = CandidateCard.of(21L, "Farah", "Toa Payoh", "Malay", false);
		RosterSnapshot snapshot = RosterSnapshot.builder()
				.candidate(speaker).candidate(neighbour)
				.elder(ElderCard.of(7L, "Mdm Tan", "Toa Payoh", "Hokkien", Map.of()))
				.build();

		Shortlist continuity = finder(RosteringRun.Objective.CONTINUITY).shortlist(SLOT, snapshot);
		assertThat(verdict(continuity, 20L).reason()).isEqualTo("Speaks the elder's dialect");
		assertThat(verdict(continuity, 21L).reason()).isEqualTo("Works in the elder's sector");

		ElderCard knownTo21 = ElderCard.of(7L, "Mdm Tan", "Clementi", null, Map.of(21L, 1));
		Shortlist travel = finder(RosteringRun.Objective.TRAVEL_TIME)
				.shortlist(SLOT, RosterSnapshot.builder().candidate(neighbour).elder(knownTo21).build());
		assertThat(travel.best().orElseThrow().reason()).isEqualTo("Has visited this elder before");
		assertThat(detail(travel.best().orElseThrow(), ReplacementRules.CONTINUITY)).isEqualTo("1 earlier visit to this elder");
		assertThat(detail(travel.best().orElseThrow(), ReplacementRules.DIALECT_MATCH))
				.isEqualTo("The elder has no preferred dialect");

		Shortlist once = finder(RosteringRun.Objective.CONTINUITY)
				.shortlist(SLOT, RosterSnapshot.builder().candidate(neighbour).elder(knownTo21).build());
		assertThat(once.best().orElseThrow().reason()).isEqualTo("Has visited this elder 1 time before");
	}

	@Test
	void tiesGoToTheLighterDayThenTheLowerId() {
		CandidateCard a = CandidateCard.of(31L, "A", null, null, false);
		CandidateCard b = CandidateCard.of(30L, "B", null, null, false);
		CandidateCard c = CandidateCard.of(32L, "C", null, null, false);
		RosterSnapshot snapshot = RosterSnapshot.builder().candidate(a).candidate(b).candidate(c)
				.bookings(List.of(new Booking(90L, 8L, 30L, NINE.plusHours(3), NINE.plusHours(4))))
				.build();

		Shortlist shortlist = new ReplacementFinder(List.of(), ScoringObjective.of(RosteringRun.Objective.TRAVEL_TIME))
				.shortlist(SLOT, snapshot);

		assertThat(shortlist.ranked()).extracting(Shortlist.Verdict::caregiverId).containsExactly(31L, 32L, 30L);
		assertThat(shortlist.ranked()).extracting(Shortlist.Verdict::rank).containsExactly(1, 2, 3);
	}

	@Test
	void anElderWithNothingRecordedLeavesTheSoftRulesNotApplicable() {
		RosterSnapshot snapshot = RosterSnapshot.builder()
				.candidate(CandidateCard.of(20L, null, null, null, false))
				.build();

		Shortlist shortlist = finder(RosteringRun.Objective.CONTINUITY)
				.shortlist(new VacatedSlot(31L, 99L, null, null, NINE, null, null), snapshot);

		Shortlist.Verdict only = shortlist.best().orElseThrow();
		assertThat(only.name()).isEqualTo("Caregiver #20");
		assertThat(result(only, ReplacementRules.SECTOR_BAND)).isEqualTo(RosteringCandidateCheck.Result.NOT_APPLICABLE);
		assertThat(result(only, ReplacementRules.DIALECT_MATCH)).isEqualTo(RosteringCandidateCheck.Result.NOT_APPLICABLE);
		assertThat(result(only, ReplacementRules.CERTIFICATION_VALID))
				.isEqualTo(RosteringCandidateCheck.Result.NOT_APPLICABLE);
		assertThat(detail(only, ReplacementRules.DAILY_HOURS_CAP)).isEqualTo("1.0 / 8.0 hours");
		assertThat(snapshot.elder(99L).name()).isEqualTo("Elder #99");
	}

	@Test
	void anExpiredCertificateIsNamedWithTheDayItLapsed() {
		RosterSnapshot snapshot = RosterSnapshot.builder()
				.candidate(CandidateCard.of(20L, "Wei Ling", null, null, false))
				.requiredTypes(Map.of(4L, Set.of(NURSING)))
				.typeNames(Map.of(NURSING, "Nursing"))
				.cover(20L, NURSING, DAY.minusDays(7))
				.build();

		Shortlist shortlist = finder(RosteringRun.Objective.CONTINUITY).shortlist(SLOT, snapshot);

		assertThat(shortlist.isEmpty()).isTrue();
		assertThat(shortlist.best()).isEmpty();
		assertThat(shortlist.excluded().get(0).reason()).isEqualTo("Nursing expired 2026-10-01");
	}

	@Test
	void theTableSwitchesRulesOffAndSetsTheirThresholds() {
		List<ReplacementRule> rules = ReplacementRules.from(List.of(
				constraint(1L, ReplacementRules.DAILY_VISIT_CAP, "2", true),
				constraint(2L, ReplacementRules.NOT_ON_LEAVE, null, false),
				constraint(3L, "TRAVEL_DISTANCE", "5", true),
				constraint(4L, ReplacementRules.DAILY_HOURS_CAP, "not a number", true),
				constraint(5L, ReplacementRules.SPOT_CHECK, "90", true)));

		assertThat(rules).extracting(ReplacementRule::code)
				.containsExactly(ReplacementRules.DAILY_VISIT_CAP, ReplacementRules.DAILY_HOURS_CAP, ReplacementRules.SPOT_CHECK);
		RosterSnapshot twoBooked = RosterSnapshot.builder()
				.candidate(CandidateCard.of(20L, "Wei Ling", null, null, false))
				.bookings(List.of(new Booking(91L, 8L, 20L, NINE.plusHours(2), null),
						new Booking(92L, 8L, 20L, NINE.plusHours(4), null)))
				.build();
		Shortlist shortlist = new ReplacementFinder(rules, ScoringObjective.of(null)).shortlist(SLOT, twoBooked);
		assertThat(shortlist.excluded()).extracting(Shortlist.Verdict::reason).containsExactly("3 / 2 visits");
		assertThat(detail(shortlist.excluded().get(0), ReplacementRules.DAILY_HOURS_CAP)).isEqualTo("2.8 / 8.0 hours");
	}

	@Test
	void aThresholdTypedWrongFallsBackToTheDefault() {
		assertThat(ReplacementRules.parseInt("12", 8)).isEqualTo(12);
		assertThat(ReplacementRules.parseInt("0", 8)).isEqualTo(8);
		assertThat(ReplacementRules.parseInt(null, 8)).isEqualTo(8);
		assertThat(ReplacementRules.parseInt("eight", 8)).isEqualTo(8);
		assertThat(ReplacementRules.parseDecimal("6.5", BigDecimal.TEN)).isEqualByComparingTo("6.5");
		assertThat(ReplacementRules.parseDecimal("-1", BigDecimal.TEN)).isEqualByComparingTo("10");
		assertThat(ReplacementRules.parseDecimal(null, BigDecimal.TEN)).isEqualByComparingTo("10");
	}

	@Test
	void costIsRefusedRatherThanQuietlyRankedBySomethingElse() {
		assertThatThrownBy(() -> ScoringObjective.of(RosteringRun.Objective.COST))
				.isInstanceOf(BusinessRuleViolation.class)
				.extracting("code").isEqualTo("OBJECTIVE_NOT_SUPPORTED");
		assertThat(ScoringObjective.of(null).objective()).isEqualTo(RosteringRun.Objective.CONTINUITY);
		assertThat(ScoringObjective.bounded(140)).isEqualByComparingTo("100.00");
		assertThat(ScoringObjective.bounded(-3)).isEqualByComparingTo("0.00");
	}

	@Test
	void theSnapshotKeepsTheLaterCoverAndTracksReservations() {
		RosterSnapshot snapshot = RosterSnapshot.builder()
				.cover(20L, NURSING, DAY.plusDays(10))
				.cover(20L, NURSING, DAY.plusDays(3))
				.bookings(List.of(new Booking(30L, 7L, 5L, NINE, NINE.plusMinutes(45))))
				.build();

		assertThat(snapshot.coveredUntil(20L, NURSING)).contains(DAY.plusDays(10));
		assertThat(snapshot.coveredUntil(21L, NURSING)).isEmpty();
		assertThat(snapshot.typeName(77L)).isEqualTo("certificate type 77");
		assertThat(snapshot.requiredTypes(null)).isEmpty();
		assertThat(snapshot.elderClash(7L, NINE.plusMinutes(30), NINE.plusHours(1), 99L)).isPresent();
		assertThat(snapshot.elderClash(7L, NINE.plusMinutes(30), NINE.plusHours(1), 30L)).isEmpty();

		snapshot.reserve(Booking.of(SLOT, 9L));
		assertThat(snapshot.clash(9L, NINE.plusMinutes(10), NINE.plusMinutes(20), 99L)).isPresent();
		assertThat(snapshot.clash(5L, NINE, NINE.plusMinutes(20), 99L)).isEmpty();
		assertThat(snapshot.bookingsOn(9L, DAY, null)).hasSize(1);
	}

	@Test
	void bookingsAndCardsAreTolerantOfWhatProfileStores() {
		Booking open = new Booking(1L, 7L, 9L, NINE, null);
		assertThat(open.end()).isEqualTo(NINE.plus(VacatedSlot.DEFAULT_LENGTH));
		assertThat(open.minutes()).isEqualTo(60);
		assertThat(open.overlaps(NINE.plusHours(1), NINE.plusHours(2))).isFalse();

		assertThat(CandidateCard.of(9L, "Aisha", null, " Hokkien ; teochew/ ,ENGLISH", false).dialects())
				.containsExactlyInAnyOrder("hokkien", "teochew", "english");
		assertThat(new CandidateCard(9L, null, null, null, true).dialects()).isEmpty();
		assertThat(ElderCard.unknown(7L).priorVisitsBy(9L)).isZero();
		assertThat(SpotCheckRecord.NONE.isEmpty()).isTrue();
		assertThatThrownBy(() -> new SpotCheckRecord(-1, 0)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void aLongDetailIsCutToTheColumn() {
		RuleCheck check = RuleCheck.fail(new ReplacementRules.SpotCheck(), "x".repeat(140));

		assertThat(check.detail()).hasSize(RuleCheck.DETAIL_LENGTH).endsWith("...");
		assertThat(check.excludes()).isFalse();
		assertThat(check.failed()).isTrue();
	}

	// ------------------------------------------------------------------ the roster ---

	private static ReplacementFinder finder(RosteringRun.Objective objective) {
		return new ReplacementFinder(ReplacementRules.defaults(), ScoringObjective.of(objective));
	}

	/**
	 * Caregivers 5 to 13, one per rule: 5 is the absent one, 6 has no Nursing certificate, 8 is
	 * booked at nine-thirty, 11 is still onboarding, 12 already has eight visits that day, 13
	 * already has eight hours. 9 knows the elder, speaks her dialect and works in her sector; 10
	 * is a stranger from another sector whose last spot check needed improvement.
	 *
	 * @param nineOthers how many other visits caregiver 9 already has that day, none clashing
	 */
	private static RosterSnapshot roster(int nineOthers) {
		return base(nineOthers, true).build();
	}

	private static RosterSnapshot.Builder base(int nineOthers, boolean withSpotChecks) {
		List<Booking> bookings = new ArrayList<>();
		bookings.add(new Booking(30L, 7L, 5L, NINE, NINE.plusMinutes(45)));
		bookings.add(new Booking(40L, 8L, 8L, NINE.plusMinutes(30), NINE.plusMinutes(90)));
		for (int i = 0; i < 8; i++) {
			bookings.add(new Booking(50L + i, 8L, 12L, DAY.atTime(10 + i, 0), DAY.atTime(10 + i, 30)));
		}
		bookings.add(new Booking(60L, 8L, 13L, DAY.atTime(10, 0), DAY.atTime(14, 0)));
		bookings.add(new Booking(61L, 8L, 13L, DAY.atTime(14, 0), DAY.atTime(18, 0)));
		for (int i = 0; i < nineOthers; i++) {
			bookings.add(new Booking(70L + i, 8L, 9L, DAY.atTime(13 + i, 0), DAY.atTime(13 + i, 30)));
		}
		RosterSnapshot.Builder builder = RosterSnapshot.builder()
				.candidates(List.of(
						CandidateCard.of(13L, "Ong", "Toa Payoh", null, false),
						CandidateCard.of(5L, "Aisha", "Toa Payoh", "Hokkien", false),
						CandidateCard.of(6L, "Ben", "Toa Payoh", null, false),
						CandidateCard.of(8L, "David", "Toa Payoh", null, false),
						CandidateCard.of(9L, "Farah", "Toa Payoh", "Hokkien, English", false),
						CandidateCard.of(10L, "Siti", "Bishan", "English", false),
						CandidateCard.of(11L, "Kumar", "Toa Payoh", null, true),
						CandidateCard.of(12L, "Lim", "Toa Payoh", null, false)))
				.elder(ElderCard.of(7L, "Mdm Tan", "Toa Payoh", "Hokkien", Map.of(9L, 3)))
				.requiredTypes(Map.of(4L, Set.of(NURSING)))
				.typeNames(Map.of(NURSING, "Nursing"))
				.absences(List.of(AbsenceReport.recordedByManager(5L, AbsenceReport.Type.SICK, DAY.minusDays(1),
						DAY.plusDays(1), "flu", 11L, DAY.minusDays(1))))
				.bookings(bookings)
				.spotChecks(withSpotChecks
						? Map.of(9L, new SpotCheckRecord(2, 0), 10L, new SpotCheckRecord(0, 1))
						: Map.of());
		for (Long caregiver : List.of(5L, 8L, 11L, 12L, 13L)) {
			builder.cover(caregiver, NURSING, DAY.plusMonths(6));
		}
		builder.cover(9L, NURSING, LocalDate.of(2027, 1, 1));
		builder.cover(10L, NURSING, LocalDate.of(9999, 12, 31));
		return builder;
	}

	private static sg.nus.carelink.rostering.domain.model.RosteringConstraint constraint(Long id, String code,
			String parameter, boolean enabled) {
		return new sg.nus.carelink.rostering.domain.model.RosteringConstraint(id, code, code, null, parameter, enabled);
	}

	private static Map<Long, String> excludedBy(Shortlist shortlist) {
		Map<Long, String> codes = new java.util.HashMap<>();
		shortlist.excluded().forEach(verdict -> codes.put(verdict.caregiverId(), verdict.excludedBy()));
		return codes;
	}

	private static Shortlist.Verdict verdict(Shortlist shortlist, Long caregiverId) {
		return shortlist.all().stream().filter(v -> v.caregiverId().equals(caregiverId)).findFirst().orElseThrow();
	}

	private static String reason(Shortlist shortlist, Long caregiverId) {
		return verdict(shortlist, caregiverId).reason();
	}

	private static String detail(Shortlist.Verdict verdict, String code) {
		return verdict.checks().stream().filter(c -> c.code().equals(code)).findFirst().orElseThrow().detail();
	}

	private static RosteringCandidateCheck.Result result(Shortlist.Verdict verdict, String code) {
		return verdict.checks().stream().filter(c -> c.code().equals(code)).findFirst().orElseThrow().result();
	}
}
