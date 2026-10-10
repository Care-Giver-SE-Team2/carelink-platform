package sg.nus.carelink.shared.web;

/**
 * Hands the real {@code GlobalExceptionHandler} to standalone MockMvc tests in other
 * packages.
 *
 * <p>The handler is package-private because nothing in production should call it directly.
 * A controller test still needs it, though: without it a standalone MockMvc setup turns
 * every domain exception into 500, and the test would prove nothing about the status codes
 * the contract promises. This lives in the same package so the visibility does not have to
 * be widened for the sake of a test.
 */
public final class GlobalExceptionHandlerTestSupport {

	private GlobalExceptionHandlerTestSupport() {
	}

	public static Object instance() {
		return new GlobalExceptionHandler();
	}
}
