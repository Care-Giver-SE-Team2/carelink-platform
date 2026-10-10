package sg.nus.carelink.shared.audit.application;

/**
 * Carries access metadata without persistence types, credentials or resource contents.
 *
 * @param actorUserId Account identifier, or null when the account could not be resolved
 * @param action Operation category such as READ
 * @param resourceType Resource or collection name
 * @param resourceId Resource identifier, or null for a collection
 * @param outcome Query outcome before any response is returned
 * @param detail Bounded metadata constructed by the application, never raw request or response text
 * @author Wang Zhili
 */
public record AccessAuditEntry(Long actorUserId, String action, String resourceType, Long resourceId,
		Outcome outcome, String detail) {

	public enum Outcome {
		OK, DENIED, FAILED
	}
}
