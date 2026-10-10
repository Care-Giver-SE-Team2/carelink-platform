package sg.nus.carelink.visit.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import sg.nus.carelink.visit.infrastructure.persistence.entity.VitalSignJpaEntity;

/** Spring Data repository for vital_sign. Used by persistence.adapter only; never exposed outwards. */
public interface VitalSignJpaRepository extends JpaRepository<VitalSignJpaEntity, Long> {
}
