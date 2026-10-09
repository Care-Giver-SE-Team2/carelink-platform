package sg.nus.carelink.rostering.domain.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import sg.nus.carelink.rostering.domain.model.AbsenceReport;

/**
 * Port for absence_report: what the application layer may ask of storage, in domain terms.
 * Implemented by infrastructure.persistence.adapter.AbsenceReportRepositoryAdapter. Add finders as
 * the use cases need them; identity.domain.repository.AppUserRepository is the template.
 */
public interface AbsenceReportRepository {

	Optional<AbsenceReport> findById(Long id);

	AbsenceReport save(AbsenceReport absenceReport);

	/** Every absence, latest start first; one status only when {@code status} is not null. */
	List<AbsenceReport> findAll(AbsenceReport.Status status);

	/** One caregiver's absences, latest start first. */
	List<AbsenceReport> findByCaregiverId(Long caregiverId);

	/** Approved absences sharing at least one day with [from, until], both ends included. */
	List<AbsenceReport> findApprovedOverlapping(LocalDate from, LocalDate until);
}
