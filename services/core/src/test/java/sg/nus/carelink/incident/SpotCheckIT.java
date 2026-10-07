package sg.nus.carelink.incident;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import sg.nus.carelink.incident.application.SpotCheckHistory;
import sg.nus.carelink.incident.application.SpotCheckService;
import sg.nus.carelink.incident.domain.model.SpotCheck;
import sg.nus.carelink.testsupport.SharedMySql;

/**
 * UC-MG08 end to end on real MySQL: V13's columns, the visit and family-member lookups, the
 * notifications to the family, the manager and the caregiver, the incident a no-show raises,
 * and the conclusions rostering reads.
 *
 * <p>Aisha has Mdm Tan's visit tomorrow at ten, Ben the one the day after. Alex is Mdm Tan's son.
 */
@SpringBootTest
@TestPropertySource(properties = {
		"carelink.rerostering.scan-initial-delay=PT1H",
		"carelink.escalation.scan-initial-delay=PT1H",
		"carelink.roster.uncovered-scan-initial-delay=PT1H"
})
class SpotCheckIT {

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		SharedMySql.register(registry, SpotCheckIT.class, null);
	}

	@Autowired
	private SpotCheckService spotChecks;
	@Autowired
	private SpotCheckHistory history;
	@Autowired
	private Clock clock;
	@Autowired
	private JdbcTemplate jdbc;

	private Long manager;
	private Long elder;
	private Long familyAccount;
	private String familyUsername;
	private String aishaUsername;
	private Long aisha;
	private Long ben;
	private Long tomorrowVisit;
	private Long laterVisit;

	@BeforeEach
	void givenAnElderHerFamilyAndTwoVisits() {
		String run = Long.toString(System.nanoTime(), 36);
		manager = account("mgr-" + run, "MANAGER");
		jdbc.update("insert into elder (full_name) values ('Mdm Tan')");
		elder = lastId();
		familyUsername = "family-" + run;
		familyAccount = account(familyUsername, "FAMILY");
		jdbc.update("insert into family_member (user_id, full_name) values (?, 'Alex Tan')", familyAccount);
		jdbc.update("insert into elder_family_binding (elder_id, family_member_id, relationship, access_scope, status,"
				+ " confirmed_at) values (?, ?, 'SON', 'FULL', 'ACTIVE', ?)", elder, lastId(),
				Timestamp.valueOf(LocalDateTime.now(clock).minusDays(30)));
		aishaUsername = "aisha-" + run;
		aisha = caregiver(aishaUsername, "Aisha");
		ben = caregiver("ben-" + run, "Ben");
		LocalDate today = LocalDate.now(clock);
		tomorrowVisit = visit(aisha, today.plusDays(1).atTime(10, 0));
		laterVisit = visit(ben, today.plusDays(2).atTime(10, 0));
	}

	@Test
	void aCheckIsAgreedConcludedAnsweredAndReachesRostering() {
		assertThat(spotChecks.visitsToCheck(elder)).extracting(SpotCheckService.VisitChoice::visitId)
				.containsExactly(tomorrowVisit, laterVisit);
		assertThat(spotChecks.visitsToCheck(elder).get(0).start()).isEqualTo(LocalDate.now(clock).plusDays(1).atTime(10, 0));

		Long id = spotChecks.request(tomorrowVisit, "Follow-up on a missed medication", manager).id();
		assertThat(notifications(familyAccount, "SPOT_CHECK_REQUESTED")).isEqualTo(1);

		SpotCheck approved = spotChecks.decide(id, familyUsername, true, null);
		assertThat(approved.approvingFamilyMemberId()).isNotNull();
		assertThat(notifications(manager, "SPOT_CHECK_APPROVED")).isEqualTo(1);

		spotChecks.conclude(id, SpotCheck.Result.NEEDS_IMPROVEMENT, "Gloves not worn for personal care");
		assertThat(jdbc.queryForObject("select concat(result, '/', outcome) from spot_check where id = ?", String.class, id))
				.isEqualTo("NEEDS_IMPROVEMENT/COMPLETED");
		assertThat(notifications(familyAccount, "SPOT_CHECK_CONCLUDED")).isEqualTo(1);
		assertThat(notifications(userOf(aisha), "SPOT_CHECK_CONCLUDED")).isEqualTo(1);

		spotChecks.respond(id, aishaUsername, "The gloves ran out; I have asked for more.");
		assertThat(jdbc.queryForObject("select caregiver_response from spot_check where id = ?", String.class, id))
				.isEqualTo("The gloves ran out; I have asked for more.");
		assertThat(spotChecks.forFamily(familyUsername)).singleElement()
				.extracting(SpotCheckService.SpotCheckView::stage).isEqualTo(SpotCheck.Stage.COMPLETED);

		assertThat(history.recentConclusions(LocalDateTime.now(clock).minusDays(90)))
				.containsEntry(aisha, new SpotCheckHistory.Conclusions(0, 1));
	}

	@Test
	void aCaregiverWhoDidNotComeLeavesAnIncidentAndAWithdrawnCheckItsReason() {
		Long id = spotChecks.request(laterVisit, "Routine", manager).id();
		spotChecks.decide(id, familyUsername, true, null);

		SpotCheck noShow = spotChecks.reportNoShow(id, "Nobody came by 10:20", "Manager (mgr)");

		assertThat(jdbc.queryForObject("select count(*) from incident where id = ? and visit_id = ?"
				+ " and source = 'SYSTEM_MISSED_CHECKIN' and responder_user_id is not null", Long.class,
				noShow.incidentId(), laterVisit)).isEqualTo(1L);
		assertThat(jdbc.queryForObject("select outcome from spot_check where id = ?", String.class, id))
				.isEqualTo("CAREGIVER_NO_SHOW");

		Long other = spotChecks.request(tomorrowVisit, "Routine", manager).id();
		spotChecks.withdraw(other, "Aisha is moving to another elder");
		assertThat(jdbc.queryForObject("select closing_reason from spot_check where id = ?", String.class, other))
				.isEqualTo("Aisha is moving to another elder");
		assertThat(notifications(familyAccount, "SPOT_CHECK_WITHDRAWN")).isEqualTo(1);
		assertThat(spotChecks.conclusionsFor(ben)).isEmpty();
	}

	// ------------------------------------------------------------------ the institution ---

	private Long account(String username, String role) {
		jdbc.update("insert into app_user (username, password_hash, display_name, enabled) values (?, '{noop}x', ?, true)",
				username, username);
		Long id = lastId();
		jdbc.update("insert into user_role (user_id, role) values (?, ?)", id, role);
		return id;
	}

	private Long caregiver(String username, String name) {
		Long user = account(username, "CAREGIVER");
		jdbc.update("insert into caregiver (user_id, full_name, status) values (?, ?, 'AVAILABLE')", user, name);
		return lastId();
	}

	private Long visit(Long caregiverId, LocalDateTime start) {
		jdbc.update("insert into visit (elder_id, caregiver_id, service_type, scheduled_start, scheduled_end, status)"
				+ " values (?, ?, 'Personal care', ?, ?, 'SCHEDULED')", elder, caregiverId, Timestamp.valueOf(start),
				Timestamp.valueOf(start.plusMinutes(45)));
		return lastId();
	}

	private Long userOf(Long caregiverId) {
		return jdbc.queryForObject("select user_id from caregiver where id = ?", Long.class, caregiverId);
	}

	private long notifications(Long userId, String eventType) {
		Long rows = jdbc.queryForObject("select count(*) from notification where recipient_user_id = ? and event_type = ?"
				+ " and resource_type = 'SPOT_CHECK'", Long.class, userId, eventType);
		return rows == null ? 0L : rows;
	}

	private Long lastId() {
		return jdbc.queryForObject("select last_insert_id()", Long.class);
	}
}
