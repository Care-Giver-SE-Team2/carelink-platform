package sg.nus.carelink.profile.application;

import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import sg.nus.carelink.identity.application.UserDirectory;
import sg.nus.carelink.shared.audit.application.AccessAudit;
import sg.nus.carelink.shared.audit.application.AccessAuditEntry;
import sg.nus.carelink.shared.audit.application.AccessAuditEntry.Outcome;
import sg.nus.carelink.shared.error.AuditUnavailable;

/**
 * Audits family reads without changing their resource authorization or business state.
 *
 * @author Wang Zhili
 */
@Service
class FamilyReadAuditService implements FamilyReadAudit {

	private static final Logger log = LoggerFactory.getLogger(FamilyReadAuditService.class);
	private final UserDirectory users;
	private final AccessAudit audit;
	private final FamilyReadTransaction queries;

	FamilyReadAuditService(UserDirectory users, AccessAudit audit, FamilyReadTransaction queries) {
		this.users = users;
		this.audit = audit;
		this.queries = queries;
	}

	@Override
	public <T> T read(String username, Resource resource, Long resourceId, String scope, Supplier<T> query) {
		Long actorId = null;
		T result;
		String detail = resource.operation() + (scope.isEmpty() ? "" : ";" + scope);
		try {
			actorId = users.findByUsername(username).map(user -> user.id()).orElse(null);
			result = queries.execute(query);
		} catch (RuntimeException failure) {
			Outcome outcome = failure instanceof AccessDeniedException ? Outcome.DENIED : Outcome.FAILED;
			try {
				append(new AccessAuditEntry(actorId, "READ", resource.type(), resourceId, outcome, detail));
			} catch (AuditUnavailable unavailable) {
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
		} catch (RuntimeException failure) {
			log.error("Access audit unavailable: actor={}, resourceType={}, resourceId={}, outcome={}, errorType={}",
					entry.actorUserId(), entry.resourceType(), entry.resourceId(), entry.outcome(),
					failure.getClass().getSimpleName());
			throw new AuditUnavailable(failure);
		}
	}
}
