package sg.nus.carelink.rostering.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
import sg.nus.carelink.rostering.domain.model.RosteringConstraint;
import sg.nus.carelink.rostering.domain.model.RosteringRun;
import sg.nus.carelink.shared.error.ResourceNotFound;
import sg.nus.carelink.shared.security.Role;

/**
 * The two views of UC-MG04: the manager's, with every candidate and every rule result, and the
 * family's, with their own elders' changes and a few options each - and nothing about other
 * caregivers' leave or certificates.
 */
class AbsenceQueryServiceTest {

	private static final ZoneId ZONE = ZoneId.of("Asia/Singapore");
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 7, 9, 0);
	private static final LocalDateTime EIGHTH = LocalDateTime.of(2026, 10, 8, 9, 0);

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
	private final IncidentService incidents = mock(IncidentService.class);
	private final UserDirectory users = mock(UserDirectory.class);

	private AbsenceReRosteringService reRostering;
	private AbsenceQueryService queries;
	private Long absenceId;

	@BeforeEach
	void setUp() {
		profiles.caregiver(5L, "Aisha", false).caregiver(9L, "Farah", false).caregiver(10L, "Siti", false)
				.caregiver(11L, "Kumar", true).elder(7L, "Mdm Tan").elder(8L, "Mr Ong");
		List<CredentialRegister.Cover> covers = new ArrayList<>();
		for (Long caregiver : List.of(5L, 9L, 10L)) {
			covers.add(new CredentialRegister.Cover(caregiver, caregiver, 2L, LocalDate.of(2027, 6, 30)));
		}
		visits.add(30L, 7L, 5L, EIGHTH, 45).add(31L, 7L, 5L, EIGHTH.plusDays(1), 45).add(32L, 8L, 5L,
				EIGHTH.withHour(14), 60);
		visits.history.put(7L, Map.of(9L, 3, 5L, 5));

		RosterSnapshotLoader loader = new RosterSnapshotLoader(profiles, () -> covers,
				planIds -> Map.of(4L, Set.of(2L)), absences, visits, since -> Map.of(), RosterLookbacks.DEFAULT, clock);
		reRostering = new AbsenceReRosteringService(absences, changes, runs, candidates, checks, constraints, loader,
				visits, profiles, incidents, family, users, new ReRosteringFakes.Alerts(), new ReRosteringFakes.Audit(),
				FamilyResponseWindow.DEFAULT, clock);
		queries = new AbsenceQueryService(absences, changes, runs, candidates, checks, constraints, visits, profiles,
				family, clock);
		absenceId = absences.save(AbsenceReport.recordedByManager(5L, AbsenceReport.Type.SICK, EIGHTH.toLocalDate(),
				EIGHTH.toLocalDate().plusDays(1), "flu", 11L, NOW.toLocalDate())).id();
		when(users.findByUsername("alex"))
				.thenReturn(Optional.of(new AppUser(41L, "alex", "Alex Tan", Set.of(Role.FAMILY), true)));
		when(incidents.raiseForUnfilledAbsence(any(), any(), any())).thenAnswer(call -> IncidentFixtures.withId(
				Incident.raisedForUncoveredVisit(call.getArgument(0), call.getArgument(1), call.getArgument(2), NOW), 900L));
	}

	@Test
	void beforeReRosteringTheManagerSeesWhatIsLeftToDo() {
		AbsenceQueryService.AbsenceCase before = queries.caseOf(absenceId);

		assertThat(before.absence().caregiverName()).isEqualTo("Aisha");
		assertThat(before.absence().notYetRerostered()).isEqualTo(3);
		assertThat(before.notYetRerostered()).extracting(AbsenceQueryService.VacatedVisit::elderName)
				.containsExactly("Mdm Tan", "Mr Ong", "Mdm Tan");
		assertThat(before.changes()).isEmpty();
		assertThat(queries.list(null)).singleElement()
				.extracting(AbsenceQueryService.AbsenceSummary::notYetRerostered).isEqualTo(3);
		assertThat(queries.list(AbsenceReport.Status.PENDING)).isEmpty();
	}

	@Test
	void afterReRosteringEveryCandidateAndEveryRuleResultIsThere() {
		reRostering.reroster(absenceId, null, 11L);

		AbsenceQueryService.AbsenceCase after = queries.caseOf(absenceId);
		assertThat(after.absence().awaitingFamily()).isEqualTo(3);
		assertThat(after.absence().notYetRerostered()).isZero();
		assertThat(after.notYetRerostered()).isEmpty();

		AbsenceQueryService.ChangeView first = after.changes().get(0);
		assertThat(first.visitId()).isEqualTo(30L);
		assertThat(first.elderName()).isEqualTo("Mdm Tan");
		assertThat(first.absentCaregiver().name()).isEqualTo("Aisha");
		assertThat(first.proposedCaregiver().name()).isEqualTo("Farah");
		assertThat(first.assignedCaregiver()).isNull();
		assertThat(first.objective()).isEqualTo(RosteringRun.Objective.CONTINUITY);
		assertThat(first.candidates()).extracting(AbsenceQueryService.CandidateView::name)
				.containsExactly("Farah", "Siti", "Aisha", "Kumar");
		AbsenceQueryService.CandidateView farah = first.candidates().get(0);
		assertThat(farah.rank()).isEqualTo(1);
		assertThat(farah.reason()).isEqualTo("Has visited this elder 3 times before");
		assertThat(farah.checks()).hasSize(9);
		assertThat(farah.checks().get(0).code()).isEqualTo("NOT_ON_LEAVE");
		assertThat(farah.checks().get(0).kind()).isEqualTo(RosteringConstraint.Kind.HARD);
		AbsenceQueryService.CandidateView aisha = first.candidates().get(2);
		assertThat(aisha.outcome()).isEqualTo(RosteringCandidate.Outcome.EXCLUDED);
		assertThat(aisha.excludedBy()).isEqualTo("NOT_ON_LEAVE");
		assertThat(aisha.reason()).isEqualTo("On leave 2026-10-08 to 2026-10-09");
		assertThat(queries.list(AbsenceReport.Status.APPROVED)).singleElement()
				.extracting(AbsenceQueryService.AbsenceSummary::awaitingFamily).isEqualTo(3);
	}

	@Test
	void theFamilySeesOnlyTheirElderAndChoosesFromAFewOptions() {
		reRostering.reroster(absenceId, null, 11L);

		List<AbsenceQueryService.FamilyChange> mine = queries.forFamily("alex");

		assertThat(mine).extracting(AbsenceQueryService.FamilyChange::elderName).containsOnly("Mdm Tan");
		assertThat(mine).hasSize(2);
		AbsenceQueryService.FamilyChange first = mine.get(0);
		assertThat(first.usualCaregiverName()).isEqualTo("Aisha");
		assertThat(first.respondBy()).isEqualTo(NOW.plusHours(2));
		assertThat(first.suggestedCaregiverId()).isEqualTo(9L);
		assertThat(first.options()).extracting(AbsenceQueryService.FamilyOption::name).containsExactly("Farah", "Siti");
		assertThat(first.options().get(0).reason()).isEqualTo("Has visited this elder 3 times before");

		assertThat(queries.forFamily("stranger")).isEmpty();
		Long otherElders = changes.forVisit(32L).id();
		assertThatThrownBy(() -> queries.forFamily("alex", otherElders)).isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> queries.forFamily("alex", 404L)).isInstanceOf(ResourceNotFound.class);
	}

	@Test
	void settledChangesComeAfterTheOnesStillWaitingAndSayWhatHappened() {
		reRostering.reroster(absenceId, null, 11L);
		Long thirtieth = changes.forVisit(30L).id();
		LocalDateTime saturday = LocalDateTime.of(2026, 10, 10, 9, 0);
		reRostering.decide(thirtieth, "alex", FamilyChoice.moveTo(saturday));

		List<AbsenceQueryService.FamilyChange> mine = queries.forFamily("alex");

		assertThat(mine).extracting(AbsenceQueryService.FamilyChange::status)
				.containsExactly(RosterChange.Status.AWAITING_FAMILY, RosterChange.Status.RESOLVED);
		AbsenceQueryService.FamilyChange moved = queries.forFamily("alex", thirtieth);
		assertThat(moved.outcome()).isEqualTo(RosterChange.Outcome.RESCHEDULED);
		assertThat(moved.rescheduledStart()).isEqualTo(saturday);
		assertThat(moved.assignedCaregiver().name()).isEqualTo("Aisha");
		assertThat(moved.options()).isEmpty();
		assertThat(queries.caseOf(absenceId).absence().settled()).isEqualTo(1);
		assertThatThrownBy(() -> queries.caseOf(404L)).isInstanceOf(ResourceNotFound.class);
	}
}
