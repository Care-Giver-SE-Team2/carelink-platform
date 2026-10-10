package sg.nus.carelink.visit.domain.service;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import sg.nus.carelink.visit.domain.model.MissedCheckInTrigger;
import sg.nus.carelink.visit.domain.model.Visit;
import sg.nus.carelink.visit.domain.model.VisitStateTransition;

/** A ledger alone, a source enum alone, or a matching wall-clock second is not proof. */
public final class MissedCheckInResumePolicy {
    private MissedCheckInResumePolicy() {}
    public static boolean permits(Visit visit, MissedCheckInTrigger trigger,
            List<VisitStateTransition> history, boolean soleIncident) {
        if (trigger == null || !soleIncident || visit.status() != Visit.Status.EXCEPTION
                || visit.checkedInAt() != null || visit.checkedOutAt() != null || visit.absenceId() != null
                || !Objects.equals(visit.id(), trigger.visitId())
                || !Objects.equals(visit.caregiverId(), trigger.caregiverId())
                || !Objects.equals(visit.scheduledStart(), trigger.scheduledStart())
                || trigger.observedVersion() == null || visit.version() == null
                || visit.version().longValue() != trigger.observedVersion().longValue() + 1
                || history.isEmpty()) return false;
        var latest = history.getLast();
        return latest.result() == VisitStateTransition.Result.APPLIED
                && Objects.equals(latest.visitId(), visit.id()) && latest.actorUserId() == null
                && "SCHEDULED".equals(latest.fromState()) && "EXCEPTION".equals(latest.toState())
                && sameStoredSecond(latest, trigger);
    }
    private static boolean sameStoredSecond(VisitStateTransition latest, MissedCheckInTrigger trigger) {
        if (latest.occurredAt() == null || trigger.triggeredAt() == null) return false;
        var floor=trigger.triggeredAt().truncatedTo(ChronoUnit.SECONDS);
        // Legacy history is DATETIME(0), ledger DATETIME(6). MySQL may truncate or round
        // fractional seconds depending on sql_mode. All other causal guards remain mandatory.
        return latest.occurredAt().equals(floor) || (trigger.triggeredAt().getNano()!=0
                && latest.occurredAt().equals(floor.plusSeconds(1)));
    }
}
