package sg.nus.carelink.testsupport;

import java.time.Clock;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * What every visit test that starts the application has in place of core: the core stand-in instead
 * of the core-api client, which only steps in when no CoreApi is defined; core's sign-in
 * ({@link SignInAsCore}), in front of the service's security chain; and the platform's tables after
 * core's migrations, as in a real run. Component scanning finds this class; slice tests leave it
 * out, as they leave out every configuration class of the application's own.
 */
@Configuration(proxyBeanMethods = false)
@Import(PlatformTablesForTests.class)
class CoreStandInConfiguration {

	@Bean
	CoreStandIn coreStandIn(JdbcTemplate jdbc, Clock clock) {
		return new CoreStandIn(jdbc, clock);
	}

	@Bean
	FilterRegistrationBean<SignInAsCore> signInAsCore(JdbcTemplate jdbc) {
		FilterRegistrationBean<SignInAsCore> registration = new FilterRegistrationBean<>(new SignInAsCore(jdbc));
		registration.addUrlPatterns("/api/auth/*");
		registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
		return registration;
	}

}
