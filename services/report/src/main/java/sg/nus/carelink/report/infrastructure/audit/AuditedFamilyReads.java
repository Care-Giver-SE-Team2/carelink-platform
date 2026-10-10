package sg.nus.carelink.report.infrastructure.audit;

import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import sg.nus.carelink.platform.security.SignedInUser;
import sg.nus.carelink.platform.security.SignedInUsers;
import sg.nus.carelink.report.application.FamilyReadAudit;
import sg.nus.carelink.shared.audit.application.AccessAudit;
import sg.nus.carelink.shared.audit.application.AccessAuditEntry;
import sg.nus.carelink.shared.audit.application.AccessAuditEntry.Outcome;
import sg.nus.carelink.shared.error.AuditUnavailable;

/**
 * {@link FamilyReadAudit} on the shared access audit, the way core records family reads: the read
 * runs in a read-only transaction of its own, and its outcome is appended afterwards (OK, DENIED
 * or FAILED). If the audit cannot be written, the reader gets {@link AuditUnavailable} instead of
 * the content. The actor is the signed-in account, taken from the shared session.
 */
@Component
class AuditedFamilyReads implements FamilyReadAudit {

	private static final Logger log = LoggerFactory.getLogger(AuditedFamilyReads.class);

	private final SignedInUsers signedIn;
	private final AccessAudit audit;
	private final TransactionTemplate readOnly;

	AuditedFamilyReads(SignedInUsers signedIn, AccessAudit audit, PlatformTransactionManager transactions) {
		this.signedIn = signedIn;
		this.audit = audit;
		this.readOnly = new TransactionTemplate(transactions);
		this.readOnly.setReadOnly(true);
	}

	@Override
	public <T> T read(String username, Resource resource, Long resourceId, String scope, Supplier<T> query) {
		Long actorId = signedIn.current()
				.filter(user -> user.username().equals(username))
				.map(SignedInUser::id)
				.orElse(null);
		String detail = resource.operation() + (scope.isEmpty() ? "" : ";" + scope);
		T result;
		try {
			result = readOnly.execute(status -> query.get());
		}
		catch (RuntimeException failure) {
			Outcome outcome = failure instanceof AccessDeniedException ? Outcome.DENIED : Outcome.FAILED;
			try {
				append(new AccessAuditEntry(actorId, "READ", resource.type(), resourceId, outcome, detail));
			}
			catch (AuditUnavailable unavailable) {
				unavailable.addSuppressed(failure);
				throw unavailable;
			}
			throw failure;
		}
		append(new AccessAuditEntry(actorId, "READ", resource.type(), resourceId, Outcome.OK, detail));
		return result;
	}

	private void append(AccessAuditEntry entry) {
		try {
			audit.append(entry);
		}
		catch (RuntimeException failure) {
			log.error("Access audit unavailable: actor={}, resourceType={}, resourceId={}, outcome={}, errorType={}",
					entry.actorUserId(), entry.resourceType(), entry.resourceId(), entry.outcome(),
					failure.getClass().getSimpleName());
			throw new AuditUnavailable(failure);
		}
	}

}
