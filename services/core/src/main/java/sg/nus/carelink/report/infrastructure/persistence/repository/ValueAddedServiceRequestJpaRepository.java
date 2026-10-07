package sg.nus.carelink.report.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import sg.nus.carelink.report.infrastructure.persistence.entity.ValueAddedServiceRequestJpaEntity;

/** Spring Data repository for value_added_service_request. Used by persistence.adapter only; never exposed outwards. */
public interface ValueAddedServiceRequestJpaRepository extends JpaRepository<ValueAddedServiceRequestJpaEntity, Long> {
}
