package sg.nus.carelink.shared.error;

/**
 * Prevents a protected read from returning without its required access record.
 *
 * @author Wang Zhili
 */
public class AuditUnavailable extends RuntimeException {

	public AuditUnavailable(Throwable cause) {
		super("Access could not be recorded. Please retry later.", cause);
	}
}
