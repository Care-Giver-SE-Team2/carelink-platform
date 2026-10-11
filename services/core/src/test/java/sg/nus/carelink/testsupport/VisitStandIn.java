package sg.nus.carelink.testsupport;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.jdbc.core.JdbcTemplate;
import sg.nus.carelink.visitapi.VisitApi;
import sg.nus.carelink.visitapi.VisitNotFound;
import sg.nus.carelink.visitapi.VisitRuleViolation;

/**
 * visit, as core's tests meet it: the internal API the visit service serves, with the visits held in
 * memory and every answer given the way visit's own services give it (VisitReassignmentService,
 * VisitSchedulingService, UpcomingAssignmentsService, StandaloneVisitsService, JdbcVisitLookups).
 * Every core test that starts the application has it in place of the HTTP client
 * (VisitStandInConfiguration), and every test starts with no visits (VisitStandInReset).
 *
 * <p>A test lays out the visits it needs with {@link #add}, and sees what core made of them through
 * {@link #current}, {@link #ofElder} and {@link #askedOf}. Calls come from the test, from the server's
 * threads and from the schedulers, so every method holds the stand-in's lock.
 */
public class VisitStandIn implements VisitApi {

	/** States in which a caregiver is, was or will be at the visit: what fills their day. */
	private static final Set<String> BOOKED = Set.of("SCHEDULED", "ARRIVED", "IN_PROGRESS", "COMPLETED", "VERIFIED",
			"AUTO_CLOSED");

	/** States of a visit that was actually carried out. */
	private static final Set<String> FINISHED = Set.of("COMPLETED", "VERIFIED", "AUTO_CLOSED");

	/** States in which the caregiver is booked for the visit or at it. */
	private static final Set<String> UNDER_WAY = Set.of("SCHEDULED", "ARRIVED", "IN_PROGRESS");

	private static final Comparator<Held> EARLIEST_FIRST = Comparator.comparing(Held::start).thenComparing(Held::id);

	private final Map<Long, Held> visits = new LinkedHashMap<>();

	private final List<Asked> asked = new ArrayList<>();

	/** Ids well clear of the ones MySQL hands out, so a visit id that core took from its own tables stands out. */
	private long lastId = 900_000;

	/**
	 * A visit as visit holds it; {@code id} is null until the visit is added.
	 *
	 * @param checkedIn whether the caregiver has checked in
	 */
	public record Held(Long id, Long elderId, Long caregiverId, Long carePlanId, Long carePlanNodeId,
			String serviceType, LocalDateTime start, LocalDateTime end, String status, Long absenceId,
			boolean checkedIn) {

		/** A visit nobody has started yet and no care plan produced: what most tests lay out. */
		public static Held scheduled(Long elderId, Long caregiverId, LocalDateTime start, LocalDateTime end) {
			return new Held(null, elderId, caregiverId, null, null, "Personal care", start, end, "SCHEDULED", null,
					false);
		}

		public Held withStatus(String newStatus) {
			return new Held(id, elderId, caregiverId, carePlanId, carePlanNodeId, serviceType, start, end, newStatus,
					absenceId, checkedIn);
		}

		public Held withPlan(Long newCarePlanId) {
			return new Held(id, elderId, caregiverId, newCarePlanId, carePlanNodeId, serviceType, start, end, status,
					absenceId, checkedIn);
		}

		public Held withService(String newServiceType) {
			return new Held(id, elderId, caregiverId, carePlanId, carePlanNodeId, newServiceType, start, end, status,
					absenceId, checkedIn);
		}

		private Held withId(Long newId) {
			return new Held(newId, elderId, caregiverId, carePlanId, carePlanNodeId, serviceType, start, end, status,
					absenceId, checkedIn);
		}

		private Held with(Long newCaregiverId, String newStatus) {
			return new Held(id, elderId, newCaregiverId, carePlanId, carePlanNodeId, serviceType, start, end,
					newStatus, absenceId, checkedIn);
		}

		private Held forAbsence(Long newCaregiverId, String newStatus, Long forAbsenceId) {
			return new Held(id, elderId, newCaregiverId, carePlanId, carePlanNodeId, serviceType, start, end,
					newStatus, forAbsenceId, checkedIn);
		}

		private boolean notStarted() {
			return "SCHEDULED".equals(status);
		}

		/** An absence left it with nobody on it, and nobody has checked in: a replacement can still take it. */
		private boolean leftUncoveredByAbsence() {
			return "EXCEPTION".equals(status) && caregiverId == null && absenceId != null && !checkedIn;
		}

		private boolean startsIn(LocalDateTime from, LocalDateTime until) {
			return !start.isBefore(from) && start.isBefore(until);
		}
	}

	/**
	 * A change core asked visit to make to a visit, the way visit's assignment history records it.
	 *
	 * @param what REASSIGN, UNCOVERED, CALL_OFF or MOVE
	 * @param toCaregiverId who takes the visit; null when nobody does
	 */
	public record Asked(String what, Long visitId, Long toCaregiverId, VisitApi.Change why) {
	}

	// ---------- What a test does ----------

	/** Lays out a visit and answers its id: the one it carries, or a new one. */
	public synchronized Long add(Held visit) {
		Long id = visit.id() != null ? visit.id() : ++lastId;
		lastId = Math.max(lastId, id);
		visits.put(id, visit.withId(id));
		return id;
	}

	/** The visit as it stands now. */
	public synchronized Held current(Long visitId) {
		return require(visitId);
	}

	/** The elder's visits, earliest first. */
	public synchronized List<Held> ofElder(Long elderId) {
		return select(visit -> elderId.equals(visit.elderId())).toList();
	}

	/** What core asked visit to do to the visit, in the order it asked. */
	public synchronized List<Asked> askedOf(Long visitId) {
		return asked.stream().filter(request -> request.visitId().equals(visitId)).toList();
	}

	/**
	 * Takes over the visits in core's database that the stand-in does not hold yet. The demo scripts
	 * still write visit's tables there, so a test that loads one copies its visits across.
	 */
	public synchronized void copyVisitTable(JdbcTemplate jdbc) {
		jdbc.query("select id, elder_id, caregiver_id, care_plan_id, care_plan_node_id, service_type, scheduled_start,"
				+ " scheduled_end, status, absence_id, checked_in_at from visit order by id", (ResultSet row) -> {
					Long id = row.getLong("id");
					if (!visits.containsKey(id)) {
						add(new Held(id, row.getLong("elder_id"), nullable(row, "caregiver_id"),
								nullable(row, "care_plan_id"), nullable(row, "care_plan_node_id"),
								row.getString("service_type"), time(row, "scheduled_start"), time(row, "scheduled_end"),
								row.getString("status"), nullable(row, "absence_id"),
								row.getTimestamp("checked_in_at") != null));
					}
				});
	}

	/** Forgets every visit and every request: each test starts with none. */
	public synchronized void clear() {
		visits.clear();
		asked.clear();
	}

	// ---------- Reassignment ----------

	@Override
	public synchronized VisitSlot visit(Long visitId) {
		return slot(require(visitId));
	}

	@Override
	public synchronized List<VisitSlot> unstartedFor(Long caregiverId, LocalDateTime from, LocalDateTime until) {
		return select(visit -> caregiverId.equals(visit.caregiverId()) && visit.startsIn(from, until)
				&& visit.notStarted()).map(VisitStandIn::slot).toList();
	}

	@Override
	public synchronized List<Booking> bookingsBetween(LocalDateTime from, LocalDateTime until) {
		return select(visit -> visit.startsIn(from, until) && visit.caregiverId() != null
				&& BOOKED.contains(visit.status()))
				.map(visit -> new Booking(visit.id(), visit.elderId(), visit.caregiverId(), visit.start(), visit.end()))
				.toList();
	}

	@Override
	public synchronized Map<Long, Integer> finishedVisitsWith(Long elderId, LocalDateTime since, LocalDateTime until) {
		return select(visit -> visit.startsIn(since, until) && elderId.equals(visit.elderId())
				&& visit.caregiverId() != null && FINISHED.contains(visit.status()))
				.collect(Collectors.groupingBy(Held::caregiverId, Collectors.summingInt(visit -> 1)));
	}

	@Override
	public synchronized void reassign(Long visitId, Reassignment reassignment) {
		Held visit = requireOpen(visitId, "change hands");
		Objects.requireNonNull(reassignment.toCaregiverId(), "toCaregiverId");
		keep(visit.forAbsence(reassignment.toCaregiverId(), "SCHEDULED", reassignment.why().absenceId()));
		asked.add(new Asked("REASSIGN", visitId, reassignment.toCaregiverId(), reassignment.why()));
	}

	@Override
	public synchronized void markUncovered(Long visitId, Change why) {
		Held visit = require(visitId);
		if (!visit.notStarted()) {
			throw notOpen(visit, "be left uncovered");
		}
		keep(visit.forAbsence(null, "EXCEPTION", why.absenceId()));
		asked.add(new Asked("UNCOVERED", visitId, null, why));
	}

	@Override
	public synchronized void callOff(Long visitId, Change why) {
		Held visit = requireOpen(visitId, "be called off");
		keep(visit.forAbsence(visit.caregiverId(), "CANCELLED", why.absenceId()));
		asked.add(new Asked("CALL_OFF", visitId, null, why));
	}

	@Override
	public synchronized VisitRef moveTo(Long visitId, Move move) {
		Held visit = requireOpen(visitId, "be moved");
		keep(visit.forAbsence(visit.caregiverId(), "CANCELLED", move.why().absenceId()));
		LocalDateTime newEnd = visit.end() == null
				? null
				: move.newStart().plus(Duration.between(visit.start(), visit.end()));
		Long moved = add(new Held(null, visit.elderId(), move.toCaregiverId(), visit.carePlanId(),
				visit.carePlanNodeId(), visit.serviceType(), move.newStart(), newEnd, "SCHEDULED",
				move.why().absenceId(), false));
		asked.add(new Asked("MOVE", visitId, move.toCaregiverId(), move.why()));
		return new VisitRef(moved);
	}

	// ---------- Scheduling ----------

	@Override
	public synchronized Outcome schedule(List<PlannedVisit> planned) {
		int created = 0;
		int covered = 0;
		Map<Long, List<PlannedVisit>> byPlan = planned.stream().collect(Collectors.groupingBy(PlannedVisit::carePlanId));
		for (var plan : byPlan.entrySet()) {
			LocalDateTime earliest = plan.getValue().stream().map(PlannedVisit::start).min(LocalDateTime::compareTo)
					.orElseThrow();
			Map<Slot, Held> existing = select(visit -> plan.getKey().equals(visit.carePlanId())
					&& !visit.start().isBefore(earliest) && visit.carePlanNodeId() != null)
					.collect(Collectors.toMap(visit -> new Slot(visit.carePlanNodeId(), visit.start()), visit -> visit,
							(first, second) -> first));
			for (PlannedVisit wanted : plan.getValue()) {
				Held visit = existing.get(new Slot(wanted.carePlanNodeId(), wanted.start()));
				if (visit == null) {
					add(new Held(null, wanted.elderId(), wanted.caregiverId(), wanted.carePlanId(),
							wanted.carePlanNodeId(), wanted.serviceType(), wanted.start(), wanted.end(), "SCHEDULED",
							null, false));
					created++;
				}
				else if (wanted.caregiverId() != null && visit.caregiverId() == null && visit.notStarted()) {
					keep(visit.with(wanted.caregiverId(), visit.status()));
					covered++;
				}
			}
		}
		return new Outcome(created, covered);
	}

	@Override
	public synchronized Cancelled cancelUntouchedFrom(CancelUntouched request) {
		List<Held> untouched = select(visit -> request.carePlanId().equals(visit.carePlanId())
				&& !visit.start().isBefore(request.from()) && visit.notStarted()).toList();
		untouched.forEach(visit -> keep(visit.with(visit.caregiverId(), "CANCELLED")));
		return new Cancelled(untouched.size());
	}

	@Override
	public synchronized List<UncoveredVisit> findUncoveredStarted(LocalDateTime since, LocalDateTime now) {
		return visits.values().stream()
				.filter(visit -> visit.caregiverId() == null && visit.notStarted() && visit.startsIn(since, now))
				.map(visit -> new UncoveredVisit(visit.id(), visit.elderId(), visit.start(), visit.serviceType()))
				.toList();
	}

	@Override
	public synchronized Marked markUncoveredAsException(Long visitId) {
		Held visit = visits.get(visitId);
		if (visit == null || visit.caregiverId() != null || !visit.notStarted()) {
			return new Marked(false);
		}
		keep(visit.with(null, "EXCEPTION"));
		return new Marked(true);
	}

	@Override
	public synchronized List<Assignment> unstartedBetween(LocalDateTime from, LocalDateTime until) {
		return select(visit -> visit.startsIn(from, until) && visit.caregiverId() != null && visit.notStarted())
				.map(visit -> new Assignment(visit.id(), visit.caregiverId(), visit.carePlanId(), visit.start()))
				.toList();
	}

	// ---------- Standalone visits and schedule queries ----------

	@Override
	public synchronized VisitRef scheduleStandalone(NewVisit visit) {
		return new VisitRef(add(Held.scheduled(visit.elderId(), visit.caregiverId(), visit.start(), visit.end())
				.withService(visit.serviceType())));
	}

	@Override
	public synchronized State visitState(Long visitId) {
		Held visit = require(visitId);
		return new State(visit.id(), visit.caregiverId(), visit.status(), visit.checkedIn());
	}

	@Override
	public synchronized List<Long> caregiverIdsForElder(Long elderId) {
		return visits.values().stream()
				.filter(visit -> elderId.equals(visit.elderId()) && visit.caregiverId() != null)
				.map(Held::caregiverId).distinct().sorted().toList();
	}

	@Override
	public synchronized Assigned hasAssignedVisit(Set<Long> elderIds, Long caregiverId) {
		return new Assigned(elderIds != null && visits.values().stream()
				.anyMatch(visit -> elderIds.contains(visit.elderId()) && caregiverId.equals(visit.caregiverId())));
	}

	// ---------- Visit reads ----------

	@Override
	public synchronized List<ElderVisit> upcomingVisits(Long elderId, LocalDateTime from, LocalDateTime until) {
		return select(visit -> elderId.equals(visit.elderId()) && visit.notStarted() && visit.caregiverId() != null
				&& visit.startsIn(from, until))
				.map(visit -> new ElderVisit(visit.id(), visit.elderId(), visit.caregiverId(), visit.start(),
						visit.status(), visit.serviceType()))
				.toList();
	}

	@Override
	public synchronized CaregiverRef latestCaregiver(Long elderId) {
		return visits.values().stream()
				.filter(visit -> elderId.equals(visit.elderId()) && visit.caregiverId() != null)
				.max(EARLIEST_FIRST)
				.map(visit -> new CaregiverRef(visit.caregiverId()))
				.orElseThrow(() -> new VisitNotFound("A visit with a caregiver for elder [%s] does not exist".formatted(
						elderId)));
	}

	@Override
	public synchronized Busy caregiverBusy(Long caregiverId, LocalDateTime from, LocalDateTime until) {
		return new Busy(visits.values().stream()
				.anyMatch(visit -> caregiverId.equals(visit.caregiverId()) && UNDER_WAY.contains(visit.status())
						&& visit.start().isBefore(until)
						&& (visit.end() == null ? visit.start().plusHours(1) : visit.end()).isAfter(from)));
	}

	// ---------- How visit answers ----------

	private Stream<Held> select(Predicate<Held> wanted) {
		return visits.values().stream().filter(wanted).sorted(EARLIEST_FIRST);
	}

	private void keep(Held visit) {
		visits.put(visit.id(), visit);
	}

	private Held require(Long visitId) {
		Held visit = visits.get(visitId);
		if (visit == null) {
			throw new VisitNotFound("Visit [%s] does not exist".formatted(visitId));
		}
		return visit;
	}

	private Held requireOpen(Long visitId, String verb) {
		Held visit = require(visitId);
		if (!visit.notStarted() && !visit.leftUncoveredByAbsence()) {
			throw notOpen(visit, verb);
		}
		return visit;
	}

	private static VisitRuleViolation notOpen(Held visit, String verb) {
		return new VisitRuleViolation("VISIT_NOT_OPEN",
				"Visit %d is %s and can no longer %s".formatted(visit.id(), visit.status(), verb));
	}

	private static VisitSlot slot(Held visit) {
		return new VisitSlot(visit.id(), visit.elderId(), visit.caregiverId(), visit.carePlanId(),
				visit.serviceType(), visit.start(), visit.end(), visit.status(), visit.absenceId());
	}

	private static Long nullable(ResultSet row, String column) throws SQLException {
		long value = row.getLong(column);
		return row.wasNull() ? null : value;
	}

	private static LocalDateTime time(ResultSet row, String column) throws SQLException {
		Timestamp value = row.getTimestamp(column);
		return value == null ? null : value.toLocalDateTime();
	}

	/** A care plan's visit: its task and its start. */
	private record Slot(Long carePlanNodeId, LocalDateTime start) {
	}

}
