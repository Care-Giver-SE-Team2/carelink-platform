package sg.nus.carelink.testsupport;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The visit stand-in in every core test that starts the application. Component scanning finds this
 * class, so the stand-in is defined before the visit-api client's auto-configuration runs, and the
 * client, which only steps in when no VisitApi is defined, never is. Slice tests leave it out, as
 * they leave out every configuration class of the application's own.
 */
@Configuration(proxyBeanMethods = false)
class VisitStandInConfiguration {

	@Bean
	VisitStandIn visitStandIn() {
		return new VisitStandIn();
	}

}
