package sg.nus.carelink.eventtypes;
import java.time.OffsetDateTime;
/** Counts only, no storage references, filenames or image payload. Snapshot counts make retries idempotent. */
public record VisitEvidenceUpdated(Long visitId,Long elderId,OffsetDateTime scheduledStart,Integer version,int evidenceCount,int verifiedEvidenceCount) {
 public static final String TYPE="VisitEvidenceUpdated";
 public VisitEvidenceUpdated {if(visitId==null || elderId==null || scheduledStart==null || version==null || version<0 || evidenceCount<0 || verifiedEvidenceCount<0 || verifiedEvidenceCount>evidenceCount)throw new IllegalArgumentException("Invalid evidence snapshot");}
}
