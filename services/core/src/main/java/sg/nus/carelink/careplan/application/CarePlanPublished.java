package sg.nus.carelink.careplan.application;

import java.time.LocalDate;

/**
 * Published after a care plan version is published, so the elder's family can be told what was
 * planned and when it starts. Profile listens; careplan cannot call it, since profile already
 * depends on careplan and the reverse call would make the two modules a cycle.
 */
public record CarePlanPublished(Long elderId, Long carePlanId, int version, LocalDate startDate) {
}
