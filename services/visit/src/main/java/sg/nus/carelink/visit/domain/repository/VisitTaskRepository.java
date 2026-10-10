package sg.nus.carelink.visit.domain.repository;

import java.util.Optional;

import sg.nus.carelink.visit.domain.model.VisitTask;

/**
 * Port for visit_task: what the application layer may ask of storage, in domain terms.
 * Implemented by infrastructure.persistence.adapter.VisitTaskRepositoryAdapter. Add finders as
 * the use cases need them; identity.domain.repository.AppUserRepository is the template.
 */
public interface VisitTaskRepository {
	java.util.List<VisitTask> findByVisitId(Long visitId);

	Optional<VisitTask> findById(Long id);

	VisitTask save(VisitTask visitTask);
}
