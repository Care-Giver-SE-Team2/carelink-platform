package sg.nus.carelink.visit.application;

import java.time.LocalDateTime;

/** Raising the incident for a missed check-in in core (SYS03); visit keeps the incident's id. */
public interface MissedCheckInIncidentGateway {

	/** @return the incident's id */
	Long raise(Long elderId, Long visitId, LocalDateTime dueAt, LocalDateTime observedAt);

}
