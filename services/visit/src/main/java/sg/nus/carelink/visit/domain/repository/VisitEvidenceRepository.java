package sg.nus.carelink.visit.domain.repository;

import java.util.Optional;

import sg.nus.carelink.visit.domain.model.VisitEvidence;

/**
 * Port for visit_evidence: what the application layer may ask of storage, in domain terms.
 * Implemented by infrastructure.persistence.adapter.VisitEvidenceRepositoryAdapter. Add finders as
 * the use cases need them; identity.domain.repository.AppUserRepository is the template.
 */
public interface VisitEvidenceRepository {

	Optional<VisitEvidence> findById(Long id);

	VisitEvidence save(VisitEvidence visitEvidence);
}
