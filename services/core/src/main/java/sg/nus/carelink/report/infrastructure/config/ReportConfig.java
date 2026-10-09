package sg.nus.carelink.report.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * The report module's only Spring configuration: its settings, and the scheduler its weekly
 * run needs.
 *
 * <p>{@code @EnableScheduling} is also declared by the incident module's
 * {@code EscalationConfig}. Declaring it again costs nothing - Spring imports the scheduling
 * configuration once - and means the weekly reports do not stop the day the incident module's
 * configuration changes. The {@code Clock} the service is dated by does still come from
 * {@code EscalationConfig}, which supplies it only when nothing else does; one clock for the
 * whole application is the point of it.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(ReportProperties.class)
public class ReportConfig {
}
