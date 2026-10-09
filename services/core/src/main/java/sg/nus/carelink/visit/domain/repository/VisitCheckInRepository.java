package sg.nus.carelink.visit.domain.repository;

import java.time.LocalDateTime;
import java.util.UUID;
import sg.nus.carelink.visit.domain.model.CheckInLocation;

public interface VisitCheckInRepository {
    java.util.Optional<String> source(Long visitId);
    void save(Long visitId, Long actor, UUID key, CheckInLocation location, LocalDateTime receivedAt);
}
