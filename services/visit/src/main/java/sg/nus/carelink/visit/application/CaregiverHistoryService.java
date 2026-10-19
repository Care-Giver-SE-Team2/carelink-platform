package sg.nus.carelink.visit.application;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sg.nus.carelink.shared.error.*;
import sg.nus.carelink.visit.domain.model.*;
import sg.nus.carelink.visit.domain.repository.*;

/** CG06 first slice: read-only, minimum own facts, no bulk export or elder/work-pack access. */
@Service
@Transactional(readOnly=true)
public class CaregiverHistoryService {
    private final CaregiverWorkDirectory directory;
    private final CaregiverHistoryStore history;
    private final HealthRecordRepository health;
    private final VisitTaskRepository tasks;
    private final VisitEvidenceFileStore evidence;
    private final CaregiverIncidentGateway incidents;
    private final CaregiverSpotChecks checks;
    public CaregiverHistoryService(CaregiverWorkDirectory directory,CaregiverHistoryStore history,HealthRecordRepository health,
            VisitTaskRepository tasks,VisitEvidenceFileStore evidence,CaregiverIncidentGateway incidents,CaregiverSpotChecks checks) {
        this.directory=directory; this.history=history; this.health=health; this.tasks=tasks; this.evidence=evidence; this.incidents=incidents; this.checks=checks;
    }
    public Month own(String username,String month,String status,int page,int size) {
        var actor=directory.require(username).requireWorking(); bounds(page,size);
        YearMonth period;
        try { period=YearMonth.parse(month); if(period.getYear()<2000 || period.getYear()>2100) throw new IllegalArgumentException(); }
        catch(RuntimeException _) { throw invalid("Use a month in YYYY-MM format, between 2000 and 2100."); }
        if(status!=null && !Arrays.stream(Visit.Status.values()).map(Enum::name).toList().contains(status)) throw invalid("Choose a supported Visit status.");
        var from=period.atDay(1).atStartOfDay(); var until=period.plusMonths(1).atDay(1).atStartOfDay();
        var items=history.visits(actor.userId(),from,until,status,page*size,size);
        return new Month(month,"Asia/Singapore",items,page,size,history.countVisits(actor.userId(),from,until,status),
                "Only actions with a recorded submitting identity are included. Older records without that evidence are not attributed to you. Status is the current Visit status, not an assessment of your work.");
    }
    public Detail detail(String username,Long visit,int page,int size) {
        var actor=directory.require(username).requireWorking(); bounds(page,size);
        long total=history.countCommands(actor.userId(),visit);
        if(total==0) throw new ResourceNotFound("Own execution history",visit);
        var facts=history.commands(actor.userId(),visit,page*size,size).stream().map(command->fact(actor.userId(),command)).toList();
        var spotChecks=checks.forVisit(username,visit).stream()
                .map(c->new Check(c.id(),c.result(),c.checkedAt(),c.caregiverResponse())).toList();
        return new Detail(visit,facts,spotChecks,page,size,total,"Asia/Singapore");
    }
    private Fact fact(Long actor,CaregiverCommandReceipt c) {
        HealthRecord observation=null; Task task=null; Evidence file=null; CaregiverIncidentGateway.Report report=null;
        if("HEALTH_RECORD".equals(c.action())) observation=health.find(c.visitId(),c.resultId()).orElse(null);
        if("TASK_RESULT".equals(c.action())) task=tasks.findById(c.resultId()).filter(t->c.visitId().equals(t.visitId()))
                .map(t->new Task(t.id(),t.status().name(),t.outcome(),t.caregiverNote())).orElse(null);
        if("EVIDENCE_UPLOAD".equals(c.action())) file=evidence.find(c.visitId(),c.resultId()).filter(e->e.file()!=null && actor.equals(e.file().uploaderUserId()))
                .map(e->new Evidence(e.evidence().id(),e.evidence().kind().name(),e.evidence().verificationStatus().name(),e.file().fileName(),e.file().submittedAt(),
                        "/api/caregivers/me/history/"+c.visitId()+"/evidence/"+c.resultId()+"/content")).orElse(null);
        if("REPORT_INCIDENT".equals(c.action())) report=incidents.own(actor,c.resultId());
        return new Fact(c.action(),c.resultId(),c.version(),c.occurredAt(),task,observation,file,report);
    }
    private static void bounds(int page,int size) { if(page<0 || page>10000 || size<1 || size>50) throw invalid("Choose a valid page and size (1–50)."); }
    private static BusinessRuleViolation invalid(String text) { return new BusinessRuleViolation("HISTORY_QUERY_INVALID",text); }
    public record Month(String month,String timeZone,List<CaregiverHistoryStore.VisitSummary> items,int page,int size,long total,String coverageNote) { }
    public record Task(Long taskId,String status,String outcome,String caregiverNote) { }
    public record Evidence(Long evidenceId,String kind,String verificationStatus,String fileName,LocalDateTime submittedAt,String downloadPath) { }
    public record Check(Long id,String result,LocalDateTime checkedAt,String ownResponse) { }
    public record Fact(String action,Long sourceId,Integer visitVersion,LocalDateTime recordedAt,Task task,HealthRecord health,Evidence evidence,CaregiverIncidentGateway.Report report) { }
    public record Detail(Long visitId,List<Fact> facts,List<Check> spotChecks,int page,int size,long total,String timeZone) { }
}
