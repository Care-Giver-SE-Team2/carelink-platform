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

@Service
@Transactional
class VisitSchedulingService implements VisitScheduling {

	private final VisitRepository visits;

	VisitSchedulingService(VisitRepository visits) {
		this.visits = visits;
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
					visits.save(Visit.scheduled(wanted.elderId(), wanted.caregiverId(), wanted.carePlanId(),
							wanted.carePlanNodeId(), wanted.serviceType(), wanted.start(), wanted.end()));
					created++;
				} else if (wanted.caregiverId() != null && visit.caregiverId() == null && visit.hasNotStarted()) {
					visits.save(visit.coveredBy(wanted.caregiverId()));
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
				.toList();
		untouched.forEach(visit -> visits.save(visit.cancelled()));
		return untouched.size();
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
				.map(visit -> visits.save(visit.uncoveredAtStart()) != null)
				.orElse(false);
	}

	private record Key(Long carePlanNodeId, LocalDateTime start) {
		static Key of(Visit visit) {
			return new Key(visit.carePlanNodeId(), visit.scheduledStart());
		}
	}
}
