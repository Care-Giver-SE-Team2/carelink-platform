package sg.nus.carelink.incident.domain.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import sg.nus.carelink.incident.domain.model.SpotCheck;

/**
 * Port for spot_check: what the application layer may ask of storage, in domain terms.
 * Implemented by infrastructure.persistence.adapter.SpotCheckRepositoryAdapter. Add finders as
 * the use cases need them; identity.domain.repository.AppUserRepository is the template.
 */
public interface SpotCheckRepository {

	Optional<SpotCheck> findById(Long id);

	SpotCheck save(SpotCheck spotCheck);

	/** Every spot check, latest visit first. */
	List<SpotCheck> findAll();

	/** Spot checks of these elders' visits, latest visit first: what a family member is shown. */
	List<SpotCheck> findByElderIds(Collection<Long> elderIds);

	/** Spot checks of one caregiver, latest visit first. */
	List<SpotCheck> findByCaregiverId(Long caregiverId);

	/** Spot checks concluded on site since then: what rostering weighs. */
	List<SpotCheck> findConcludedSince(LocalDateTime since);
}
