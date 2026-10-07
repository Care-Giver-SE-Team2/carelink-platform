package sg.nus.carelink.profile.domain.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import sg.nus.carelink.profile.domain.model.Caregiver;
import sg.nus.carelink.profile.domain.model.Elder;
import sg.nus.carelink.profile.domain.model.FamilyMember;
import sg.nus.carelink.profile.domain.model.IntakeApplication;

/**
 * What the manager sees before answering a family's intake application: which caregiver sector
 * the elder would fall in, and the checks that inform the decision without blocking it. Whether
 * the person is already on record is not one of them: DuplicateElderRule refuses that outright.
 *
 * <p>There is no postcode-to-sector table. An application's sector is learned from the elders
 * already on record: the sector most of them hold among those whose postcode starts with the
 * same two digits (Singapore's postal sector). Ties go to the alphabetically first sector, so
 * the answer is stable. With no elder nearby the sector is unknown and the elder is created
 * without one; the manager sets it later.
 */
public final class IntakeScreening {

	public enum CheckKey { CONTACT, SECTOR, DIALECT }

	/**
	 * One check's outcome.
	 *
	 * @param count caregivers counted by SECTOR (free now) and DIALECT (could take the elder);
	 *        null for CONTACT and when there is no sector to count in
	 */
	public record Check(CheckKey key, boolean pass, Integer count) {
	}

	/** The application's sector and its checks. */
	public record Result(String sector, List<Check> checks) {
	}

	/**
	 * Screens one application against the people already on record.
	 *
	 * @param applicant the family member who applied; null if their profile is gone
	 */
	public Result screen(IntakeApplication application, FamilyMember applicant, List<Elder> elders,
			List<Caregiver> caregivers) {
		String sector = sectorFor(application.postalCode(), elders);
		List<Check> checks = new ArrayList<>();
		checks.add(new Check(CheckKey.CONTACT, applicant != null && !isBlank(applicant.phone()), null));
		checks.add(sectorCheck(sector, caregivers));
		Set<String> dialects = dialects(application.preferredDialects());
		if (!dialects.isEmpty()) {
			checks.add(dialectCheck(sector, dialects, caregivers));
		}
		return new Result(sector, List.copyOf(checks));
	}

	/** The sector most nearby elders hold, or null when no elder on record shares the postal sector. */
	public String sectorFor(String postalCode, List<Elder> elders) {
		String district = postalSector(postalCode);
		if (district == null) {
			return null;
		}
		Map<String, Long> counts = elders.stream()
				.filter(elder -> !isBlank(elder.sector()) && district.equals(postalSector(elder.postalCode())))
				.collect(Collectors.groupingBy(elder -> elder.sector().strip(), Collectors.counting()));
		return counts.entrySet().stream()
				.max(Map.Entry.<String, Long>comparingByValue().thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder())))
				.map(Map.Entry::getKey)
				.orElse(null);
	}

	private static Check sectorCheck(String sector, List<Caregiver> caregivers) {
		if (sector == null) {
			return new Check(CheckKey.SECTOR, false, null);
		}
		int free = (int) caregivers.stream()
				.filter(c -> sameSector(c.sector(), sector) && c.status() == Caregiver.Status.AVAILABLE)
				.count();
		return new Check(CheckKey.SECTOR, free > 0, free);
	}

	private static Check dialectCheck(String sector, Set<String> wanted, List<Caregiver> caregivers) {
		if (sector == null) {
			return new Check(CheckKey.DIALECT, false, null);
		}
		int speakers = (int) caregivers.stream()
				.filter(c -> sameSector(c.sector(), sector) && c.isAssignable())
				.filter(c -> dialects(c.dialects()).stream().anyMatch(wanted::contains))
				.count();
		return new Check(CheckKey.DIALECT, speakers > 0, speakers);
	}

	/** First two digits of a Singapore postcode, or null when it doesn't start with two. */
	private static String postalSector(String postalCode) {
		String code = strip(postalCode);
		return code.length() >= 2 && Character.isDigit(code.charAt(0)) && Character.isDigit(code.charAt(1))
				? code.substring(0, 2)
				: null;
	}

	/** "Hokkien, Mandarin" → {hokkien, mandarin}. */
	private static Set<String> dialects(String commaSeparated) {
		if (commaSeparated == null) {
			return Set.of();
		}
		return Arrays.stream(commaSeparated.split(","))
				.map(d -> d.strip().toLowerCase(Locale.ROOT))
				.filter(d -> !d.isEmpty())
				.collect(Collectors.toSet());
	}

	private static boolean sameSector(String a, String b) {
		return a != null && a.strip().equalsIgnoreCase(b);
	}

	private static String strip(String text) {
		return Objects.requireNonNullElse(text, "").strip();
	}

	private static boolean isBlank(String text) {
		return text == null || text.isBlank();
	}
}
