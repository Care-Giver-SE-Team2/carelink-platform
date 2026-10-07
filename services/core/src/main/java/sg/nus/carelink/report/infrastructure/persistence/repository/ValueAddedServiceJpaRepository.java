package sg.nus.carelink.report.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import sg.nus.carelink.report.infrastructure.persistence.entity.ValueAddedServiceJpaEntity;

/** Spring Data repository for value_added_service. Used by persistence.adapter only; never exposed outwards. */
public interface ValueAddedServiceJpaRepository extends JpaRepository<ValueAddedServiceJpaEntity, Long> {
}
