package sg.nus.carelink.profile.domain.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import sg.nus.carelink.profile.domain.model.Caregiver;
import sg.nus.carelink.profile.domain.model.CaregiverApplication;
import sg.nus.carelink.profile.domain.model.CaregiverApplication.*;

/** All mutations participate in the caller's transaction. */
public interface CaregiverAdmissionStore {
    Caregiver lockCaregiver(Long caregiverId);
    long version(Long caregiverId);
    void advance(Long caregiverId);
    Optional<CaregiverApplication> findByCaregiver(Long caregiverId);
    Optional<CaregiverApplication> findById(Long applicationId);
    List<CaregiverApplication> pending(int offset, int size);
    long pendingCount();
    CaregiverApplication save(CaregiverApplication application);
    void history(CaregiverApplication application, Long actorId, String action, long version, LocalDateTime now);
    List<History> history(Long applicationId);
    Optional<Receipt> receipt(Long actorId, String requestId);
    void receipt(Long actorId, String requestId, String hash, View result);
    void approveProfile(Long caregiverId, Details details);
    void contact(Long caregiverId, String phone);
    void auditContact(Long caregiverId, Long actorId, LocalDateTime now);
}
