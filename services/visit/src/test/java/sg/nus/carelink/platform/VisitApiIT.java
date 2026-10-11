package sg.nus.carelink.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import sg.nus.carelink.testsupport.SharedMySql;
import sg.nus.carelink.visitapi.VisitApi;
import sg.nus.carelink.visitapi.VisitApiClients;
import sg.nus.carelink.visitapi.VisitNotFound;
import sg.nus.carelink.visitapi.VisitRuleViolation;

/**
 * visit's internal API in the running application, over real HTTP, for the reads of the visit table
 * incident and report make today: the same visits their own statements find, at the wall-clock
 * times they were booked for, with a database whose clock is not Singapore's. And re-rostering's
 * changes sent again with their request key: made once, with one assignment and one event, and the
 * first answer given again.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"carelink.report.schedule-cron=-",
		"carelink.escalation.scan-initial-delay=PT1H"})
class VisitApiIT {

	private static final LocalDateTime NINE = LocalDateTime.of(2026, 10, 12, 9, 0);

	private static final AtomicLong ACCOUNTS = new AtomicLong(990_000);

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbc;

	private VisitApi visit;

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		SharedMySql.register(registry, VisitApiIT.class, "+05:00", "connectionTimeZone=Asia/Singapore");
	}

	@BeforeEach
	void client() {
		visit = VisitApiClients.create(RestClient.builder().baseUrl("http://localhost:" + port));
	}

	@Test
	void anEldersComingVisitsAreTheBookedOnesInTheWindowEarliestFirst() {
		long elder = elder();
		long siti = caregiver("Siti Rahman");
		long ahmad = caregiver("Ahmad Ismail");
		long later = visit(elder, siti, NINE.plusDays(2), "SCHEDULED");
		long first = visit(elder, ahmad, NINE.plusDays(1), "SCHEDULED");
		visit(elder, null, NINE.plusDays(1), "SCHEDULED");
		visit(elder, siti, NINE.plusDays(1), "CANCELLED");
		visit(elder, siti, NINE.plusDays(14), "SCHEDULED");
		visit(elder(), siti, NINE.plusDays(1), "SCHEDULED");

		assertThat(visit.upcomingVisits(elder, NINE, NINE.plusDays(14)))
				.extracting(VisitApi.ElderVisit::visitId, VisitApi.ElderVisit::caregiverId, VisitApi.ElderVisit::start)
				.containsExactly(tuple(first, ahmad, NINE.plusDays(1)), tuple(later, siti, NINE.plusDays(2)));
	}

	@Test
	void theLatestCaregiverIsTheOneOnTheMostRecentVisitThatHadOne() {
		long elder = elder();
		long siti = caregiver("Siti Rahman");
		long ahmad = caregiver("Ahmad Ismail");
		visit(elder, siti, NINE.minusDays(3), "COMPLETED");
		visit(elder, ahmad, NINE.minusDays(1), "COMPLETED");
		visit(elder, null, NINE, "SCHEDULED");

		assertThat(visit.findLatestCaregiverId(elder)).contains(ahmad);
		assertThat(visit.findLatestCaregiverId(elder())).isEmpty();
	}

	@Test
	void aVisitComesBackAsBookedAndOneThatDoesNotExistIsNotFound() {
		long elder = elder();
		long siti = caregiver("Siti Rahman");
		long booked = visit(elder, siti, NINE, "SCHEDULED");

		assertThat(visit.visit(booked)).extracting(VisitApi.VisitSlot::elderId, VisitApi.VisitSlot::caregiverId,
				VisitApi.VisitSlot::start, VisitApi.VisitSlot::status).containsExactly(elder, siti, NINE, "SCHEDULED");
		assertThat(visit.findVisit(999_999L)).isEmpty();
		assertThatThrownBy(() -> visit.visit(999_999L)).isInstanceOf(VisitNotFound.class)
				.hasMessage("Visit [999999] does not exist");
	}

	@Test
	void aCaregiverIsBusyWhileABookedVisitOverlapsAndAVisitWithNoEndLastsAnHour() {
		long elder = elder();
		long siti = caregiver("Siti Rahman");
		visit(elder, siti, NINE, "SCHEDULED");
		visit(elder, siti, NINE.plusHours(3), "CANCELLED");
		jdbc.update("INSERT INTO visit (elder_id, caregiver_id, scheduled_start, status) VALUES (?, ?, ?, 'SCHEDULED')",
				elder, siti, Timestamp.valueOf(NINE.plusHours(6)));

		assertThat(visit.caregiverBusy(siti, NINE.plusMinutes(30), NINE.plusMinutes(45)).busy()).isTrue();
		assertThat(visit.caregiverBusy(siti, NINE.plusHours(1), NINE.plusHours(2)).busy()).as("from the visit's end on")
				.isFalse();
		assertThat(visit.caregiverBusy(siti, NINE.plusHours(3), NINE.plusHours(4)).busy()).as("a visit called off")
				.isFalse();
		assertThat(visit.caregiverBusy(siti, NINE.plusHours(6).plusMinutes(30), NINE.plusHours(7)).busy())
				.as("a visit with no end lasts an hour").isTrue();
		assertThat(visit.caregiverBusy(siti, NINE.plusHours(7), NINE.plusHours(8)).busy()).isFalse();
	}

	@Test
	void aHandOverSentAgainWithItsKeyIsMadeOnce() {
		long elder = elder();
		long siti = caregiver("Siti Rahman");
		long ahmad = caregiver("Ahmad Ismail");
		long booked = visit(elder, siti, NINE, "SCHEDULED");
		VisitApi.Reassignment toAhmad = new VisitApi.Reassignment(ahmad, new VisitApi.Change(4L, 10L, null,
				"Annual leave"));

		visit.reassign(booked, toAhmad, "it-hand-over-" + booked);
		visit.reassign(booked, toAhmad, "it-hand-over-" + booked);

		assertThat(visit.visit(booked).caregiverId()).isEqualTo(ahmad);
		assertThat(assignments(booked)).as("Siti's, ended, and Ahmad's").containsExactly("REPLACED " + siti,
				"ACTIVE " + ahmad);
		assertThat(events(booked, "VisitCaregiverChanged")).isEqualTo(1);
	}

	@Test
	void aMoveSentAgainWithItsKeyAnswersTheVisitItMade() {
		long elder = elder();
		long siti = caregiver("Siti Rahman");
		long booked = visit(elder, siti, NINE, "SCHEDULED");
		VisitApi.Move move = new VisitApi.Move(NINE.plusDays(1), siti, new VisitApi.Change(4L, 10L, null,
				"Moved at the family's request"));

		Long moved = visit.moveTo(booked, move, "it-move-" + booked).visitId();

		assertThat(visit.moveTo(booked, move, "it-move-" + booked).visitId()).isEqualTo(moved);
		assertThat(visit.visit(booked).status()).isEqualTo("CANCELLED");
		assertThat(visit.visit(moved).start()).isEqualTo(NINE.plusDays(1));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visit WHERE elder_id = ?", Long.class, elder)).isEqualTo(2);
		assertThat(events(booked, "VisitCancelled")).isEqualTo(1);
		assertThat(events(moved, "VisitScheduled")).isEqualTo(1);
	}

	@Test
	void withoutAKeyAChangeIsMadeEachTimeItIsSentAsBefore() {
		long elder = elder();
		long siti = caregiver("Siti Rahman");
		long ahmad = caregiver("Ahmad Ismail");
		long booked = visit(elder, siti, NINE, "SCHEDULED");
		VisitApi.Change why = new VisitApi.Change(4L, 10L, null, "Annual leave");

		visit.reassign(booked, new VisitApi.Reassignment(ahmad, why), null);
		visit.reassign(booked, new VisitApi.Reassignment(ahmad, why), null);
		visit.callOff(booked, why, null);

		assertThat(assignments(booked)).containsExactly("REPLACED " + siti, "REPLACED " + ahmad, "CANCELLED " + ahmad);
		assertThat(events(booked, "VisitCaregiverChanged")).isEqualTo(2);
		assertThatThrownBy(() -> visit.callOff(booked, why, null)).isInstanceOfSatisfying(VisitRuleViolation.class,
				refused -> assertThat(refused.code()).isEqualTo("VISIT_NOT_OPEN"));
	}

	@Test
	void aRefusalIsNotRememberedAndAKeyChangesOnlyItsOwnVisit() {
		long elder = elder();
		long siti = caregiver("Siti Rahman");
		long started = visit(elder, siti, NINE, "IN_PROGRESS");
		long booked = visit(elder, siti, NINE.plusDays(1), "SCHEDULED");
		long other = visit(elder, siti, NINE.plusDays(2), "SCHEDULED");
		VisitApi.Change why = new VisitApi.Change(4L, null, null, "Nobody was free to cover this visit");

		assertThatThrownBy(() -> visit.markUncovered(started, why, "it-uncover-" + started))
				.isInstanceOfSatisfying(VisitRuleViolation.class,
						refused -> assertThat(refused.code()).isEqualTo("VISIT_NOT_OPEN"));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visit_request WHERE request_key = ?", Long.class,
				"it-uncover-" + started)).isZero();

		visit.markUncovered(booked, why, "it-uncover-" + booked);
		assertThatThrownBy(() -> visit.markUncovered(other, why, "it-uncover-" + booked))
				.isInstanceOfSatisfying(VisitRuleViolation.class,
						refused -> assertThat(refused.code()).isEqualTo("REQUEST_KEY_REUSED"));
		assertThat(visit.visit(booked).status()).isEqualTo("EXCEPTION");
		assertThat(visit.visit(other).status()).isEqualTo("SCHEDULED");
	}

	private List<String> assignments(long visitId) {
		return jdbc.queryForList("SELECT CONCAT(status, ' ', caregiver_id) FROM visit_assignment WHERE visit_id = ?"
				+ " ORDER BY id", String.class, visitId);
	}

	private long events(long visitId, String type) {
		Long events = jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE source = 'visit' AND type = ?"
				+ " AND json_value(payload, '$.visitId' RETURNING SIGNED) = ?", Long.class, type, visitId);
		return events == null ? 0 : events;
	}

	private long elder() {
		jdbc.update("INSERT INTO elder (full_name) VALUES ('Visit API elder')");
		return jdbc.queryForObject("SELECT MAX(id) FROM elder", Long.class);
	}

	private long caregiver(String name) {
		jdbc.update("INSERT INTO caregiver (user_id, full_name, status) VALUES (?, ?, 'AVAILABLE')",
				ACCOUNTS.incrementAndGet(), name);
		return jdbc.queryForObject("SELECT MAX(id) FROM caregiver", Long.class);
	}

	private long visit(long elder, Long caregiver, LocalDateTime start, String status) {
		jdbc.update("INSERT INTO visit (elder_id, caregiver_id, service_type, scheduled_start, scheduled_end, status) "
				+ "VALUES (?, ?, 'BATHING', ?, ?, ?)", elder, caregiver, Timestamp.valueOf(start),
				Timestamp.valueOf(start.plusHours(1)), status);
		return jdbc.queryForObject("SELECT MAX(id) FROM visit", Long.class);
	}

}
