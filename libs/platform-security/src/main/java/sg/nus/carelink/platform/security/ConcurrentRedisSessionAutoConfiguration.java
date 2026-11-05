package sg.nus.carelink.platform.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.session.autoconfigure.SessionAutoConfiguration;
import org.springframework.boot.session.data.redis.autoconfigure.SessionDataRedisAutoConfiguration;
import org.springframework.boot.session.data.redis.autoconfigure.SessionDataRedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.session.FlushMode;
import org.springframework.session.SessionIdGenerator;
import org.springframework.session.SessionRepository;
import org.springframework.session.UuidSessionIdGenerator;
import org.springframework.session.data.redis.RedisSessionRepository;

/**
 * Keep Boot's Redis serializers, namespace, timeout, ID generator, cookie resolver and filter.
 * Only the repository injected into that filter changes. Excluding Redis session auto-configuration
 * in service tests still disables this configuration as well. Every service already uses this library.
 */
@AutoConfiguration(after = SessionDataRedisAutoConfiguration.class, before = SessionAutoConfiguration.class)
@ConditionalOnClass({RedisSessionRepository.class, SessionDataRedisProperties.class})
@ConditionalOnBean(RedisSessionRepository.class)
public class ConcurrentRedisSessionAutoConfiguration {

	@Bean
	@Primary
	ConcurrentRedisSessionRepository concurrentRedisSessionRepository(RedisSessionRepository defaults,
			SessionDataRedisProperties properties, ObjectProvider<SessionIdGenerator> generators) {
		// The default repository supplies configured new-session metadata only. It must never
		// perform an immediate, non-atomic write while creating that seed snapshot.
		defaults.setFlushMode(FlushMode.ON_SAVE);
		return new ConcurrentRedisSessionRepository(defaults.getSessionRedisOperations(), ((SessionRepository<?>) defaults)::createSession,
				properties.getNamespace(), properties.getFlushMode(), properties.getSaveMode(),
				generators.getIfAvailable(UuidSessionIdGenerator::getInstance));
	}
}
