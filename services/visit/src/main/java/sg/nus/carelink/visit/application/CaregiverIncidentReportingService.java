package sg.nus.carelink.visit.application;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sg.nus.carelink.incident.application.CaregiverIncidentGateway;
import sg.nus.carelink.profile.application.CaregiverWorkDirectory;
import sg.nus.carelink.visit.domain.model.CaregiverCommandReceipt;
import sg.nus.carelink.visit.domain.model.CommandFingerprint;
import sg.nus.carelink.visit.domain.model.VisitStateTransition;
import sg.nus.carelink.visit.domain.repository.CaregiverCommandStore;
import sg.nus.carelink.visit.domain.repository.VisitCommandRepository;
import sg.nus.carelink.visit.domain.repository.VisitStateTransitionRepository;

@Service
public class CaregiverIncidentReportingService {
    private static final String ACTION = "REPORT_INCIDENT";
    private final CaregiverCommandExecutor executor;
    private final CaregiverCommandStore receipts;
    private final VisitCommandRepository visits;
    private final VisitStateTransitionRepository transitions;
    private final CaregiverIncidentGateway incidents;
    private final CaregiverWorkDirectory directory;
    public CaregiverIncidentReportingService(CaregiverCommandExecutor executor, CaregiverCommandStore receipts, VisitCommandRepository visits,
            VisitStateTransitionRepository transitions, CaregiverIncidentGateway incidents, CaregiverWorkDirectory directory) {
        this.executor = executor; this.receipts = receipts; this.visits = visits; this.transitions = transitions; this.incidents = incidents; this.directory = directory;
    }
    public Result report(String username, Long visitId, String category, String severity, String description, Integer version, UUID key) {
        String text = description.strip();
        String hash = CommandFingerprint.of(visitId, category, severity, text, version);
        return executor.execute(username, visitId, ACTION, "EXCEPTION", (actor, visit) -> {
            var previous = receipts.find(actor.userId(), key);
            if (previous.isPresent()) {
                previous.get().requireSame(ACTION, visitId, hash);
                return new Result(incidents.own(actor.userId(), previous.get().resultId()), key, previous.get().version(), true);
            }
            CaregiverCommandExecutor.version(visit, version);
            var now = executor.now();
            var paused = visit.reportedException(now);
            var report = incidents.report(visit.elderId(), visitId, actor.userId(), category, severity, text);
            var saved = visits.save(paused);
            if (visit.status() != paused.status()) transitions.save(new VisitStateTransition(null, visitId, visit.status().name(), paused.status().name(),
                    actor.userId(), VisitStateTransition.Result.APPLIED, null, now));
            receipts.audit(actor.userId(), visitId, ACTION, "OK", "SAVED");
            receipts.save(new CaregiverCommandReceipt(actor.userId(), key, ACTION, visitId, hash, report.id(), saved.version(), now));
            return new Result(report, key, saved.version(), false);
        });
    }
    @Transactional(readOnly = true)
    public View own(String username, Long id) {
        Long actor = directory.require(username).userId();
        return new View(incidents.own(actor, id), receipts.incidentKey(actor, id).orElse(null));
    }
    @Transactional(readOnly = true)
    public Page list(String username, Long visit, int page, int size) {
        Long actor = directory.require(username).userId();
        var reports = incidents.list(actor, visit, page, size);
        return new Page(reports.items().stream().map(r -> new View(r, receipts.incidentKey(actor, r.id()).orElse(null))).toList(), page, size, reports.totalElements());
    }
    public record Result(CaregiverIncidentGateway.Report report, UUID clientRequestId, Integer visitVersion, boolean replayed) {}
    public record View(CaregiverIncidentGateway.Report report, UUID clientRequestId) {}
    public record Page(java.util.List<View> items, int page, int size, long totalElements) {}
}
