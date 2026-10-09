package sg.nus.carelink.incident.infrastructure.persistence.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import sg.nus.carelink.incident.infrastructure.persistence.entity.IncidentAcknowledgementJpaEntity;

/** Spring Data repository for incident_acknowledgement. Used by persistence.adapter only; never exposed outwards. */
public interface IncidentAcknowledgementJpaRepository extends JpaRepository<IncidentAcknowledgementJpaEntity, Long> {

	Optional<IncidentAcknowledgementJpaEntity> findByIncidentIdAndFamilyMemberId(Long incidentId, Long familyMemberId);

	@Modifying
	@Query(value = "INSERT INTO incident_acknowledgement (incident_id, family_member_id) VALUES (:incidentId, :familyMemberId) ON DUPLICATE KEY UPDATE id = id", nativeQuery = true)
	void ensurePair(@Param("incidentId") Long incidentId, @Param("familyMemberId") Long familyMemberId);

	// A locking current read also sees the row committed while ensurePair waited on its unique key.
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from IncidentAcknowledgementJpaEntity a where a.incidentId = :incidentId and a.familyMemberId = :familyMemberId")
	IncidentAcknowledgementJpaEntity lockPair(@Param("incidentId") Long incidentId, @Param("familyMemberId") Long familyMemberId);
}
