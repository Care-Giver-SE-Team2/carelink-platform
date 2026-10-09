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
    private static final ZoneId SINGAPORE = ZoneId.of("Asia/Singapore");
    private final VisitRepository visits;
    private final VisitTaskRepository tasks;
    private final CaregiverWorkDirectory directory;
    private final VisitPlanReader plans;
    private final AccessAudit audit;
    private final Clock clock;
    private final sg.nus.carelink.visit.domain.model.VisitExecutionPolicy policy;
    private final sg.nus.carelink.visit.domain.repository.VisitCheckInRepository checkIns;
    private final sg.nus.carelink.visit.domain.repository.VisitInstructionRepository instructions;

    public CaregiverWorkService(VisitRepository visits, VisitTaskRepository tasks, CaregiverWorkDirectory directory,
            VisitPlanReader plans, AccessAudit audit, Clock clock, sg.nus.carelink.visit.domain.model.VisitExecutionPolicy policy,
            sg.nus.carelink.visit.domain.repository.VisitCheckInRepository checkIns,
            sg.nus.carelink.visit.domain.repository.VisitInstructionRepository instructions) {
        this.visits = visits; this.tasks = tasks; this.directory = directory;
        this.plans = plans; this.audit = audit; this.clock = clock;
        this.policy = policy; this.checkIns = checkIns; this.instructions = instructions;
    }

    public CaregiverWorkDirectory.Profile profile(String username) { return directory.require(username); }

    public Schedule schedule(String username, LocalDate from, LocalDate to) {
        var caregiver = directory.require(username);
        var today = LocalDate.now(clock.withZone(SINGAPORE));
        if (from == null && to == null) { from = today; to = today.plusDays(6); }
        if (from == null || to == null || to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 30
                || to.equals(LocalDate.MAX)) {
            throw new InvalidDateRange();
        }
        var rows = visits.findAssigned(caregiver.id(), from.atStartOfDay(), to.plusDays(1).atStartOfDay()).stream()
                .map(v -> summary(v, directory.elder(v.elderId()).preferredName())).toList();
        var alerts = directory.alerts(caregiver.id(), today);
        return new Schedule(from, to, SINGAPORE.getId(), rows, alerts.items(), alerts.context());
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
            // A standalone visit's instructions are its service, which becomes its one task at
            // check-in, then any special instructions it was booked with.
            var instructions = visit.standalone() ? standaloneInstructions(visit)
                    : relevant.stream().map(VisitPlanReader.Task::name).toList();
            var pack = new WorkPack(summary(visit, elder.preferredName()), elder, visit.carePlanId(),
                    snapshot == null ? null : snapshot.version(),
                    instructions, executionTasks,
                    relevant.stream().map(VisitPlanReader.Task::evidenceType).filter(e -> !"NONE".equals(e)).distinct().toList(),
                    execution(visit, !instructions.isEmpty()),
                    new sg.nus.carelink.visit.domain.model.HealthObservation(visit.healthFlag(), visit.healthNote()));
            audit.workPack(caregiver.userId(), visitId, "OK");
            return pack;
        } catch (RuntimeException failure) {
            audit.workPack(caregiver.userId(), visitId, "FAILED");
            throw failure;
        }
    }

    private List<String> standaloneInstructions(Visit visit) {
        var lines = new java.util.ArrayList<String>();
        lines.add(visit.standaloneTask().name());
        this.instructions.find(visit.id()).ifPresent(lines::add);
        return List.copyOf(lines);
    }

    private ExecutionContext execution(Visit visit, boolean hasPlanTasks) {
        var now = LocalDateTime.now(clock.withZone(SINGAPORE));
        var actions = new java.util.ArrayList<String>();
        String reason = null;
        try { visit.reportedException(now); actions.add("REPORT_INCIDENT"); } catch (BusinessRuleViolation _) { /* unavailable */ }
        try {
            var state = sg.nus.carelink.visit.domain.model.VisitStateFactory.forVisit(visit);
            if (visit.status() == Visit.Status.SCHEDULED) {
                if (!hasPlanTasks) reason = "VISIT_TASKS_REQUIRED";
                else { policy.requireWindow(visit, now); actions.add("CHECK_IN"); }
            } else if (visit.status() == Visit.Status.IN_PROGRESS) {
                state.requireTaskResult(); actions.add("TASK_RESULT");
                if (visit.checkedInAt() != null) actions.add("HEALTH_RECORD");
            }
            else reason = "VISIT_EXECUTION_NOT_ALLOWED";
        } catch (BusinessRuleViolation blocked) { reason = blocked.code(); }
        return new ExecutionContext(List.copyOf(actions), reason, now, policy.opens(visit), policy.closes(visit), visit.checkedInAt(), visit.checkedOutAt(),
                visit.checkedInAt() != null && policy.late(visit, visit.checkedInAt()), checkIns.source(visit.id()).orElse(null));
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
            List<String> requiredEvidenceKinds, ExecutionContext execution,
            sg.nus.carelink.visit.domain.model.HealthObservation healthObservation) {
        public WorkPack(VisitSummary visit, CaregiverWorkDirectory.ElderView elder, Long carePlanId, Integer carePlanVersion,
                List<String> serviceInstructions, List<VisitTask> tasks, List<String> requiredEvidenceKinds, ExecutionContext execution) {
            this(visit,elder,carePlanId,carePlanVersion,serviceInstructions,tasks,requiredEvidenceKinds,execution,null);
        }
        public WorkPack(VisitSummary visit, CaregiverWorkDirectory.ElderView elder, Long carePlanId, Integer carePlanVersion,
                List<String> serviceInstructions, List<VisitTask> tasks, List<String> requiredEvidenceKinds) {
            this(visit,elder,carePlanId,carePlanVersion,serviceInstructions,tasks,requiredEvidenceKinds,null);
        }
    }
    public record ExecutionContext(List<String> allowedActions,String blockedReason,LocalDateTime serverNow,LocalDateTime checkInOpensAt,
            LocalDateTime checkInClosesAt,LocalDateTime checkedInAt,LocalDateTime checkedOutAt,boolean lateArrival,String locationSource) {}
    public static class InvalidDateRange extends RuntimeException {
        public InvalidDateRange() { super("Provide both dates in order, with a range of at most 31 days."); }
    }
}
