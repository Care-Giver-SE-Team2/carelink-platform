package sg.nus.carelink.incident.infrastructure.audit;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.incident.domain.repository.IncidentReceiptAudit;
import sg.nus.carelink.shared.error.AuditUnavailable;

/**
 * Uses the current receipt transaction so neither the success audit nor receipt can commit alone.
 *
 * @author Wang Zhili
 */
@Repository
public class JdbcIncidentReceiptAudit implements IncidentReceiptAudit {

	private final JdbcTemplate jdbc;

	public JdbcIncidentReceiptAudit(JdbcTemplate jdbc) { this.jdbc = jdbc; }

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public void appendSuccess(Long accountId, Long incidentId, String operation, LocalDateTime occurredAt) {
		try {
			jdbc.update("""
					INSERT INTO audit_log (actor_user_id, action, resource_type, resource_id, result, detail, occurred_at)
					VALUES (?, 'UPDATE', 'INCIDENT', ?, 'OK', ?, ?)
					""", accountId, incidentId, operation, Timestamp.valueOf(occurredAt));
		} catch (RuntimeException failure) {
			throw new AuditUnavailable(failure);
		}
	}
}
