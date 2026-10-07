package sg.nus.carelink.profile.domain.model;

import java.time.LocalDateTime;

/**
 * The caregiver a manager has named as an elder's primary caregiver. One per elder at most;
 * reassigning replaces it and unassigning removes it. Independent of per-visit rostering.
 */
public record PrimaryCaregiverAssignment(Long elderId, Long caregiverId, LocalDateTime assignedAt) {
}
