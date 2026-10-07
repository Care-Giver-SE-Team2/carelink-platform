package sg.nus.carelink.visit.domain.repository;

import java.util.Set;

import sg.nus.carelink.visit.domain.model.VisitPage;
import sg.nus.carelink.visit.domain.model.VisitScheduleFilter;

/**
 * Reads visits within an authorized set of elders.
 *
 * @author Wang Zhili
 */
public interface VisitScheduleQuery {

	/**
	 * Queries and counts the same filtered scope before applying pagination.
	 *
	 * @param elderIds Authorized elder IDs selected for this request
	 * @param filter Optional caregiver and status filters, and pagination
	 * @param dates Resolved local timestamp bounds
	 * @return Matching visits ordered by scheduled start and ID, both ascending
	 * @author Wang Zhili
	 */
	VisitPage findForElders(Set<Long> elderIds, VisitScheduleFilter filter, VisitScheduleFilter.DateRange dates);

	/**
	 * Checks a caregiver's current or historical visit relationship with readable elders.
	 *
	 * @param elderIds Currently readable elder IDs
	 * @param caregiverId Caregiver profile ID
	 * @return Whether a visit assigns this caregiver to any of these elders
	 * @author Wang Zhili
	 */
	boolean hasAssignedVisit(Set<Long> elderIds, Long caregiverId);
}
