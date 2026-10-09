package sg.nus.carelink.incident.domain.repository;

import java.time.LocalDateTime;

/**
 * Records a successful family command in the same transaction as its receipt.
 *
 * @author Wang Zhili
 */
public interface IncidentReceiptAudit {

	void appendSuccess(Long accountId, Long incidentId, String operation, LocalDateTime occurredAt);
}
