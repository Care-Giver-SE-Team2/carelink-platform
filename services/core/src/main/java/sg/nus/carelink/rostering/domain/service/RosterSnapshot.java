package sg.nus.carelink.rostering.domain.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import sg.nus.carelink.rostering.domain.model.AbsenceReport;

/**
 * Everything the replacement search knows at the moment it runs: the caregivers, what their
 * certificates cover, the care plans' requirements, who is on leave, who is booked when, and
 * the elders the vacated visits are for.
 *
 * <p>Gathered once per search by the application layer, from four modules, so the rules
 * themselves read plain data and can be tested without any of them.
 *
 * <p>Bookings are the one thing that changes during a search. When one vacated visit is given
 * to somebody, {@link #reserve} books them, so the next visit in the same search sees them busy
 * at that hour and one more visit into their day.
 */
public final class RosterSnapshot {

	private final List<CandidateCard> candidates;
	private final Map<Long, ElderCard> elders;
	private final Map<Long, Set<Long>> requiredTypesByPlan;
	private final Map<Long, Map<Long, LocalDate>> coveredUntil;
	private final Map<Long, String> typeNames;
	private final List<AbsenceReport> absences;
	private final List<Booking> bookings;
	private final Map<Long, SpotCheckRecord> spotChecks;

	private RosterSnapshot(Builder builder) {
		this.candidates = List.copyOf(builder.candidates);
		this.elders = Map.copyOf(builder.elders);
		this.requiredTypesByPlan = Map.copyOf(builder.requiredTypesByPlan);
		this.coveredUntil = copyNested(builder.coveredUntil);
		this.typeNames = Map.copyOf(builder.typeNames);
		this.absences = List.copyOf(builder.absences);
		this.bookings = new ArrayList<>(builder.bookings);
		this.spotChecks = Map.copyOf(builder.spotChecks);
	}

	public static Builder builder() {
		return new Builder();
	}

	/** Every caregiver the search considers, in a stable order. */
	public List<CandidateCard> candidates() {
		return candidates;
	}

	public ElderCard elder(Long elderId) {
		return elders.getOrDefault(elderId, ElderCard.unknown(elderId));
	}

	/** The credential types a care plan requires; none for a visit with no plan. */
	public Set<Long> requiredTypes(Long carePlanId) {
		return carePlanId == null ? Set.of() : requiredTypesByPlan.getOrDefault(carePlanId, Set.of());
	}

	/** The last day a caregiver may work visits needing this type, if they hold it at all. */
	public Optional<LocalDate> coveredUntil(Long caregiverId, Long credentialTypeId) {
		return Optional.ofNullable(coveredUntil.getOrDefault(caregiverId, Map.of()).get(credentialTypeId));
	}

	public String typeName(Long credentialTypeId) {
		return typeNames.getOrDefault(credentialTypeId, "certificate type " + credentialTypeId);
	}

	/** The approved absence keeping this caregiver away that day, if there is one. */
	public Optional<AbsenceReport> absenceKeepingAway(Long caregiverId, LocalDate day) {
		return absences.stream()
				.filter(absence -> Objects.equals(absence.caregiverId(), caregiverId))
				.filter(absence -> absence.keepsAwayOn(day))
				.findFirst();
	}

	/** The caregiver's first booking overlapping [start, end), ignoring the visit being filled. */
	public Optional<Booking> clash(Long caregiverId, LocalDateTime start, LocalDateTime end, Long excludingVisitId) {
		return bookings.stream()
				.filter(booking -> Objects.equals(booking.caregiverId(), caregiverId))
				.filter(booking -> !Objects.equals(booking.visitId(), excludingVisitId))
				.filter(booking -> booking.overlaps(start, end))
				.min(Comparator.comparing(Booking::start));
	}

	/** The caregiver's bookings starting that day, ignoring the visit being filled. */
	public List<Booking> bookingsOn(Long caregiverId, LocalDate day, Long excludingVisitId) {
		return bookings.stream()
				.filter(booking -> Objects.equals(booking.caregiverId(), caregiverId))
				.filter(booking -> !Objects.equals(booking.visitId(), excludingVisitId))
				.filter(booking -> booking.start().toLocalDate().equals(day))
				.toList();
	}

	/** Another visit the elder already has overlapping [start, end): you cannot be in two rooms. */
	public Optional<Booking> elderClash(Long elderId, LocalDateTime start, LocalDateTime end, Long excludingVisitId) {
		return bookings.stream()
				.filter(booking -> Objects.equals(booking.elderId(), elderId))
				.filter(booking -> !Objects.equals(booking.visitId(), excludingVisitId))
				.filter(booking -> booking.overlaps(start, end))
				.findFirst();
	}

	public SpotCheckRecord spotChecks(Long caregiverId) {
		return spotChecks.getOrDefault(caregiverId, SpotCheckRecord.NONE);
	}

	/** Books somebody on a visit for the rest of this search. */
	public void reserve(Booking booking) {
		bookings.removeIf(existing -> Objects.equals(existing.visitId(), booking.visitId()));
		bookings.add(Objects.requireNonNull(booking, "booking"));
	}

	private static Map<Long, Map<Long, LocalDate>> copyNested(Map<Long, Map<Long, LocalDate>> source) {
		Map<Long, Map<Long, LocalDate>> copy = new HashMap<>();
		source.forEach((caregiverId, byType) -> copy.put(caregiverId, Map.copyOf(byType)));
		return Map.copyOf(copy);
	}

	/** Assembles a snapshot; every part is optional and empty by default. */
	public static final class Builder {

		private final List<CandidateCard> candidates = new ArrayList<>();
		private final Map<Long, ElderCard> elders = new HashMap<>();
		private final Map<Long, Set<Long>> requiredTypesByPlan = new HashMap<>();
		private final Map<Long, Map<Long, LocalDate>> coveredUntil = new HashMap<>();
		private final Map<Long, String> typeNames = new HashMap<>();
		private final List<AbsenceReport> absences = new ArrayList<>();
		private final List<Booking> bookings = new ArrayList<>();
		private final Map<Long, SpotCheckRecord> spotChecks = new HashMap<>();

		private Builder() {
		}

		public Builder candidate(CandidateCard card) {
			candidates.add(card);
			return this;
		}

		public Builder candidates(List<CandidateCard> cards) {
			candidates.addAll(cards);
			return this;
		}

		public Builder elder(ElderCard card) {
			elders.put(card.elderId(), card);
			return this;
		}

		public Builder requiredTypes(Map<Long, Set<Long>> byPlan) {
			byPlan.forEach((planId, types) -> requiredTypesByPlan.put(planId, Set.copyOf(types)));
			return this;
		}

		/**
		 * One certificate. When a caregiver holds the same type twice - a renewal waiting next
		 * to the one it replaces - the later day counts, because either one lets them work.
		 */
		public Builder cover(Long caregiverId, Long credentialTypeId, LocalDate until) {
			coveredUntil.computeIfAbsent(caregiverId, id -> new HashMap<>())
					.merge(credentialTypeId, until, (a, b) -> a.isAfter(b) ? a : b);
			return this;
		}

		public Builder typeNames(Map<Long, String> names) {
			typeNames.putAll(names);
			return this;
		}

		public Builder absences(List<AbsenceReport> approved) {
			absences.addAll(approved);
			return this;
		}

		public Builder bookings(List<Booking> booked) {
			bookings.addAll(booked);
			return this;
		}

		public Builder spotChecks(Map<Long, SpotCheckRecord> records) {
			spotChecks.putAll(records);
			return this;
		}

		public RosterSnapshot build() {
			candidates.sort(Comparator.comparing(CandidateCard::caregiverId));
			return new RosterSnapshot(this);
		}
	}
}
