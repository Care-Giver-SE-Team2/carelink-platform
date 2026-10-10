package sg.nus.carelink.events;

import java.time.Clock;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** The consumer of this service's SQS queue, when {@code carelink.events.queue-url} is set. */
@AutoConfiguration(after = EventsAutoConfiguration.class)
@ConditionalOnProperty(prefix = "carelink.events", name = "queue-url")
@EnableConfigurationProperties(EventsProperties.class)
public class EventConsumerAutoConfiguration {

	@Bean
	QueueConsumer eventConsumer(JdbcTemplate jdbc, PlatformTransactionManager transactionManager, JsonMapper json,
			ObjectProvider<EventHandler<?>> handlers, EventsProperties properties, Environment environment) {
		return new QueueConsumer(AwsClients.sqs(properties), jdbc, new TransactionTemplate(transactionManager), json,
				Clock.systemUTC(), EventsAutoConfiguration.serviceName(properties, environment), properties,
				handlers.orderedStream().toList());
	}

}
