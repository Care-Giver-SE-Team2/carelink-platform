package sg.nus.carelink.careplan.infrastructure.persistence.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import sg.nus.carelink.careplan.infrastructure.persistence.entity.CarePlanNodeJpaEntity;

/** Spring Data repository for care_plan_node. Used by persistence.adapter only; never exposed outwards. */
public interface CarePlanNodeJpaRepository extends JpaRepository<CarePlanNodeJpaEntity, Long> {

	List<CarePlanNodeJpaEntity> findByCarePlanId(Long carePlanId);

	void deleteByCarePlanId(Long carePlanId);
}
