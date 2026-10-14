package sg.nus.carelink.openplatform.infrastructure.config;

import javax.sql.DataSource;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * notification runs on more than one replica, and every replica runs the same {@code @Scheduled}
 * methods. A row in notification's {@code shedlock} table lets one replica at a time run each job;
 * the others skip that run. The webhook attempts are notification's first scheduled job, so this
 * lives with them; it serves any job of the service's.
 *
 * <p>{@code lockAtMostFor} frees a lock if a replica dies in the middle of a run, and
 * {@code lockAtLeastFor} keeps the other replicas from running the same job again straight after.
 * The lock times come from the database clock ({@code usingDbTime}), so clock drift between
 * replicas does not matter.
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
