package sg.nus.carelink.report.application;

import java.time.LocalDateTime;

/** Application port: inform managers of every approved extra-service work order, which they assign. */
public interface ValueAddedManagerAlert {
    void approved(Long visitId, Long elderId, String serviceName, LocalDateTime start);

    /** A provider that had taken the work order on was removed: its new visit needs a caregiver. */
    void providerRemoved(Long visitId, Long elderId, String serviceName, LocalDateTime start, String providerName);
}
