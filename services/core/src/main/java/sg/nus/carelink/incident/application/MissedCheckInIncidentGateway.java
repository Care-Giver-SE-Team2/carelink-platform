package sg.nus.carelink.incident.application;

import java.time.LocalDateTime;

/** Application contract: visit owns eligibility/locking, incident owns MG05 and FM05 routing. */
public interface MissedCheckInIncidentGateway {
    Long raise(Long elderId, Long visitId, LocalDateTime dueAt, LocalDateTime observedAt);
}
