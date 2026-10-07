package sg.nus.carelink.profile.infrastructure.persistence.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import sg.nus.carelink.profile.infrastructure.persistence.entity.ElderFamilyBindingJpaEntity;

/**
 * Spring Data repository for elder_family_binding.
 * Used by persistence adapters only.
 */
public interface ElderFamilyBindingJpaRepository
        extends JpaRepository<ElderFamilyBindingJpaEntity, Long> {

    List<ElderFamilyBindingJpaEntity>
            findByElderIdOrderByCreatedAtDesc(Long elderId);

    List<ElderFamilyBindingJpaEntity> findByFamilyMemberIdOrderByElderIdAsc(Long familyMemberId);

    Optional<ElderFamilyBindingJpaEntity>
            findByElderIdAndFamilyMemberId(
                    Long elderId,
                    Long familyMemberId
            );
}
