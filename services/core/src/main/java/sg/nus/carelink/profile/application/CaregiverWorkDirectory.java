package sg.nus.carelink.profile.application;

import java.time.LocalDate;
import java.util.List;

/** Minimal cross-module projections for an authenticated caregiver's assigned work. */
public interface CaregiverWorkDirectory {
    Profile require(String username);
    ElderView elder(Long elderId);
    CredentialAlerts alerts(Long caregiverId, LocalDate today);

    record Profile(Long id, Long userId, String fullName, String phone, String sector, String dialects, String status) {
        /** Work access is separate from authentication and optional qualification uploads. */
        public Profile requireWorking() {
            if (!"AVAILABLE".equals(status) && !"BUSY".equals(status))
                throw new org.springframework.security.access.AccessDeniedException("Manager admission approval is required for caregiver work.");
            return this;
        }
        public Profile requireAdmittedHistory() {
            if (!List.of("AVAILABLE", "BUSY", "INACTIVE").contains(status == null ? "" : status))
                throw new org.springframework.security.access.AccessDeniedException("Manager admission approval is required.");
            return this;
        }
    }
    record ElderView(Long elderId, String preferredName, String serviceAddress, String postalSector,
                     List<String> languageNeeds, String accessNotes, String emergencyNotes) {}
    record CredentialAlert(Long id, String name, String certificateNo, LocalDate expiryDate, String status, String warning,
                           long daysUntilExpiry, String renewalState, LocalDate renewalValidFrom) {}
    record CredentialAlertContext(LocalDate asOfDate, int warningDays, boolean reviewRequired) {}
    record CredentialAlerts(List<CredentialAlert> items, CredentialAlertContext context) {}
}
