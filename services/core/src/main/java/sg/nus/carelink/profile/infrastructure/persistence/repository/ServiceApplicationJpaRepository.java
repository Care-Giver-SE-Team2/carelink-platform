package sg.nus.carelink.profile.infrastructure.persistence.repository;

import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import sg.nus.carelink.profile.infrastructure.persistence.entity.ServiceApplicationJpaEntity;

public interface ServiceApplicationJpaRepository extends JpaRepository<ServiceApplicationJpaEntity, Long> {
    long countByApplicantFamilyMemberIdAndElderIdIn(Long familyId, Set<Long> elderIds);
    List<ServiceApplicationJpaEntity> findByApplicantFamilyMemberIdAndElderIdIn(
            Long familyId, Set<Long> elderIds, Pageable pageable);
    List<ServiceApplicationJpaEntity> findByElderIdOrderByCreatedAtDescIdDesc(Long elderId);
}
