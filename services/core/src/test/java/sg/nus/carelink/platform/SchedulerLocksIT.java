package sg.nus.carelink.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import sg.nus.carelink.testsupport.SharedMySql;

/**
 * The scheduler locks work end to end: the platform's own migration creates the lock table, a lock
 * taken by one replica keeps the others out, and every scheduled job in core really goes through
 * its lock when it runs.
 */
@SpringBootTest(properties = {
		"carelink.report.schedule-cron=-",
		"carelink.escalation.scan-initial-delay=PT1H"})
class SchedulerLocksIT {

	/** Each locked job in core: its class, the method that runs, and the lock it must take. */
	private static final Map<String, String[]> JOBS = Map.of(
			"incident.escalation-sweep",
			new String[] {"sg.nus.carelink.incident.infrastructure.schedule.EscalationScheduler", "sweep"},
			"incident.spot-check-reminders",
			new String[] {"sg.nus.carelink.incident.infrastructure.schedule.SpotCheckReminderScheduler", "remindFamilies"},
			"profile.credential-expiry-scan",
			new String[] {"sg.nus.carelink.profile.infrastructure.schedule.CredentialExpiryScheduler", "scanDaily"},
			"rostering.change-sweep",
			new String[] {"sg.nus.carelink.rostering.infrastructure.schedule.RosterChangeScheduler", "sweep"},
			"rostering.uncovered-visits",
			new String[] {"sg.nus.carelink.rostering.infrastructure.schedule.RosterScheduler", "flagUncoveredVisits"},
			"rostering.leave-reminders",
			new String[] {"sg.nus.carelink.rostering.infrastructure.schedule.RosterScheduler", "remindOfLeaveVisits"},
			"rostering.nightly-refresh",
			new String[] {"sg.nus.carelink.rostering.infrastructure.schedule.RosterScheduler", "refreshNightly"});

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private LockProvider locks;

	@Autowired
	private ApplicationContext context;

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		SharedMySql.register(registry, SchedulerLocksIT.class, "+08:00", "connectionTimeZone=Asia/Singapore");
	}

	@Test
	void theLockTableComesFromThePlatformsOwnMigrationHistory() {
		assertThat(jdbc.queryForObject(
				"SELECT COUNT(*) FROM " + PlatformMigrations.HISTORY_TABLE + " WHERE version = '1' AND success = 1",
				Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject(
				"SELECT COUNT(*) FROM flyway_schema_history WHERE script LIKE '%shedlock%'", Integer.class)).isZero();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM shedlock", Integer.class)).isNotNull();
	}

	@Test
	void aLockHeldByOneReplicaKeepsTheOthersOut() {
		LockConfiguration lock = new LockConfiguration(Instant.now(), "test.exclusive", Duration.ofMinutes(1), Duration.ZERO);

		Optional<SimpleLock> first = locks.lock(lock);
		assertThat(first).isPresent();
		assertThat(locks.lock(lock)).as("a second replica asking for the same lock").isEmpty();

		first.get().unlock();
		assertThat(locks.lock(lock)).as("free again once the first replica is done").isPresent();
	}

	@Test
	void everyScheduledJobInCoreTakesItsLockWhenItRuns() throws Exception {
		for (Map.Entry<String, String[]> job : JOBS.entrySet()) {
			Class<?> type = Class.forName(job.getValue()[0]);
			Object bean = context.getBean(type);
			Method run = type.getDeclaredMethod(job.getValue()[1]);
			run.setAccessible(true);
			try {
				run.invoke(bean);
			}
			catch (InvocationTargetException failedRun) {
				// What the job does with an empty database is not under test here, only that it took its lock
			}
			assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM shedlock WHERE name = ?", Integer.class, job.getKey()))
					.as("lock row for %s", job.getKey())
					.isEqualTo(1);
		}
	}

}
