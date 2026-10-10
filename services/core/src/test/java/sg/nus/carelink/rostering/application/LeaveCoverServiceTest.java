package sg.nus.carelink.rostering.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import sg.nus.carelink.profile.application.CredentialRegister;
import sg.nus.carelink.rostering.domain.model.AbsenceReport;
import sg.nus.carelink.rostering.domain.model.RosterChange;
import sg.nus.carelink.rostering.domain.model.RosteringCandidate;
import sg.nus.carelink.rostering.domain.model.RosteringRun;
import sg.nus.carelink.rostering.domain.model.VacatedSlot;
import sg.nus.carelink.rostering.domain.repository.VisitReassignment;

/**
 * UC-MG03 while the primary caregiver is on leave: once somebody covers an elder for the
 * absence, the elder's new visits on the leave days go to them too, checked against the hard
 * rules and recorded like any re-rostered visit. With no cover, or a cover who cannot make it,
 * nothing changes and the Absences screen re-rosters the visit as before.
 */
class LeaveCoverServiceTest {

	private static final ZoneId ZONE = ZoneId.of("Asia/Singapore");
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 7, 0, 0);
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
	private final ReRosteringFakes.Alerts alerts = new ReRosteringFakes.Alerts();
	private final ReRosteringFakes.Audit audit = new ReRosteringFakes.Audit();
	private final List<CredentialRegister.Cover> covers = new ArrayList<>();

	private LeaveCoverService service;
	private Long absenceId;

	@BeforeEach
	void setUp() {
		profiles.caregiver(5L, "Aisha", false).caregiver(9L, "Farah", false).caregiver(10L, "Siti", false)
				.elder(7L, "Mdm Tan").elder(8L, "Mr Ong");
		for (Long caregiver : List.of(5L, 9L, 10L)) {
			covers.add(new CredentialRegister.Cover(caregiver, caregiver, 2L, LocalDate.of(2027, 6, 30)));
		}
		// Aisha is Mdm Tan's and Mr Ong's primary caregiver; the refresh has given her these.
		visits.add(30L, 7L, 5L, EIGHTH, 45)
				.add(31L, 7L, 5L, EIGHTH.plusDays(1), 45)
				.add(32L, 8L, 5L, EIGHTH.withHour(14), 60);

		RosterSnapshotLoader loader = new RosterSnapshotLoader(profiles, () -> covers,
				planIds -> Map.of(4L, Set.of(2L)), absences, visits, since -> Map.of(), RosterLookbacks.DEFAULT, clock);
		service = new LeaveCoverService(absences, changes, runs, candidates, checks, constraints, loader, visits,
				profiles, alerts, audit, clock);
		absenceId = absences.save(AbsenceReport.recordedByManager(5L, AbsenceReport.Type.SICK, FIRST_DAY,
				FIRST_DAY.plusDays(1), "flu", 11L, NOW.toLocalDate())).id();
	}

	@Test
	void aNewVisitOnTheLeaveDaysGoesToWhoeverAlreadyCoversTheElder() {
		coveredBy(30L, 9L, RosterChange.DecidedBy.FAMILY, NOW);

		int moved = service.continueCover(7L);

		assertThat(moved).isEqualTo(1);
		assertThat(visits.rows.get(31L).caregiverId()).isEqualTo(9L);
		assertThat(visits.rows.get(32L).caregiverId()).as("nobody covers Mr Ong yet").isEqualTo(5L);
		RosterChange change = changes.forVisit(31L);
		assertThat(change.outcome()).isEqualTo(RosterChange.Outcome.REPLACED);
		assertThat(change.decidedBy()).isEqualTo(RosterChange.DecidedBy.DEFAULT_PLAN);
		assertThat(change.originalCaregiverId()).isEqualTo(5L);
		assertThat(change.note()).isEqualTo("Farah already covers this elder during the leave, so the new visit went to them too");
		assertThat(alerts.sent).containsExactly("settled REPLACED visit 31");
		assertThat(alerts.notices.get(0).caregiverName()).isEqualTo("Farah");
		assertThat(audit.entries).singleElement().asString().startsWith("31: Farah already covers");

		RosteringRun run = runs.findById(change.rosteringRunId()).orElseThrow();
		assertThat(run.requestedByUserId()).as("nobody asked; the refresh did it").isNull();
		assertThat(run.status()).isEqualTo(RosteringRun.Status.COMMITTED);
		assertThat(candidates.findByRunAndVisit(run.id(), 31L))
				.filteredOn(candidate -> candidate.outcome() == RosteringCandidate.Outcome.SELECTED)
				.extracting(RosteringCandidate::caregiverId).containsExactly(9L);

		assertThat(service.continueCover(7L)).as("a visit already moved is not moved again").isZero();
	}

	@Test
	void theLatestCoverIsTheOneThatCarriesOn() {
		coveredBy(30L, 9L, RosterChange.DecidedBy.DEFAULT_PLAN, NOW);
		visits.add(33L, 7L, 5L, EIGHTH.withHour(16), 30);
		coveredBy(33L, 10L, RosterChange.DecidedBy.FAMILY, NOW.plusHours(1));

		service.continueCover(7L);

		assertThat(visits.rows.get(31L).caregiverId()).isEqualTo(10L);
	}

	@Test
	void aCoverWhoCannotMakeItLeavesTheVisitForTheAbsencesScreen() {
		coveredBy(30L, 9L, RosterChange.DecidedBy.FAMILY, NOW);
		absences.save(AbsenceReport.recordedByManager(9L, AbsenceReport.Type.EMERGENCY, FIRST_DAY.plusDays(1),
				FIRST_DAY.plusDays(1), null, 11L, NOW.toLocalDate()));

		assertThat(service.continueCover(7L)).isZero();

		assertThat(visits.rows.get(31L).caregiverId()).isEqualTo(5L);
		assertThat(changes.rows.values()).extracting(RosterChange::visitId).containsExactly(30L);
		assertThat(alerts.sent).isEmpty();
	}

	@Test
	void withNobodyCoveringTheElderNothingChanges() {
		assertThat(service.continueCover(7L)).isZero();

		assertThat(visits.rows.get(30L).caregiverId()).isEqualTo(5L);
		assertThat(visits.rows.get(31L).caregiverId()).isEqualTo(5L);
		assertThat(runs.findById(1L)).isEmpty();
	}

	@Test
	void leaveThatIsOverIsLeftAlone() {
		coveredBy(30L, 9L, RosterChange.DecidedBy.FAMILY, NOW);
		clock.set(FIRST_DAY.plusDays(2).atStartOfDay());

		assertThat(service.continueCover(7L)).isZero();
		assertThat(visits.rows.get(31L).caregiverId()).isEqualTo(5L);
	}

	/** Somebody already took this visit over for the absence, the way UC-MG04 settles one. */
	private void coveredBy(Long visitId, Long caregiverId, RosterChange.DecidedBy by, LocalDateTime when) {
		VisitReassignment.VisitSlot visit = visits.rows.get(visitId);
		VacatedSlot slot = new VacatedSlot(visitId, visit.elderId(), visit.carePlanId(), visit.serviceType(),
				visit.start(), visit.end(), 5L);
		changes.save(RosterChange.offered(absenceId, slot, null, caregiverId, when, when)
				.replacedBy(caregiverId, null, by, by == RosterChange.DecidedBy.DEFAULT_PLAN ? null : 41L, "taken", when));
		visits.reassign(visitId, caregiverId, new VisitReassignment.Change(absenceId, null, null, "taken"));
		visits.calls.clear();
	}
}
