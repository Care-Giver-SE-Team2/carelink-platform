package sg.nus.carelink.visit.controller.dto;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import sg.nus.carelink.visit.domain.model.Visit;

/**
 * Exposes family-visible visit fields with Singapore offsets and a server read time.
 *
 * @author Wang Zhili
 */
public record FamilyVisitResponse(Long id, Long elderId, Long caregiverId, String serviceType,
		OffsetDateTime scheduledStart, OffsetDateTime scheduledEnd, OffsetDateTime checkedInAt,
		OffsetDateTime checkedOutAt, Visit.Status status, OffsetDateTime asOf) {

	public static FamilyVisitResponse from(Visit visit, OffsetDateTime asOf) {
		return new FamilyVisitResponse(visit.id(), visit.elderId(), visit.caregiverId(), visit.serviceType(),
				atSingapore(visit.scheduledStart()), atSingapore(visit.scheduledEnd()),
				atSingapore(visit.checkedInAt()), atSingapore(visit.checkedOutAt()), visit.status(), asOf);
	}

	private static OffsetDateTime atSingapore(LocalDateTime time) {
		return time == null ? null : time.atZone(ZoneId.of("Asia/Singapore")).toOffsetDateTime();
	}
}
