package sg.nus.carelink.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import sg.nus.carelink.incident.application.IncidentService;
import sg.nus.carelink.incident.domain.model.Incident;
import sg.nus.carelink.incident.domain.model.SpotCheck;
import sg.nus.carelink.incident.domain.repository.SpotCheckRepository;
import sg.nus.carelink.rostering.domain.model.RosterChange;
import sg.nus.carelink.rostering.domain.model.VacatedSlot;
import sg.nus.carelink.rostering.domain.repository.RosterChangeRepository;
import sg.nus.carelink.testsupport.SharedMySql;

/**
 * The events core publishes for report (docs/platform/event-catalogue.md), against MySQL and with
 * the application's own JSON settings. An incident and every entry on its timeline leave their
 * events in the outbox in the order they were written, a spot check and a roster change leave
 * their whole state, each time is what the database keeps with Singapore's offset, and a change
 * that rolls back leaves no event behind.
 */
@SpringBootTest(properties = {
		"carelink.report.schedule-cron=-",
		"carelink.escalation.scan-initial-delay=PT1H"})
class CoreEventsIT {

	private static final ZoneId SINGAPORE = ZoneId.of("Asia/Singapore");

	private static final AtomicLong ACCOUNTS = new AtomicLong(980_000);

	private static Long manager;

	@Autowired
	private IncidentService incidents;

	@Autowired
	private SpotCheckRepository spotChecks;

	@Autowired
	private RosterChangeRepository rosterChanges;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private final JsonMapper json = JsonMapper.builder().build();

	private Long elder;

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		SharedMySql.register(registry, CoreEventsIT.class, null, "connectionTimeZone=Asia/Singapore");
	}

	@BeforeEach
	void aManagerAndAFreshElder() {
		if (manager == null) {
			jdbc.update("insert into app_user (username, password_hash, display_name, enabled)"
					+ " values ('events-manager', '{noop}unused-here', 'Events Manager', true)");
			manager = jdbc.queryForObject("select last_insert_id()", Long.class);
			jdbc.update("insert into user_role (user_id, role) values (?, 'MANAGER')", manager);
		}
		jdbc.update("insert into elder (full_name) values ('Events Elder')");
		elder = jdbc.queryForObject("select last_insert_id()", Long.class);
	}

	@Test
	void anIncidentAndEveryEntryOnItsTimelineArePublishedInTheOrderTheyWereWritten() {
		Incident raised = incidents.reportByCaregiver(elder, null, null, Incident.Category.FALL, Incident.Severity.HIGH,
				"Slipped in the bathroom");
		incidents.claim(raised.id(), raised.responderUserId(), "test");
		incidents.resolve(raised.id(), raised.responderUserId(), "Checked and settled", "HANDLED_ON_SITE", "test");

		List<Event> events = eventsAbout("incidentId", raised.id());
		assertThat(events).extracting(Event::source).containsOnly("core");
		assertThat(events.getFirst().type()).isEqualTo("IncidentRaised");
		JsonNode created = events.getFirst().payload();
		assertThat(created.get("elderId").asLong()).isEqualTo(elder);
		assertThat(created.get("category").asString()).isEqualTo("FALL");
		assertThat(created.get("severity").asString()).isEqualTo("HIGH");
		assertThat(created.get("status").asString()).isEqualTo("OPEN");
		assertThat(created.get("description").asString()).isEqualTo("Slipped in the bathroom");
		assertThat(created.get("reportedAt").asString()).as("what the database keeps, with Singapore's offset")
				.isEqualTo(databaseTime("select reported_at from incident where id = ?", raised.id()));

		List<JsonNode> timeline = events.stream().filter(event -> event.type().equals("IncidentUpdated"))
				.map(Event::payload).toList();
		assertThat(timeline).extracting(entry -> entry.get("logId").asLong())
				.as("one event per entry, in the order the entries were written")
				.containsExactlyElementsOf(jdbc.queryForList(
						"select id from incident_log where incident_id = ? order by id", Long.class, raised.id()));
		assertThat(timeline.getFirst().get("action").asString()).isEqualTo("REPORTED");
		JsonNode last = timeline.getLast();
		assertThat(last.get("action").asString()).isEqualTo("RESOLVED");
		assertThat(last.get("status").asString()).isEqualTo("RESOLVED");
		assertThat(last.get("detail").asString()).startsWith("HANDLED_ON_SITE");
		assertThat(last.get("resolvedAt").asString())
				.isEqualTo(databaseTime("select resolved_at from incident where id = ?", raised.id()));
	}

	@Test
	void aSpotCheckAndARosterChangeArePublishedWithTheirWholeState() {
		long caregiver = caregiver();
		LocalDateTime visitStart = LocalDateTime.now(SINGAPORE).plusDays(2).withHour(10).withMinute(0).withSecond(0)
				.withNano(0);
		SpotCheck check = spotChecks.save(SpotCheck.requested(elder, 812L, caregiver, visitStart, "Routine check", manager,
				LocalDateTime.now(SINGAPORE)));
		RosterChange change = rosterChanges.save(RosterChange.offered(33L,
				new VacatedSlot(812L, elder, null, "BATHING", visitStart, visitStart.plusHours(1), caregiver), null,
				caregiver + 1, visitStart.minusHours(2), LocalDateTime.now(SINGAPORE)));

		JsonNode spotCheck = single(eventsAbout("spotCheckId", check.id()), "SpotCheckUpdated");
		assertThat(spotCheck.get("elderId").asLong()).isEqualTo(elder);
		assertThat(spotCheck.get("caregiverId").asLong()).isEqualTo(caregiver);
		assertThat(spotCheck.get("approvalStatus").asString()).isEqualTo("PENDING_APPROVAL");
		assertThat(spotCheck.get("proposedTime").asString()).isEqualTo(offset(visitStart));
		assertThat(spotCheck.get("outcome").isNull()).isTrue();

		JsonNode rosterChange = single(eventsAbout("rosterChangeId", change.id()), "RosterChangeUpdated");
		assertThat(rosterChange.get("status").asString()).isEqualTo("AWAITING_FAMILY");
		assertThat(rosterChange.get("absenceId").asLong()).isEqualTo(33L);
		assertThat(rosterChange.get("visitStart").asString()).isEqualTo(offset(visitStart));
		assertThat(rosterChange.get("outcome").isNull()).isTrue();
	}

	@Test
	void aChangeThatRollsBackLeavesNoEvent() {
		Long[] raised = new Long[1];
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			raised[0] = incidents.reportByCaregiver(elder, null, null, Incident.Category.FALL, Incident.Severity.LOW,
					"Written, then rolled back").id();
			status.setRollbackOnly();
		});

		assertThat(eventsAbout("incidentId", raised[0])).isEmpty();
	}

	private record Event(long sequence, String type, String source, JsonNode payload) {
	}

	/** The outbox rows whose payload names this record, in the order they were written. */
	private List<Event> eventsAbout(String field, Long id) {
		return jdbc.query("select id, type, source, payload from outbox_event"
						+ " where json_unquote(json_extract(payload, ?)) = ? order by id",
				(row, number) -> new Event(row.getLong("id"), row.getString("type"), row.getString("source"),
						json.readTree(row.getString("payload"))),
				"$." + field, String.valueOf(id));
	}

	private static JsonNode single(List<Event> events, String type) {
		assertThat(events).extracting(Event::type).containsExactly(type);
		return events.getFirst().payload();
	}

	/** A time as the database keeps it, read the way the application reads it, written as events write it. */
	private String databaseTime(String sql, Long id) {
		return offset(jdbc.queryForObject(sql, Timestamp.class, id).toLocalDateTime());
	}

	private static String offset(LocalDateTime wallClock) {
		return wallClock.atZone(SINGAPORE).toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
	}

	private long caregiver() {
		jdbc.update("insert into caregiver (user_id, full_name, status) values (?, 'Events Caregiver', 'AVAILABLE')",
				ACCOUNTS.incrementAndGet());
		return jdbc.queryForObject("select last_insert_id()", Long.class);
	}

}
