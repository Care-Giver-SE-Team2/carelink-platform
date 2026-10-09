package sg.nus.carelink.incident.application;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import sg.nus.carelink.identity.application.UserDirectory;
import sg.nus.carelink.incident.domain.model.Incident;
import sg.nus.carelink.incident.domain.model.IncidentAcknowledgement;
import sg.nus.carelink.incident.domain.repository.IncidentAcknowledgementRepository;
import sg.nus.carelink.incident.domain.repository.IncidentReceiptAudit;
import sg.nus.carelink.incident.domain.repository.IncidentRepository;
import sg.nus.carelink.profile.application.FamilyAccessQuery;
import sg.nus.carelink.profile.application.FamilyIdentityQuery;
import sg.nus.carelink.shared.audit.application.AccessAudit;
import sg.nus.carelink.shared.audit.application.AccessAuditEntry;
import sg.nus.carelink.shared.audit.application.AccessAuditEntry.Outcome;
import sg.nus.carelink.shared.error.AuditUnavailable;
import sg.nus.carelink.shared.error.ResourceNotFound;

/**
 * Authorizes each family command and commits its receipt together with the success audit.
 *
 * @author Wang Zhili
 */
@Service
public class FamilyIncidentReceiptService {

	private final IncidentRepository incidents;
	private final IncidentAcknowledgementRepository receipts;
	private final FamilyIdentityQuery identity;
	private final FamilyAccessQuery access;
	private final UserDirectory users;
	private final IncidentReceiptAudit successAudit;
	private final AccessAudit failureAudit;
	private final TransactionTemplate transaction;
	private final Clock clock;

	public FamilyIncidentReceiptService(IncidentRepository incidents, IncidentAcknowledgementRepository receipts,
			FamilyIdentityQuery identity, FamilyAccessQuery access, UserDirectory users, IncidentReceiptAudit successAudit,
			AccessAudit failureAudit, PlatformTransactionManager transactions, Clock clock) {
		this.incidents = incidents;
		this.receipts = receipts;
		this.identity = identity;
		this.access = access;
		this.users = users;
		this.successAudit = successAudit;
		this.failureAudit = failureAudit;
		this.transaction = new TransactionTemplate(transactions);
		this.clock = clock;
	}

	public IncidentAcknowledgement view(String username, Long incidentId) {
		return record(username, incidentId, false, null);
	}

	public IncidentAcknowledgement acknowledge(String username, Long incidentId, String note) {
		return record(username, incidentId, true, note);
	}

	private IncidentAcknowledgement record(String username, Long incidentId, boolean acknowledge, String note) {
		String operation = acknowledge ? "FM05_ACKNOWLEDGE_INCIDENT" : "FM05_VIEW_INCIDENT";
		Long actorId = null;
		try {
			actorId = users.findByUsername(username).map(user -> user.id()).orElse(null);
			Long accountId = actorId;
			return transaction.execute(status -> {
				Long familyId = identity.requireFamilyMemberId(username);
				Incident incident = incidents.findById(incidentId)
						.orElseThrow(() -> new ResourceNotFound("Incident", incidentId));
				access.requireReadableElder(username, incident.elderId());
				var receipt = receipts.findOrCreateForUpdate(incidentId, familyId);
				// Receipt DATETIME columns store seconds; return the same first time that a reload reads.
				LocalDateTime now = LocalDateTime.now(clock.withZone(Incident.CARELINK_ZONE)).withNano(0);
				var updated = acknowledge ? receipt.acknowledgeAt(now, note) : receipt.viewAt(now);
				if (updated != receipt) { receipts.save(updated); }
				successAudit.appendSuccess(accountId, incidentId, operation, now);
				return updated;
			});
		} catch (RuntimeException failure) {
			try {
				failureAudit.append(new AccessAuditEntry(actorId, "UPDATE", "INCIDENT", incidentId,
						failure instanceof AccessDeniedException ? Outcome.DENIED : Outcome.FAILED, operation));
			} catch (RuntimeException unavailable) {
				var auditFailure = new AuditUnavailable(unavailable);
				auditFailure.addSuppressed(failure);
				throw auditFailure;
			}
			throw failure;
		}
	}
}
