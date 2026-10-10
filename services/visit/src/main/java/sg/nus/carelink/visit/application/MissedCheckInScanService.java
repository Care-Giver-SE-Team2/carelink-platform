package sg.nus.carelink.visit.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import sg.nus.carelink.incident.application.MissedCheckInIncidentGateway;
import sg.nus.carelink.visit.domain.model.MissedCheckInTrigger;
import sg.nus.carelink.visit.domain.repository.MissedCheckInRepository;
import sg.nus.carelink.visit.domain.repository.VisitCommandRepository;
import sg.nus.carelink.visit.domain.service.MissedCheckInPolicy;

/** A bounded keyset round; each fact commits independently and retries only on the next round. */
@Service
public class MissedCheckInScanService {
    public record Settings(boolean enabled, int batchSize) {}
    public record Outcome(int considered, int triggered, int failed) {}
    private static final Logger log = LoggerFactory.getLogger(MissedCheckInScanService.class);
    private final TransactionTemplate tx;
    private final VisitCommandRepository visits;
    private final MissedCheckInRepository triggers;
    private final MissedCheckInIncidentGateway incidents;
    private final MissedCheckInPolicy policy;
    private final Settings settings;
    private final Clock clock;
    public MissedCheckInScanService(PlatformTransactionManager manager, VisitCommandRepository visits,
            MissedCheckInRepository triggers, MissedCheckInIncidentGateway incidents,
            MissedCheckInPolicy policy,
            Settings settings, Clock clock) {
        tx = new TransactionTemplate(manager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.visits = visits; this.triggers = triggers; this.incidents = incidents;
        this.policy = policy; this.settings = settings; this.clock = clock;
    }
    public Outcome scan() {
        if (!settings.enabled()) return new Outcome(0, 0, 0);
        var roundNow = now();
        var since = roundNow.minus(policy.lookback());
        var before = roundNow.minus(policy.lateThreshold());
        MissedCheckInRepository.Candidate cursor = null;
        int considered = 0;
        int triggered = 0;
        int failed = 0;
        while (true) {
            var page = triggers.candidates(since, before, cursor, settings.batchSize());
            if (page.isEmpty()) break;
            if (cursor != null && !advances(page.getFirst(), cursor)) {
                throw new IllegalStateException("SYS03 candidate cursor did not advance");
            }
            for (var candidate : page) {
                considered++;
                try {
                    if (trigger(candidate.visitId())) triggered++;
                } catch (RuntimeException failure) {
                    failed++;
                    // No private narrative or stack trace containing SQL parameters.
                    log.warn("SYS03 visit {} failed: {}", candidate.visitId(), failure.getClass().getSimpleName());
                }
            }
            cursor = page.getLast();
        }
        return new Outcome(considered, triggered, failed);
    }
    public boolean trigger(Long visitId) {
        if (!settings.enabled()) return false;
        return Boolean.TRUE.equals(tx.execute(_ -> {
            var current = visits.lock(visitId);
            var observedAt = now();
            if (current.isEmpty() || !policy.eligible(current.get(), observedAt) || triggers.exists(visitId)) return false;
            var visit = current.get();
            var dueAt = policy.dueAt(visit);
            // Alert facts advance the parent version, but do not pause service or invent a transition.
            visits.save(visit);
            Long incident = incidents.raise(visit.elderId(), visit.id(), dueAt, observedAt);
            triggers.save(new MissedCheckInTrigger(visit.id(), incident, visit.caregiverId(), visit.scheduledStart(),
                    dueAt, visit.version(), observedAt));
            return true;
        }));
    }
    private LocalDateTime now() { return LocalDateTime.now(clock.withZone(ZoneId.of("Asia/Singapore"))); }
    private static boolean advances(MissedCheckInRepository.Candidate next, MissedCheckInRepository.Candidate previous) {
        int order = next.scheduledStart().compareTo(previous.scheduledStart());
        return order > 0 || (order == 0 && next.visitId() > previous.visitId());
    }
}
