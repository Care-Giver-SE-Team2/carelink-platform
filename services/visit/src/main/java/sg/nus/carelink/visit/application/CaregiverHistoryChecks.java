package sg.nus.carelink.visit.application;
import java.time.LocalDateTime;
import java.util.List;
/** Own published spot-check conclusions from core; unavailable core must fail the history request. */
public interface CaregiverHistoryChecks {
 record Check(Long id,Long visitId,String result,LocalDateTime checkedAt,String caregiverResponse) {}
 List<Check> forCaregiver(String username);
}
