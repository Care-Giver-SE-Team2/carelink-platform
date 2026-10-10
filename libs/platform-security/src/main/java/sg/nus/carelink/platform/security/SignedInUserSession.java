package sg.nus.carelink.platform.security;

import jakarta.servlet.http.HttpSession;

/**
 * The two session attributes core writes when someone signs in, next to the security context:
 * the account id and the display name. Every service reads them, so the names are defined here
 * and nowhere else.
 *
 * <p>Both values are plain {@code Long} and {@code String}. The session lives in Redis and is read
 * by every service, so it must only hold types that every service can deserialize.
 */
public final class SignedInUserSession {

	public static final String ID = "carelink.user.id";

	public static final String DISPLAY_NAME = "carelink.user.display-name";

	private SignedInUserSession() {
	}

	public static void remember(HttpSession session, long id, String displayName) {
		session.setAttribute(ID, id);
		session.setAttribute(DISPLAY_NAME, displayName);
	}

}
