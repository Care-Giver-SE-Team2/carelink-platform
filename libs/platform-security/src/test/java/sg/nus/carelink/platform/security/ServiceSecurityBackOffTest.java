package sg.nus.carelink.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * core signs people in and keeps its own security configuration; the library must stay out of its
 * way. Every other service gets the library's chain.
 */
class ServiceSecurityBackOffTest {

	private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(
					ServiceSecurityAutoConfiguration.class,
					SecurityAutoConfiguration.class,
					ServletWebSecurityAutoConfiguration.class));

	@Test
	void aServiceWithoutItsOwnSecurityGetsTheLibrarysChain() {
		runner.run(context -> {
			assertThat(context).hasSingleBean(SecurityFilterChain.class);
			assertThat(context).hasBean("serviceSecurityFilterChain");
			assertThat(context).hasSingleBean(SignedInUsers.class);
		});
	}

	@Test
	void anApplicationWithItsOwnChainKeepsIt() {
		runner.withUserConfiguration(OwnSecurity.class).run(context -> {
			assertThat(context).hasSingleBean(SecurityFilterChain.class);
			assertThat(context).hasBean("ownChain");
			assertThat(context).doesNotHaveBean(SignedInUsers.class);
		});
	}

	@Test
	void anApplicationWithItsOwnChainGetsNoOpenPlatformChainEither() {
		runner.withUserConfiguration(OwnSecurity.class)
				.withPropertyValues("carelink.open-platform.jwk-set-uri=http://keys.invalid/jwks.json")
				.run(context -> {
					assertThat(context).hasSingleBean(SecurityFilterChain.class);
					assertThat(context).doesNotHaveBean("openPlatformSecurityFilterChain");
				});
	}

	@Test
	void aServiceThatScansThisPackageStillGetsBothChains() {
		runner.withUserConfiguration(ScansTheLibrary.class)
				.withPropertyValues("carelink.open-platform.jwk-set-uri=http://keys.invalid/jwks.json")
				.run(context -> {
					assertThat(context.getBeanNamesForType(SecurityFilterChain.class))
							.containsExactlyInAnyOrder("openPlatformSecurityFilterChain", "serviceSecurityFilterChain");
					assertThat(context).hasSingleBean(SignedInUsers.class);
				});
	}

	@Test
	void aServiceThatServesNoOpenPathGetsNoOpenPlatformChain() {
		runner.run(context -> {
			assertThat(context).doesNotHaveBean("openPlatformSecurityFilterChain");
			assertThat(context.getBean(OpenPlatformCallers.class).current()).isEmpty();
		});
	}

	/** As every service is: its application class in sg.nus.carelink, scanning this package too (not its tests). */
	@Configuration(proxyBeanMethods = false)
	@ComponentScan(basePackageClasses = ServiceSecurityAutoConfiguration.class,
			excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*Test(\\$.*)?"))
	static class ScansTheLibrary {
	}

	@Configuration(proxyBeanMethods = false)
	static class OwnSecurity {

		@Bean
		SecurityFilterChain ownChain(HttpSecurity http) throws Exception {
			return http.build();
		}

	}

}
