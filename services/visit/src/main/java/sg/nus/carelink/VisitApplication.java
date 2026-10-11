package sg.nus.carelink;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * The visit service: the visits, their execution by caregivers, the elder's confirmation and the
 * family's view of them. In {@code sg.nus.carelink}, like core's application class, so component
 * scanning finds the module and the shared classes from {@code libs/shared}, and the settings
 * classes are found as in core. Sign-in stays in core; the security chain comes from
 * {@code libs/platform-security}. Method security is on for the {@code @PreAuthorize} rules the
 * controllers carry.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableMethodSecurity
public class VisitApplication {

	public static void main(String[] args) {
		SpringApplication.run(VisitApplication.class, args);
	}

}
