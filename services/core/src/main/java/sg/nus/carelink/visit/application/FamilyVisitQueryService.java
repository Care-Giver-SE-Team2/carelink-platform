package sg.nus.carelink.visit.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import sg.nus.carelink.profile.application.FamilyAccessQuery;
import sg.nus.carelink.profile.application.FamilyReadAudit;
import sg.nus.carelink.visit.domain.model.VisitPage;
import sg.nus.carelink.visit.domain.model.VisitScheduleFilter;
import sg.nus.carelink.visit.domain.repository.VisitScheduleQuery;

/**
 * Queries family schedules after validating current elder and caregiver access.
 *
 * @author Wang Zhili
 */
@Service
public class FamilyVisitQueryService {

	private final FamilyAccessQuery access;
	private final VisitScheduleQuery visits;
	private final Clock clock;
	private final FamilyReadAudit audit;

	public FamilyVisitQueryService(FamilyAccessQuery access, VisitScheduleQuery visits, Clock clock, FamilyReadAudit audit) {
		this.access = access;
		this.visits = visits;
		this.clock = clock;
		this.audit = audit;
	}

	/**
	 * Lists visits for the authenticated family's currently readable elders.
	 *
	 * @param authenticatedUsername Username supplied by the authenticated session
	 * @param filter Elder, caregiver, date, status and page selections
	 * @return Filtered visit page and its server snapshot time
	 * @author Wang Zhili
	 */
	public FamilyVisitSchedule listMine(String authenticatedUsername, VisitScheduleFilter filter) {
		OffsetDateTime asOf = OffsetDateTime.now(clock.withZone(ZoneId.of("Asia/Singapore")));
		var range = filter.dateRange(asOf.toLocalDate());
		String scope = "elderId=%s;caregiverId=%s;dateFrom=%s;dateTo=%s;status=%s;page=%d;size=%d".formatted(
				filter.elderId(), filter.caregiverId(), range.fromInclusive().toLocalDate(),
				range.toExclusive().toLocalDate().minusDays(1), filter.status(), filter.page(), filter.size());
		return audit.read(authenticatedUsername, FamilyReadAudit.Resource.VISITS, null, scope,
				() -> queryMine(authenticatedUsername, filter, asOf));
	}

	private FamilyVisitSchedule queryMine(String authenticatedUsername, VisitScheduleFilter filter, OffsetDateTime asOf) {
		Set<Long> readableElders = access.readableElderIds(authenticatedUsername);
		if (filter.elderId() != null && !readableElders.contains(filter.elderId())) {
			throw new AccessDeniedException("A readable elder binding is required");
		}
		if (filter.caregiverId() != null && !visits.hasAssignedVisit(readableElders, filter.caregiverId())) {
			throw new AccessDeniedException("A caregiver relationship with a readable elder is required");
		}
		if (readableElders.isEmpty()) {
			return new FamilyVisitSchedule(new VisitPage(List.of(), filter.page(), filter.size(), 0), asOf);
		}
		Set<Long> selectedElders = filter.elderId() == null ? readableElders : Set.of(filter.elderId());
		return new FamilyVisitSchedule(
				visits.findForElders(selectedElders, filter, filter.dateRange(asOf.toLocalDate())), asOf);
	}
}
