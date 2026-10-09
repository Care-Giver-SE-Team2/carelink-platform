package sg.nus.carelink.rostering.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.profile.application.FamilyAccessQuery;
import sg.nus.carelink.profile.application.RosteringProfiles;
import sg.nus.carelink.rostering.domain.model.AbsenceReport;
import sg.nus.carelink.rostering.domain.model.RosterChange;
import sg.nus.carelink.rostering.domain.model.RosteringCandidate;
import sg.nus.carelink.rostering.domain.model.RosteringCandidateCheck;
import sg.nus.carelink.rostering.domain.model.RosteringConstraint;
import sg.nus.carelink.rostering.domain.model.RosteringRun;
import sg.nus.carelink.rostering.domain.repository.AbsenceReportRepository;
import sg.nus.carelink.rostering.domain.repository.RosterChangeRepository;
import sg.nus.carelink.rostering.domain.repository.RosteringCandidateCheckRepository;
import sg.nus.carelink.rostering.domain.repository.RosteringCandidateRepository;
import sg.nus.carelink.rostering.domain.repository.RosteringConstraintRepository;
import sg.nus.carelink.rostering.domain.repository.RosteringRunRepository;
import sg.nus.carelink.shared.error.ResourceNotFound;
import sg.nus.carelink.visit.application.VisitReassignment;

/**
 * What UC-MG04 looks like from each side. The manager sees every absence, every visit it
 * vacated, and for each visit every candidate with every rule result - the "why not her" the
 * search keeps for exactly this. The family sees only their own elders' changes and, for each,
 * the few suggestions they may choose between, with a reason each; other caregivers' leave and
 * certificates are not theirs to read.
 */
@Service
@Transactional(readOnly = true)
public class AbsenceQueryService {

	private final AbsenceReportRepository absences;
	private final RosterChangeRepository changes;
	private final RosteringRunRepository runs;
	private final RosteringCandidateRepository candidates;
	private final RosteringCandidateCheckRepository checks;
	private final RosteringConstraintRepository constraints;
	private final VisitReassignment visits;
	private final RosteringProfiles profiles;
	private final FamilyAccessQuery familyAccess;
	private final Clock clock;

	@SuppressWarnings("java:S107") // one collaborator per table and module the views are drawn from
	public AbsenceQueryService(AbsenceReportRepository absences, RosterChangeRepository changes,
			RosteringRunRepository runs, RosteringCandidateRepository candidates,
			RosteringCandidateCheckRepository checks, RosteringConstraintRepository constraints,
			VisitReassignment visits, RosteringProfiles profiles, FamilyAccessQuery familyAccess, Clock clock) {
		this.absences = absences;
		this.changes = changes;
		this.runs = runs;
		this.candidates = candidates;
		this.checks = checks;
		this.constraints = constraints;
		this.visits = visits;
		this.profiles = profiles;
		this.familyAccess = familyAccess;
		this.clock = clock;
	}

	// ------------------------------------------------------------------ manager ---

	/** The manager's list: each absence with how far its re-rostering has got. */
	public List<AbsenceSummary> list(AbsenceReport.Status status) {
		Map<Long, String> names = caregiverNames();
		LocalDateTime now = now();
		return absences.findAll(status).stream()
				.map(absence -> summarise(absence, changes.findByAbsenceId(absence.id()), names, now))
				.toList();
	}

	/** One absence in full: what is left to re-roster, and every change with its candidates. */
	public AbsenceCase caseOf(Long absenceId) {
		AbsenceReport absence = absences.findById(absenceId)
				.orElseThrow(() -> new ResourceNotFound("Absence", absenceId));
		Map<Long, String> names = caregiverNames();
		Map<Long, String> elderNames = new HashMap<>();
		LocalDateTime now = now();
		List<RosterChange> all = changes.findByAbsenceId(absenceId);
		Set<Long> handled = all.stream().map(RosterChange::visitId).collect(Collectors.toSet());

		List<VacatedVisit> notYet = Vacancies.notYetRerostered(visits, absence, handled, now).stream()
				.map(visit -> new VacatedVisit(visit.visitId(), visit.elderId(), elderName(visit.elderId(), elderNames),
						visit.serviceType(), visit.start(), visit.end()))
				.toList();
		Map<Long, RosteringConstraint> rules = constraints.findAll().stream()
				.collect(Collectors.toMap(RosteringConstraint::id, Function.identity(), (a, b) -> a));
		List<ChangeView> views = all.stream()
				.map(change -> managerView(change, names, elderNames, rules))
				.toList();
		return new AbsenceCase(summarise(absence, all, names, now), notYet, views);
	}

	// ------------------------------------------------------------------ family ---

	/** Changes for the elders this family member may read: those still waiting first, then the rest. */
	public List<FamilyChange> forFamily(String familyUsername) {
		Set<Long> elderIds = familyAccess.readableElderIds(familyUsername);
		if (elderIds.isEmpty()) {
			return List.of();
		}
		Map<Long, String> names = caregiverNames();
		Map<Long, String> elderNames = new HashMap<>();
		return changes.findByElderIds(elderIds).stream()
				.sorted(Comparator.comparing((RosterChange c) -> !c.awaitingFamily())
						.thenComparing(RosterChange::visitStart))
				.map(change -> familyView(change, names, elderNames))
				.toList();
	}

	/** One change, for a family member bound to its elder. */
	public FamilyChange forFamily(String familyUsername, Long changeId) {
		RosterChange change = changes.findById(changeId)
				.orElseThrow(() -> new ResourceNotFound("RosterChange", changeId));
		familyAccess.requireReadableElder(familyUsername, change.elderId());
		return familyView(change, caregiverNames(), new HashMap<>());
	}

	// ------------------------------------------------------------------ assembling ---

	private AbsenceSummary summarise(AbsenceReport absence, List<RosterChange> all, Map<Long, String> names,
			LocalDateTime now) {
		Set<Long> handled = all.stream().map(RosterChange::visitId).collect(Collectors.toSet());
		int notYet = Vacancies.notYetRerostered(visits, absence, handled, now).size();
		int waiting = (int) all.stream().filter(RosterChange::awaitingFamily).count();
		int uncovered = (int) all.stream().filter(RosterChange::isUncovered).count();
		int settled = all.size() - waiting - uncovered;
		return new AbsenceSummary(absence.id(), absence.caregiverId(), nameOr(names, absence.caregiverId()),
				absence.type(), absence.startDate(), absence.endDate(), absence.reason(), absence.status(),
				absence.reviewedByUserId(), absence.coverageConfirmedAt(), notYet, waiting, uncovered, settled);
	}

	private ChangeView managerView(RosterChange change, Map<Long, String> names, Map<Long, String> elderNames,
			Map<Long, RosteringConstraint> rules) {
		List<RosteringCandidate> considered = change.rosteringRunId() == null
				? List.of()
				: candidates.findByRunAndVisit(change.rosteringRunId(), change.visitId());
		Map<Long, List<RosteringCandidateCheck>> checksByCandidate = considered.isEmpty()
				? Map.of()
				: checks.findByCandidateIds(considered.stream().map(RosteringCandidate::id).toList()).stream()
						.collect(Collectors.groupingBy(RosteringCandidateCheck::rosteringCandidateId));
		List<CandidateView> views = considered.stream()
				.map(candidate -> new CandidateView(candidate.caregiverId(), nameOr(names, candidate.caregiverId()),
						candidate.optionRank(), candidate.score(), candidate.matchReason(), candidate.outcome(),
						candidate.excludedByCode(),
						checksByCandidate.getOrDefault(candidate.id(), List.of()).stream()
								.map(check -> checkView(check, rules))
								.toList()))
				.toList();
		RosteringRun.Objective objective = change.rosteringRunId() == null
				? null
				: runs.findById(change.rosteringRunId()).map(RosteringRun::objective).orElse(null);
		return new ChangeView(change.id(), change.visitId(), change.elderId(), elderName(change.elderId(), elderNames),
				change.visitStart(), change.visitEnd(), change.status(), change.outcome(), change.decidedBy(),
				change.decidedAt(), change.respondBy(), change.note(),
				person(change.originalCaregiverId(), names), person(change.proposedCaregiverId(), names),
				person(change.assignedCaregiverId(), names), change.rescheduledVisitId(),
				rescheduledStart(change), change.incidentId(), change.rosteringRunId(), objective, views,
				change.managerMayAssign() && change.visitStart().isAfter(now()));
	}

	private FamilyChange familyView(RosterChange change, Map<Long, String> names, Map<Long, String> elderNames) {
		List<FamilyOption> options = !change.awaitingFamily() || change.rosteringRunId() == null
				? List.of()
				: candidates.findByRunAndVisit(change.rosteringRunId(), change.visitId()).stream()
						.filter(RosteringCandidate::isSuggestion)
						.filter(c -> c.optionRank() != null && c.optionRank() <= AbsenceReRosteringService.FAMILY_OPTIONS)
						.sorted(Comparator.comparing(RosteringCandidate::optionRank))
						.map(c -> new FamilyOption(c.caregiverId(), nameOr(names, c.caregiverId()), c.optionRank(),
								c.matchReason()))
						.toList();
		return new FamilyChange(change.id(), change.elderId(), elderName(change.elderId(), elderNames),
				change.visitStart(), change.visitEnd(), nameOr(names, change.originalCaregiverId()), change.status(),
				change.respondBy(), change.proposedCaregiverId(), options, change.outcome(), change.decidedBy(),
				change.decidedAt(), person(change.assignedCaregiverId(), names), rescheduledStart(change),
				change.note());
	}

	private static CheckView checkView(RosteringCandidateCheck check, Map<Long, RosteringConstraint> rules) {
		RosteringConstraint rule = rules.get(check.rosteringConstraintId());
		return new CheckView(rule == null ? null : rule.code(), rule == null ? null : rule.name(),
				rule == null ? null : rule.kind(), check.result(), check.detail());
	}

	private LocalDateTime rescheduledStart(RosterChange change) {
		return change.rescheduledVisitId() == null
				? null
				: visits.find(change.rescheduledVisitId()).map(VisitReassignment.VisitSlot::start).orElse(null);
	}

	private Map<Long, String> caregiverNames() {
		return profiles.candidates().stream()
				.collect(Collectors.toMap(RosteringProfiles.Candidate::caregiverId,
						c -> Objects.requireNonNullElse(c.fullName(), "Caregiver #" + c.caregiverId()), (a, b) -> a));
	}

	private String elderName(Long elderId, Map<Long, String> cache) {
		return cache.computeIfAbsent(elderId, id -> profiles.elder(id)
				.map(RosteringProfiles.ElderFacts::fullName)
				.orElse("Elder #" + id));
	}

	private static Person person(Long caregiverId, Map<Long, String> names) {
		return caregiverId == null ? null : new Person(caregiverId, nameOr(names, caregiverId));
	}

	private static String nameOr(Map<Long, String> names, Long caregiverId) {
		return caregiverId == null ? null : Optional.ofNullable(names.get(caregiverId)).orElse("Caregiver #" + caregiverId);
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock);
	}

	// ------------------------------------------------------------------ views ---

	/**
	 * An absence and how far its re-rostering has got.
	 *
	 * @param notYetRerostered vacated visits nobody has searched for yet
	 * @param settled changes resolved one way or another
	 */
	public record AbsenceSummary(Long id, Long caregiverId, String caregiverName, AbsenceReport.Type type,
			LocalDate startDate, LocalDate endDate, String reason, AbsenceReport.Status status, Long reviewedByUserId,
			LocalDateTime coverageConfirmedAt, int notYetRerostered, int awaitingFamily, int uncovered, int settled) {
	}

	public record AbsenceCase(AbsenceSummary absence, List<VacatedVisit> notYetRerostered, List<ChangeView> changes) {
	}

	public record VacatedVisit(Long visitId, Long elderId, String elderName, String serviceType, LocalDateTime start,
			LocalDateTime end) {
	}

	public record Person(Long caregiverId, String name) {
	}

	/**
	 * One vacated visit as the manager sees it, with the run behind its current state.
	 *
	 * @param managerMayAssign whether the manager may hand-pick a caregiver for it now: see
	 *     {@link RosterChange#managerMayAssign()}, and the visit is still ahead
	 */
	public record ChangeView(Long id, Long visitId, Long elderId, String elderName, LocalDateTime visitStart,
			LocalDateTime visitEnd, RosterChange.Status status, RosterChange.Outcome outcome,
			RosterChange.DecidedBy decidedBy, LocalDateTime decidedAt, LocalDateTime respondBy, String note,
			Person absentCaregiver, Person proposedCaregiver, Person assignedCaregiver, Long rescheduledVisitId,
			LocalDateTime rescheduledStart, Long incidentId, Long rosteringRunId, RosteringRun.Objective objective,
			List<CandidateView> candidates, boolean managerMayAssign) {
	}

	public record CandidateView(Long caregiverId, String name, Integer rank, BigDecimal score, String reason,
			RosteringCandidate.Outcome outcome, String excludedBy, List<CheckView> checks) {
	}

	public record CheckView(String code, String name, RosteringConstraint.Kind kind,
			RosteringCandidateCheck.Result result, String detail) {
	}

	/** A change as the family sees it: what is happening, what they may choose, and what was decided. */
	public record FamilyChange(Long id, Long elderId, String elderName, LocalDateTime visitStart,
			LocalDateTime visitEnd, String usualCaregiverName, RosterChange.Status status, LocalDateTime respondBy,
			Long suggestedCaregiverId, List<FamilyOption> options, RosterChange.Outcome outcome,
			RosterChange.DecidedBy decidedBy, LocalDateTime decidedAt, Person assignedCaregiver,
			LocalDateTime rescheduledStart, String note) {
	}

	public record FamilyOption(Long caregiverId, String name, Integer rank, String reason) {
	}
}
