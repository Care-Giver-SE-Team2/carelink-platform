package sg.nus.carelink;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import sg.nus.carelink.testsupport.SharedMySql;

/**
 * Checks that the demonstration seed still loads, that loading it twice is harmless, and
 * that it does not care what is already in the database.
 *
 * <p>The last of those is why this test exists in its present form. An earlier seed wrote
 * its primary keys by hand, which works on an empty schema and nowhere else; staging had
 * been in use for a fortnight and already held id 1, so the load failed on its first
 * statement. It was a Flyway migration at the time, so the failure was recorded and the
 * application then refused to start on every boot afterwards. A test that only ever saw an
 * empty database could not have caught any of it, and the earlier version of this test did
 * not.
 *
 * <p>So the seed is no longer a migration and this no longer loads it like one. It runs the
 * file the way {@code deploy/staging/load-demo-data.sh} runs it - as a script, on one
 * connection, against a database that is already in use - because a test of a deployment
 * step is worth only as much as its resemblance to the step.
 *
 * <p>The assertions are deliberately structural. They say "at least two managers", not
 * "exactly three", because the seed is demonstration material that anyone may extend; a
 * test that counted rows would turn every addition into a build failure. What is asserted
 * is what the demonstrations actually depend on, and nothing beyond it.
 *
 * <p>Named *IT: runs under the integration-tests job of the pipeline; needs Docker.
 */
@SpringBootTest
class DemoSeedIT {

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		SharedMySql.register(registry, DemoSeedIT.class, null);
	}

	private static final Resource SEED = new ClassPathResource("db/demo/demo-seed.sql");

	/** Rows that were here before the seed was, as they are on staging. */
	private static boolean databaseAlreadyInUse;

	@Autowired
	private JdbcTemplate jdbc;

	/**
	 * Puts somebody else's rows in first, once for the class.
	 *
	 * <p>They take the low ids, which is the entire point: on an empty database a seed that
	 * writes {@code id = 1} looks correct. These two rows are what makes this database
	 * resemble the one the seed actually has to load into.
	 */
	@BeforeEach
	void letSomebodyElseHaveTheLowIdsFirst() {
		if (!databaseAlreadyInUse) {
			jdbc.update("insert into app_user (username, password_hash, display_name) "
					+ "values ('someone-else', '{noop}unused-test-password', 'Someone Else')");
			jdbc.update("insert into elder (full_name) values ('Somebody Else''s Elder')");
			databaseAlreadyInUse = true;
		}
	}

	@Test
	void loadsIntoADatabaseThatIsAlreadyInUse() {
		loadSeed();

		List<String> usernames = jdbc.queryForList(
				"select username from app_user where username like 'demo-%'", String.class);

		assertThat(usernames).isNotEmpty();
	}

	/**
	 * Loading twice is not an unusual thing to do - it is what happens when the first
	 * attempt failed halfway, or when nobody is sure whether it was run. It has to be
	 * uneventful, or nobody will dare run it before a demonstration.
	 */
	@Test
	void loadingItAgainChangesNothing() {
		loadSeed();
		long usersBefore = count("app_user");
		long eldersBefore = count("elder");
		long bindingsBefore = count("elder_family_binding");
		long incidentsBefore = count("incident");
		long entriesBefore = count("incident_log");
		long visitsBefore = count("visit");
		long readingsBefore = count("vital_sign");
		long evidenceBefore = count("visit_evidence");
		long tasksBefore = count("visit_task");

		loadSeed();

		assertThat(count("app_user")).isEqualTo(usersBefore);
		assertThat(count("elder")).isEqualTo(eldersBefore);
		assertThat(count("elder_family_binding")).isEqualTo(bindingsBefore);
		assertThat(count("incident")).isEqualTo(incidentsBefore);
		assertThat(count("incident_log")).isEqualTo(entriesBefore);
		assertThat(count("visit")).isEqualTo(visitsBefore);
		assertThat(count("vital_sign")).isEqualTo(readingsBefore);
		assertThat(count("visit_evidence")).isEqualTo(evidenceBefore);
		assertThat(count("visit_task")).isEqualTo(tasksBefore);
	}

	/**
	 * UC-MG07's reports are made from visits and the readings taken on them. A seed with no
	 * verified visit carrying readings would leave every report's vital-signs section saying
	 * there were none, and the family's ranges could not be told from the regulator's readings.
	 */
	@Test
	void aVerifiedVisitCarriesVitalSignsForTheReportsToSummarise() {
		loadSeed();

		Long verifiedWithReadings = jdbc.queryForObject(
				"select count(distinct v.id) from visit v join vital_sign s on s.visit_id = v.id "
						+ "where v.status = 'VERIFIED'",
				Long.class);

		assertThat(verifiedWithReadings).isPositive();
	}

	/**
	 * UC-MG05's chain hands an unanswered incident to the next manager, so a demonstration
	 * needs somebody to hand it to. With one manager the chain has nowhere to go and the
	 * escalation can only ever be shown failing.
	 */
	@Test
	void thereAreEnoughManagersForAnEscalationToHaveSomewhereToGo() {
		loadSeed();

		List<Long> managers = jdbc.queryForList(
				"select u.id from app_user u join user_role r on r.user_id = u.id "
						+ "where u.enabled = true and r.role = 'MANAGER' and u.username like 'demo-%'",
				Long.class);

		assertThat(managers).hasSizeGreaterThanOrEqualTo(2);
	}

	/**
	 * The last step of the chain tells the family, and it finds them with this join. A
	 * binding that expired, or a family member without an account, would leave the final
	 * escalation notifying nobody - visible in a demonstration only as silence.
	 *
	 * <p>Written as the same shape of query the notifier uses, on purpose: asserting that
	 * the rows exist is weaker than asserting that the real lookup returns them.
	 */
	@Test
	void anElderIsReachableThroughFamilyTheWayTheNotifierLooksThemUp() {
		loadSeed();

		List<Long> recipients = jdbc.queryForList(
				"select f.user_id from elder_family_binding b "
						+ "join family_member f on f.id = b.family_member_id "
						+ "where b.status = 'ACTIVE' "
						+ "  and (b.expires_at is null or b.expires_at > now()) "
						+ "  and f.user_id is not null",
				Long.class);

		assertThat(recipients).isNotEmpty();
	}

	/**
	 * The continuity tier of the chain prefers a manager who has handled this elder before,
	 * which it reads from closed incidents. Without history the tier finds nothing and every
	 * demonstration falls through to the same generic fallback.
	 */
	@Test
	void aClosedIncidentGivesTheContinuityTierSomeHistoryToFind() {
		loadSeed();

		Long withResponder = jdbc.queryForObject(
				"select count(*) from incident where status = 'RESOLVED' and responder_user_id is not null",
				Long.class);

		assertThat(withResponder).isPositive();
	}

	/**
	 * A resolved incident whose timeline is empty would show the manager's screen working
	 * and say nothing about what the screen is for.
	 *
	 * <p>The ASSIGNED entry is checked for the responder id the escalation flow reads back
	 * out of it. The seed builds that from whatever id the database gave Ben, and getting it
	 * wrong would leave the timeline looking right and parsing to nothing.
	 */
	@Test
	void theClosedIncidentCarriesATimelineThatNamesItsResponder() {
		loadSeed();

		Long entries = jdbc.queryForObject(
				"select count(*) from incident_log l join incident i on i.id = l.incident_id "
						+ "where i.status = 'RESOLVED'",
				Long.class);
		assertThat(entries).isGreaterThanOrEqualTo(2L);

		Long named = jdbc.queryForObject(
				"select count(*) from incident_log l "
						+ "join incident i on i.id = l.incident_id "
						+ "join app_user u on u.username = 'demo-ben' "
						+ "where l.action = 'ASSIGNED' "
						+ "  and l.detail = concat('responder=', u.id, ' :: first responder')",
				Long.class);
		assertThat(named).isPositive();
	}

	/**
	 * These accounts share one password and the file says so in the open. What must not
	 * happen is the password being stored as itself: a seed is copied and adapted, and a
	 * plain text credential in it would outlive the proof of concept.
	 */
	@Test
	void everyDemonstrationAccountStoresAHashedPassword() {
		loadSeed();

		List<String> hashes = jdbc.queryForList(
				"select password_hash from app_user where username like 'demo-%'", String.class);

		assertThat(hashes).isNotEmpty().allSatisfy(hash ->
				assertThat(hash).startsWith("{bcrypt}$"));
	}

	/**
	 * One connection for the whole file, which the seed depends on: it carries its ids
	 * between statements in session variables rather than writing them down.
	 */
	private void loadSeed() {
		jdbc.execute((ConnectionCallback<Void>) connection -> {
			ScriptUtils.executeSqlScript(connection, new EncodedResource(SEED, StandardCharsets.UTF_8));
			return null;
		});
	}

	private long count(String table) {
		Long rows = jdbc.queryForObject("select count(*) from " + table, Long.class);
		return rows == null ? 0L : rows;
	}
}
