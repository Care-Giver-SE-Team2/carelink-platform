package sg.nus.carelink.rostering.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.profile.application.CaregiverWorkDirectory;
import sg.nus.carelink.profile.application.RosteringProfiles;
import sg.nus.carelink.rostering.domain.model.AbsenceReport;
import sg.nus.carelink.rostering.domain.repository.AbsenceAlert;
import sg.nus.carelink.rostering.domain.repository.AbsenceReportRepository;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.ResourceNotFound;

/**
 * Absences: recorded by a manager (UC-MG04 step 1), or asked for by a caregiver and reviewed by
 * a manager (UC-CG02). What an absence does to the roster is {@link AbsenceReRosteringService}'s
 * business; this class only keeps the absences themselves.
 *
 * <p>The caregiver's side is the entry point UC-CG02 needs, written here because the absence
 * lives here: the caregiver app only has to call it.
 */
@Service
@Transactional
public class AbsenceService {

	private final AbsenceReportRepository absences;
	private final RosteringProfiles profiles;
	private final CaregiverWorkDirectory caregivers;
	private final AbsenceAlert alert;
	private final Clock clock;

	public AbsenceService(AbsenceReportRepository absences, RosteringProfiles profiles,
			CaregiverWorkDirectory caregivers, AbsenceAlert alert, Clock clock) {
		this.absences = absences;
		this.profiles = profiles;
		this.caregivers = caregivers;
		this.alert = alert;
		this.clock = clock;
	}

	/**
	 * UC-MG04 step 1: a manager records that a caregiver will be away. Approved at once.
	 *
	 * @throws ResourceNotFound if no current caregiver has this id
	 * @throws BusinessRuleViolation ABSENCE_OVERLAPS if it shares a day with one already on record
	 */
	public AbsenceReport recordForCaregiver(Long caregiverId, AbsenceReport.Type type, LocalDate startDate,
			LocalDate endDate, String reason, Long managerUserId) {
		boolean known = profiles.candidates().stream().anyMatch(c -> c.caregiverId().equals(caregiverId));
		if (!known) {
			throw new ResourceNotFound("Caregiver", caregiverId);
		}
		return keep(AbsenceReport.recordedByManager(caregiverId, type, startDate, endDate, reason, managerUserId,
				today()));
	}

	/**
	 * UC-CG02: the signed-in caregiver asks for leave; a manager reviews it. Every manager is
	 * told, which is UC-MG04's step 1 for a request rather than a phone call.
	 */
	public AbsenceReport requestForSelf(String caregiverUsername, AbsenceReport.Type type, LocalDate startDate,
			LocalDate endDate, String reason) {
		CaregiverWorkDirectory.Profile caregiver = caregivers.require(caregiverUsername);
		AbsenceReport asked = keep(AbsenceReport.requested(caregiver.id(), type, startDate, endDate, reason, today()));
		alert.requested(asked, caregiver.fullName());
		return asked;
	}

	public AbsenceReport approve(Long absenceId, Long managerUserId) {
		return absences.save(require(absenceId).approvedBy(managerUserId));
	}

	public AbsenceReport reject(Long absenceId, Long managerUserId) {
		return absences.save(require(absenceId).rejectedBy(managerUserId));
	}

	@Transactional(readOnly = true)
	public List<AbsenceReport> list(AbsenceReport.Status status) {
		return absences.findAll(status);
	}

	/** The signed-in caregiver's own absences, latest first. */
	@Transactional(readOnly = true)
	public List<AbsenceReport> listForSelf(String caregiverUsername) {
		return absences.findByCaregiverId(caregivers.require(caregiverUsername).id());
	}

	@Transactional(readOnly = true)
	public AbsenceReport require(Long absenceId) {
		return absences.findById(absenceId).orElseThrow(() -> new ResourceNotFound("Absence", absenceId));
	}

	/**
	 * Two absences on the same day would vacate the same visits twice; a request that is still
	 * waiting counts, a rejected one does not.
	 */
	private AbsenceReport keep(AbsenceReport absence) {
		absences.findByCaregiverId(absence.caregiverId()).stream()
				.filter(existing -> existing.status() != AbsenceReport.Status.REJECTED)
				.filter(existing -> existing.overlaps(absence))
				.findFirst()
				.ifPresent(existing -> {
					throw new BusinessRuleViolation("ABSENCE_OVERLAPS",
							"This caregiver is already recorded away from %s to %s"
									.formatted(existing.startDate(), existing.endDate()));
				});
		return absences.save(absence);
	}

	private LocalDate today() {
		return LocalDate.now(clock);
	}
}
