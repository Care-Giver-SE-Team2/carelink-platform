package sg.nus.carelink.visit.domain.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import sg.nus.carelink.visit.domain.model.MissedCheckInTrigger;

public interface MissedCheckInRepository {
    record Candidate(Long visitId, LocalDateTime scheduledStart) {}
    List<Candidate> candidates(LocalDateTime since, LocalDateTime before, Candidate after, int limit);
    boolean exists(Long visitId);
    Optional<MissedCheckInTrigger> find(Long visitId);
    void save(MissedCheckInTrigger trigger);
}
