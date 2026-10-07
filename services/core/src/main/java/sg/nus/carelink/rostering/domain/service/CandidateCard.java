package sg.nus.carelink.rostering.domain.service;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A caregiver as the replacement search sees them: who they are, where they work, what they
 * speak, and whether they are still onboarding.
 *
 * @param dialects lower-cased, so matching is not defeated by "Hokkien" against "hokkien"
 */
public record CandidateCard(Long caregiverId, String name, String sector, Set<String> dialects, boolean onboarding) {

	public CandidateCard {
		Objects.requireNonNull(caregiverId, "caregiverId");
		name = name == null ? "Caregiver #" + caregiverId : name;
		dialects = dialects == null ? Set.of() : Set.copyOf(dialects);
	}

	/** Builds a card from the comma-separated dialect list profile stores. */
	public static CandidateCard of(Long caregiverId, String name, String sector, String dialects, boolean onboarding) {
		return new CandidateCard(caregiverId, name, sector, splitDialects(dialects), onboarding);
	}

	/** "Hokkien, Teochew" to {hokkien, teochew}; blanks dropped. Shared with {@link ElderCard}. */
	static Set<String> splitDialects(String list) {
		if (list == null || list.isBlank()) {
			return Set.of();
		}
		return Arrays.stream(list.split("[,;/]"))
				.map(String::strip)
				.filter(part -> !part.isEmpty())
				.map(part -> part.toLowerCase(Locale.ROOT))
				.collect(Collectors.toUnmodifiableSet());
	}
}
