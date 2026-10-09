package sg.nus.carelink.rostering.application;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.incident.application.IncidentService;
import sg.nus.carelink.profile.application.RosteringProfiles;
import sg.nus.carelink.rostering.domain.model.AbsenceReport;
import sg.nus.carelink.rostering.domain.model.RosterChange;
import sg.nus.carelink.rostering.domain.repository.AbsenceReportRepository;
import sg.nus.carelink.rostering.domain.repository.RosterChangeRepository;
import sg.nus.carelink.visit.application.VisitReassignment;

/**
 * The last safety net for leave: a visit on a caregiver's approved leave that is due within
 * {@code lead} and is still theirs - nobody re-rostered it and no cover took it - gets an
 * incident in the manager's queue, while there is still time to act. Nothing is moved: the
 * manager re-rosters it on the Absences screen.
 *
 * <p>Raised once per visit. A visit that already has an incident, for this or any reason, is
 * left alone, so a manager who closes the reminder is not reminded again.
 */
@Service
public class LeaveReminderService {

	private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.ENGLISH);

	private final AbsenceReportRepository absences;
	private final RosterChangeRepository changes;
	private final VisitReassignment visits;
	private final IncidentService incidents;
	private final RosteringProfiles profiles;
	private final Clock clock;
	private final Duration lead;

	public LeaveReminderService(AbsenceReportRepository absences, RosterChangeRepository changes,
			VisitReassignment visits, IncidentService incidents, RosteringProfiles profiles, Clock clock,
			@Value("${carelink.roster.leave-reminder-lead:PT24H}") Duration lead) {
		this.absences = absences;
		this.changes = changes;
		this.visits = visits;
		this.incidents = incidents;
		this.profiles = profiles;
		this.clock = clock;
		this.lead = lead;
	}

	/** Visits on approved leave, due within the lead, still with the caregiver who is away; earliest first. */
	@Transactional(readOnly = true)
	public List<DueVisit> dueStillOnLeave() {
		LocalDateTime now = LocalDateTime.now(clock);
		LocalDateTime horizon = now.plus(lead);
		return absences.findAll(AbsenceReport.Status.APPROVED).stream()
				.filter(absence -> absence.windowEnd().isAfter(now) && absence.windowStart().isBefore(horizon))
				.flatMap(absence -> {
					Set<Long> handled = changes.findByAbsenceId(absence.id()).stream()
							.map(RosterChange::visitId)
							.collect(Collectors.toSet());
					return Vacancies.notYetRerostered(visits, absence, handled, now).stream()
							.filter(visit -> visit.start().isBefore(horizon))
							.map(visit -> new DueVisit(absence.id(), absence.caregiverId(), visit.visitId(),
									visit.elderId(), visit.serviceType(), visit.start()));
				})
				.sorted(Comparator.comparing(DueVisit::start))
				.toList();
	}

	/**
	 * Raises the reminder for one visit unless it already has an incident, or has meanwhile
	 * left the absent caregiver. Its own transaction, so one failure leaves the others alone.
	 *
	 * @return whether an incident was raised
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public boolean remind(DueVisit due) {
		boolean stillOnLeave = visits.find(due.visitId())
				.filter(visit -> "SCHEDULED".equals(visit.status()))
				.filter(visit -> due.caregiverId().equals(visit.caregiverId()))
				.isPresent();
		boolean alreadyRaised = incidents.forElder(due.elderId()).stream()
				.anyMatch(incident -> due.visitId().equals(incident.visitId()));
		if (!stillOnLeave || alreadyRaised) {
			return false;
		}
		incidents.raiseForUnrosteredLeaveVisit(due.elderId(), due.visitId(),
				"%s visit at %s is still with %s, who is on approved leave; re-roster it on the Absences screen (absence %d)"
						.formatted(due.serviceType() == null ? "A" : due.serviceType(), due.start().format(WHEN),
								caregiverName(due.caregiverId()), due.absenceId()));
		return true;
	}

	private String caregiverName(Long caregiverId) {
		return profiles.candidates().stream()
				.filter(candidate -> candidate.caregiverId().equals(caregiverId))
				.map(RosteringProfiles.Candidate::fullName)
				.findFirst()
				.orElse("caregiver " + caregiverId);
	}

	/** One visit on leave the reminder is about. */
	public record DueVisit(Long absenceId, Long caregiverId, Long visitId, Long elderId, String serviceType,
			LocalDateTime start) {
	}
}
