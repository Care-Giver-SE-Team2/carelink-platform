package sg.nus.carelink.visit.application;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.ResourceNotFound;
import sg.nus.carelink.visit.domain.model.MissedCheckInTrigger;
import sg.nus.carelink.visit.domain.model.Visit;
import sg.nus.carelink.visit.domain.repository.MissedCheckInRepository;
import sg.nus.carelink.visit.domain.repository.VisitRepository;

/** Immutable own trigger facts only: never incident narrative, elder identity or manager timeline. */
@Service
@Transactional(readOnly=true)
public class CaregiverSystemAlertService {
    private final CaregiverWorkDirectory directory;
    private final MissedCheckInRepository triggers;
    private final VisitRepository visits;
    public CaregiverSystemAlertService(CaregiverWorkDirectory directory,MissedCheckInRepository triggers,VisitRepository visits) {
        this.directory=directory; this.triggers=triggers; this.visits=visits;
    }
    public Page own(String username,int page,int size) {
        if(page<0 || page>10000 || size<1 || size>50) throw new BusinessRuleViolation("SYSTEM_ALERT_PAGE_INVALID","Choose a valid page and size (1–50).");
        var actor=directory.require(username).requireAdmittedHistory();
        return new Page(triggers.forCaregiver(actor.id(),page*size,size).stream().map(t->summary(t,false)).toList(),page,size,triggers.countForCaregiver(actor.id()));
    }
    public Summary detail(String username,Long incidentId) {
        var actor=directory.require(username).requireAdmittedHistory();
        var fact=triggers.findByIncident(incidentId).filter(t->Objects.equals(t.caregiverId(),actor.id()))
                .orElseThrow(()->new ResourceNotFound("System alert",incidentId));
        var visit=visits.findById(fact.visitId());
        boolean workPackAvailable=("AVAILABLE".equals(actor.status()) || "BUSY".equals(actor.status())) && visit.filter(v->Objects.equals(v.caregiverId(),actor.id()) && v.status()!=Visit.Status.CANCELLED).isPresent();
        return summary(fact,workPackAvailable);
    }
    private static Summary summary(MissedCheckInTrigger t,boolean available) {
        return new Summary(t.incidentId(),t.visitId(),"SYSTEM_MISSED_CHECKIN",t.scheduledStart(),t.dueAt(),t.triggeredAt(),"Asia/Singapore",available);
    }
    public record Summary(Long incidentId,Long visitId,String source,LocalDateTime scheduledStart,LocalDateTime checkInDueAt,LocalDateTime triggeredAt,String timeZone,boolean workPackAvailable) { }
    public record Page(List<Summary> items,int page,int size,long total) { }
}
