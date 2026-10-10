package sg.nus.carelink.visitapi;

/**
 * visit answered 409: the request broke one of visit's business rules. {@link #code()} is visit's
 * rule code, so a caller can pass it on unchanged.
 */
public class VisitRuleViolation extends RuntimeException {

	private final String code;

	public VisitRuleViolation(String code, String detail) {
		super(detail);
		this.code = code;
	}

	public String code() {
		return code;
	}

}
