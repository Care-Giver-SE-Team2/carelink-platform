package sg.nus.carelink.shared.audit.persistence;

import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import sg.nus.carelink.shared.audit.AccessAudit;
import sg.nus.carelink.shared.audit.persistence.entity.AuditLogJpaEntity;
import sg.nus.carelink.shared.audit.persistence.repository.AuditLogJpaRepository;

@Component
class WorkPackAccessAudit implements AccessAudit {
    private final AuditLogJpaRepository logs;
    private final Clock clock;
    WorkPackAccessAudit(AuditLogJpaRepository logs, Clock clock) { this.logs = logs; this.clock = clock; }

    // A refused read must survive the caller's rollback.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void workPack(Long userId, Long visitId, String result) {
        var row = new AuditLogJpaEntity();
        row.setActorUserId(userId);
        row.setAction("READ");
        row.setResourceType("VISIT_WORK_PACK");
        row.setResourceId(visitId);
        row.setResult(AuditLogJpaEntity.Result.valueOf(result));
        row.setOccurredAt(LocalDateTime.now(clock));
        logs.saveAndFlush(row);
    }
}
