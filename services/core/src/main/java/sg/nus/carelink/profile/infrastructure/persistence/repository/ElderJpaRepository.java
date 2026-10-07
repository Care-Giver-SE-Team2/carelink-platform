package sg.nus.carelink.profile.infrastructure.persistence.repository;

import java.util.Optional;
import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;

import sg.nus.carelink.profile.infrastructure.persistence.entity.ElderJpaEntity;

/**
 * Spring Data repository for elder.
 * Used by persistence.adapter only; never exposed outwards.
 */
public interface ElderJpaRepository extends JpaRepository<ElderJpaEntity, Long> {

    List<ElderJpaEntity> findByIdInOrderByIdAsc(Set<Long> elderIds);

    /**
     * Finds an elder by the linked app_user id.
     *
     * Spring Data automatically translates this method into a query using
     * the userId property of ElderJpaEntity.
     */
    Optional<ElderJpaEntity> findByUserId(Long userId);

    List<ElderJpaEntity> findByPostalCode(String postalCode);
}
