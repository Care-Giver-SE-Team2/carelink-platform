package sg.nus.carelink.rostering.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import sg.nus.carelink.identity.application.UserDirectory;
import sg.nus.carelink.identity.domain.model.AppUser;
import sg.nus.carelink.incident.application.IncidentService;
import sg.nus.carelink.incident.domain.model.Incident;
import sg.nus.carelink.incident.support.IncidentFixtures;
import sg.nus.carelink.profile.application.CredentialRegister;
import sg.nus.carelink.rostering.domain.model.AbsenceReport;
import sg.nus.carelink.rostering.domain.model.FamilyResponseWindow;
import sg.nus.carelink.rostering.domain.model.RosterChange;
import sg.nus.carelink.rostering.domain.model.RosteringCandidate;
import sg.nus.carelink.rostering.domain.model.RosteringRun;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.security.Role;

/**
 * UC-MG04 end to end on in-memory ports: an absence vacates three visits, the search offers
 * each to the family, and every way it can end - the family's choice, the default plan, a move,
 * a skip, nobody free - leaves the visit where the use case says and a record of how.
 *
 * <p>The roster: caregiver 5 (Aisha) is off sick on the 8th and 9th. She had visit 30 (Mdm Tan,
 * 8th 09:00), visit 32 (Mr Ong, 8th 14:00) and visit 31 (Mdm Tan, 9th 09:00). Farah (9) has
 * visited Mdm Tan three times, Siti (10) never, Kumar (11) is still onboarding. It is 09:00 on
 * the 7th.
 */
class AbsenceReRosteringServiceTest {

	private static final ZoneId ZONE = ZoneId.of("Asia/Singapore");
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 7, 9, 0);
	private static final LocalDateTime EIGHTH = LocalDateTime.of(2026, 10, 8, 9, 0);
	private static final LocalDate FIRST_DAY = EIGHTH.toLocalDate();

	private final ReRosteringFakes.MutableClock clock = new ReRosteringFakes.MutableClock(NOW, ZONE);
	private final ReRosteringFakes.Absences absences = new ReRosteringFakes.Absences();
	private final ReRosteringFakes.Changes changes = new ReRosteringFakes.Changes();
	private final InMemoryRosteringRunRepository runs = new InMemoryRosteringRunRepository();
	private final ReRosteringFakes.Candidates candidates = new ReRosteringFakes.Candidates();
	private final ReRosteringFakes.Checks checks = new ReRosteringFakes.Checks();
	private final ReRosteringFakes.Constraints constraints = new ReRosteringFakes.Constraints();
	private final ReRosteringFakes.Visits visits = new ReRosteringFakes.Visits();
	private final ReRosteringFakes.Profiles profiles = new ReRosteringFakes.Profiles();
	private final ReRosteringFakes.Family family = new ReRosteringFakes.Family();
	private final ReRosteringFakes.Alerts alerts = new ReRosteringFakes.Alerts();
	private final ReRosteringFakes.Audit audit = new ReRosteringFakes.Audit();
	private final IncidentService incidents = mock(IncidentService.class);
	private final UserDirectory users = mock(UserDirectory.class);
	private final List<CredentialRegister.Cover> covers = new ArrayList<>();
	private final AtomicLong incidentIds = new AtomicLong(900);

	private AbsenceReRosteringService service;
	private RosterChangeScanService scan;
	private Long absenceId;

	@BeforeEach
	void setUp() {
		profiles.caregiver(5L, "Aisha", false).caregiver(9L, "Farah", false).caregiver(10L, "Siti", false)
				.caregiver(11L, "Kumar", true).elder(7L, "Mdm Tan").elder(8L, "Mr Ong");
		for (Long caregiver : List.of(5L, 9L, 10L)) {
			covers.add(new CredentialRegister.Cover(caregiver, caregiver, 2L, LocalDate.of(2027, 6, 30)));
		}
		visits.add(30L, 7L, 5L, EIGHTH, 45)
				.add(31L, 7L, 5L, EIGHTH.plusDays(1), 45)
				.add(32L, 8L, 5L, EIGHTH.withHour(14), 60)
				.add(33L, 7L, 6L, EIGHTH.withHour(13), 60);
		visits.history.put(7L, Map.of(9L, 3, 5L, 5));

		RosterSnapshotLoader loader = new RosterSnapshotLoader(profiles, () -> covers,
				planIds -> Map.of(4L, Set.of(2L)), absences, visits, since -> Map.of(), RosterLookbacks.DEFAULT, clock);
		service = new AbsenceReRosteringService(absences, changes, runs, candidates, checks, constraints, loader, visits,
				profiles, incidents, family, users, alerts, audit, FamilyResponseWindow.DEFAULT, clock);
		scan = new RosterChangeScanService(changes, service, clock);

		absenceId = absences.save(AbsenceReport.recordedByManager(5L, AbsenceReport.Type.SICK, FIRST_DAY,
				FIRST_DAY.plusDays(1), "flu", 11L, NOW.toLocalDate())).id();
		when(users.findByUsername("alex"))
				.thenReturn(Optional.of(new AppUser(41L, "alex", "Alex Tan", Set.of(Role.FAMILY), true)));
		when(incidents.raiseForUnfilledAbsence(any(), any(), any())).thenAnswer(call -> IncidentFixtures.withId(
				Incident.raisedForUncoveredVisit(call.getArgument(0), call.getArgument(1), call.getArgument(2), NOW),
				incidentIds.getAndIncrement()));
	}

	// ------------------------------------------------------------------ steps 2-4 ---

	@Test
	void reRosteringOffersEachVacatedVisitToTheFamilyWithTheBestReplacement() {
		AbsenceReRosteringService.ReRosterOutcome outcome = service.reroster(absenceId, null, 11L);

		assertThat(outcome).isEqualTo(new AbsenceReRosteringService.ReRosterOutcome(absenceId, 1L, 3, 3, 0, 0));
		assertThat(changes.forVisit(30L).proposedCaregiverId()).isEqualTo(9L);
		assertThat(changes.forVisit(31L).proposedCaregiverId()).isEqualTo(9L);
		// Farah is already reserved for the morning, so the lighter day wins Mr Ong's afternoon
		assertThat(changes.forVisit(32L).proposedCaregiverId()).isEqualTo(10L);
		assertThat(changes.rows.values()).hasSize(3).allSatisfy(change -> {
			assertThat(change.status()).isEqualTo(RosterChange.Status.AWAITING_FAMILY);
			assertThat(change.respondBy()).isEqualTo(NOW.plusHours(2));
		});
		assertThat(alerts.sent).containsExactly("offered visit 30", "offered visit 32", "offered visit 31");
		assertThat(alerts.notices.get(0).elderName()).isEqualTo("Mdm Tan");
		assertThat(alerts.notices.get(0).caregiverName()).isEqualTo("Farah");

		RosteringRun run = runs.findById(1L).orElseThrow();
		assertThat(run.status()).isEqualTo(RosteringRun.Status.COMMITTED);
		assertThat(run.triggerType()).isEqualTo(RosteringRun.TriggerType.ABSENCE);
		assertThat(run.visitsTotal()).isEqualTo(3);
		assertThat(run.visitsCovered()).isEqualTo(3);
		assertThat(run.continuityKept()).isEqualTo(2);
		assertThat(candidates.rows).hasSize(12);
		assertThat(checks.rows).hasSize(108);
		assertThat(candidates.findByRunAndVisit(1L, 30L)).extracting(RosteringCandidate::caregiverId)
				.containsExactly(9L, 10L, 5L, 11L);
		assertThat(candidates.findByRunAndVisit(1L, 30L).get(2).excludedByCode()).isEqualTo("NOT_ON_LEAVE");
		assertThat(visits.rows.get(30L).caregiverId()).as("nothing changes until somebody decides").isEqualTo(5L);
	}

	@Test
	void reRosteringAgainLeavesHandledVisitsAlone() {
		service.reroster(absenceId, null, 11L);

		AbsenceReRosteringService.ReRosterOutcome again = service.reroster(absenceId, null, 11L);

		assertThat(again.searched()).isZero();
		assertThat(again.runId()).isNull();
		assertThat(changes.rows).hasSize(3);
	}

	@Test
	void anAbsenceStillWaitingForReviewVacatesNothing() {
		Long requested = absences.save(AbsenceReport.requested(9L, AbsenceReport.Type.ANNUAL, FIRST_DAY, FIRST_DAY,
				null, NOW.toLocalDate())).id();

		assertThatThrownBy(() -> service.reroster(requested, null, 11L))
				.isInstanceOf(BusinessRuleViolation.class)
				.extracting("code").isEqualTo("ABSENCE_NOT_APPROVED");
	}

	@Test
	void theChosenObjectiveIsRecordedAndCostIsRefused() {
		service.reroster(absenceId, RosteringRun.Objective.EVEN_WORKLOAD, 11L);

		assertThat(runs.findById(1L).orElseThrow().objective()).isEqualTo(RosteringRun.Objective.EVEN_WORKLOAD);
		assertThatThrownBy(() -> service.reroster(absenceId, RosteringRun.Objective.COST, 11L))
				.isInstanceOf(BusinessRuleViolation.class);
	}

	@Test
	void aVisitTooCloseToAskTheFamilyGetsTheDefaultPlanAtOnce() {
		clock.set(EIGHTH.minusMinutes(30));

		AbsenceReRosteringService.ReRosterOutcome outcome = service.reroster(absenceId, null, 11L);

		assertThat(outcome.settled()).isEqualTo(1);
		assertThat(outcome.offered()).isEqualTo(2);
		RosterChange settled = changes.forVisit(30L);
		assertThat(settled.decidedBy()).isEqualTo(RosterChange.DecidedBy.DEFAULT_PLAN);
		assertThat(settled.assignedCaregiverId()).isEqualTo(9L);
		assertThat(visits.rows.get(30L).caregiverId()).isEqualTo(9L);
		assertThat(audit.entries).singleElement().asString()
				.startsWith("30: The visit starts too soon to ask the family first");
		assertThat(changes.forVisit(32L).respondBy()).isEqualTo(EIGHTH.minusMinutes(30).plusHours(2));
	}

	@Test
	void nobodyFreeLeavesTheVisitsUncoveredAndAManagerTakesThemUpLater() {
		profiles.candidates.removeIf(c -> c.caregiverId() == 9L || c.caregiverId() == 10L);

		AbsenceReRosteringService.ReRosterOutcome outcome = service.reroster(absenceId, null, 11L);

		assertThat(outcome.uncovered()).isEqualTo(3);
		assertThat(changes.rows.values()).hasSize(3).allSatisfy(change -> {
			assertThat(change.status()).isEqualTo(RosterChange.Status.UNCOVERED);
			assertThat(change.incidentId()).isBetween(900L, 902L);
		});
		assertThat(visits.rows.get(30L).status()).isEqualTo("EXCEPTION");
		assertThat(visits.rows.get(30L).caregiverId()).isNull();
		assertThat(alerts.sent).containsExactly("coordinating visit 30", "coordinating visit 32", "coordinating visit 31");
		verify(incidents).raiseForUnfilledAbsence(eq(7L), eq(30L),
				contains("Personal care visit at 8 Oct 09:00: the caregiver is absent and nobody is free"));

		AbsenceReRosteringService.ReRosterOutcome stillNobody = service.reroster(absenceId, null, 11L);
		assertThat(stillNobody.uncovered()).isEqualTo(3);
		assertThat(changes.forVisit(30L).rosteringRunId()).isEqualTo(2L);

		profiles.caregiver(9L, "Farah", false);
		AbsenceReRosteringService.ReRosterOutcome takenUp = service.reroster(absenceId, null, 12L);

		assertThat(takenUp.settled()).isEqualTo(3);
		RosterChange settled = changes.forVisit(30L);
		assertThat(settled.decidedBy()).isEqualTo(RosterChange.DecidedBy.MANAGER);
		assertThat(settled.decidedByUserId()).isEqualTo(12L);
		assertThat(settled.incidentId()).isEqualTo(900L);
		assertThat(visits.rows.get(30L).status()).isEqualTo("SCHEDULED");
		assertThat(visits.rows.get(30L).caregiverId()).isEqualTo(9L);
	}

	@Test
	void theNightlyRunTakesUpAnUncoveredVisitAsTheDefaultPlanNotInAManagersName() {
		profiles.candidates.removeIf(c -> c.caregiverId() == 9L || c.caregiverId() == 10L);
		service.reroster(absenceId, null, 11L);
		profiles.caregiver(9L, "Farah", false);

		AbsenceReRosteringService.ReRosterOutcome takenUp = service.reroster(absenceId, null, null);

		assertThat(takenUp.settled()).isEqualTo(3);
		RosterChange settled = changes.forVisit(30L);
		assertThat(settled.decidedBy()).isEqualTo(RosterChange.DecidedBy.DEFAULT_PLAN);
		assertThat(settled.decidedByUserId()).isNull();
		assertThat(settled.note()).startsWith("Somebody became free for the uncovered visit");
		assertThat(runs.findById(takenUp.runId()).orElseThrow().requestedByUserId()).isNull();
		assertThat(visits.rows.get(30L).caregiverId()).isEqualTo(9L);
	}

	// ------------------------------------------------------------------ the manager's own pick ---

	@Test
	void aManagerMayHandPickOverTheDefaultPlan() {
		clock.set(EIGHTH.minusMinutes(30));
		service.reroster(absenceId, null, 11L);
		Long change = changes.forVisit(30L).id();
		assertThat(changes.forVisit(30L).assignedCaregiverId()).as("the default plan's pick").isEqualTo(9L);

		RosterChange picked = service.assignByManager(absenceId, change, 10L, 12L);

		assertThat(picked.outcome()).isEqualTo(RosterChange.Outcome.REPLACED);
		assertThat(picked.decidedBy()).isEqualTo(RosterChange.DecidedBy.MANAGER);
		assertThat(picked.decidedByUserId()).isEqualTo(12L);
		assertThat(picked.assignedCaregiverId()).isEqualTo(10L);
		assertThat(picked.note()).isEqualTo("A manager chose Siti for the visit");
		assertThat(visits.rows.get(30L).caregiverId()).isEqualTo(10L);
		assertThat(alerts.sent).endsWith("settled REPLACED visit 30");
		assertThat(alerts.notices.get(alerts.notices.size() - 1).caregiverName()).isEqualTo("Siti");
		assertThat(candidates.findByRunAndVisit(picked.rosteringRunId(), 30L))
				.filteredOn(c -> c.outcome() == RosteringCandidate.Outcome.SELECTED)
				.extracting(RosteringCandidate::caregiverId).containsExactly(10L);

		assertThatThrownBy(() -> service.assignByManager(absenceId, change, 10L, 12L))
				.isInstanceOf(BusinessRuleViolation.class)
				.extracting("code").isEqualTo("ALREADY_ASSIGNED");
		assertThat(service.assignByManager(absenceId, change, 9L, 12L).assignedCaregiverId())
				.as("a manager may change their own pick").isEqualTo(9L);
	}

	@Test
	void aManagerMayNotPickWhileTheFamilyIsChoosingOrOverTheirChoice() {
		service.reroster(absenceId, null, 11L);
		Long deciding = changes.forVisit(30L).id();
		Long chosen = changes.forVisit(31L).id();
		service.decide(chosen, "alex", FamilyChoice.keepSuggestion());

		assertThatThrownBy(() -> service.assignByManager(absenceId, deciding, 10L, 12L))
				.isInstanceOf(BusinessRuleViolation.class)
				.extracting("code").isEqualTo("FAMILY_STILL_DECIDING");
		assertThatThrownBy(() -> service.assignByManager(absenceId, chosen, 10L, 12L))
				.isInstanceOf(BusinessRuleViolation.class)
				.extracting("code").isEqualTo("ROSTER_CHANGE_NOT_OPEN");
		assertThat(visits.rows.get(30L).caregiverId()).isEqualTo(5L);
		assertThat(visits.rows.get(31L).caregiverId()).isEqualTo(9L);
	}

	@Test
	void aHandPickMustStillPassEveryHardRule() {
		clock.set(EIGHTH.minusMinutes(30));
		service.reroster(absenceId, null, 11L);
		Long change = changes.forVisit(30L).id();

		assertThatThrownBy(() -> service.assignByManager(absenceId, change, 5L, 12L))
				.isInstanceOf(BusinessRuleViolation.class)
				.hasMessageStartingWith("Aisha cannot take this visit")
				.extracting("code").isEqualTo("CAREGIVER_CANNOT_TAKE_VISIT");
		assertThatThrownBy(() -> service.assignByManager(absenceId, change, 404L, 12L))
				.isInstanceOf(sg.nus.carelink.shared.error.ResourceNotFound.class);
		assertThatThrownBy(() -> service.assignByManager(absenceId + 1, change, 10L, 12L))
				.as("the change must belong to the absence in the path")
				.isInstanceOf(sg.nus.carelink.shared.error.ResourceNotFound.class);
		assertThat(visits.rows.get(30L).caregiverId()).isEqualTo(9L);

		clock.set(EIGHTH.plusMinutes(1));
		assertThatThrownBy(() -> service.assignByManager(absenceId, change, 10L, 12L))
				.isInstanceOf(BusinessRuleViolation.class)
				.extracting("code").isEqualTo("VISIT_NOT_OPEN");
	}

	@Test
	void aManagerMayHandPickForAnUncoveredVisit() {
		profiles.candidates.removeIf(c -> c.caregiverId() == 9L || c.caregiverId() == 10L);
		service.reroster(absenceId, null, 11L);
		profiles.caregiver(10L, "Siti", false);

		RosterChange picked = service.assignByManager(absenceId, changes.forVisit(30L).id(), 10L, 12L);

		assertThat(picked.decidedBy()).isEqualTo(RosterChange.DecidedBy.MANAGER);
		assertThat(picked.incidentId()).as("the incident stays on record").isEqualTo(900L);
		assertThat(visits.rows.get(30L).status()).isEqualTo("SCHEDULED");
		assertThat(visits.rows.get(30L).caregiverId()).isEqualTo(10L);
	}

	// ------------------------------------------------------------------ steps 5-6 ---

	@Test
	void theFamilyKeepsTheSuggestionAndTheRosterIsRewritten() {
		service.reroster(absenceId, null, 11L);

		RosterChange settled = service.decide(changes.forVisit(30L).id(), "alex", FamilyChoice.keepSuggestion());

		assertThat(settled.outcome()).isEqualTo(RosterChange.Outcome.REPLACED);
		assertThat(settled.decidedBy()).isEqualTo(RosterChange.DecidedBy.FAMILY);
		assertThat(settled.decidedByUserId()).isEqualTo(41L);
		assertThat(settled.note()).isEqualTo("The family kept the suggested replacement");
		assertThat(settled.rosteringRunId()).as("the re-check is a run of its own").isEqualTo(2L);
		assertThat(visits.rows.get(30L).caregiverId()).isEqualTo(9L);
		assertThat(visits.calls).containsExactly("reassign 30 to 9");
		assertThat(candidates.findByRunAndVisit(2L, 30L).get(0).outcome()).isEqualTo(RosteringCandidate.Outcome.SELECTED);
		assertThat(alerts.sent).endsWith("settled REPLACED visit 30");
		assertThat(audit.entries).isEmpty();
	}

	@Test
	void theFamilyMayPickAnotherOfTheOptionsButNotSomebodyElse() {
		service.reroster(absenceId, null, 11L);
		Long change = changes.forVisit(30L).id();

		FamilyChoice onboarding = FamilyChoice.pick(11L);
		assertThatThrownBy(() -> service.decide(change, "alex", onboarding))
				.isInstanceOf(BusinessRuleViolation.class)
				.extracting("code").isEqualTo("NOT_ONE_OF_THE_OPTIONS");

		RosterChange settled = service.decide(change, "alex", FamilyChoice.pick(10L));
		assertThat(settled.assignedCaregiverId()).isEqualTo(10L);
		assertThat(settled.note()).isEqualTo("The family chose another of the suggestions");
	}

	@Test
	void aFamilyMayOnlyAnswerForTheirOwnElderAndOnlyOnce() {
		service.reroster(absenceId, null, 11L);

		Long otherElder = changes.forVisit(32L).id();
		FamilyChoice keep = FamilyChoice.keepSuggestion();
		assertThatThrownBy(() -> service.decide(otherElder, "alex", keep))
				.isInstanceOf(AccessDeniedException.class);

		Long change = changes.forVisit(30L).id();
		service.decide(change, "alex", FamilyChoice.skip());
		assertThatThrownBy(() -> service.decide(change, "alex", keep))
				.isInstanceOf(BusinessRuleViolation.class)
				.hasMessageContaining("SKIPPED by FAMILY")
				.extracting("code").isEqualTo("ROSTER_CHANGE_NOT_OPEN");
	}

	@Test
	void aLateAnswerLosesToTheDefaultPlan() {
		service.reroster(absenceId, null, 11L);
		clock.set(NOW.plusHours(2));

		Long change = changes.forVisit(30L).id();
		FamilyChoice skip = FamilyChoice.skip();
		assertThatThrownBy(() -> service.decide(change, "alex", skip))
				.isInstanceOf(BusinessRuleViolation.class)
				.hasMessageContaining("ended at 7 Oct 11:00")
				.extracting("code").isEqualTo("FAMILY_WINDOW_CLOSED");
	}

	@Test
	void skippingCallsTheVisitOffAsTheFamilysOwnCancellation() {
		service.reroster(absenceId, null, 11L);

		RosterChange skipped = service.decide(changes.forVisit(30L).id(), "alex", FamilyChoice.skip());

		assertThat(skipped.outcome()).isEqualTo(RosterChange.Outcome.SKIPPED);
		assertThat(visits.rows.get(30L).status()).isEqualTo("CANCELLED");
		assertThat(visits.rows.get(30L).absenceId()).isEqualTo(absenceId);
		assertThat(alerts.sent).endsWith("settled SKIPPED visit 30");
	}

	@Test
	void movingTheVisitPastTheLeaveBringsTheUsualCaregiverBack() {
		service.reroster(absenceId, null, 11L);
		LocalDateTime saturday = LocalDateTime.of(2026, 10, 10, 9, 0);

		RosterChange moved = service.decide(changes.forVisit(30L).id(), "alex", FamilyChoice.moveTo(saturday));

		assertThat(moved.outcome()).isEqualTo(RosterChange.Outcome.RESCHEDULED);
		assertThat(moved.assignedCaregiverId()).as("Aisha is back and knows Mdm Tan best").isEqualTo(5L);
		assertThat(moved.rescheduledVisitId()).isEqualTo(500L);
		assertThat(moved.note()).isEqualTo("Moved to 10 Oct 09:00 at the family's request");
		assertThat(visits.rows.get(30L).status()).isEqualTo("CANCELLED");
		assertThat(visits.rows.get(500L).start()).isEqualTo(saturday);
		assertThat(visits.rows.get(500L).end()).isEqualTo(saturday.plusMinutes(45));
		assertThat(alerts.notices.get(alerts.sent.indexOf("settled RESCHEDULED visit 30")).newStart()).isEqualTo(saturday);
	}

	/** Back to step 3 for the new time: the family chooses who comes, as they did the first time. */
	@Test
	void afterAMoveTheFamilyChoosesAgainForTheNewTime() {
		service.reroster(absenceId, null, 11L);
		LocalDateTime saturday = LocalDateTime.of(2026, 10, 10, 9, 0);

		service.decide(changes.forVisit(30L).id(), "alex", FamilyChoice.moveTo(saturday));

		RosterChange offer = changes.forVisit(500L);
		assertThat(offer.status()).isEqualTo(RosterChange.Status.AWAITING_FAMILY);
		assertThat(offer.proposedCaregiverId()).as("the person pencilled in at the new time").isEqualTo(5L);
		assertThat(offer.respondBy()).isAfter(NOW);
		assertThat(alerts.sent).endsWith("settled RESCHEDULED visit 30", "offered visit 500");

		RosterChange kept = service.decide(offer.id(), "alex", FamilyChoice.keepSuggestion());

		assertThat(kept.outcome()).isEqualTo(RosterChange.Outcome.REPLACED);
		assertThat(kept.decidedBy()).isEqualTo(RosterChange.DecidedBy.FAMILY);
		assertThat(visits.rows.get(500L).caregiverId()).isEqualTo(5L);
	}

	@Test
	void aMoveNeedsAFutureTimeTheElderIsFreeAndSomebodyCanCome() {
		service.reroster(absenceId, null, 11L);
		Long change = changes.forVisit(30L).id();

		FamilyChoice noTime = new FamilyChoice(FamilyChoice.Kind.RESCHEDULE, null, null);
		FamilyChoice tooSoon = FamilyChoice.moveTo(NOW.plusMinutes(30));
		FamilyChoice whileBusy = FamilyChoice.moveTo(EIGHTH.withHour(13).plusMinutes(15));
		assertThatThrownBy(() -> service.decide(change, "alex", noTime))
				.extracting("code").isEqualTo("NEW_TIME_REQUIRED");
		assertThatThrownBy(() -> service.decide(change, "alex", tooSoon))
				.extracting("code").isEqualTo("NEW_TIME_TOO_SOON");
		assertThatThrownBy(() -> service.decide(change, "alex", whileBusy))
				.extracting("code").isEqualTo("ELDER_BUSY_THEN");

		LocalDateTime ninthAfternoon = EIGHTH.plusDays(1).withHour(15);
		visits.add(34L, 8L, 9L, ninthAfternoon, 60).add(35L, 8L, 10L, ninthAfternoon, 60);
		FamilyChoice nobodyFree = FamilyChoice.moveTo(ninthAfternoon);
		assertThatThrownBy(() -> service.decide(change, "alex", nobodyFree))
				.isInstanceOf(BusinessRuleViolation.class)
				.extracting("code").isEqualTo("NOBODY_FREE_THEN");
		assertThat(changes.findById(change).orElseThrow().awaitingFamily()).isTrue();
	}

	// ------------------------------------------------------------------ alternative 4a ---

	@Test
	void silenceRunsTheDefaultPlanAndAuditsIt() {
		service.reroster(absenceId, null, 11L);
		clock.set(NOW.plusHours(2));

		assertThat(scan.sweep()).isEqualTo(3);

		assertThat(changes.rows.values()).hasSize(3).allSatisfy(change -> {
			assertThat(change.status()).isEqualTo(RosterChange.Status.RESOLVED);
			assertThat(change.decidedBy()).isEqualTo(RosterChange.DecidedBy.DEFAULT_PLAN);
			assertThat(change.decidedByUserId()).isNull();
		});
		assertThat(visits.rows.get(30L).caregiverId()).isEqualTo(9L);
		assertThat(visits.rows.get(32L).caregiverId()).isEqualTo(10L);
		assertThat(visits.rows.get(31L).caregiverId()).isEqualTo(9L);
		assertThat(audit.entries).hasSize(3);
		assertThat(audit.entries.get(0))
				.isEqualTo("30: No answer from the family by 7 Oct 11:00, so the default plan ran; caregiver 9 assigned to visit 30");
		assertThat(alerts.notices.get(alerts.notices.size() - 1).why()).contains("default plan ran");
		assertThat(scan.sweep()).as("nothing is due twice").isZero();
	}

	@Test
	void theDefaultPlanChecksAgainAndFallsBackWhenTheSuggestionIsNoLongerFree() {
		service.reroster(absenceId, null, 11L);
		absences.save(AbsenceReport.recordedByManager(9L, AbsenceReport.Type.EMERGENCY, FIRST_DAY, FIRST_DAY, null, 11L,
				NOW.toLocalDate()));
		clock.set(NOW.plusHours(2));

		scan.sweep();

		RosterChange fellBack = changes.forVisit(30L);
		assertThat(fellBack.assignedCaregiverId()).isEqualTo(10L);
		assertThat(fellBack.note()).endsWith("Farah was no longer free, so Siti took the visit");
		assertThat(changes.forVisit(31L).assignedCaregiverId()).as("Farah is back on the 9th").isEqualTo(9L);
	}

	@Test
	void whenNobodyIsFreeAnyMoreTheDefaultPlanLeavesTheVisitUncovered() {
		service.reroster(absenceId, null, 11L);
		profiles.candidates.removeIf(c -> c.caregiverId() == 9L || c.caregiverId() == 10L);
		clock.set(NOW.plusHours(2));

		scan.sweep();

		RosterChange uncovered = changes.forVisit(30L);
		assertThat(uncovered.status()).isEqualTo(RosterChange.Status.UNCOVERED);
		assertThat(uncovered.note()).startsWith("No answer from the family").endsWith("so the visit is uncovered");
		assertThat(visits.rows.get(30L).status()).isEqualTo("EXCEPTION");
	}

	@Test
	void aVisitCalledOffElsewhereMeanwhileWithdrawsItsChange() {
		service.reroster(absenceId, null, 11L);
		visits.setStatus(30L, "CANCELLED");
		clock.set(NOW.plusHours(2));

		scan.sweep();

		RosterChange withdrawn = changes.forVisit(30L);
		assertThat(withdrawn.outcome()).isEqualTo(RosterChange.Outcome.WITHDRAWN);
		assertThat(withdrawn.note()).isEqualTo("The visit was cancelled before the change was settled");
		Long alreadySettled = changes.forVisit(31L).id();
		FamilyChoice skip = FamilyChoice.skip();
		assertThatThrownBy(() -> service.decide(alreadySettled, "alex", skip))
				.extracting("code").isEqualTo("ROSTER_CHANGE_NOT_OPEN");
	}

	@Test
	void theDefaultPlanIsNotRunForAChangeTheFamilyHasAlreadyAnswered() {
		service.reroster(absenceId, null, 11L);
		RosterChange answered = service.decide(changes.forVisit(30L).id(), "alex", FamilyChoice.skip());
		clock.set(NOW.plusHours(2));

		assertThat(service.applyDefaultIfStillDue(answered.id())).isFalse();
		assertThat(service.applyDefaultIfStillDue(999L)).isFalse();
	}

	// ------------------------------------------------------------------ step 7 ---

	@Test
	void coverageIsConfirmedOnlyOnceEveryVacatedVisitIsAccountedFor() {
		assertThatThrownBy(() -> service.confirmCoverage(absenceId, 12L))
				.isInstanceOf(BusinessRuleViolation.class)
				.hasMessageContaining("3 visits the absence vacates have not been re-rostered yet")
				.extracting("code").isEqualTo("VISITS_NOT_REROSTERED");

		service.reroster(absenceId, null, 11L);
		assertThatThrownBy(() -> service.confirmCoverage(absenceId, 12L))
				.isInstanceOf(BusinessRuleViolation.class)
				.hasMessageContaining("3 visits are still waiting")
				.extracting("code").isEqualTo("FAMILY_STILL_DECIDING");

		clock.set(NOW.plusHours(2));
		scan.sweep();
		AbsenceReport confirmed = service.confirmCoverage(absenceId, 12L);

		assertThat(confirmed.coverageConfirmedAt()).isEqualTo(NOW.plusHours(2));
		assertThat(confirmed.coverageConfirmedByUserId()).isEqualTo(12L);
	}

	@Test
	void anUnknownAbsenceIsNotFound() {
		assertThatThrownBy(() -> service.reroster(404L, null, 11L))
				.isInstanceOf(sg.nus.carelink.shared.error.ResourceNotFound.class);
		FamilyChoice skip = FamilyChoice.skip();
		assertThatThrownBy(() -> service.decide(404L, "alex", skip))
				.isInstanceOf(sg.nus.carelink.shared.error.ResourceNotFound.class);
	}
}
