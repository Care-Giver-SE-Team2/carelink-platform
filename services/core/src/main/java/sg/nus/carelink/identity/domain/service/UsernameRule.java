package sg.nus.carelink.identity.domain.service;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * How an issued account's username is formed from the person's name: lower-case ASCII words
 * joined by dots ("Tan Bee Choo" → "tan.bee.choo"), with a number appended when the name is
 * taken ("tan.bee.choo2"). Short and readable, since it is read out or written down for the
 * person, who may not use email.
 */
public final class UsernameRule {

	/** Leaves room for a numeric suffix inside app_user.username's 64 characters. */
	static final int MAX_BASE_LENGTH = 50;

	private UsernameRule() {
	}

	/** "Tan Bee Choo" → "tan.bee.choo"; accents dropped, anything else non-alphanumeric splits words. */
	public static String base(String displayName) {
		String ascii = Normalizer.normalize(displayName == null ? "" : displayName, Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "")
				.toLowerCase(Locale.ROOT);
		String joined = Arrays.stream(ascii.split("[^a-z0-9]+"))
				.filter(word -> !word.isEmpty())
				.collect(Collectors.joining("."));
		if (joined.length() > MAX_BASE_LENGTH) {
			joined = joined.substring(0, MAX_BASE_LENGTH).replaceAll("\\.+$", "");
		}
		return joined.isEmpty() ? "user" : joined;
	}

	/** The {@code attempt}-th username to try: the base first, then base2, base3, … */
	public static String candidate(String base, int attempt) {
		return attempt <= 1 ? base : base + attempt;
	}
}
