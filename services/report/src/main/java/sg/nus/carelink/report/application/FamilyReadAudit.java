package sg.nus.carelink.report.application;

import java.util.function.Supplier;

/**
 * Records every family read of a report or a weekly summary in the access audit, whether it was
 * served, refused or failed. The operation names are the ones core records for the same reads.
 */
public interface FamilyReadAudit {

	enum Resource {
		REPORTS("REPORT", "FM04_LIST_REPORTS"),
		REPORT_DETAIL("REPORT", "FM04_READ_REPORT"),
		WEEKLY_SUMMARY("ELDER", "FM04_READ_WEEKLY_SUMMARY");

		private final String type;
		private final String operation;

		Resource(String type, String operation) {
			this.type = type;
			this.operation = operation;
		}

		public String type() {
			return type;
		}

		public String operation() {
			return operation;
		}
	}

	/** Runs the read and records its outcome; the read's own exception reaches the caller. */
	<T> T read(String username, Resource resource, Long resourceId, String scope, Supplier<T> query);

}
