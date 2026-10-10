package sg.nus.carelink.shared.audit.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import sg.nus.carelink.shared.audit.persistence.entity.AuditLogJpaEntity;

/** Spring Data repository for audit_log. Used by persistence.adapter only; never exposed outwards. */
public interface AuditLogJpaRepository extends JpaRepository<AuditLogJpaEntity, Long> {
}
