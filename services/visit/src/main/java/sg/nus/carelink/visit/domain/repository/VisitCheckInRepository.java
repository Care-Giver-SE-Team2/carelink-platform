package sg.nus.carelink.visit.domain.repository;

import java.time.LocalDateTime;
import java.util.UUID;

public interface VisitCheckInRepository {
    void save(Long visitId, Long actor, UUID key, LocalDateTime receivedAt);
}
