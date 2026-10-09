package sg.nus.carelink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import sg.nus.carelink.incident.application.SpotCheckHistory;
import sg.nus.carelink.rostering.application.AbsenceReRosteringService;
import sg.nus.carelink.rostering.domain.model.RosteringRun;
import sg.nus.carelink.testsupport.SharedMySql;

/**
 * The Absences and Quality screens' demonstration script loads after demo-seed.sql, can be loaded
 * again, and gives the re-rostering search the picture its header promises: the absent caregiver's
 * visits waiting, one person ranked first on continuity, and one excluded for each hard rule the
 * data can show. The search itself is run, as the manager's button runs it, because a seed that
 * only looks right in the tables proves nothing about the screen.
 *
 * <p>Loaded as a script on one connection, as DemoSeedIT does, through the JDBC time-zone setting
 * the application runs with.
 *
 * @author Wang Ziyu
 */
@SpringBootTest
@AutoConfigureMockMvc
class AbsencesAndSpotChecksDemoIT {

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		SharedMySql.register(registry, AbsencesAndSpotChecksDemoIT.class, null, "connectionTimeZone=Asia/Singapore");
	}

	private static final String SEED = "db/demo/manager-absences-and-spot-checks.sql";

	private static final List<String> TABLES = List.of("app_user", "user_role", "caregiver", "credential",
			"family_member", "elder_family_binding", "absence_report", "visit", "visit_task", "visit_assignment",
			"spot_check", "notification");

	@Autowired
	private MockMvc mvc;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private AbsenceReRosteringService rerostering;
	@Autowired
	private SpotCheckHistory spotChecks;
	@Autowired
	private Clock clock;

	@Test
	void laysOutTheScenarioOnceAndAgainOnlyAfterItHasBeenUsed() throws Exception {
		load("db/demo/demo-seed.sql");
		load(SEED);
		Map<String, Long> before = counts();

		load(SEED);

		assertThat(counts()).isEqualTo(before);

		// The absence is approved and its three visits wait to be re-rostered.
		long absence = aishasLatestAbsence();
		assertThat(jdbc.queryForObject("select status from absence_report where id = ?", String.class, absence))
				.isEqualTo("APPROVED");

		var outcome = rerostering.reroster(absence, RosteringRun.Objective.CONTINUITY, idOf("demo-alice"));

		assertThat(outcome.searched()).isEqualTo(3);
		assertThat(outcome.offered()).isEqualTo(3);
		assertThat(outcome.uncovered()).isZero();

		// Grace's first visit: who ranked first, and the hard rule that kept each of the others off.
		long run = outcome.runId();
		long graceFirst = jdbc.queryForObject(
				"select visit_id from roster_change where absence_id = ? order by visit_start, id limit 1", Long.class,
				absence);
		assertThat(candidate(run, graceFirst, "demo-meiling")).containsEntry("option_rank", 1)
				.containsEntry("outcome", "SUGGESTED");
		assertThat(candidate(run, graceFirst, "demo-aisha")).containsEntry("excluded_by_code", "NOT_ON_LEAVE");
		assertThat(candidate(run, graceFirst, "demo-siewlan")).containsEntry("excluded_by_code", "NOT_ON_LEAVE");
		assertThat(candidate(run, graceFirst, "demo-ravi")).containsEntry("excluded_by_code", "CERTIFICATION_VALID");
		assertThat(candidate(run, graceFirst, "demo-joseph")).containsEntry("excluded_by_code", "NO_TIME_CLASH");

		// The spot checks concluded at Chua's visits reach the search through its soft rule.
		assertThat(check(run, graceFirst, "demo-huimin", "SPOT_CHECK")).isEqualTo("FAIL");
		assertThat(check(run, graceFirst, "demo-farah", "SPOT_CHECK")).isEqualTo("PASS");
		var conclusions = spotChecks.recentConclusions(LocalDateTime.now(clock).minusDays(90));
		assertThat(conclusions.get(caregiverOf("demo-huimin")).needsImprovement()).isEqualTo(1);
		assertThat(conclusions.get(caregiverOf("demo-farah")).metStandard()).isEqualTo(1);

		// What the three people see on their screens.
		assertThat(as("demo-fiona", "FAMILY", "/api/roster-changes")).contains("Tan Mei Ling");
		assertThat(as("demo-fiona", "FAMILY", "/api/spot-checks"))
				.contains("Routine quality check: Mei Ling has looked after Grace for five weeks")
				.contains("Check the shower-chair routine set up after Grace's fall in September");
		assertThat(as("demo-alice", "MANAGER", "/api/absences")).contains("Aisha Rahim").contains("Nora Ismail");

		// Re-rostered, so a second load lays out a fresh absence, after the first and not overlapping it.
		LocalDate firstEnds = jdbc.queryForObject("select end_date from absence_report where id = ?", LocalDate.class,
				absence);
		load(SEED);

		long fresh = aishasLatestAbsence();
		assertThat(fresh).isNotEqualTo(absence);
		assertThat(jdbc.queryForObject("select start_date from absence_report where id = ?", LocalDate.class, fresh))
				.isAfter(firstEnds);
		assertThat(rerostering.reroster(fresh, RosteringRun.Objective.EVEN_WORKLOAD, idOf("demo-alice")).searched())
				.isEqualTo(3);
	}

	private Map<String, Object> candidate(long run, long visit, String username) {
		return jdbc.queryForMap("select c.option_rank, c.outcome, c.excluded_by_code from rostering_candidate c "
				+ "join caregiver g on g.id = c.caregiver_id join app_user u on u.id = g.user_id "
				+ "where c.rostering_run_id = ? and c.visit_id = ? and u.username = ?", run, visit, username);
	}

	private String check(long run, long visit, String username, String code) {
		return jdbc.queryForObject("select x.result from rostering_candidate_check x "
				+ "join rostering_constraint k on k.id = x.rostering_constraint_id "
				+ "join rostering_candidate c on c.id = x.rostering_candidate_id "
				+ "join caregiver g on g.id = c.caregiver_id join app_user u on u.id = g.user_id "
				+ "where c.rostering_run_id = ? and c.visit_id = ? and u.username = ? and k.code = ?",
				String.class, run, visit, username, code);
	}

	private String as(String username, String role, String path) throws Exception {
		return mvc.perform(get(path).with(user(username).roles(role)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

	private long aishasLatestAbsence() {
		return jdbc.queryForObject("select a.id from absence_report a join caregiver g on g.id = a.caregiver_id "
				+ "join app_user u on u.id = g.user_id where u.username = 'demo-aisha' "
				+ "order by a.end_date desc, a.id desc limit 1", Long.class);
	}

	private long idOf(String username) {
		return jdbc.queryForObject("select id from app_user where username = ?", Long.class, username);
	}

	private long caregiverOf(String username) {
		return jdbc.queryForObject("select g.id from caregiver g join app_user u on u.id = g.user_id "
				+ "where u.username = ?", Long.class, username);
	}

	private Map<String, Long> counts() {
		return TABLES.stream().collect(Collectors.toMap(table -> table,
				table -> jdbc.queryForObject("select count(*) from " + table, Long.class)));
	}

	private void load(String script) {
		jdbc.execute((ConnectionCallback<Void>) connection -> {
			ScriptUtils.executeSqlScript(connection,
					new EncodedResource(new ClassPathResource(script), StandardCharsets.UTF_8));
			return null;
		});
	}
}
