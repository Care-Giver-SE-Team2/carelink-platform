package sg.nus.carelink.visit.infrastructure.config;

import javax.sql.DataSource;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * visit's scheduled jobs, and the lock each one takes. In core, another module switched scheduling
 * on; here this class does.
 *
 * <p>visit runs on more than one replica, and every replica runs the same {@code @Scheduled}
 * methods. A row in the {@code shedlock} table lets one replica at a time run each job; the others
 * skip that run. Until the schema split this is the table core created; the {@code visit.} in
 * each lock's name keeps visit's jobs apart from the others'. The lock times come from the
 * database clock ({@code usingDbTime}), so clock drift between replicas does not matter.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
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
