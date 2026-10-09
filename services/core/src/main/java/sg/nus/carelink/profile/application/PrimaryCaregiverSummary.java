package sg.nus.carelink.profile.application;

import java.time.LocalDateTime;

/** An elder's primary caregiver as the Elders index shows it: who, and since when. */
public record PrimaryCaregiverSummary(Long caregiverId, String fullName, LocalDateTime assignedAt) {
}
