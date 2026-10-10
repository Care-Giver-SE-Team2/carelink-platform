package sg.nus.carelink.visit.domain.model;

import java.time.LocalDateTime;

/** Durable, once-per-Visit trigger; no pending state or private narrative. */
public record MissedCheckInTrigger(Long visitId, Long incidentId, Long caregiverId,
        LocalDateTime scheduledStart, LocalDateTime dueAt, Integer observedVersion, LocalDateTime triggeredAt) {}
