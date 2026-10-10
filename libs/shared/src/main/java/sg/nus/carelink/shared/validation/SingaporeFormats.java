package sg.nus.carelink.shared.validation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Singapore formats that request DTOs check with {@code @Pattern}, and the helpers that bring what
 * people type into that form first, so "9123 4567" and "+65 9123-4567" are both accepted and both
 * saved as "+6591234567". {@code frontend/src/shared/validation/sg.ts} mirrors these rules.
 */
public final class SingaporeFormats {

	/** "+65" then 8 digits: 6 for a landline, 8 or 9 for a mobile, 3 for an internet calling number. */
	public static final String PHONE = "\\+65[3689][0-9]{7}";
	public static final String PHONE_MESSAGE = "Enter an 8-digit Singapore number starting with 3, 6, 8 or 9";

	/** A Singapore mobile number only, for someone who must be reachable by SMS. */
	public static final String MOBILE = "\\+65[89][0-9]{7}";
	public static final String MOBILE_MESSAGE = "Enter an 8-digit Singapore mobile number starting with 8 or 9";

	/** Six digits whose first two are a postal district: 01 to 82, where 74 is not used. */
	public static final String POSTAL_CODE = "(0[1-9]|[1-6][0-9]|7[0-35-9]|8[0-2])[0-9]{4}";
	public static final String POSTAL_CODE_MESSAGE = "Enter a 6-digit Singapore postal code";

	/**
	 * Letters in any script, with the spaces, apostrophes, hyphens, dots, "@" and "/" that names
	 * such as "Ravi s/o Krishnan" or "Tan Ah Kow @ Chen Ah Kow" use, and at least one letter.
	 */
	public static final String PERSON_NAME = "(?=.*\\p{L})[\\p{L}\\p{M} .'’@/-]+";
	public static final String PERSON_NAME_MESSAGE = "Use letters, spaces and ' - . @ / only";

	/** The languages an elder may prefer; rostering matches them against what caregivers speak. */
	public static final List<String> DIALECTS = List.of(
			"English", "Mandarin", "Malay", "Tamil", "Hokkien", "Teochew", "Cantonese", "Hakka", "Hainanese");

	private static final String ONE_DIALECT =
			"(English|Mandarin|Malay|Tamil|Hokkien|Teochew|Cantonese|Hakka|Hainanese)";

	/** A comma-separated list of {@link #DIALECTS}, as {@link #normalizeDialects} writes it. */
	public static final String DIALECT_LIST = ONE_DIALECT + "(," + ONE_DIALECT + ")*";
	public static final String DIALECT_LIST_MESSAGE = "Choose from the listed languages";

	private SingaporeFormats() {
	}

	/**
	 * "+6591234567" from "9123 4567", "+65 9123-4567" or "6591234567"; anything else comes back
	 * stripped but otherwise as typed, so {@link #PHONE} rejects it. Null or blank becomes null.
	 */
	public static String normalizePhone(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String digits = raw.replaceAll("[\\s()-]", "");
		if (digits.startsWith("+65")) {
			digits = digits.substring(3);
		} else if (digits.length() == 10 && digits.startsWith("65")) {
			digits = digits.substring(2);
		}
		return digits.matches("[0-9]{8}") ? "+65" + digits : raw.strip();
	}

	/** Strips the name and collapses runs of spaces inside it. Null or blank becomes null. */
	public static String normalizeName(String raw) {
		return raw == null || raw.isBlank() ? null : raw.strip().replaceAll("\\s+", " ");
	}

	/**
	 * "Hokkien,Mandarin" from " hokkien , MANDARIN,hokkien": each entry in its listed spelling,
	 * once, in the order given. An entry not on the list is kept as typed, so
	 * {@link #DIALECT_LIST} rejects it. Null or blank becomes null.
	 */
	public static String normalizeDialects(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		List<String> entries = new ArrayList<>();
		Arrays.stream(raw.split(",")).map(String::strip).filter(entry -> !entry.isEmpty())
				.map(entry -> DIALECTS.stream().filter(known -> known.equalsIgnoreCase(entry)).findFirst()
						.orElse(entry))
				.filter(entry -> entries.stream().noneMatch(seen -> seen.toLowerCase(Locale.ROOT)
						.equals(entry.toLowerCase(Locale.ROOT))))
				.forEach(entries::add);
		return entries.isEmpty() ? null : String.join(",", entries);
	}
}
