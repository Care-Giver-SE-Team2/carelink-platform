package sg.nus.carelink.rostering.application;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;

import sg.nus.carelink.profile.application.FamilyAccessQuery;
import sg.nus.carelink.profile.application.RosteringProfiles;
import sg.nus.carelink.rostering.domain.model.AbsenceReport;
import sg.nus.carelink.rostering.domain.model.RosterChange;
import sg.nus.carelink.rostering.domain.model.RosteringCandidate;
import sg.nus.carelink.rostering.domain.model.RosteringCandidateCheck;
import sg.nus.carelink.rostering.domain.model.RosteringConstraint;
import sg.nus.carelink.rostering.domain.repository.AbsenceAlert;
import sg.nus.carelink.rostering.domain.repository.AbsenceReportRepository;
import sg.nus.carelink.rostering.domain.repository.RosterAudit;
import sg.nus.carelink.rostering.domain.repository.RosterChangeAlert;
import sg.nus.carelink.rostering.domain.repository.RosterChangeRepository;
import sg.nus.carelink.rostering.domain.repository.RosteringCandidateCheckRepository;
import sg.nus.carelink.rostering.domain.repository.RosteringCandidateRepository;
import sg.nus.carelink.rostering.domain.repository.RosteringConstraintRepository;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.visit.application.VisitReassignment;

/**
 * In-memory stand-ins for every port UC-MG04 reaches, so the whole re-rostering flow runs as a
 * unit test: absences and changes kept in maps, visits that really change state, and alerts and
 * audits that are recorded rather than written.
 */
final class ReRosteringFakes {

	private ReRosteringFakes() {
	}

	static final class Absences implements AbsenceReportRepository {

		private final Map<Long, AbsenceReport> rows = new LinkedHashMap<>();
		private long nextId = 1;

		@Override
		public Optional<AbsenceReport> findById(Long id) {
			return Optional.ofNullable(rows.get(id));
		}

		@Override
		public AbsenceReport save(AbsenceReport a) {
			AbsenceReport stored = a.id() != null ? a
					: new AbsenceReport(nextId++, a.caregiverId(), a.reviewedByUserId(), a.type(), a.startDate(),
							a.endDate(), a.reason(), a.status(), a.createdAt(), a.updatedAt(), a.coverageConfirmedAt(),
							a.coverageConfirmedByUserId());
			rows.put(stored.id(), stored);
			return stored;
		}

		@Override
		public List<AbsenceReport> findAll(AbsenceReport.Status status) {
			return rows.values().stream().filter(a -> status == null || a.status() == status)
					.sorted(Comparator.comparing(AbsenceReport::startDate).reversed()).toList();
		}

		@Override
		public List<AbsenceReport> findByCaregiverId(Long caregiverId) {
			return rows.values().stream().filter(a -> a.caregiverId().equals(caregiverId)).toList();
		}

		@Override
		public List<AbsenceReport> findApprovedOverlapping(java.time.LocalDate from, java.time.LocalDate until) {
			return rows.values().stream().filter(AbsenceReport::isApproved)
					.filter(a -> !a.startDate().isAfter(until) && !a.endDate().isBefore(from)).toList();
		}
	}

	static final class Changes implements RosterChangeRepository {

		final Map<Long, RosterChange> rows = new LinkedHashMap<>();
		private long nextId = 100;

		@Override
		public Optional<RosterChange> findById(Long id) {
			return Optional.ofNullable(rows.get(id));
		}

		@Override
		public Optional<RosterChange> lock(Long id) {
			return findById(id);
		}

		@Override
		public RosterChange save(RosterChange c) {
			RosterChange stored = c.id() != null ? c
					: new RosterChange(nextId++, c.absenceId(), c.visitId(), c.elderId(), c.originalCaregiverId(),
							c.visitStart(), c.visitEnd(), c.rosteringRunId(), c.proposedCaregiverId(), c.status(),
							c.outcome(), c.decidedBy(), c.decidedByUserId(), c.assignedCaregiverId(),
							c.rescheduledVisitId(), c.incidentId(), c.respondBy(), c.decidedAt(), c.note(),
							c.createdAt(), c.updatedAt());
			rows.put(stored.id(), stored);
			return stored;
		}

		@Override
		public List<RosterChange> findByAbsenceId(Long absenceId) {
			return rows.values().stream().filter(c -> c.absenceId().equals(absenceId))
					.sorted(Comparator.comparing(RosterChange::visitStart)).toList();
		}

		@Override
		public List<RosterChange> findByElderIds(Collection<Long> elderIds) {
			return rows.values().stream().filter(c -> elderIds.contains(c.elderId()))
					.sorted(Comparator.comparing(RosterChange::visitStart)).toList();
		}

		@Override
		public List<RosterChange> findAwaitingFamilyDueBy(LocalDateTime now) {
			return rows.values().stream().filter(c -> c.defaultPlanDue(now)).toList();
		}

		RosterChange forVisit(Long visitId) {
			return rows.values().stream().filter(c -> c.visitId().equals(visitId)).reduce((a, b) -> b).orElseThrow();
		}
	}

	static final class Candidates implements RosteringCandidateRepository {

		final Map<Long, RosteringCandidate> rows = new LinkedHashMap<>();
		private long nextId = 1;

		@Override
		public Optional<RosteringCandidate> findById(Long id) {
			return Optional.ofNullable(rows.get(id));
		}

		@Override
		public RosteringCandidate save(RosteringCandidate c) {
			RosteringCandidate stored = c.id() != null ? c
					: new RosteringCandidate(nextId++, c.rosteringRunId(), c.visitId(), c.caregiverId(), c.optionRank(),
							c.score(), c.outcome(), c.excludedByCode(), c.matchReason());
			rows.put(stored.id(), stored);
			return stored;
		}

		@Override
		public List<RosteringCandidate> findByRunAndVisit(Long runId, Long visitId) {
			return rows.values().stream()
					.filter(c -> c.rosteringRunId().equals(runId) && c.visitId().equals(visitId))
					.sorted(Comparator.comparing((RosteringCandidate c) -> c.optionRank() == null)
							.thenComparing(c -> c.optionRank() == null ? Integer.MAX_VALUE : c.optionRank()))
					.toList();
		}
	}

	static final class Checks implements RosteringCandidateCheckRepository {

		final List<RosteringCandidateCheck> rows = new ArrayList<>();

		@Override
		public Optional<RosteringCandidateCheck> findById(Long id) {
			return rows.stream().filter(c -> c.id().equals(id)).findFirst();
		}

		@Override
		public RosteringCandidateCheck save(RosteringCandidateCheck c) {
			RosteringCandidateCheck stored = new RosteringCandidateCheck((long) rows.size() + 1, c.rosteringCandidateId(),
					c.rosteringConstraintId(), c.result(), c.detail());
			rows.add(stored);
			return stored;
		}

		@Override
		public List<RosteringCandidateCheck> findByCandidateIds(Collection<Long> ids) {
			return rows.stream().filter(c -> ids.contains(c.rosteringCandidateId())).toList();
		}
	}

	/** The rule set as V12 seeds it. */
	static final class Constraints implements RosteringConstraintRepository {

		final List<RosteringConstraint> rows = new ArrayList<>(List.of(
				rule(1L, "NOT_ON_LEAVE", RosteringConstraint.Kind.HARD, null),
				rule(2L, "CERTIFICATION_VALID", RosteringConstraint.Kind.HARD, null),
				rule(3L, "NO_TIME_CLASH", RosteringConstraint.Kind.HARD, null),
				rule(4L, "DAILY_VISIT_CAP", RosteringConstraint.Kind.HARD, "8"),
				rule(5L, "DAILY_HOURS_CAP", RosteringConstraint.Kind.HARD, "8.0"),
				rule(6L, "CONTINUITY", RosteringConstraint.Kind.SOFT, null),
				rule(7L, "SECTOR_BAND", RosteringConstraint.Kind.SOFT, null),
				rule(8L, "DIALECT_MATCH", RosteringConstraint.Kind.SOFT, null),
				rule(9L, "SPOT_CHECK", RosteringConstraint.Kind.SOFT, "90")));

		private static RosteringConstraint rule(Long id, String code, RosteringConstraint.Kind kind, String parameter) {
			return new RosteringConstraint(id, code, code.toLowerCase(), kind, parameter, true);
		}

		@Override
		public Optional<RosteringConstraint> findById(Long id) {
			return rows.stream().filter(c -> c.id().equals(id)).findFirst();
		}

		@Override
		public RosteringConstraint save(RosteringConstraint c) {
			rows.add(c);
			return c;
		}

		@Override
		public List<RosteringConstraint> findAll() {
			return List.copyOf(rows);
		}
	}

	/** Visits that change state the way the visit module changes them. */
	static final class Visits implements VisitReassignment {

		private static final Set<String> BOOKED = Set.of("SCHEDULED", "ARRIVED", "IN_PROGRESS", "COMPLETED");
		private static final java.time.ZoneId SINGAPORE = java.time.ZoneId.of("Asia/Singapore");

		final Map<Long, VisitSlot> rows = new LinkedHashMap<>();
		final Map<Long, Map<Long, Integer>> history = new HashMap<>();
		final List<String> calls = new ArrayList<>();
		private long nextId = 500;

		Visits add(Long id, Long elderId, Long caregiverId, LocalDateTime start, int minutes) {
			rows.put(id, new VisitSlot(id, elderId, caregiverId, 4L, "Personal care", start, start.plusMinutes(minutes),
					"SCHEDULED", null));
			return this;
		}

		void setStatus(Long id, String status) {
			VisitSlot v = rows.get(id);
			rows.put(id, new VisitSlot(v.visitId(), v.elderId(), v.caregiverId(), v.carePlanId(), v.serviceType(),
					v.start(), v.end(), status, v.absenceId()));
		}

		@Override
		public List<VisitSlot> unstartedFor(Long caregiverId, LocalDateTime from, LocalDateTime until) {
			return rows.values().stream()
					.filter(v -> Objects.equals(v.caregiverId(), caregiverId) && "SCHEDULED".equals(v.status()))
					.filter(v -> !v.start().isBefore(from) && v.start().isBefore(until))
					.sorted(Comparator.comparing(VisitSlot::start)).toList();
		}

		@Override
		public Optional<VisitSlot> find(Long visitId) {
			return Optional.ofNullable(rows.get(visitId));
		}

		@Override
		public List<Booking> bookingsBetween(LocalDateTime from, LocalDateTime until) {
			return rows.values().stream()
					.filter(v -> v.caregiverId() != null && BOOKED.contains(v.status()))
					.filter(v -> !v.start().isBefore(from) && v.start().isBefore(until))
					.map(v -> new Booking(v.visitId(), v.elderId(), v.caregiverId(), v.start(), v.end())).toList();
		}

		@Override
		public Map<Long, Integer> finishedVisitsWith(Long elderId, LocalDateTime since, LocalDateTime until) {
			return history.getOrDefault(elderId, Map.of());
		}

		@Override
		public void reassign(Long visitId, Long toCaregiverId, Change why) {
			VisitSlot v = requireOpen(visitId);
			rows.put(visitId, new VisitSlot(visitId, v.elderId(), toCaregiverId, v.carePlanId(), v.serviceType(),
					v.start(), v.end(), "SCHEDULED", why.absenceId()));
			calls.add("reassign " + visitId + " to " + toCaregiverId);
		}

		@Override
		public void markUncovered(Long visitId, Change why) {
			VisitSlot v = rows.get(visitId);
			if (!"SCHEDULED".equals(v.status())) {
				throw new BusinessRuleViolation("VISIT_NOT_OPEN", "not open");
			}
			rows.put(visitId, new VisitSlot(visitId, v.elderId(), null, v.carePlanId(), v.serviceType(), v.start(),
					v.end(), "EXCEPTION", why.absenceId()));
			calls.add("uncover " + visitId);
		}

		@Override
		public void callOff(Long visitId, Change why) {
			VisitSlot v = requireOpen(visitId);
			rows.put(visitId, new VisitSlot(visitId, v.elderId(), v.caregiverId(), v.carePlanId(), v.serviceType(),
					v.start(), v.end(), "CANCELLED", why.absenceId()));
			calls.add("call off " + visitId);
		}

		@Override
		public Long moveTo(Long visitId, LocalDateTime newStart, Long toCaregiverId, Change why) {
			VisitSlot v = requireOpen(visitId);
			callOff(visitId, why);
			Long id = nextId++;
			rows.put(id, new VisitSlot(id, v.elderId(), toCaregiverId, v.carePlanId(), v.serviceType(), newStart,
					newStart.plus(java.time.Duration.between(v.start().atZone(SINGAPORE), v.end().atZone(SINGAPORE))),
					"SCHEDULED", why.absenceId()));
			calls.add("move " + visitId + " to " + id);
			return id;
		}

		private VisitSlot requireOpen(Long visitId) {
			VisitSlot v = rows.get(visitId);
			boolean uncovered = "EXCEPTION".equals(v.status()) && v.caregiverId() == null && v.absenceId() != null;
			if (!"SCHEDULED".equals(v.status()) && !uncovered) {
				throw new BusinessRuleViolation("VISIT_NOT_OPEN", "Visit " + visitId + " is " + v.status());
			}
			return v;
		}
	}

	static final class Profiles implements RosteringProfiles {

		final List<Candidate> candidates = new ArrayList<>();
		final Map<Long, ElderFacts> elders = new HashMap<>();

		Profiles caregiver(Long id, String name, boolean onboarding) {
			candidates.add(new Candidate(id, 1000 + id, name, null, null, onboarding));
			return this;
		}

		Profiles elder(Long id, String name) {
			elders.put(id, new ElderFacts(id, 2000 + id, name, null, null));
			return this;
		}

		@Override
		public List<Candidate> candidates() {
			return List.copyOf(candidates);
		}

		@Override
		public Map<Long, String> credentialTypeNames(Collection<Long> ids) {
			return ids.stream().collect(Collectors.toMap(id -> id, id -> id == 2L ? "Nursing" : "Type " + id));
		}

		@Override
		public Optional<ElderFacts> elder(Long elderId) {
			return Optional.ofNullable(elders.get(elderId));
		}
	}

	/** Family "alex" is bound to elder 7 only. */
	static final class Family implements FamilyAccessQuery {

		final Map<String, Set<Long>> bindings = new HashMap<>(Map.of("alex", Set.of(7L)));

		@Override
		public Set<Long> readableElderIds(String username) {
			return bindings.getOrDefault(username, Set.of());
		}

		@Override
		public void requireReadableElder(String username, Long elderId) {
			if (!readableElderIds(username).contains(elderId)) {
				throw new AccessDeniedException("A readable elder binding is required");
			}
		}

		@Override
		public void requireWritableElder(String username, Long elderId) {
			requireReadableElder(username, elderId);
		}
	}

	static final class Alerts implements RosterChangeAlert {

		final List<String> sent = new ArrayList<>();
		final List<Notice> notices = new ArrayList<>();

		@Override
		public void offered(RosterChange change, Notice notice) {
			record("offered", change, notice);
		}

		@Override
		public void coordinating(RosterChange change, Notice notice) {
			record("coordinating", change, notice);
		}

		@Override
		public void settled(RosterChange change, Notice notice) {
			record("settled " + change.outcome(), change, notice);
		}

		private void record(String what, RosterChange change, Notice notice) {
			sent.add(what + " visit " + change.visitId());
			notices.add(notice);
		}
	}

	/** Records each absence notice as "name absenceId", plus what the nightly run did with added visits. */
	static final class AbsenceAlerts implements AbsenceAlert {

		final List<String> told;

		AbsenceAlerts(List<String> told) {
			this.told = told;
		}

		@Override
		public void requested(AbsenceReport absence, String caregiverName) {
			told.add(caregiverName + " " + absence.id());
		}

		@Override
		public void visitsRerostered(AbsenceReport absence, String caregiverName, Rerostered what) {
			told.add("%s %d offered %d, settled %d, uncovered %d".formatted(caregiverName, absence.id(), what.offered(),
					what.settled(), what.uncovered()));
		}
	}

	static final class Audit implements RosterAudit {

		final List<String> entries = new ArrayList<>();

		@Override
		public void defaultPlanApplied(RosterChange change, String detail) {
			entries.add(change.visitId() + ": " + detail);
		}
	}

	/** Lets a test step the clock forward. */
	static final class MutableClock extends java.time.Clock {

		private java.time.Instant now;
		private final java.time.ZoneId zone;

		MutableClock(LocalDateTime start, java.time.ZoneId zone) {
			this.now = start.atZone(zone).toInstant();
			this.zone = zone;
		}

		void set(LocalDateTime moment) {
			now = moment.atZone(zone).toInstant();
		}

		@Override
		public java.time.ZoneId getZone() {
			return zone;
		}

		@Override
		public java.time.Clock withZone(java.time.ZoneId other) {
			return this;
		}

		@Override
		public java.time.Instant instant() {
			return now;
		}
	}
}
