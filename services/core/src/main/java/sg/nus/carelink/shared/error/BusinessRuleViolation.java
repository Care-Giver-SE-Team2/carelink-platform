package sg.nus.carelink.shared.error;

/**
 * A business rule was violated. Thrown by the domain layer, mapped to HTTP 409.
 *
 * <p>It lives in shared.error rather than inside one module's domain package so that
 * every module's domain layer can depend on it. It is plain Java — no Spring, no JPA —
 * so depending on it does not break the rule that the domain layer must be unit
 * testable without a framework.
 */
public class BusinessRuleViolation extends RuntimeException {

	private final String code;

	public BusinessRuleViolation(String code, String message) {
		super(message);
		this.code = code;
	}

	/** Stable machine-readable identifier. Clients localise on this, never by parsing the message. */
	public String code() {
		return code;
	}
}
