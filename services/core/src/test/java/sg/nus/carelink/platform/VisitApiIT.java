package sg.nus.carelink.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.sql.Timestamp;
import java.time.LocalDateTime;
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

/**
 * visit's internal API in the running application, over real HTTP, for the reads of the visit table
 * incident makes today: the same visits incident's own statements find, at the wall-clock times
 * they were booked for, with a database whose clock is not Singapore's.
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
