package sg.nus.carelink.visit.application;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.visit.domain.model.Visit;
import sg.nus.carelink.visit.domain.repository.VisitRepository;
import sg.nus.carelink.visit.domain.repository.VisitCommandRepository;

@Service
@Transactional
class VisitSchedulingService implements VisitScheduling {

	private final VisitRepository visits;
	private final VisitCommandRepository commands;
	private final VisitEvents events;

	VisitSchedulingService(VisitRepository visits, VisitCommandRepository commands, VisitEvents events) {
		this.visits = visits;
		this.commands = commands;
		this.events = events;
	}

	@Override
	public Outcome schedule(List<PlannedVisit> planned) {
		int created = 0;
		int covered = 0;
		Map<Long, List<PlannedVisit>> byPlan = planned.stream()
				.collect(Collectors.groupingBy(PlannedVisit::carePlanId));
		for (var entry : byPlan.entrySet()) {
			LocalDateTime earliest = entry.getValue().stream()
					.map(PlannedVisit::start).min(LocalDateTime::compareTo).orElseThrow();
			Map<Key, Visit> existing = visits.findByCarePlanIdStartingFrom(entry.getKey(), earliest).stream()
					.filter(v -> v.carePlanNodeId() != null)
					.collect(Collectors.toMap(Key::of, Function.identity(), (a, b) -> a));
			for (PlannedVisit wanted : entry.getValue()) {
				Visit visit = existing.get(new Key(wanted.carePlanNodeId(), wanted.start()));
				if (visit == null) {
					events.scheduled(visits.save(Visit.scheduled(wanted.elderId(), wanted.caregiverId(),
							wanted.carePlanId(), wanted.carePlanNodeId(), wanted.serviceType(), wanted.start(),
							wanted.end())), "PLAN", null);
					created++;
				} else if (wanted.caregiverId() != null && visit.caregiverId() == null && visit.hasNotStarted()) {
					events.caregiverChanged(visits.save(visit.coveredBy(wanted.caregiverId())), null);
					covered++;
				}
			}
		}
		return new Outcome(created, covered);
	}

	@Override
	public int cancelUntouchedFrom(Long carePlanId, LocalDateTime from) {
		List<Visit> untouched = visits.findByCarePlanIdStartingFrom(carePlanId, from).stream()
				.filter(Visit::hasNotStarted)
				.sorted(java.util.Comparator.comparing(Visit::id))
				.toList();
		int cancelled = 0;
		// Plan refresh runs AFTER the plan commits. Lock/re-read avoids leaving a stopped
		// plan's visits open when SYS03 advances their version between selection and save.
		for (Visit candidate : untouched) {
			var current = commands.lock(candidate.id()).filter(Visit::hasNotStarted);
			if (current.isPresent()) {
				events.cancelled(commands.save(current.get().cancelled()), "PLAN_CHANGED", null);
				cancelled++;
			}
		}
		return cancelled;
	}

	@Override
	public int releaseUntouchedFrom(Long elderId, Long caregiverId, LocalDateTime from) {
		List<Visit> held = visits.findScheduledForElderAndCaregiverFrom(elderId, caregiverId, from).stream()
				.filter(visit -> !visit.standalone() && visit.absenceId() == null)
				.toList();
		int released = 0;
		// Same lock and re-read as cancelUntouchedFrom: a check-in may land between the read and the save.
		for (Visit candidate : held) {
			var current = commands.lock(candidate.id())
					.filter(visit -> visit.hasNotStarted() && caregiverId.equals(visit.caregiverId()));
			if (current.isPresent()) {
				events.caregiverChanged(commands.save(current.get().released()), caregiverId);
				released++;
			}
		}
		return released;
	}

	@Override
	@Transactional(readOnly = true)
	public List<UncoveredVisit> findUncoveredStarted(LocalDateTime since, LocalDateTime now) {
		return visits.findUnassignedScheduledStartingBetween(since, now).stream()
				.map(v -> new UncoveredVisit(v.id(), v.elderId(), v.scheduledStart(), v.serviceType()))
				.toList();
	}

	@Override
	public boolean markUncoveredAsException(Long visitId) {
		return visits.findById(visitId)
				.filter(visit -> visit.caregiverId() == null && visit.hasNotStarted())
				.map(visit -> {
					events.exceptionRaised(visits.save(visit.uncoveredAtStart()), "UNCOVERED_AT_START", null);
					return true;
				})
				.orElse(false);
	}

	private record Key(Long carePlanNodeId, LocalDateTime start) {
		static Key of(Visit visit) {
			return new Key(visit.carePlanNodeId(), visit.scheduledStart());
		}
	}
}
