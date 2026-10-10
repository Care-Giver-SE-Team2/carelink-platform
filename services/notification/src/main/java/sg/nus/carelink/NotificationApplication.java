package sg.nus.carelink;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * The notification service: the in-app inbox. In {@code sg.nus.carelink}, like core's application
 * class, so component scanning finds the module and the shared classes from {@code libs/shared}.
 * Sign-in stays in core; the security chain comes from {@code libs/platform-security}. Method
 * security is on for the {@code @PreAuthorize} rules the controller carries.
 */
@SpringBootApplication
@EnableMethodSecurity
public class NotificationApplication {

	public static void main(String[] args) {
		SpringApplication.run(NotificationApplication.class, args);
	}

}
