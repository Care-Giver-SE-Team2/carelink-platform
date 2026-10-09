package sg.nus.carelink.visit.application;

import java.time.OffsetDateTime;

import sg.nus.carelink.visit.domain.model.VisitPage;

/**
 * A readable visit page with a single server timestamp for this query.
 *
 * @author Wang Zhili
 */
public record FamilyVisitSchedule(VisitPage visits, OffsetDateTime asOf) {
}
