package sg.nus.carelink.rostering.domain.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import sg.nus.carelink.rostering.domain.model.VisitChangeStep;

/** Port for visit_change_step: the re-rostering saga's record of each change visit is asked to make. */
public interface VisitChangeStepRepository {

	/** Records a new step. */
	VisitChangeStep add(VisitChangeStep step);

	Optional<VisitChangeStep> findById(Long id);

	/**
	 * Moves a step from {@code from} to {@code to} if nobody has moved it since {@code from} was read - the
	 * same status and the same number of tries. A step can be taken by the request that decided it and by
	 * the sweep at the same moment; only the one this answers true for goes on to settle it, so what follows
	 * a step happens once.
	 *
	 * @return whether the step was still as {@code from} has it, and so was moved
	 */
	boolean advance(VisitChangeStep from, VisitChangeStep to);

	/** The steps of an absence that still hold their visit (PENDING or ABANDONED), earliest first. */
	List<VisitChangeStep> findHoldingByAbsenceId(Long absenceId);

	/** PENDING steps whose next try is due, earliest first. */
	List<VisitChangeStep> findDue(LocalDateTime now, int limit);

	/** ABANDONED steps whose manager's incident has not been raised yet, earliest first. */
	List<VisitChangeStep> findGivenUpWithoutIncident(int limit);
}
