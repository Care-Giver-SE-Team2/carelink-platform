package sg.nus.carelink.rostering.infrastructure.persistence.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import sg.nus.carelink.rostering.infrastructure.persistence.entity.AbsenceReportJpaEntity;

/** Spring Data repository for absence_report. Used by persistence.adapter only; never exposed outwards. */
public interface AbsenceReportJpaRepository extends JpaRepository<AbsenceReportJpaEntity, Long> {

	List<AbsenceReportJpaEntity> findAllByOrderByStartDateDescIdDesc();

	List<AbsenceReportJpaEntity> findByStatusOrderByStartDateDescIdDesc(AbsenceReportJpaEntity.Status status);

	List<AbsenceReportJpaEntity> findByCaregiverIdOrderByStartDateDescIdDesc(Long caregiverId);

	/** Shares a day with [from, until]: starts on or before its end and ends on or after its start. */
	List<AbsenceReportJpaEntity> findByStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
			AbsenceReportJpaEntity.Status status, LocalDate until, LocalDate from);
}
