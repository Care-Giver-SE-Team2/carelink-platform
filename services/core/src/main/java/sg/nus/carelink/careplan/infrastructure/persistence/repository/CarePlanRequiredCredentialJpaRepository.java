package sg.nus.carelink.careplan.infrastructure.persistence.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import sg.nus.carelink.careplan.infrastructure.persistence.entity.CarePlanRequiredCredentialJpaEntity;

/** Spring Data repository for care_plan_required_credential. Used by persistence.adapter only; never exposed outwards. */
public interface CarePlanRequiredCredentialJpaRepository extends JpaRepository<CarePlanRequiredCredentialJpaEntity, CarePlanRequiredCredentialJpaEntity.Id> {

	List<CarePlanRequiredCredentialJpaEntity> findByIdCarePlanIdIn(Collection<Long> carePlanIds);
}
