package sg.nus.carelink.rostering.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import sg.nus.carelink.rostering.infrastructure.persistence.entity.RosteringConstraintJpaEntity;

/** Spring Data repository for rostering_constraint. Used by persistence.adapter only; never exposed outwards. */
public interface RosteringConstraintJpaRepository extends JpaRepository<RosteringConstraintJpaEntity, Long> {
}
