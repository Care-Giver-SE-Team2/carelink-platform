package sg.nus.carelink.incident.infrastructure.lookup;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import sg.nus.carelink.incident.domain.repository.SpotCheckLookups.VisitFacts;

/**
 * The visits a spot check is about, which visit owns: one visit, or an elder's coming ones. One
 * adapter asks visit's service while visit runs inside core, the other asks visit's internal API
 * once it runs on its own.
 */
interface SpotCheckVisits {

	Optional<VisitFacts> visit(Long visitId);

	/** The elder's scheduled, assigned visits starting in [from, until), earliest first. */
	List<VisitFacts> upcomingVisits(Long elderId, LocalDateTime from, LocalDateTime until);

}
