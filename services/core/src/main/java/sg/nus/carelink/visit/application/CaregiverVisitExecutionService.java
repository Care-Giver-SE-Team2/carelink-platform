package sg.nus.carelink.visit.application;

import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import sg.nus.carelink.careplan.application.VisitPlanReader;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.ResourceNotFound;
import sg.nus.carelink.visit.domain.model.*;
import sg.nus.carelink.visit.domain.repository.*;

@Service
public class CaregiverVisitExecutionService {
    private static final String CHECK_IN = "CHECK_IN";
    private static final String TASK_RESULT = "TASK_RESULT";
    private final CaregiverCommandExecutor executor;
    private final CaregiverCommandStore receipts;
    private final VisitCommandRepository visits;
    private final VisitTaskRepository tasks;
    private final VisitStateTransitionRepository transitions;
    private final VisitCheckInRepository checkIns;
    private final VisitPlanReader plans;
    private final VisitExecutionPolicy policy;
    public CaregiverVisitExecutionService(CaregiverCommandExecutor executor, CaregiverCommandStore receipts, VisitCommandRepository visits,
            VisitTaskRepository tasks, VisitStateTransitionRepository transitions, VisitCheckInRepository checkIns, VisitPlanReader plans, VisitExecutionPolicy policy) {
        this.executor=executor;this.receipts=receipts;this.visits=visits;this.tasks=tasks;this.transitions=transitions;this.checkIns=checkIns;this.plans=plans;this.policy=policy;
    }
    public ExecutionResult checkIn(String username, Long id, Integer version, UUID key, CheckInLocation loc) {
        String hash=CommandFingerprint.of(id,version,loc.source(),loc.latitude(),loc.longitude(),loc.accuracy(),loc.note(),loc.clientCapturedAt());
        return executor.execute(username,id,CHECK_IN,Visit.Status.ARRIVED.name(),(actor,visit)->{
            var previous=receipts.find(actor.userId(),key);
            if(previous.isPresent()) { var r=previous.get();r.requireSame(CHECK_IN,id,hash);return new ExecutionResult(id,r.version(),"IN_PROGRESS",null,true); }
            CaregiverCommandExecutor.version(visit,version);
            var now=executor.now();policy.requireWindow(visit,now);
            var arrived=VisitStateFactory.forVisit(visit).arrive(visit,now);
            materialize(visit);
            var started=VisitStateFactory.forVisit(arrived).start(arrived);
            checkIns.save(id,actor.userId(),key,loc,now);
            var saved=visits.save(started);
            transitions.save(new VisitStateTransition(null,id,Visit.Status.SCHEDULED.name(),Visit.Status.ARRIVED.name(),actor.userId(),VisitStateTransition.Result.APPLIED,null,now));
            transitions.save(new VisitStateTransition(null,id,Visit.Status.ARRIVED.name(),Visit.Status.IN_PROGRESS.name(),actor.userId(),VisitStateTransition.Result.APPLIED,null,now));
            receipts.audit(actor.userId(),id,CHECK_IN,"OK","SAVED");
            receipts.save(new CaregiverCommandReceipt(actor.userId(),key,CHECK_IN,id,hash,id,saved.version(),now));
            return new ExecutionResult(id,saved.version(),saved.status().name(),null,false);
        });
    }
    private void materialize(Visit visit) {
        if(visit.standalone()) {
            if(tasks.findByVisitId(visit.id()).isEmpty()) tasks.save(visit.standaloneTask());
            return;
        }
        if(visit.carePlanId()==null) throw new BusinessRuleViolation("VISIT_PLAN_REQUIRED","An assigned plan is required.");
        var snapshot=plans.read(visit.carePlanId(),visit.elderId());
        var existing=tasks.findByVisitId(visit.id());
        var ids=snapshot.tasks().stream().map(VisitPlanReader.Task::id).toList();
        if(existing.stream().anyMatch(t->t.carePlanNodeId()==null || !ids.contains(t.carePlanNodeId()))
                || (visit.carePlanNodeId()!=null && !ids.contains(visit.carePlanNodeId()))) throw new BusinessRuleViolation("VISIT_TASK_PLAN_MISMATCH","Task nodes do not match the assigned plan version.");
        if(visit.carePlanNodeId()!=null && existing.stream().noneMatch(t->Objects.equals(t.carePlanNodeId(),visit.carePlanNodeId()))) {
            var task=snapshot.tasks().stream().filter(t->Objects.equals(t.id(),visit.carePlanNodeId())).findFirst().orElseThrow();
            tasks.save(new VisitTask(null,visit.id(),task.id(),task.name(),VisitTask.Status.PENDING,null,null,null));
        }
        if(existing.isEmpty() && visit.carePlanNodeId()==null) throw new BusinessRuleViolation("VISIT_TASKS_REQUIRED","No executable task is assigned.");
    }
    public ExecutionResult taskResult(String username, Long id, Long taskId, TaskCommand input) {
        String status=input.status();
        Integer version=input.expectedVersion();
        UUID key=input.clientRequestId();
        String cleanOutcome=input.outcome()==null?null:input.outcome().strip();
        String cleanNote=input.caregiverNote()==null?null:input.caregiverNote().strip();
        String hash=CommandFingerprint.of(id,taskId,version,status,cleanOutcome,cleanNote);
        return executor.execute(username,id,TASK_RESULT,null,(actor,visit)->{
            var previous=receipts.find(actor.userId(),key);
            if(previous.isPresent()) { var r=previous.get();r.requireSame(TASK_RESULT,id,hash);return new ExecutionResult(id,r.version(),null,taskId,true); }
            CaregiverCommandExecutor.version(visit,version);
            VisitStateFactory.forVisit(visit).requireTaskResult();
            var task=tasks.findById(taskId).filter(t->Objects.equals(id,t.visitId())).orElseThrow(()->new ResourceNotFound("Task",taskId));
            var now=executor.now();
            tasks.save(task.result(VisitTask.Status.valueOf(status),cleanOutcome,cleanNote,now));
            var saved=visits.save(visit);
            receipts.audit(actor.userId(),id,TASK_RESULT,"OK","SAVED");
            receipts.save(new CaregiverCommandReceipt(actor.userId(),key,TASK_RESULT,id,hash,taskId,saved.version(),now));
            return new ExecutionResult(id,saved.version(),saved.status().name(),taskId,false);
        });
    }
    public record TaskCommand(String status,String outcome,String caregiverNote,Integer expectedVersion,UUID clientRequestId) {}
    public record ExecutionResult(Long visitId,Integer visitVersion,String savedState,Long taskId,boolean replayed) {}
}
