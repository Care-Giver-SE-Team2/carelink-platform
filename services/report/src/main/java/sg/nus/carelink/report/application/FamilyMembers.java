package sg.nus.carelink.report.application;

import java.util.Optional;

/** The family member profiles core keeps for family accounts. */
public interface FamilyMembers {

	/** The family member profile of an account, or empty when the account has none. */
	Optional<Long> findIdByUserId(Long userId);

}
