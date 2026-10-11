package sg.nus.carelink.visit.application;

import java.util.function.Supplier;

/**
 * Records every family read of a visit or a caregiver in the access audit, whether it was served,
 * refused or failed. The operation names are the ones core recorded for the same reads.
 */
public interface FamilyReadAudit {

	enum Resource {
		VISITS("VISIT", "FM02_LIST_VISITS"),
		VISIT_DETAIL("VISIT", "FM03_READ_VISIT"),
		VISIT_TIMELINE("VISIT", "FM03_READ_VISIT_TIMELINE"),
		VISIT_TASKS("VISIT", "FM03_READ_VISIT_TASKS"),
		CAREGIVER("CAREGIVER", "FM02_READ_CAREGIVER"),
		CREDENTIALS("CAREGIVER_CREDENTIALS", "FM02_LIST_CREDENTIALS");

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
