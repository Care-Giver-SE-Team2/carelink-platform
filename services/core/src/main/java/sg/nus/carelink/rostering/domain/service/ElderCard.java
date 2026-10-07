package sg.nus.carelink.rostering.domain.service;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The elder a vacated visit is for, as far as choosing a replacement goes: their sector, the
 * dialects they prefer, and how many visits each caregiver has already made to them.
 *
 * @param priorVisits caregiver id to the number of that caregiver's finished visits to this elder
 */
public record ElderCard(Long elderId, String name, String sector, Set<String> dialects,
		Map<Long, Integer> priorVisits) {

	public ElderCard {
		Objects.requireNonNull(elderId, "elderId");
		name = name == null ? "Elder #" + elderId : name;
		dialects = dialects == null ? Set.of() : Set.copyOf(dialects);
		priorVisits = priorVisits == null ? Map.of() : Map.copyOf(priorVisits);
	}

	/** Builds a card from the comma-separated dialect list profile stores. */
	public static ElderCard of(Long elderId, String name, String sector, String dialects,
			Map<Long, Integer> priorVisits) {
		return new ElderCard(elderId, name, sector, CandidateCard.splitDialects(dialects), priorVisits);
	}

	/** An elder nothing is known about beyond the id: every soft rule reads as not applicable. */
	public static ElderCard unknown(Long elderId) {
		return new ElderCard(elderId, null, null, Set.of(), Map.of());
	}

	public int priorVisitsBy(Long caregiverId) {
		return priorVisits.getOrDefault(caregiverId, 0);
	}
}
