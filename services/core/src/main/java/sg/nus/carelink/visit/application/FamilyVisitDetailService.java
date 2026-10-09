package sg.nus.carelink.visit.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import org.springframework.stereotype.Service;

import sg.nus.carelink.profile.application.FamilyAccessQuery;
import sg.nus.carelink.profile.application.FamilyReadAudit;
import sg.nus.carelink.shared.error.ResourceNotFound;
import sg.nus.carelink.visit.domain.model.Visit;
import sg.nus.carelink.visit.domain.repository.VisitRepository;

/**
 * Reads a visit under the family's current binding and records the access outcome.
 *
 * @author Wang Zhili
 */
@Service
public class FamilyVisitDetailService {

	private final VisitRepository visits;
	private final FamilyAccessQuery access;
	private final FamilyReadAudit audit;
	private final Clock clock;

	public FamilyVisitDetailService(VisitRepository visits, FamilyAccessQuery access, FamilyReadAudit audit, Clock clock) {
		this.visits = visits;
		this.access = access;
		this.audit = audit;
		this.clock = clock;
	}

	/** Returns stored facts only after checking the visit's actual elder, on every request. */
	public Detail findDetail(String username, Long id) {
		return audit.read(username, FamilyReadAudit.Resource.VISIT_DETAIL, id, "", () -> {
			OffsetDateTime asOf = OffsetDateTime.now(clock.withZone(ZoneId.of("Asia/Singapore")));
			Visit visit = visits.findById(id).orElseThrow(() -> new ResourceNotFound("Visit", id));
			access.requireReadableElder(username, visit.elderId());
			return new Detail(visit, asOf);
		});
	}

	/** A permitted visit and its server read time, for the controller's family projection. */
	public record Detail(Visit visit, OffsetDateTime asOf) {
	}
}
