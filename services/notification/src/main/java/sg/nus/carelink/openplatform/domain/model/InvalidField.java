package sg.nus.carelink.openplatform.domain.model;

/**
 * A field of a request the Open Platform cannot take, found after the request's own validation: a
 * webhook URL that is not HTTPS, a password too short. Answered as the contract's 400
 * {@code VALIDATION_FAILED}, with {@code fields} naming it.
 */
public class InvalidField extends RuntimeException {

	private final String field;

	public InvalidField(String field, String problem) {
		super(problem);
		this.field = field;
	}

	public String field() {
		return field;
	}
}
