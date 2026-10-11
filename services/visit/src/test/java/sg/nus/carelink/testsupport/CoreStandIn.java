package sg.nus.carelink.testsupport;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import sg.nus.carelink.coreapi.CoreApi;
import sg.nus.carelink.coreapi.CoreNotFound;
import sg.nus.carelink.coreapi.CoreRuleViolation;

/**
 * core, as visit's tests meet it: the internal API core serves, answered the way core answers it. The
 * tests lay out people, families and plans in core's tables, which their database has because it is
 * built from core's migrations, and the stand-in reads them there with the rules of core's services
 * (FamilyAccessQueryService, CaregiverDirectoryService, CaregiverWorkDirectoryService,
 * VisitPlanReaderService). Its refusals are the client's exceptions (AccessDeniedException,
 * CoreNotFound, CoreRuleViolation), so visit's adapters translate them as they do in a real run.
 *
 * <p>What visit asks core to raise - a caregiver's report, a missed check-in, a service dispute -
 * the stand-in keeps in memory, as core's incident record would; a test reads it with
 * {@link #incidentsOf} and moves it on with {@link #resolve}. Credential alerts are core's policy, so
 * the stand-in answers none unless a test gives it some ({@link #alerts}). Every visit test that
 * starts the application has it in place of the HTTP client (CoreStandInConfiguration), and every
 * test starts with no incidents and no alerts (CoreStandInReset).
 *
 * <p>What visit never asks of core (the report service's and rostering's questions) is not here.
 */
public class CoreStandIn implements CoreApi {

	private static final ZoneId SINGAPORE = ZoneId.of("Asia/Singapore");

	/** How long staff have to respond to a new incident; core's own rule depends on the severity. */
	private static final long RESPOND_WITHIN_MINUTES = 15;

	private final JdbcTemplate jdbc;

	private final Clock clock;

	private final Map<Long, Raised> incidents = new HashMap<>();

	private final Map<Long, CredentialAlerts> alerts = new HashMap<>();

	/** Ids well clear of MySQL's, so an incident id that visit took from somewhere else stands out. */
	private long lastIncidentId = 700_000;

	/**
	 * An incident as core keeps it.
	 *
	 * @param source CAREGIVER, SYSTEM_MISSED_CHECKIN or ELDER_SERVICE_DISPUTE
	 * @param reportedByUserId null for one the system raised
	 */
	public record Raised(Long id, Long elderId, Long visitId, String source, Long reportedByUserId, String category,
			String severity, String description, String status, LocalDateTime reportedAt, LocalDateTime respondBy,
			LocalDateTime resolvedAt) {

		private Raised resolvedAt(LocalDateTime when) {
			return new Raised(id, elderId, visitId, source, reportedByUserId, category, severity, description,
					"RESOLVED", reportedAt, respondBy, when);
		}

		private IncidentReport report() {
			return new IncidentReport(id, visitId, category, severity, description, status, reportedAt, respondBy,
					resolvedAt);
		}
	}

	public CoreStandIn(JdbcTemplate jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	// ---------- What a test does ----------

	/** What visit asked core to raise about the visit, oldest first. */
	public synchronized List<Raised> incidentsOf(Long visitId) {
		return incidents.values().stream().filter(incident -> Objects.equals(incident.visitId(), visitId))
				.sorted((a, b) -> a.id().compareTo(b.id())).toList();
	}

	/** Staff resolve the incident in core. */
	public synchronized void resolve(Long incidentId) {
		Raised incident = Objects.requireNonNull(incidents.get(incidentId), "no such incident");
		incidents.put(incidentId, incident.resolvedAt(now()));
	}

	/** What core's alert policy answers for the caregiver from now on. */
	public synchronized void alerts(Long caregiverId, CredentialAlerts answer) {
		alerts.put(caregiverId, answer);
	}

	/** Forgets every incident and alert: each test starts with none. */
	public synchronized void clear() {
		incidents.clear();
		alerts.clear();
	}

	// ---------- Accounts ----------

	@Override
	public Account account(String username) {
		List<Account> found = jdbc.query("select id, username, display_name, enabled from app_user where username = ?",
				(row, n) -> new Account(row.getLong("id"), row.getString("username"), row.getString("display_name"),
						row.getBoolean("enabled"), Set.copyOf(roles(row.getLong("id")))),
				username);
		if (found.isEmpty()) {
			throw new CoreNotFound("Account [%s] does not exist".formatted(username));
		}
		return found.getFirst();
	}

	@Override
	public AccountIds enabledAccountsWithRole(String role) {
		return new AccountIds(jdbc.queryForList("select u.id from app_user u join user_role r on r.user_id = u.id"
				+ " where r.role = ? and u.enabled = true order by u.id", Long.class, role));
	}

	// ---------- Family access ----------

	@Override
	public ReadableElders readableElders(String username) {
		long familyMemberId = requireFamilyMemberId(username);
		return new ReadableElders(bindings(familyMemberId, null).stream()
				.filter(binding -> binding.allowsRead(now()))
				.map(Binding::elderId)
				.collect(Collectors.toUnmodifiableSet()));
	}

	@Override
	public void checkElderAccess(String username, Long elderId, Access access) {
		long familyMemberId = requireFamilyMemberId(username);
		String needed = access == Access.WRITE ? "A writable elder binding is required"
				: "A readable elder binding is required";
		if (elderId == null || elderId <= 0) {
			throw new AccessDeniedException(needed);
		}
		boolean allowed = bindings(familyMemberId, elderId).stream()
				.anyMatch(binding -> access == Access.WRITE ? binding.allowsWrite(now()) : binding.allowsRead(now()));
		if (!allowed) {
			throw new AccessDeniedException(needed);
		}
	}

	// ---------- Caregivers ----------

	@Override
	public CaregiverPublicProfile caregiverPublicProfile(Long caregiverId) {
		List<CaregiverPublicProfile> found = jdbc.query("select id, full_name, dialects from caregiver where id = ?",
				(row, n) -> new CaregiverPublicProfile(row.getLong("id"), row.getString("full_name"),
						dialects(row.getString("dialects"))),
				caregiverId);
		if (found.isEmpty()) {
			throw new CoreNotFound("Caregiver [%s] does not exist".formatted(caregiverId));
		}
		return found.getFirst();
	}

	@Override
	public List<CaregiverPublicCredential> caregiverPublicCredentials(Long caregiverId) {
		caregiverPublicProfile(caregiverId);
		LocalDate today = now().toLocalDate();
		List<CaregiverPublicCredential> shown = new ArrayList<>();
		jdbc.query("""
				select c.id, c.caregiver_id, c.credential_type_id, t.name, c.issuing_body, c.valid_from, c.expiry_date,
				       c.status
				from credential c join credential_type t on t.id = c.credential_type_id
				where c.caregiver_id = ? order by c.credential_type_id, c.id
				""", (ResultSet row) -> {
			LocalDate expiry = row.getDate("expiry_date").toLocalDate();
			String status = publicStatus(row.getString("status"), expiry, today);
			if (status != null) {
				shown.add(new CaregiverPublicCredential(row.getLong("id"), row.getLong("caregiver_id"),
						row.getLong("credential_type_id"), row.getString("name"), row.getString("issuing_body"),
						date(row, "valid_from"), expiry, status));
			}
		}, caregiverId);
		return shown;
	}

	@Override
	public CaregiverProfile caregiverByUsername(String username) {
		List<Long> account = jdbc.queryForList("select u.id from app_user u join user_role r on r.user_id = u.id"
				+ " where u.username = ? and u.enabled = true and r.role = 'CAREGIVER'", Long.class, username);
		if (account.isEmpty()) {
			throw new AccessDeniedException("A caregiver account is required");
		}
		List<CaregiverProfile> found = jdbc.query(
				"select id, user_id, full_name, phone, sector, dialects, status from caregiver where user_id = ?",
				(row, n) -> new CaregiverProfile(row.getLong("id"), row.getLong("user_id"), row.getString("full_name"),
						row.getString("phone"), row.getString("sector"), row.getString("dialects"),
						row.getString("status")),
				account.getFirst());
		if (found.isEmpty()) {
			throw new CoreNotFound("Caregiver profile for user [%s] does not exist".formatted(account.getFirst()));
		}
		return found.getFirst();
	}

	@Override
	public synchronized CredentialAlerts credentialAlerts(Long caregiverId, LocalDate today) {
		return alerts.getOrDefault(caregiverId,
				new CredentialAlerts(List.of(), new CredentialAlertContext(today, 30, false)));
	}

	// ---------- Elders and families ----------

	@Override
	public ElderView elderCaregiverView(Long elderId) {
		List<ElderView> found = jdbc.query(
				"select id, full_name, address, sector, preferred_dialects from elder where id = ?",
				(row, n) -> new ElderView(row.getLong("id"), row.getString("full_name"), row.getString("address"),
						row.getString("sector"), dialects(row.getString("preferred_dialects")), null, null),
				elderId);
		if (found.isEmpty()) {
			throw new CoreNotFound("Elder [%s] does not exist".formatted(elderId));
		}
		return found.getFirst();
	}

	@Override
	public ElderRef elderByUser(Long userId) {
		List<Long> found = jdbc.queryForList("select id from elder where user_id = ?", Long.class, userId);
		if (found.isEmpty()) {
			throw new CoreNotFound("Elder for user [%s] does not exist".formatted(userId));
		}
		return new ElderRef(found.getFirst());
	}

	@Override
	public FamilyMemberRef familyMemberByUser(Long userId) {
		List<Long> found = jdbc.queryForList("select id from family_member where user_id = ?", Long.class, userId);
		if (found.isEmpty()) {
			throw new CoreNotFound("Family member for user [%s] does not exist".formatted(userId));
		}
		return new FamilyMemberRef(found.getFirst());
	}

	@Override
	public List<Long> familyMemberIds(Long elderId) {
		throw notAskedByVisit();
	}

	@Override
	public AlertRecipient alertRecipient(Long elderId, Long familyMemberId) {
		throw notAskedByVisit();
	}

	@Override
	public CaregiverRef primaryCaregiver(Long elderId) {
		throw notAskedByVisit();
	}

	@Override
	public ElderReportProfile elderReportProfile(Long elderId) {
		throw notAskedByVisit();
	}

	// ---------- Care plans ----------

	@Override
	public CarePlanSnapshot carePlanSnapshot(Long planId, Long elderId) {
		List<Map<String, Object>> plan = jdbc.queryForList(
				"select id, elder_id, version, published_at from care_plan where id = ?", planId);
		if (plan.isEmpty()) {
			throw new CoreNotFound("Visit care plan [%s] does not exist".formatted(planId));
		}
		if (!elderId.equals(((Number) plan.getFirst().get("elder_id")).longValue())
				|| plan.getFirst().get("published_at") == null) {
			throw new CoreRuleViolation("VISIT_PLAN_UNAVAILABLE",
					"This visit does not reference a published plan for its elder.");
		}
		List<CarePlanTask> tasks = jdbc.query("select id, name, evidence_type from care_plan_node where care_plan_id = ?"
				+ " order by id", (row, n) -> new CarePlanTask(row.getLong("id"), row.getString("name"),
						Objects.requireNonNullElse(row.getString("evidence_type"), "NONE")),
				planId);
		return new CarePlanSnapshot(planId, ((Number) plan.getFirst().get("version")).intValue(), tasks);
	}

	// ---------- Incidents ----------

	@Override
	public synchronized IncidentReport reportIncident(CaregiverIncidentRequest request) {
		return raise(request.elderId(), request.visitId(), "CAREGIVER", request.actor(), request.category(),
				request.severity(), request.description()).report();
	}

	@Override
	public synchronized IncidentReport incident(Long incidentId, Long actor) {
		Raised incident = incidents.get(incidentId);
		if (incident == null || !"CAREGIVER".equals(incident.source()) || !actor.equals(incident.reportedByUserId())) {
			throw new CoreNotFound("Report [%s] does not exist".formatted(incidentId));
		}
		return incident.report();
	}

	@Override
	public synchronized IncidentReports incidents(Long actor, Long visitId, int page, int size) {
		List<IncidentReport> reports = incidents.values().stream()
				.filter(incident -> "CAREGIVER".equals(incident.source()) && actor.equals(incident.reportedByUserId()))
				.filter(incident -> visitId == null || visitId.equals(incident.visitId()))
				.sorted((a, b) -> b.id().compareTo(a.id()))
				.map(Raised::report)
				.toList();
		List<IncidentReport> onPage = reports.stream().skip((long) page * size).limit(size).toList();
		return new IncidentReports(onPage, page, size, reports.size());
	}

	@Override
	public synchronized IncidentRef raiseMissedCheckIn(MissedCheckInRequest request) {
		return new IncidentRef(raise(request.elderId(), request.visitId(), "SYSTEM_MISSED_CHECKIN", null, "SERVICE",
				"MEDIUM", "Caregiver has not checked in; the visit was due at " + request.dueAt()).id());
	}

	@Override
	public synchronized IncidentRef raiseServiceDispute(ServiceDisputeRequest request) {
		return new IncidentRef(raise(request.elderId(), request.visitId(), "ELDER_SERVICE_DISPUTE",
				request.reportedByUserId(), "SERVICE", "MEDIUM", request.description()).id());
	}

	/** core's MissedCheckInPauseEvidence: the visit's one incident, the missed check-in the system raised. */
	@Override
	public synchronized SoleIncident soleMissedCheckInIncident(Long incidentId, Long elderId, Long visitId) {
		List<Raised> related = incidents.values().stream()
				.filter(incident -> elderId.equals(incident.elderId()) && Objects.equals(incident.visitId(), visitId))
				.toList();
		return new SoleIncident(related.size() == 1 && related.getFirst().id().equals(incidentId)
				&& "SYSTEM_MISSED_CHECKIN".equals(related.getFirst().source())
				&& related.getFirst().reportedByUserId() == null);
	}

	// ---------- Rostering (asked by the report service, never by visit) ----------

	@Override
	public List<CoverOption> coverOptions(Long visitId) {
		throw notAskedByVisit();
	}

	@Override
	public void cover(Long visitId, CoverRequest request) {
		throw notAskedByVisit();
	}

	@Override
	public OnLeave onLeave(Long caregiverId, LocalDate day) {
		throw notAskedByVisit();
	}

	// ---------- How core answers ----------

	private Raised raise(Long elderId, Long visitId, String source, Long reportedBy, String category, String severity,
			String description) {
		LocalDateTime at = now();
		Raised incident = new Raised(++lastIncidentId, elderId, visitId, source, reportedBy, category, severity,
				description, "OPEN", at, at.plusMinutes(RESPOND_WITHIN_MINUTES), null);
		incidents.put(incident.id(), incident);
		return incident;
	}

	private long requireFamilyMemberId(String username) {
		if (username == null || username.isBlank()) {
			throw new AccessDeniedException("An authenticated family account is required");
		}
		List<Long> account = jdbc.queryForList("select u.id from app_user u join user_role r on r.user_id = u.id"
				+ " where u.username = ? and u.enabled = true and r.role = 'FAMILY'", Long.class, username);
		if (account.isEmpty()) {
			throw new AccessDeniedException("An enabled family account is required");
		}
		List<Long> family = jdbc.queryForList("select id from family_member where user_id = ?", Long.class,
				account.getFirst());
		if (family.isEmpty()) {
			throw new AccessDeniedException("A family profile is required");
		}
		return family.getFirst();
	}

	private List<Binding> bindings(long familyMemberId, Long elderId) {
		return jdbc.query("""
				select elder_id, access_scope, status, expires_at from elder_family_binding
				where family_member_id = ? and (? is null or elder_id = ?)
				""", (row, n) -> {
			Timestamp expiresAt = row.getTimestamp("expires_at");
			return new Binding(row.getLong("elder_id"), row.getString("access_scope"), row.getString("status"),
					expiresAt == null ? null : expiresAt.toLocalDateTime());
		}, familyMemberId, elderId, elderId);
	}

	private List<String> roles(long userId) {
		return jdbc.queryForList("select role from user_role where user_id = ? order by role", String.class, userId);
	}

	/** Credential.publicStatusOn in core: submitted and rejected ones are not shown, lapsed ones show as expired. */
	private static String publicStatus(String status, LocalDate expiry, LocalDate today) {
		return switch (status) {
			case "SUBMITTED", "REJECTED" -> null;
			case "REVOKED", "EXPIRED" -> status;
			default -> expiry.isBefore(today) ? "EXPIRED" : status;
		};
	}

	private static List<String> dialects(String stored) {
		return stored == null ? List.of()
				: Arrays.stream(stored.split(",")).map(String::strip).filter(dialect -> !dialect.isEmpty()).toList();
	}

	private static LocalDate date(ResultSet row, String column) throws SQLException {
		Date value = row.getDate(column);
		return value == null ? null : value.toLocalDate();
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock.withZone(SINGAPORE));
	}

	private static UnsupportedOperationException notAskedByVisit() {
		return new UnsupportedOperationException("visit never asks core this");
	}

	private record Binding(long elderId, String scope, String status, LocalDateTime expiresAt) {

		boolean allowsRead(LocalDateTime at) {
			return "ACTIVE".equals(status) && ("FULL".equals(scope) || "READ_ONLY".equals(scope))
					&& (expiresAt == null || expiresAt.isAfter(at));
		}

		boolean allowsWrite(LocalDateTime at) {
			return "ACTIVE".equals(status) && "FULL".equals(scope) && (expiresAt == null || expiresAt.isAfter(at));
		}

	}

}
