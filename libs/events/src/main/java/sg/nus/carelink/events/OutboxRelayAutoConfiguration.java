package sg.nus.carelink.events;

import java.time.Clock;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** The relay that publishes the outbox to SNS, when {@code carelink.events.topic-arn} is set. */
@AutoConfiguration(after = EventsAutoConfiguration.class)
@ConditionalOnProperty(prefix = "carelink.events", name = "topic-arn")
@EnableConfigurationProperties(EventsProperties.class)
public class OutboxRelayAutoConfiguration {

	@Bean
	OutboxRelay outboxRelay(JdbcTemplate jdbc, PlatformTransactionManager transactionManager, JsonMapper json,
			EventsProperties properties, Environment environment) {
		return new OutboxRelay(jdbc, new TransactionTemplate(transactionManager), AwsClients.sns(properties), json,
				Clock.systemUTC(), EventsAutoConfiguration.serviceName(properties, environment), properties);
	}

}
