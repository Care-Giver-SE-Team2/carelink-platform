package sg.nus.carelink.coreapi;

/** core answered 404: what was asked for does not exist. */
public class CoreNotFound extends RuntimeException {

	public CoreNotFound(String detail) {
		super(detail);
	}

}
