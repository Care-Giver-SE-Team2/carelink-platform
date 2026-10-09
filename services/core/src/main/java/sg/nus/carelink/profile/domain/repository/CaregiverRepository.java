package sg.nus.carelink.profile.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import sg.nus.carelink.profile.domain.model.Caregiver;

/**
 * Port for caregiver: what the application layer may ask of storage, in domain terms.
 * Implemented by infrastructure.persistence.adapter.CaregiverRepositoryAdapter. Add finders as
 * the use cases need them; identity.domain.repository.AppUserRepository is the template.
 */
public interface CaregiverRepository {

	Optional<Caregiver> findByUserId(Long userId);

	Optional<Caregiver> findById(Long id);

	/** Every caregiver, in ascending name order. */
	List<Caregiver> findAll();

	List<Caregiver> findByIds(Set<Long> ids);

	Caregiver save(Caregiver caregiver);
}
