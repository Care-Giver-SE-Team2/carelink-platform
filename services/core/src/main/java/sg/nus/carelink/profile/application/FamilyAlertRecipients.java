package sg.nus.carelink.profile.application;

import java.util.List;

/** Current family alert eligibility across the profile boundary. @author Wang Zhili */
public interface FamilyAlertRecipients {
	List<Long> familyMemberIds(Long elderId);
	Candidate resolve(Long elderId, Long familyMemberId);
	record Candidate(Long familyMemberId, Long userId, String exclusionReason) {
		public boolean eligible() { return exclusionReason == null; }
	}
}
