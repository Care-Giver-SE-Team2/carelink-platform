package sg.nus.carelink.rostering.domain.repository;

import java.util.Optional;

import sg.nus.carelink.rostering.domain.model.CaregiverAvailability;

/**
 * Port for caregiver_availability: what the application layer may ask of storage, in domain terms.
 * Implemented by infrastructure.persistence.adapter.CaregiverAvailabilityRepositoryAdapter. Add finders as
 * the use cases need them; identity.domain.repository.AppUserRepository is the template.
 */
public interface CaregiverAvailabilityRepository {

	Optional<CaregiverAvailability> findById(Long id);

	CaregiverAvailability save(CaregiverAvailability caregiverAvailability);
}
