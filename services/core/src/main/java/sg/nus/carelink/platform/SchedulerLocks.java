package sg.nus.carelink.platform;

import javax.sql.DataSource;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * core runs on more than one replica, and every replica runs the same {@code @Scheduled} methods.
 * A row in the {@code shedlock} table lets one replica at a time run each job; the others skip
 * that run.
 *
 * <p>Each job names its lock with {@code @SchedulerLock}. {@code lockAtMostFor} frees the lock if a
 * replica dies in the middle of a run. {@code lockAtLeastFor} sits just under the job's default
 * interval, so that across all replicas the job still runs about once per interval, not once per
 * replica. The lock times come from the database clock ({@code usingDbTime}), so clock drift
 * between replicas does not matter.
 */
@Configuration(proxyBeanMethods = false)
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
class SchedulerLocks {

	@Bean
	LockProvider lockProvider(DataSource dataSource) {
		return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
				.withJdbcTemplate(new JdbcTemplate(dataSource))
				.usingDbTime()
				.build());
	}

}
