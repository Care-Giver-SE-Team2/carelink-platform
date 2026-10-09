package sg.nus.carelink.visit.domain.repository;

import java.util.Optional;

import sg.nus.carelink.visit.domain.model.VitalSign;

/**
 * Port for vital_sign: what the application layer may ask of storage, in domain terms.
 * Implemented by infrastructure.persistence.adapter.VitalSignRepositoryAdapter. Add finders as
 * the use cases need them; identity.domain.repository.AppUserRepository is the template.
 */
public interface VitalSignRepository {

	Optional<VitalSign> findById(Long id);

	VitalSign save(VitalSign vitalSign);
}
