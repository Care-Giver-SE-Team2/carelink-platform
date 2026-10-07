package sg.nus.carelink.careplan.domain.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import sg.nus.carelink.careplan.domain.model.CarePlanRequiredCredential;

/**
 * Port for care_plan_required_credential: what the application layer may ask of storage, in domain terms.
 * Implemented by infrastructure.persistence.adapter.CarePlanRequiredCredentialRepositoryAdapter. Add finders as
 * the use cases need them; identity.domain.repository.AppUserRepository is the template.
 */
public interface CarePlanRequiredCredentialRepository {

	Optional<CarePlanRequiredCredential> findById(CarePlanRequiredCredential.Id id);

	/** The credential types each of these plans requires, one row per plan and type. */
	List<CarePlanRequiredCredential> findByCarePlanIds(Collection<Long> carePlanIds);

	CarePlanRequiredCredential save(CarePlanRequiredCredential carePlanRequiredCredential);
}
