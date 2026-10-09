package sg.nus.carelink.incident.infrastructure.persistence.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import sg.nus.carelink.incident.infrastructure.persistence.entity.IncidentLogJpaEntity;

/** Spring Data repository for incident_log. Used by persistence.adapter only; never exposed outwards. */
public interface IncidentLogJpaRepository extends JpaRepository<IncidentLogJpaEntity, Long> {

	/**
	 * The timeline of one incident, oldest first.
	 *
	 * <p>Ordered by id as well as by time because several entries can share a timestamp -
	 * an escalation writes the timeout, the hand-off and the notification in one
	 * transaction - and a timeline that reorders itself between two reads is not evidence.
	 */
	List<IncidentLogJpaEntity> findByIncidentIdOrderByOccurredAtAscIdAsc(Long incidentId);
}
