package sg.nus.carelink.report.application;

import java.util.List;
import java.util.Set;

/** Who visits whom, from visit: the caregiver relationships a family review rests on. */
public interface VisitScheduleQuery {

	/** Whether the caregiver has a visit with any of the elders. */
	boolean hasAssignedVisit(Set<Long> elderIds, Long caregiverId);

	/** The caregivers who have visited the elder. */
	List<Long> caregiverIdsForElder(Long elderId);

}
