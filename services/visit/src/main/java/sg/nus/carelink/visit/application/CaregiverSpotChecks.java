package sg.nus.carelink.visit.application;

import java.time.LocalDateTime;
import java.util.List;

/** The spot checks core's incident module concluded on a caregiver's visit, which their own history shows. */
public interface CaregiverSpotChecks {

	/**
	 * The signed-in caregiver's own concluded checks on this visit.
	 *
	 * @throws org.springframework.security.access.AccessDeniedException when the account is not an admitted caregiver's
	 */
	List<Conclusion> forVisit(String username, Long visitId);

	/** @param result null until the check is concluded */
	record Conclusion(Long id, Long visitId, String result, LocalDateTime checkedAt, String caregiverResponse) {
	}

}
