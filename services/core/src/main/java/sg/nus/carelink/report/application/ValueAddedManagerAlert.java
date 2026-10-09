package sg.nus.carelink.report.application;

import java.time.LocalDateTime;

/** Application port: inform managers of every approved extra-service work order. */
public interface ValueAddedManagerAlert {
    void approved(Long visitId, Long elderId, String serviceName, LocalDateTime start, Long caregiverId);
}
