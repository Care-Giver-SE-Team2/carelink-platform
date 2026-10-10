package sg.nus.carelink.shared.audit.application;

/**
 * Provides append-only access records to application modules.
 *
 * @author Wang Zhili
 */
public interface AccessAudit {

	/**
	 * Persists an access outcome in an independent transaction before returning.
	 *
	 * @param entry Caller, operation, resource and safe metadata without response contents
	 * @author Wang Zhili
	 */
	void append(AccessAuditEntry entry);
}
