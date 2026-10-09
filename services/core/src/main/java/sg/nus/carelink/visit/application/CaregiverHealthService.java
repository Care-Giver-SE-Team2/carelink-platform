package sg.nus.carelink.visit.application;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sg.nus.carelink.profile.application.CaregiverWorkDirectory;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.ResourceNotFound;
import sg.nus.carelink.visit.domain.model.*;
import sg.nus.carelink.visit.domain.repository.*;

@Service
public class CaregiverHealthService {
    private static final String ACTION = "HEALTH_RECORD";
    private final CaregiverCommandExecutor executor;
    private final CaregiverCommandStore receipts;
    private final VisitCommandRepository commands;
    private final VisitRepository visits;
    private final HealthRecordRepository records;
    private final CaregiverWorkDirectory directory;

    public CaregiverHealthService(CaregiverCommandExecutor executor, CaregiverCommandStore receipts,
            VisitCommandRepository commands, VisitRepository visits, HealthRecordRepository records, CaregiverWorkDirectory directory) {
        this.executor = executor; this.receipts = receipts; this.commands = commands;
        this.visits = visits; this.records = records; this.directory = directory;
    }

    public Saved record(String username, Long visitId, Integer version, UUID key, HealthMeasurement input) {
        String hash = CommandFingerprint.of(visitId, version, input.systolic(), input.diastolic(), input.pulse(),
                input.temperature(), input.healthFlag(), input.healthNote());
        return executor.execute(username, visitId, ACTION, null, (actor, visit) -> {
            if (visit.status() == Visit.Status.CANCELLED) throw new BusinessRuleViolation("VISIT_CANCELLED", "This visit was cancelled.");
            var previous = receipts.find(actor.userId(), key);
            if (previous.isPresent()) {
                var receipt = previous.get();
                receipt.requireSame(ACTION, visitId, hash);
                var original = records.find(visitId, receipt.resultId()).orElseThrow(() -> new ResourceNotFound("Health record", receipt.resultId()));
                return new Saved(original, receipt.version(), true);
            }
            CaregiverCommandExecutor.version(visit, version);
            var observed = visit.observedHealth(input);
            var now = executor.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
            var saved = records.save(new HealthRecord(null, visitId, input.healthFlag(), input.healthNote(), now, input.readings()), actor.userId());
            var changed = commands.save(observed);
            receipts.audit(actor.userId(), visitId, ACTION, "OK", "SAVED");
            receipts.save(new CaregiverCommandReceipt(actor.userId(), key, ACTION, visitId, hash, saved.id(), changed.version(), now));
            return new Saved(saved, changed.version(), false);
        });
    }

    @Transactional(readOnly = true)
    public Page history(String username, Long visitId, int page, int size) {
        if (page < 0 || page > 100000 || size < 1 || size > 50) {
            throw new IllegalArgumentException("Page must be 0–100000 and size 1–50.");
        }
        var actor = directory.require(username);
        var visit = visits.findById(visitId).orElseThrow(() -> new ResourceNotFound("Visit", visitId));
        if (!Objects.equals(visit.caregiverId(), actor.id())) throw new AccessDeniedException("Not assigned");
        if (visit.status() == Visit.Status.CANCELLED) throw new BusinessRuleViolation("VISIT_CANCELLED", "This visit was cancelled.");
        return new Page(records.history(visitId, page * size, size), page, size, records.count(visitId));
    }

    public record Saved(HealthRecord record, Integer visitVersion, boolean replayed) {}
    public record Page(List<HealthRecord> items, int page, int size, long total) {}
}
