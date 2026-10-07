package sg.nus.carelink.incident.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import sg.nus.carelink.incident.infrastructure.persistence.entity.IncidentAcknowledgementJpaEntity;

/** Spring Data repository for incident_acknowledgement. Used by persistence.adapter only; never exposed outwards. */
public interface IncidentAcknowledgementJpaRepository extends JpaRepository<IncidentAcknowledgementJpaEntity, Long> {
}
