package sg.nus.carelink.visit.application;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** What a family may see of a caregiver and their certificates, from core. */
public interface CaregiverDirectory {

	Optional<CaregiverPublicProfile> findPublicProfile(Long caregiverId);

	List<CaregiverPublicCredential> listPublicCredentials(Long caregiverId);

	record CaregiverPublicProfile(Long id, String fullName, List<String> dialects) {

		public CaregiverPublicProfile {
			dialects = List.copyOf(dialects);
		}

	}

	record CaregiverPublicCredential(Long id, Long caregiverId, Long credentialTypeId, String credentialTypeName,
			String issuingBody, LocalDate validFrom, LocalDate expiryDate, String status) {
	}

}
