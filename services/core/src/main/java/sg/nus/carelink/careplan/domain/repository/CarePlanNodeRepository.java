package sg.nus.carelink.careplan.domain.repository;

import java.util.List;
import java.util.Optional;

import sg.nus.carelink.careplan.domain.model.CarePlanNode;

/**
 * Port for care_plan_node: what the application layer may ask of storage, in domain terms.
 * Implemented by infrastructure.persistence.adapter.CarePlanNodeRepositoryAdapter. Add finders as
 * the use cases need them; identity.domain.repository.AppUserRepository is the template.
 */
public interface CarePlanNodeRepository {

	Optional<CarePlanNode> findById(Long id);

	/** Every node belonging to a plan, parents and children alike, in no particular order. */
	List<CarePlanNode> findByCarePlanId(Long carePlanId);

	CarePlanNode save(CarePlanNode carePlanNode);

	/** Clears a plan's node tree before a fresh publish writes it back. */
	void deleteByCarePlanId(Long carePlanId);
}
