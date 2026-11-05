package sg.nus.carelink.visit.domain.model;

import java.time.LocalDateTime;

/** Private metadata; never serialize storageKey to a browser. */
public record VisitEvidenceFile(Long evidenceId,Long uploaderUserId,String storageKey,String contentType,
        long byteSize,String sha256,String originalSha256,String fileName,Long taskId,LocalDateTime submittedAt) { }
