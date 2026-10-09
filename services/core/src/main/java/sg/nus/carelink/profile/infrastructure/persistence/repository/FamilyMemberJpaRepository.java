package sg.nus.carelink.profile.infrastructure.persistence.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import sg.nus.carelink.profile.infrastructure.persistence.entity.FamilyMemberJpaEntity;

/** Spring Data repository for family_member. Used by persistence.adapter only; never exposed outwards. */
public interface FamilyMemberJpaRepository extends JpaRepository<FamilyMemberJpaEntity, Long> {

	Optional<FamilyMemberJpaEntity> findByUserId(Long userId);
}
