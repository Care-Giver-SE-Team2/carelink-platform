package sg.nus.carelink.profile.domain.service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * UC-MG02: whether a caregiver holds the certificates an elder's care plan requires, so they may
 * be named its primary caregiver and be given all its visits. The rule rostering's
 * CERTIFICATION_VALID applies to one visit, applied from the first day the caregiver would work
 * each plan version: every type it requires must still cover them on that day. A certificate
 * that lapses later is the certification register's to flag, as it is for any booked visit.
 */
public final class PrimaryCaregiverCertification {

	private PrimaryCaregiverCertification() {
	}

	/**
	 * One plan version in force or yet to start: the first day the caregiver would work it (today,
	 * or its start date if later) and the credential types it requires.
	 */
	public record Need(LocalDate from, Set<Long> credentialTypeIds) {
	}

	/**
	 * A required type the caregiver lacks on {@code neededFrom}. {@code coveredUntil} is the last
	 * day their certificate of that type covers them, or null when they hold none.
	 */
	public record Gap(Long credentialTypeId, LocalDate neededFrom, LocalDate coveredUntil) {
	}

	/**
	 * The first type the caregiver lacks, earliest plan first and lowest type id within a plan,
	 * so the same caregiver is always refused for the same reason.
	 *
	 * @param coveredUntil the caregiver's credential types, each with the last day it covers them
	 */
	public static Optional<Gap> firstGap(List<Need> needs, Map<Long, LocalDate> coveredUntil) {
		return needs.stream()
				.sorted(Comparator.comparing(Need::from))
				.flatMap(need -> new TreeSet<>(need.credentialTypeIds()).stream()
						.map(type -> new Gap(type, need.from(), coveredUntil.get(type))))
				.filter(gap -> gap.coveredUntil() == null || gap.coveredUntil().isBefore(gap.neededFrom()))
				.findFirst();
	}
}
