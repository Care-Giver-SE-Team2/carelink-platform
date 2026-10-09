package sg.nus.carelink.careplan.infrastructure.persistence.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import sg.nus.carelink.careplan.infrastructure.persistence.entity.CarePlanJpaEntity;

/** Spring Data repository for care_plan. Used by persistence.adapter only; never exposed outwards. */
public interface CarePlanJpaRepository extends JpaRepository<CarePlanJpaEntity, Long> {

	Optional<CarePlanJpaEntity> findFirstByElderIdOrderByVersionDesc(Long elderId);

	List<CarePlanJpaEntity> findByElderIdOrderByVersionAsc(Long elderId);

	@Query("select distinct p.elderId from CarePlanJpaEntity p where p.status <> sg.nus.carelink.careplan.infrastructure.persistence.entity.CarePlanJpaEntity.Status.DRAFT")
	List<Long> findElderIdsWithIssuedPlans();
}
