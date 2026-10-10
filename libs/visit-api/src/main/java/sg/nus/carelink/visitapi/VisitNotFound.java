package sg.nus.carelink.visitapi;

/** visit answered 404: what was asked for does not exist. */
public class VisitNotFound extends RuntimeException {

	public VisitNotFound(String detail) {
		super(detail);
	}

}
