package sg.nus.carelink.careplan.domain.repository;

import java.util.List;
import java.util.Optional;

import sg.nus.carelink.careplan.domain.model.CarePlan;

/**
 * Port for care_plan: what the application layer may ask of storage, in domain terms.
 * Implemented by infrastructure.persistence.adapter.CarePlanRepositoryAdapter. Add finders as
 * the use cases need them; identity.domain.repository.AppUserRepository is the template.
 */
public interface CarePlanRepository {

	Optional<CarePlan> findById(Long id);

	/** The elder's highest-version plan (draft, published or superseded), if any. */
	Optional<CarePlan> findLatestByElderId(Long elderId);

	/** Every version of the elder's plan, drafts included, lowest version first. */
	List<CarePlan> findByElderId(Long elderId);

	/** Elders with at least one plan that has left draft (published, superseded or stopped). */
	List<Long> findElderIdsWithIssuedPlans();

	CarePlan save(CarePlan carePlan);
}
