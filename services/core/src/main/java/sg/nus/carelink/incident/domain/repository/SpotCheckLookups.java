package sg.nus.carelink.incident.domain.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * What a spot check needs to know about tables other modules own: the visit being watched,
 * an elder's upcoming visits to choose from, and which family member an account belongs to.
 *
 * <p>Read through plain statements in infrastructure, the way the incident alerts read visits
 * and bindings: the visit module already depends on this one (a disputed visit raises an
 * incident), so depending back on it would make a cycle. Reading is not owning.
 */
public interface SpotCheckLookups {

	Optional<VisitFacts> visit(Long visitId);

	/** The elder's visits with somebody on them that have not started, in [from, until), earliest first. */
	List<VisitFacts> upcomingVisits(Long elderId, LocalDateTime from, LocalDateTime until);

	/** The family member profile of an account, if it has one. */
	Optional<Long> familyMemberIdOf(Long userId);

	/** When the family was last asked about this request, first or again; empty if never. */
	Optional<LocalDateTime> lastAskedAt(Long checkId);

	/** A visit as a spot check sees it. */
	record VisitFacts(Long visitId, Long elderId, Long caregiverId, LocalDateTime start, String status,
			String serviceType) {

		public boolean isScheduled() {
			return "SCHEDULED".equals(status);
		}
	}
}
