package sg.nus.carelink.rostering.domain.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import sg.nus.carelink.rostering.domain.model.RosterChange;

/** Port for roster_change: what UC-MG04 keeps about each visit an absence vacated. */
public interface RosterChangeRepository {

	Optional<RosterChange> findById(Long id);

	/**
	 * Reads a change and holds it until the transaction ends. A family's answer and the default
	 * plan can arrive in the same second; whichever locks second finds the change already
	 * settled instead of settling it again.
	 */
	Optional<RosterChange> lock(Long id);

	RosterChange save(RosterChange change);

	/** Every change an absence has produced, earliest visit first. */
	List<RosterChange> findByAbsenceId(Long absenceId);

	/** Changes for these elders, earliest visit first: what a family member is shown. */
	List<RosterChange> findByElderIds(Collection<Long> elderIds);

	/** Changes still waiting for a family whose time to answer is up. */
	List<RosterChange> findAwaitingFamilyDueBy(LocalDateTime now);
}
