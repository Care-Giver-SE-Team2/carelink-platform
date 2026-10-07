package sg.nus.carelink.profile.application;

import java.util.function.Supplier;

/**
 * Records family query outcomes with the account resolved from the session username.
 *
 * @author Wang Zhili
 */
public interface FamilyReadAudit {

	enum Resource {
		ELDERS("ELDER", "FM02_LIST_ELDERS"),
		VISITS("VISIT", "FM02_LIST_VISITS"),
		VISIT_DETAIL("VISIT", "FM03_READ_VISIT"),
		VISIT_TIMELINE("VISIT", "FM03_READ_VISIT_TIMELINE"),
		VISIT_TASKS("VISIT", "FM03_READ_VISIT_TASKS"),
		CAREGIVER("CAREGIVER", "FM02_READ_CAREGIVER"),
		CREDENTIALS("CAREGIVER_CREDENTIALS", "FM02_LIST_CREDENTIALS"),
		REPORTS("REPORT", "FM04_LIST_REPORTS"),
		REPORT_DETAIL("REPORT", "FM04_READ_REPORT"),
		WEEKLY_SUMMARY("ELDER", "FM04_READ_WEEKLY_SUMMARY");

		private final String type;
		private final String operation;

		Resource(String type, String operation) {
			this.type = type;
			this.operation = operation;
		}

		String type() { return type; }
		String operation() { return operation; }
	}

	/**
	 * Runs a query and persists its outcome before returning data or rethrowing its error.
	 *
	 * @param username Username supplied by the authenticated session
	 * @param resource Query operation and resource category
	 * @param resourceId Resource identifier, or null for a collection
	 * @param scope Validated numeric, enum and date filters; empty for an unfiltered resource
	 * @param query Authorized application query, which still performs its own access checks
	 * @return Query result after its access record has been committed
	 * @author Wang Zhili
	 */
	<T> T read(String username, Resource resource, Long resourceId, String scope, Supplier<T> query);
}
