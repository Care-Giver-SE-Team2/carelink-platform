package sg.nus.carelink.rostering.application;

import java.util.List;

/**
 * Cross-module contract: a manager puts somebody on one visit nobody holds, such as an extra
 * service the family approved when the elder's primary caregiver was not free. The same hard
 * rules and ranking as an absence's replacement search decide who may go, so a visit covered
 * by hand is held to what one covered by re-rostering is. Other modules import this interface
 * only, never rostering's domain.
 */
public interface VisitCover {

	/**
	 * Every caregiver considered for the visit: those who pass every hard rule, best first, then
	 * those who do not, each with the rule that stopped them.
	 *
	 * @throws sg.nus.carelink.shared.error.ResourceNotFound when there is no such visit
	 * @throws sg.nus.carelink.shared.error.BusinessRuleViolation when the visit has a caregiver
	 *     or has started
	 */
	List<Option> options(Long visitId);

	/**
	 * Puts the caregiver on the visit, after checking again that they pass every hard rule -
	 * their day may have filled up since the options were read.
	 *
	 * @param byUserId the manager making the change, for the visit's assignment history
	 */
	void cover(Long visitId, Long caregiverId, Long byUserId);

	/**
	 * One caregiver's result.
	 *
	 * @param rank 1 for the best suggestion; null when a hard rule excluded them
	 * @param reason why they are suggested, or what excluded them
	 */
	record Option(Long caregiverId, String name, Integer rank, String reason) {

		public boolean eligible() {
			return rank != null;
		}
	}
}
