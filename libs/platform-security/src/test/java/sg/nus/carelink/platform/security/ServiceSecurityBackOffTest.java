package sg.nus.carelink.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

	@Configuration(proxyBeanMethods = false)
	static class OwnSecurity {

		@Bean
		SecurityFilterChain ownChain(HttpSecurity http) throws Exception {
			return http.build();
		}

	}

}
