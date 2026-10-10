package sg.nus.carelink.report.application;

import java.util.List;
import java.util.Optional;

/** What a family may see of a caregiver, from core. */
public interface CaregiverDirectory {

	Optional<CaregiverPublicProfile> findPublicProfile(Long caregiverId);

	record CaregiverPublicProfile(Long id, String fullName, List<String> dialects) {

		public CaregiverPublicProfile {
			dialects = List.copyOf(dialects);
		}

	}

}
