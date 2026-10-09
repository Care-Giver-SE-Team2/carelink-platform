package sg.nus.carelink.rostering.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.profile.application.RosteringProfiles;
import sg.nus.carelink.rostering.domain.model.AbsenceReport;
import sg.nus.carelink.rostering.domain.model.RosterChange;
import sg.nus.carelink.rostering.domain.repository.AbsenceAlert;
import sg.nus.carelink.rostering.domain.repository.AbsenceReportRepository;
import sg.nus.carelink.rostering.domain.repository.RosterChangeRepository;
import sg.nus.carelink.shared.error.ResourceNotFound;
import sg.nus.carelink.visit.application.VisitReassignment;

/**
 * Keeps UC-MG04 step 7 true after the nightly roster has run. Visits are only laid out a
 * fortnight ahead, and they go to the elder's primary caregiver whether or not that caregiver
 * is on leave, so an absence confirmed today can gain visits on its later days tomorrow. Left
 * alone, those visits would stay with the absent caregiver.
 *
 * <p>The manager has already handled the absence once, so the new visits are re-rostered the
 * same way at once - offered to the family, or settled by the default plan when too close -
 * rather than waiting for somebody to notice. The confirmation is then withdrawn and the
 * managers are told, so a manager still reviews what was done, and may reassign, before
 * confirming again.
 */
@Service
@Transactional
public class AbsenceCoverageService {

	private final AbsenceReportRepository absences;
	private final RosterChangeRepository changes;
	private final VisitReassignment visits;
	private final AbsenceReRosteringService reRostering;
	private final RosteringProfiles profiles;
	private final AbsenceAlert alert;
	private final Clock clock;

	public AbsenceCoverageService(AbsenceReportRepository absences, RosterChangeRepository changes,
			VisitReassignment visits, AbsenceReRosteringService reRostering, RosteringProfiles profiles,
			AbsenceAlert alert, Clock clock) {
		this.absences = absences;
		this.changes = changes;
		this.visits = visits;
		this.reRostering = reRostering;
		this.profiles = profiles;
		this.alert = alert;
		this.clock = clock;
	}

	/** Approved absences with coverage confirmed that are not over yet: the ones the roster can still add to. */
	@Transactional(readOnly = true)
	public List<Long> confirmedAbsencesStillAhead() {
		LocalDateTime now = LocalDateTime.now(clock);
		return absences.findAll(AbsenceReport.Status.APPROVED).stream()
				.filter(AbsenceReport::isCoverageConfirmed)
				.filter(absence -> absence.windowEnd().isAfter(now))
				.map(AbsenceReport::id)
				.toList();
	}

	/**
	 * Re-rosters the visits a confirmed absence now vacates that nobody has re-rostered, then
	 * withdraws its confirmation and tells the managers. Its own transaction, called once per
	 * absence by the nightly run, so one absence that fails does not hold back the others.
	 *
	 * @return whether there were visits to re-roster
	 */
	public boolean rerosterAddedVisits(Long absenceId) {
		AbsenceReport absence = absences.findById(absenceId)
				.orElseThrow(() -> new ResourceNotFound("Absence", absenceId));
		if (!absence.isCoverageConfirmed()) {
			return false;
		}
		Set<Long> handled = changes.findByAbsenceId(absenceId).stream()
				.map(RosterChange::visitId)
				.collect(Collectors.toSet());
		if (Vacancies.notYetRerostered(visits, absence, handled, LocalDateTime.now(clock)).isEmpty()) {
			return false;
		}
		AbsenceReRosteringService.ReRosterOutcome outcome = reRostering.reroster(absenceId, null, null);
		AbsenceReport reopened = absences.save(absences.findById(absenceId).orElseThrow().coverageReopened());
		alert.visitsRerostered(reopened, caregiverName(absence.caregiverId()),
				new AbsenceAlert.Rerostered(outcome.offered(), outcome.settled(), outcome.uncovered()));
		return true;
	}

	private String caregiverName(Long caregiverId) {
		return profiles.candidates().stream()
				.filter(candidate -> Objects.equals(candidate.caregiverId(), caregiverId))
				.map(RosteringProfiles.Candidate::fullName)
				.findFirst()
				.orElse(null);
	}
}
