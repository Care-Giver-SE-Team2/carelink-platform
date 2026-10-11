package sg.nus.carelink.visit.domain.repository;

import java.util.List;
import java.util.Set;

import sg.nus.carelink.visit.domain.model.VisitPage;
import sg.nus.carelink.visit.domain.model.VisitScheduleFilter;

/** Reads visits within an authorized set of elders. */
public interface VisitScheduleQuery {

    VisitPage findForElders(
            Set<Long> elderIds,
            VisitScheduleFilter filter,
            VisitScheduleFilter.DateRange dates);

    /** Checks a caregiver's current or historical visit relationship with readable elders. */
    boolean hasAssignedVisit(Set<Long> elderIds, Long caregiverId);

    /** Returns caregivers that have a current or historical visit relationship with one elder. */
    List<Long> caregiverIdsForElder(Long elderId);
}
