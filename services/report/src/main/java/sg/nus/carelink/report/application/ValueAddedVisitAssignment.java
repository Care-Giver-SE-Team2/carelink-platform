package sg.nus.carelink.report.application;

import java.time.LocalDateTime;
import java.util.Optional;

/** Application port: determine whether an elder's primary caregiver can cover a new visit in [start, end). */
public interface ValueAddedVisitAssignment {
    Optional<Long> chooseCaregiver(Long elderId, LocalDateTime start, LocalDateTime end);
}
