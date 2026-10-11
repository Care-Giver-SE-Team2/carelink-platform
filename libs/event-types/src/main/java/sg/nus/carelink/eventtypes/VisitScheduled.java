package sg.nus.carelink.eventtypes;

import java.time.OffsetDateTime;

/**
 * A visit was created: from a care plan, as a standalone visit, or as the new time of a moved visit.
 * Published by visit in the transaction that saves it.
 *
 * @param caregiverId null while nobody is on the visit
 * @param carePlanId null for a standalone visit, as is {@code carePlanNodeId}
 * @param scheduledEnd may be null
 * @param origin {@code PLAN}, {@code STANDALONE} or {@code MOVED}
 * @param movedFromVisitId the visit it replaces, when {@code origin} is {@code MOVED}
 * @param version the visit's version; every visit event carries it, for ordering
 */
public record VisitScheduled(Long visitId, Long elderId, Long caregiverId, Long carePlanId, Long carePlanNodeId,
		String serviceType, OffsetDateTime scheduledStart, OffsetDateTime scheduledEnd, String origin,
		Long movedFromVisitId, Integer version) {

	/** The event's name in the catalogue. */
	public static final String TYPE = "VisitScheduled";

}
