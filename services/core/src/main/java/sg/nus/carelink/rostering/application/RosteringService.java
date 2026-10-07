package sg.nus.carelink.rostering.application;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.rostering.domain.model.RosteringRun;
import sg.nus.carelink.rostering.domain.repository.RosteringRunRepository;

/**
 * Application layer of the rostering module (caregiver availability and absences, rostering runs, candidates and constraint checks).
 *
 * <p>One public method per use case (UC-MG03, UC-MG04, UC-CG02): it loads what it needs through
 * the domain ports, calls the domain model, saves, and returns. Business rules stay in
 * domain.model. identity.application.IdentityService is the template.
 */
@Service
@Transactional
public class RosteringService {

	private final RosteringRunRepository rosteringRuns;

	public RosteringService(RosteringRunRepository rosteringRuns) {
		this.rosteringRuns = rosteringRuns;
	}

	@Transactional(readOnly = true)
	public Optional<RosteringRun> findRosteringRun(Long id) {
		return rosteringRuns.findById(id);
	}
}
