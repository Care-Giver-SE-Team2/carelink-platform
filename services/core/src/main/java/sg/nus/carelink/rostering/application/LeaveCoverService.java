package sg.nus.carelink.rostering.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.profile.application.RosteringProfiles;
import sg.nus.carelink.rostering.domain.model.AbsenceReport;
import sg.nus.carelink.rostering.domain.model.RosterChange;
import sg.nus.carelink.rostering.domain.model.RosteringRun;
import sg.nus.carelink.rostering.domain.model.VacatedSlot;
import sg.nus.carelink.rostering.domain.repository.AbsenceReportRepository;
import sg.nus.carelink.rostering.domain.repository.RosterAudit;
import sg.nus.carelink.rostering.domain.repository.RosterChangeAlert;
import sg.nus.carelink.rostering.domain.repository.RosterChangeRepository;
import sg.nus.carelink.rostering.domain.repository.RosteringCandidateCheckRepository;
import sg.nus.carelink.rostering.domain.repository.RosteringCandidateRepository;
import sg.nus.carelink.rostering.domain.repository.RosteringConstraintRepository;
import sg.nus.carelink.rostering.domain.repository.RosteringRunRepository;
import sg.nus.carelink.rostering.domain.service.Booking;
import sg.nus.carelink.rostering.domain.service.ReplacementFinder;
import sg.nus.carelink.rostering.domain.service.RosterSnapshot;
import sg.nus.carelink.rostering.domain.service.ScoringObjective;
import sg.nus.carelink.rostering.domain.service.Shortlist;
import sg.nus.carelink.visit.application.VisitReassignment;

/**
 * UC-MG03 while the primary caregiver is on leave: once somebody covers an elder for an
 * absence, the elder's new visits during that absence go to them too, instead of to the
 * caregiver who is away. The refresh gives visits to the primary caregiver; this moves the ones
 * on their leave days to the cover, so the family keeps the person they already accepted and
 * nobody has to re-roster each new visit by hand.
 *
 * <p>The cover is whoever the latest settled change for that elder under the absence put on
 * the visit - the family's pick, the default plan or a manager's. They must still pass every
 * hard rule at the new visit's time; if they do not, or nobody covers the elder yet, the visit
 * stays with the absent caregiver for the Absences screen's re-rostering, as before.
 *
 * <p>Each move is recorded the way UC-MG04 records one, through {@link ReplacementSearchRecord}
 * and its own repositories, so the absence still accounts for every visit it vacated: a run
 * with its candidates and rule results, and a change settled by the default plan. The family
 * is told who is coming.
 */
@Service
@Transactional
public class LeaveCoverService {

	private final AbsenceReportRepository absences;
	private final RosterChangeRepository changes;
	private final RosteringRunRepository runs;
	private final ReplacementSearchRecord search;
	private final RosterSnapshotLoader snapshots;
	private final VisitReassignment visits;
	private final RosteringProfiles profiles;
	private final RosterChangeAlert alert;
	private final RosterAudit audit;
	private final Clock clock;

	@SuppressWarnings("java:S107") // one collaborator per table a UC-MG04 record touches, as in AbsenceReRosteringService
	public LeaveCoverService(AbsenceReportRepository absences, RosterChangeRepository changes,
			RosteringRunRepository runs, RosteringCandidateRepository candidates,
			RosteringCandidateCheckRepository checks, RosteringConstraintRepository constraints,
			RosterSnapshotLoader snapshots, VisitReassignment visits, RosteringProfiles profiles,
			RosterChangeAlert alert, RosterAudit audit, Clock clock) {
		this.absences = absences;
		this.changes = changes;
		this.runs = runs;
		this.search = new ReplacementSearchRecord(constraints, candidates, checks);
		this.snapshots = snapshots;
		this.visits = visits;
		this.profiles = profiles;
		this.alert = alert;
		this.audit = audit;
		this.clock = clock;
	}

	/**
	 * Gives the elder's visits still held by a caregiver on approved leave to whoever covers the
	 * elder for that absence. Its own transaction, run after the elder's refresh has committed,
	 * so a failure here never undoes the visits the refresh made.
	 *
	 * @return how many visits went to a cover
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public int continueCover(Long elderId) {
		LocalDateTime now = LocalDateTime.now(clock);
		int moved = 0;
		List<AbsenceReport> stillAhead = absences.findAll(AbsenceReport.Status.APPROVED).stream()
				.filter(absence -> absence.windowEnd().isAfter(now))
				.toList();
		for (AbsenceReport absence : stillAhead) {
			List<RosterChange> recorded = changes.findByAbsenceId(absence.id());
			Optional<Long> cover = coverFor(elderId, recorded);
			if (cover.isEmpty()) {
				continue;
			}
			Set<Long> handled = recorded.stream().map(RosterChange::visitId).collect(Collectors.toSet());
			List<VacatedSlot> slots = Vacancies.notYetRerostered(visits, absence, handled, now).stream()
					.filter(visit -> visit.elderId().equals(elderId))
					.map(visit -> new VacatedSlot(visit.visitId(), visit.elderId(), visit.carePlanId(),
							visit.serviceType(), visit.start(), visit.end(), absence.caregiverId()))
					.toList();
			if (!slots.isEmpty()) {
				moved += giveToCover(absence, cover.get(), slots, now);
			}
		}
		return moved;
	}

	/** The caregiver the latest settled change for this elder put on the visit, if any. */
	private static Optional<Long> coverFor(Long elderId, List<RosterChange> recorded) {
		return recorded.stream()
				.filter(change -> elderId.equals(change.elderId()))
				.filter(change -> change.outcome() == RosterChange.Outcome.REPLACED && change.assignedCaregiverId() != null)
				.max(Comparator.comparing(RosterChange::decidedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
				.map(RosterChange::assignedCaregiverId);
	}

	private int giveToCover(AbsenceReport absence, Long coverId, List<VacatedSlot> slots, LocalDateTime now) {
		ReplacementSearchRecord.RuleSet rules = search.ruleSet();
		ReplacementFinder finder = new ReplacementFinder(rules.rules(), ScoringObjective.of(RosteringRun.Objective.CONTINUITY));
		RosterSnapshot snapshot = snapshots.load(slots);
		RosteringRun run = runs.save(RosteringRun.forAbsence(absence.id(), finder.objective().objective(), null, now));
		int moved = 0;
		int continuity = 0;
		for (VacatedSlot slot : slots) {
			Shortlist shortlist = finder.shortlist(slot, snapshot);
			Map<Long, Long> candidateIds = search.record(run.id(), shortlist, rules);
			Optional<Shortlist.Verdict> verdict = shortlist.ranked().stream()
					.filter(candidate -> candidate.caregiverId().equals(coverId))
					.findFirst();
			if (verdict.isEmpty()) {
				continue;
			}
			snapshot.reserve(Booking.of(slot, coverId));
			if (snapshot.elder(slot.elderId()).priorVisitsBy(coverId) > 0) {
				continuity++;
			}
			Long candidateId = candidateIds.get(coverId);
			String why = "%s already covers this elder during the leave, so the new visit went to them too"
					.formatted(verdict.get().name());
			visits.reassign(slot.visitId(), coverId, new VisitReassignment.Change(absence.id(), null, candidateId,
					"Default plan (absence %d): %s".formatted(absence.id(), why)));
			search.select(candidateId);
			RosterChange settled = changes.save(RosterChange.offered(absence.id(), slot, run.id(), coverId, now, now)
					.replacedBy(coverId, run.id(), RosterChange.DecidedBy.DEFAULT_PLAN, null, why, now));
			alert.settled(settled, new RosterChangeAlert.Notice(elderName(slot.elderId()), verdict.get().name(), coverId,
					null, why));
			audit.defaultPlanApplied(settled, "%s; caregiver %d assigned to visit %d".formatted(why, coverId,
					slot.visitId()));
			moved++;
		}
		runs.save(run.committed(slots.size(), moved, continuity, now));
		return moved;
	}

	private String elderName(Long elderId) {
		return profiles.elder(elderId).map(RosteringProfiles.ElderFacts::fullName).orElse(null);
	}
}
