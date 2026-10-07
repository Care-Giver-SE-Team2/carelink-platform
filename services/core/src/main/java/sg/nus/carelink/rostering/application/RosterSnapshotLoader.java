package sg.nus.carelink.rostering.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import sg.nus.carelink.careplan.application.CarePlanRequirements;
import sg.nus.carelink.incident.application.SpotCheckHistory;
import sg.nus.carelink.profile.application.CredentialRegister;
import sg.nus.carelink.profile.application.RosteringProfiles;
import sg.nus.carelink.rostering.domain.model.VacatedSlot;
import sg.nus.carelink.rostering.domain.repository.AbsenceReportRepository;
import sg.nus.carelink.rostering.domain.service.Booking;
import sg.nus.carelink.rostering.domain.service.CandidateCard;
import sg.nus.carelink.rostering.domain.service.ElderCard;
import sg.nus.carelink.rostering.domain.service.RosterSnapshot;
import sg.nus.carelink.rostering.domain.service.SpotCheckRecord;
import sg.nus.carelink.visit.application.VisitReassignment;

/**
 * Gathers what the replacement search reads, from the four modules that own it: caregivers and
 * certificates from profile, each plan's required certificates from careplan, bookings and
 * visit history from visit, and approved leave from this module. One read each, so a search
 * over seven vacated visits costs the same handful of queries as a search over one.
 *
 * <p>Everything here is somebody else's published interface used as it is (CredentialRegister
 * and CarePlanRequirements were written for UC-MG06 and answer this question too).
 */
@Component
class RosterSnapshotLoader {

	private final RosteringProfiles profiles;
	private final CredentialRegister register;
	private final CarePlanRequirements requirements;
	private final AbsenceReportRepository absences;
	private final VisitReassignment visits;
	private final SpotCheckHistory spotChecks;
	private final RosterLookbacks lookbacks;
	private final Clock clock;

	RosterSnapshotLoader(RosteringProfiles profiles, CredentialRegister register, CarePlanRequirements requirements,
			AbsenceReportRepository absences, VisitReassignment visits, SpotCheckHistory spotChecks,
			RosterLookbacks lookbacks, Clock clock) {
		this.profiles = profiles;
		this.register = register;
		this.requirements = requirements;
		this.absences = absences;
		this.visits = visits;
		this.spotChecks = spotChecks;
		this.lookbacks = lookbacks;
		this.clock = clock;
	}

	/** Everything the search needs to fill these visits; the days they fall on bound the reads. */
	RosterSnapshot load(Collection<VacatedSlot> slots) {
		if (slots.isEmpty()) {
			return RosterSnapshot.builder().build();
		}
		LocalDate firstDay = slots.stream().map(VacatedSlot::day).min(Comparator.naturalOrder()).orElseThrow();
		LocalDate lastDay = slots.stream().map(VacatedSlot::day).max(Comparator.naturalOrder()).orElseThrow();

		Set<Long> planIds = slots.stream().map(VacatedSlot::carePlanId).filter(Objects::nonNull)
				.collect(Collectors.toSet());
		Map<Long, Set<Long>> required = planIds.isEmpty() ? Map.of() : requirements.requiredCredentialTypes(planIds);
		Set<Long> typeIds = new HashSet<>();
		required.values().forEach(typeIds::addAll);

		RosterSnapshot.Builder snapshot = RosterSnapshot.builder()
				.candidates(profiles.candidates().stream()
						.map(c -> CandidateCard.of(c.caregiverId(), c.fullName(), c.sector(), c.dialects(), c.onboarding()))
						.toList())
				.requiredTypes(required)
				.typeNames(profiles.credentialTypeNames(typeIds))
				.absences(absences.findApprovedOverlapping(firstDay, lastDay))
				.bookings(visits.bookingsBetween(firstDay.atStartOfDay(), lastDay.plusDays(1).atStartOfDay()).stream()
						.map(b -> new Booking(b.visitId(), b.elderId(), b.caregiverId(), b.start(), b.end()))
						.toList());
		register.covers().forEach(cover -> snapshot.cover(cover.caregiverId(), cover.credentialTypeId(),
				cover.coveredUntil()));

		LocalDateTime now = LocalDateTime.now(clock);
		slots.stream().map(VacatedSlot::elderId).distinct().forEach(elderId -> {
			Map<Long, Integer> prior = visits.finishedVisitsWith(elderId, now.minus(lookbacks.continuity()), now);
			snapshot.elder(profiles.elder(elderId)
					.map(e -> ElderCard.of(elderId, e.fullName(), e.sector(), e.preferredDialects(), prior))
					.orElseGet(() -> ElderCard.of(elderId, null, null, null, prior)));
		});
		Map<Long, SpotCheckRecord> conclusions = new HashMap<>();
		spotChecks.recentConclusions(now.minus(lookbacks.spotChecks())).forEach((caregiverId, c) ->
				conclusions.put(caregiverId, new SpotCheckRecord(c.metStandard(), c.needsImprovement())));
		return snapshot.spotChecks(conclusions).build();
	}
}
