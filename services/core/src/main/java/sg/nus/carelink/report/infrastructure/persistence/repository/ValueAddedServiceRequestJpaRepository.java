package sg.nus.carelink.report.infrastructure.persistence.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import sg.nus.carelink.report.infrastructure.persistence.entity.ValueAddedServiceRequestJpaEntity;

/** Spring Data repository for value_added_service_request. */
public interface ValueAddedServiceRequestJpaRepository extends JpaRepository<ValueAddedServiceRequestJpaEntity, Long> {
    List<ValueAddedServiceRequestJpaEntity> findByElderIdOrderByCreatedAtDesc(Long elderId);
    List<ValueAddedServiceRequestJpaEntity> findAllByOrderByCreatedAtDescIdDesc();
    List<ValueAddedServiceRequestJpaEntity> findByStatus(ValueAddedServiceRequestJpaEntity.Status status);
}
