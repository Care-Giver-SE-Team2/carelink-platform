package sg.nus.carelink.rostering.domain.repository;

import java.util.Optional;

import sg.nus.carelink.rostering.domain.model.CaregiverPreference;

/**
 * Port for caregiver_preference: what the application layer may ask of storage, in domain terms.
 * Implemented by infrastructure.persistence.adapter.CaregiverPreferenceRepositoryAdapter. Add finders as
 * the use cases need them; identity.domain.repository.AppUserRepository is the template.
 */
public interface CaregiverPreferenceRepository {

	Optional<CaregiverPreference> findById(Long id);

	CaregiverPreference save(CaregiverPreference caregiverPreference);
}
