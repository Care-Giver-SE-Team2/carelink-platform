package sg.nus.carelink.profile.domain.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import sg.nus.carelink.profile.domain.model.Credential;
import sg.nus.carelink.profile.domain.model.CredentialDocument;
import sg.nus.carelink.profile.domain.model.CredentialType;

/** Database operations participate in the caller's submission transaction. */
public interface CredentialSubmissionStore {
    record Result(Long credentialId, String status, LocalDateTime submittedAt, List<Long> documentIds) { }
    record Receipt(String payloadHash, Result result) { }
    void lockCaregiver(Long caregiverId, LocalDateTime now);
    List<CredentialType> types();
    Optional<Receipt> receipt(Long actorId, String requestId);
    Long insertCredential(Credential credential);
    Long insertDocument(CredentialDocument document);
    void saveSubmission(Long actorId, String requestId, String action, Long resubmitsId,
            String payloadHash, Result result);
    Map<Long, Long> resubmissions(Long caregiverId);
    List<CredentialDocument> documents(Long credentialId);
    void audit(Long actorId, String action, Long credentialId, LocalDateTime now);
}
