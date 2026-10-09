package sg.nus.carelink.rostering.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import sg.nus.carelink.rostering.infrastructure.persistence.entity.RosteringRunJpaEntity;

/** Spring Data repository for rostering_run. Used by persistence.adapter only; never exposed outwards. */
public interface RosteringRunJpaRepository extends JpaRepository<RosteringRunJpaEntity, Long> {
}
