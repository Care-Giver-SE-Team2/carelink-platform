package sg.nus.carelink.shared.audit.persistence.adapter;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.shared.audit.application.AccessAudit;
import sg.nus.carelink.shared.audit.application.AccessAuditEntry;
import sg.nus.carelink.shared.audit.persistence.entity.AuditLogJpaEntity;
import sg.nus.carelink.shared.audit.persistence.repository.AuditLogJpaRepository;

/**
 * Appends audit rows independently of a read-only or rolled-back query transaction.
 *
 * @author Wang Zhili
 */
@Repository
public class JpaAccessAudit implements AccessAudit {

	private final AuditLogJpaRepository repository;
	private final Clock clock;

	public JpaAccessAudit(AuditLogJpaRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = false)
	public void append(AccessAuditEntry entry) {
		var entity = new AuditLogJpaEntity();
		entity.setActorUserId(entry.actorUserId());
		entity.setAction(entry.action());
		entity.setResourceType(entry.resourceType());
		entity.setResourceId(entry.resourceId());
		entity.setResult(AuditLogJpaEntity.Result.valueOf(entry.outcome().name()));
		entity.setDetail(entry.detail());
		entity.setOccurredAt(LocalDateTime.now(clock.withZone(ZoneId.of("Asia/Singapore"))));
		repository.saveAndFlush(entity);
	}
}
