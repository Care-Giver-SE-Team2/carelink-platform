package sg.nus.carelink.incident.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import sg.nus.carelink.identity.application.UserDirectory;
import sg.nus.carelink.identity.domain.model.AppUser;
import sg.nus.carelink.incident.domain.model.Incident;
import sg.nus.carelink.incident.domain.model.SpotCheck;
import sg.nus.carelink.incident.domain.repository.SpotCheckAlert;
import sg.nus.carelink.incident.domain.repository.SpotCheckLookups;
import sg.nus.carelink.incident.domain.repository.SpotCheckRepository;
import sg.nus.carelink.incident.domain.service.EscalationPolicy;
import sg.nus.carelink.incident.support.FakeManagerDirectory;
import sg.nus.carelink.incident.support.InMemoryIncidentLogRepository;
import sg.nus.carelink.incident.support.InMemoryIncidentRepository;
import sg.nus.carelink.incident.support.IncidentFixtures;
import sg.nus.carelink.incident.support.RecordingAlert;
import sg.nus.carelink.profile.application.CaregiverWorkDirectory;
import sg.nus.carelink.profile.application.FamilyAccessQuery;
import sg.nus.carelink.profile.application.RosteringProfiles;
import sg.nus.carelink.shared.error.ResourceNotFound;
import sg.nus.carelink.shared.security.Role;

/**
 * UC-MG08 end to end on in-memory ports: a manager asks to watch Mdm Tan's Friday visit with
 * Aisha, the family agrees or declines, and the check ends with a conclusion Aisha can answer,
 * with Aisha not turning up (an incident), with Mdm Tan out (another visit, asked again), or
 * withdrawn.
 */
class SpotCheckServiceTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 7, 9, 0);
	private static final LocalDateTime FRIDAY = LocalDateTime.of(2026, 10, 9, 10, 0);

	private final Checks checks = new Checks();
	private final Lookups lookups = new Lookups();
	private final Alerts alerts = new Alerts();
	private final InMemoryIncidentRepository incidentRows = new InMemoryIncidentRepository();
	private final InMemoryIncidentLogRepository timeline = new InMemoryIncidentLogRepository();
	private final UserDirectory users = mock(UserDirectory.class);
	private final CaregiverWorkDirectory caregivers = mock(CaregiverWorkDirectory.class);
	private final Clock clock = IncidentFixtures.clockAt(NOW);

	private SpotCheckService service;

	@BeforeEach
	void setUp() {
		EscalationService escalation = new EscalationService(incidentRows, timeline,
				FakeManagerDirectory.with(IncidentFixtures.ALICE, IncidentFixtures.BEN), new RecordingAlert(),
				EscalationPolicy.defaults(), clock);
		IncidentService incidents = new IncidentService(incidentRows, timeline, escalation, clock);
		service = new SpotCheckService(checks, lookups, alerts, incidents, new Family(), users, caregivers,
				new Profiles(), clock);

		lookups.visits.put(30L, new SpotCheckLookups.VisitFacts(30L, 7L, 5L, FRIDAY, "SCHEDULED", "Personal care"));
		lookups.visits.put(31L, new SpotCheckLookups.VisitFacts(31L, 7L, 6L, FRIDAY.plusDays(7), "SCHEDULED", "Meals"));
		lookups.visits.put(32L, new SpotCheckLookups.VisitFacts(32L, 8L, 5L, FRIDAY, "SCHEDULED", null));
		lookups.visits.put(33L, new SpotCheckLookups.VisitFacts(33L, 7L, 5L, NOW.minusDays(1), "COMPLETED", null));
		lookups.familyMembers.put(41L, 21L);
		when(users.findByUsername("alex"))
				.thenReturn(Optional.of(new AppUser(41L, "alex", "Alex Tan", Set.of(Role.FAMILY), true)));
		when(caregivers.require("aisha"))
				.thenReturn(new CaregiverWorkDirectory.Profile(5L, 1005L, "Aisha", null, null, null, "AVAILABLE"));
		when(caregivers.require("ben"))
				.thenReturn(new CaregiverWorkDirectory.Profile(6L, 1006L, "Ben", null, null, null, "AVAILABLE"));
	}

	@Test
	void aRequestAsksTheFamilyAndOnlyOncePerVisit() {
		SpotCheck asked = service.request(30L, "Follow-up on a missed medication", 11L);

		assertThat(asked.stage()).isEqualTo(SpotCheck.Stage.AWAITING_FAMILY);
		assertThat(asked.caregiverId()).isEqualTo(5L);
		assertThat(asked.proposedTime()).isEqualTo(FRIDAY);
		assertThat(alerts.sent).containsExactly("asked 30: Mdm Tan / Aisha");
		assertThatThrownBy(() -> service.request(30L, "again", 11L))
				.extracting("code").isEqualTo("SPOT_CHECK_ALREADY_REQUESTED");
		assertThatThrownBy(() -> service.request(33L, "too late", 11L))
				.extracting("code").isEqualTo("SPOT_CHECK_VISIT_NOT_OPEN");
		assertThatThrownBy(() -> service.request(404L, "missing", 11L)).isInstanceOf(ResourceNotFound.class);
	}

	@Test
	void theFamilyAgreesAndTheManagerRecordsAConclusionTheCaregiverAnswers() {
		Long id = service.request(30L, "Routine", 11L).id();

		SpotCheck approved = service.decide(id, "alex", true, null);
		assertThat(approved.stage()).isEqualTo(SpotCheck.Stage.SCHEDULED);
		assertThat(approved.approvingFamilyMemberId()).isEqualTo(21L);

		SpotCheck concluded = service.conclude(id, SpotCheck.Result.NEEDS_IMPROVEMENT, "Gloves not worn");
		assertThat(concluded.stage()).isEqualTo(SpotCheck.Stage.COMPLETED);
		assertThat(alerts.sent).containsExactly("asked 30: Mdm Tan / Aisha", "answered 30", "concluded 30");

		SpotCheck answered = service.respond(id, "aisha", "The gloves ran out.");
		assertThat(answered.caregiverResponse()).isEqualTo("The gloves ran out.");
		assertThat(service.forCaregiver("aisha")).singleElement()
				.extracting(SpotCheckService.SpotCheckView::caregiverResponse).isEqualTo("The gloves ran out.");
		assertThat(service.forCaregiver("ben")).isEmpty();
		assertThatThrownBy(() -> service.respond(id, "ben", "not mine")).extracting("code").isEqualTo("SPOT_CHECK_NOT_YOURS");
	}

	@Test
	void aFamilyMemberAnswersOnlyForTheirOwnElder() {
		Long otherElder = service.request(32L, "Routine", 11L).id();

		assertThatThrownBy(() -> service.decide(otherElder, "alex", true, null)).isInstanceOf(AccessDeniedException.class);

		Long theirs = service.request(30L, "Routine", 11L).id();
		SpotCheck declined = service.decide(theirs, "alex", false, "Mother is unwell");
		assertThat(declined.stage()).isEqualTo(SpotCheck.Stage.DECLINED);
		assertThat(declined.closingReason()).isEqualTo("Mother is unwell");
		assertThat(service.forFamily("alex")).extracting(SpotCheckService.SpotCheckView::id).containsExactly(theirs);
	}

	@Test
	void aCaregiverWhoDidNotComeBecomesAnIncidentInTheQueue() {
		Long id = service.request(30L, "Routine", 11L).id();

		assertThatThrownBy(() -> service.reportNoShow(id, "nobody came", "Alice Tan (alice)"))
				.extracting("code").isEqualTo("SPOT_CHECK_NOT_APPROVED");
		assertThat(incidentRows.findByElder(7L)).isEmpty();

		service.decide(id, "alex", true, null);
		SpotCheck noShow = service.reportNoShow(id, "nobody came", "Alice Tan (alice)");

		assertThat(noShow.stage()).isEqualTo(SpotCheck.Stage.CAREGIVER_NO_SHOW);
		Incident incident = incidentRows.findById(noShow.incidentId()).orElseThrow();
		assertThat(incident.visitId()).isEqualTo(30L);
		assertThat(incident.source()).isEqualTo(Incident.Source.SYSTEM_MISSED_CHECKIN);
		assertThat(incident.responderUserId()).isNotNull();
		assertThat(timeline.actionsFor(incident.id())).containsSubsequence("REPORTED", "ASSIGNED");
	}

	@Test
	void anElderWhoWasOutMovesTheCheckToAnotherOfTheirVisits() {
		Long id = service.request(30L, "Routine", 11L).id();
		service.decide(id, "alex", true, null);

		SpotCheck moved = service.moveTo(id, 31L);

		assertThat(moved.stage()).isEqualTo(SpotCheck.Stage.AWAITING_FAMILY);
		assertThat(moved.caregiverId()).isEqualTo(6L);
		assertThat(alerts.sent).endsWith("asked 31: Mdm Tan / Ben");
		assertThatThrownBy(() -> service.moveTo(id, 32L)).extracting("code").isEqualTo("SPOT_CHECK_OTHER_ELDER");
	}

	@Test
	void aWithdrawnCheckIsKeptWithItsReason() {
		Long id = service.request(30L, "Routine", 11L).id();

		SpotCheck withdrawn = service.withdraw(id, "Caregiver changed elders");

		assertThat(withdrawn.stage()).isEqualTo(SpotCheck.Stage.WITHDRAWN);
		assertThat(alerts.sent).endsWith("withdrawn 30");
		assertThat(service.list(SpotCheck.Stage.WITHDRAWN, null, null)).hasSize(1);
		assertThat(service.list(null, 5L, 7L)).hasSize(1);
		assertThat(service.list(SpotCheck.Stage.COMPLETED, null, null)).isEmpty();
		assertThat(service.view(id).closingReason()).isEqualTo("Caregiver changed elders");
	}

	@Test
	void rosteringSeesEachCaregiversRecentConclusions() {
		Long good = service.request(30L, "Routine", 11L).id();
		service.decide(good, "alex", true, null);
		service.conclude(good, SpotCheck.Result.MEETS_STANDARD, null);
		Long poor = service.request(31L, "Routine", 11L).id();
		service.decide(poor, "alex", true, null);
		service.conclude(poor, SpotCheck.Result.NEEDS_IMPROVEMENT, "late");

		Map<Long, SpotCheckHistory.Conclusions> recent = service.recentConclusions(NOW.minusDays(90));

		assertThat(recent).containsEntry(5L, new SpotCheckHistory.Conclusions(1, 0))
				.containsEntry(6L, new SpotCheckHistory.Conclusions(0, 1));
		assertThat(service.conclusionsFor(6L)).singleElement()
				.extracting(SpotCheckService.SpotCheckView::caregiverName).isEqualTo("Ben");
	}

	@Test
	void theManagerChoosesFromTheEldersUpcomingVisits() {
		assertThat(service.visitsToCheck(7L)).extracting(SpotCheckService.VisitChoice::visitId).containsExactly(30L, 31L);
		assertThat(service.visitsToCheck(7L).get(0).caregiverName()).isEqualTo("Aisha");
	}

	// ------------------------------------------------------------------ fakes ---

	private static final class Checks implements SpotCheckRepository {

		private final Map<Long, SpotCheck> rows = new LinkedHashMap<>();
		private long nextId = 100;

		@Override
		public Optional<SpotCheck> findById(Long id) {
			return Optional.ofNullable(rows.get(id));
		}

		@Override
		public SpotCheck save(SpotCheck c) {
			SpotCheck stored = c.id() != null ? c
					: new SpotCheck(nextId++, c.elderId(), c.caregiverId(), c.visitId(), c.raisedByUserId(),
							c.approvingFamilyMemberId(), c.proposedTime(), c.reason(), c.approvalStatus(), c.decidedAt(),
							c.finding(), c.caregiverResponse(), c.checkedAt(), c.createdAt(), c.result(), c.outcome(),
							c.closingReason(), c.incidentId());
			rows.put(stored.id(), stored);
			return stored;
		}

		@Override
		public List<SpotCheck> findAll() {
			return rows.values().stream().sorted(Comparator.comparing(SpotCheck::proposedTime).reversed()).toList();
		}

		@Override
		public List<SpotCheck> findByElderIds(Collection<Long> elderIds) {
			return findAll().stream().filter(c -> elderIds.contains(c.elderId())).toList();
		}

		@Override
		public List<SpotCheck> findByCaregiverId(Long caregiverId) {
			return findAll().stream().filter(c -> caregiverId.equals(c.caregiverId())).toList();
		}

		@Override
		public List<SpotCheck> findConcludedSince(LocalDateTime since) {
			return findAll().stream()
					.filter(c -> c.outcome() == SpotCheck.Outcome.COMPLETED && !c.checkedAt().isBefore(since))
					.toList();
		}
	}

	private static final class Lookups implements SpotCheckLookups {

		final Map<Long, VisitFacts> visits = new HashMap<>();
		final Map<Long, Long> familyMembers = new HashMap<>();

		@Override
		public Optional<VisitFacts> visit(Long visitId) {
			return Optional.ofNullable(visits.get(visitId));
		}

		@Override
		public List<VisitFacts> upcomingVisits(Long elderId, LocalDateTime from, LocalDateTime until) {
			return visits.values().stream()
					.filter(v -> v.elderId().equals(elderId) && v.isScheduled() && v.caregiverId() != null)
					.filter(v -> !v.start().isBefore(from) && v.start().isBefore(until))
					.sorted(Comparator.comparing(VisitFacts::start))
					.toList();
		}

		@Override
		public Optional<Long> familyMemberIdOf(Long userId) {
			return Optional.ofNullable(familyMembers.get(userId));
		}
	}

	private static final class Alerts implements SpotCheckAlert {

		final List<String> sent = new ArrayList<>();

		@Override
		public void approvalRequested(SpotCheck check, Names names) {
			sent.add("asked " + check.visitId() + ": " + names.elderName() + " / " + names.caregiverName());
		}

		@Override
		public void familyAnswered(SpotCheck check, Names names) {
			sent.add("answered " + check.visitId());
		}

		@Override
		public void concluded(SpotCheck check, Names names) {
			sent.add("concluded " + check.visitId());
		}

		@Override
		public void withdrawn(SpotCheck check, Names names) {
			sent.add("withdrawn " + check.visitId());
		}
	}

	/** Family "alex" is bound to elder 7 only. */
	private static final class Family implements FamilyAccessQuery {

		@Override
		public Set<Long> readableElderIds(String username) {
			return "alex".equals(username) ? Set.of(7L) : Set.of();
		}

		@Override
		public void requireReadableElder(String username, Long elderId) {
			if (!readableElderIds(username).contains(elderId)) {
				throw new AccessDeniedException("A readable elder binding is required");
			}
		}
	}

	private static final class Profiles implements RosteringProfiles {

		@Override
		public List<Candidate> candidates() {
			return List.of(new Candidate(5L, 1005L, "Aisha", null, null, false),
					new Candidate(6L, 1006L, "Ben", null, null, false));
		}

		@Override
		public Map<Long, String> credentialTypeNames(Collection<Long> ids) {
			return Map.of();
		}

		@Override
		public Optional<ElderFacts> elder(Long elderId) {
			return Long.valueOf(7L).equals(elderId)
					? Optional.of(new ElderFacts(7L, null, "Mdm Tan", null, null))
					: Optional.empty();
		}
	}
}
