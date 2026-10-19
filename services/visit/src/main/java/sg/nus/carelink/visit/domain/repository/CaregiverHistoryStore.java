package sg.nus.carelink.visit.domain.repository;

import java.time.LocalDateTime;
import java.util.List;
import sg.nus.carelink.visit.domain.model.CaregiverCommandReceipt;

/** History entitlement is an actual actor fact, never just the Visit's current assignment. */
public interface CaregiverHistoryStore {
    record VisitSummary(Long visitId,String currentVisitStatus,LocalDateTime firstOwnActionAt,LocalDateTime lastOwnActionAt,
            LocalDateTime ownCheckInAt,LocalDateTime ownCheckOutAt,long commandCount) { }
    List<VisitSummary> visits(Long actor,LocalDateTime from,LocalDateTime until,String status,int offset,int size);
    long countVisits(Long actor,LocalDateTime from,LocalDateTime until,String status);
    List<CaregiverCommandReceipt> commands(Long actor,Long visitId,int offset,int size);
    long countCommands(Long actor,Long visitId);
}
