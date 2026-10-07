package sg.nus.carelink.visit.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sg.nus.carelink.careplan.application.VisitPlanReader;
import sg.nus.carelink.profile.application.CaregiverWorkDirectory;
import sg.nus.carelink.shared.audit.AccessAudit;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.ResourceNotFound;
import sg.nus.carelink.visit.domain.model.Visit;
import sg.nus.carelink.visit.domain.model.VisitTask;
import sg.nus.carelink.visit.domain.repository.VisitRepository;
import sg.nus.carelink.visit.domain.repository.VisitTaskRepository;

@Service
@Transactional(readOnly = true)
public class CaregiverWorkService {
    private final VisitRepository visits;
    private final VisitTaskRepository tasks;
    private final CaregiverWorkDirectory directory;
    private final VisitPlanReader plans;
    private final AccessAudit audit;
    private final Clock clock;

    public CaregiverWorkService(VisitRepository visits, VisitTaskRepository tasks, CaregiverWorkDirectory directory,
            VisitPlanReader plans, AccessAudit audit, Clock clock) {
        this.visits = visits; this.tasks = tasks; this.directory = directory;
        this.plans = plans; this.audit = audit; this.clock = clock;
    }

    public CaregiverWorkDirectory.Profile profile(String username) { return directory.require(username); }

    public Schedule schedule(String username, LocalDate from, LocalDate to) {
        var caregiver = directory.require(username);
        var today = LocalDate.now(clock.withZone(ZoneId.of("Asia/Singapore")));
        if (from == null && to == null) { from = today; to = today.plusDays(6); }
        if (from == null || to == null || to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 30
                || to.equals(LocalDate.MAX)) {
            throw new InvalidDateRange();
        }
        var rows = visits.findAssigned(caregiver.id(), from.atStartOfDay(), to.plusDays(1).atStartOfDay()).stream()
                .map(v -> summary(v, directory.elder(v.elderId()).preferredName())).toList();
        var alerts = directory.alerts(caregiver.id(), today);
        return new Schedule(from, to, "Asia/Singapore", rows, alerts.items(), alerts.context());
    }

    public WorkPack workPack(String username, Long visitId) {
        var caregiver = directory.require(username);
        Visit visit = visits.findById(visitId).orElse(null);
        if (visit == null) {
            audit.workPack(caregiver.userId(), visitId, "FAILED");
            throw new ResourceNotFound("Visit", visitId);
        }
        if (!Objects.equals(visit.caregiverId(), caregiver.id())) {
            audit.workPack(caregiver.userId(), visitId, "DENIED");
            throw new AccessDeniedException("This visit is not assigned to you");
        }
        if (visit.status() == Visit.Status.CANCELLED) {
            audit.workPack(caregiver.userId(), visitId, "DENIED");
            throw new BusinessRuleViolation("VISIT_CANCELLED", "This visit has been cancelled. Return to your schedule.");
        }
        try {
            var elder = directory.elder(visit.elderId());
            var snapshot = visit.carePlanId() == null ? null : plans.read(visit.carePlanId(), visit.elderId());
            var executionTasks = tasks.findByVisitId(visitId);
            var planTasks = snapshot == null ? List.<VisitPlanReader.Task>of() : snapshot.tasks();
            var assignedNodeIds = executionTasks.stream().map(VisitTask::carePlanNodeId)
                    .filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
            if (visit.carePlanNodeId() != null) assignedNodeIds.add(visit.carePlanNodeId());
            if (!planTasks.stream().map(VisitPlanReader.Task::id).toList().containsAll(assignedNodeIds)) {
                throw new BusinessRuleViolation("VISIT_TASK_PLAN_MISMATCH", "Visit tasks do not match the assigned plan version.");
            }
            var relevant = planTasks.stream().filter(t -> assignedNodeIds.contains(t.id())).toList();
            var pack = new WorkPack(summary(visit, elder.preferredName()), elder, visit.carePlanId(),
                    snapshot == null ? null : snapshot.version(),
                    relevant.stream().map(VisitPlanReader.Task::name).toList(), executionTasks,
                    relevant.stream().map(VisitPlanReader.Task::evidenceType).filter(e -> !"NONE".equals(e)).distinct().toList());
            audit.workPack(caregiver.userId(), visitId, "OK");
            return pack;
        } catch (RuntimeException failure) {
            audit.workPack(caregiver.userId(), visitId, "FAILED");
            throw failure;
        }
    }

    private VisitSummary summary(Visit v, String elderName) {
        return new VisitSummary(v.id(), v.elderId(), elderName, v.serviceType(), v.scheduledStart(),
                v.scheduledEnd(), v.status().name(), v.version());
    }

    public record VisitSummary(Long id, Long elderId, String elderName, String serviceType,
            LocalDateTime scheduledStart, LocalDateTime scheduledEnd, String status, Integer version) {}
    public record Schedule(LocalDate dateFrom, LocalDate dateTo, String timeZone,
            List<VisitSummary> upcomingVisits, List<CaregiverWorkDirectory.CredentialAlert> certificationAlerts,
            CaregiverWorkDirectory.CredentialAlertContext credentialAlertContext) {}
    public record WorkPack(VisitSummary visit, CaregiverWorkDirectory.ElderView elder, Long carePlanId,
            Integer carePlanVersion, List<String> serviceInstructions, List<VisitTask> tasks,
            List<String> requiredEvidenceKinds) {}
    public static class InvalidDateRange extends RuntimeException {
        public InvalidDateRange() { super("Provide both dates in order, with a range of at most 31 days."); }
    }
}
