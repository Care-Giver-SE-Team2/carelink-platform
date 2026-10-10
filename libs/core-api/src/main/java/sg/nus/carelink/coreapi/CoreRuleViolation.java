package sg.nus.carelink.coreapi;

/**
 * core answered 409: the request broke one of core's business rules. {@link #code()} is core's rule
 * code (for example {@code VISIT_PLAN_UNAVAILABLE}), so a service can pass it on unchanged.
 */
public class CoreRuleViolation extends RuntimeException {

	private final String code;

	public CoreRuleViolation(String code, String detail) {
		super(detail);
		this.code = code;
	}

	public String code() {
		return code;
	}

}
