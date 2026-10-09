package sg.nus.carelink.report.infrastructure.persistence.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import sg.nus.carelink.report.infrastructure.persistence.entity.ValueAddedServiceJpaEntity;

/** Spring Data repository for value_added_service. */
public interface ValueAddedServiceJpaRepository extends JpaRepository<ValueAddedServiceJpaEntity, Long> {
    List<ValueAddedServiceJpaEntity> findByStatusOrderByNameAsc(ValueAddedServiceJpaEntity.Status status);
}
