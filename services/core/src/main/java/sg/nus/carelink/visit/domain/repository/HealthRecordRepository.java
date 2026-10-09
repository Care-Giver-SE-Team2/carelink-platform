package sg.nus.carelink.visit.domain.repository;

import java.util.List;
import java.util.Optional;
import sg.nus.carelink.visit.domain.model.HealthRecord;

public interface HealthRecordRepository {
    HealthRecord save(HealthRecord record, Long actorUserId);
    Optional<HealthRecord> find(Long visitId, Long recordId);
    List<HealthRecord> history(Long visitId, int offset, int limit);
    long count(Long visitId);
}
