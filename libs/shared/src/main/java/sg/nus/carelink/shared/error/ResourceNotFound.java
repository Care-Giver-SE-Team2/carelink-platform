package sg.nus.carelink.shared.error;

/** The requested resource does not exist. Mapped to HTTP 404. */
public class ResourceNotFound extends RuntimeException {

	public ResourceNotFound(String resource, Object id) {
		super("%s [%s] does not exist".formatted(resource, id));
	}
}
