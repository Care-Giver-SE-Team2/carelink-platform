package sg.nus.carelink.platform.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderNotFoundException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * Security for a service other than core. Sign-in happens in core; a service only reads the shared
 * session that core wrote. The rules match core's, so the front end calls every service the same
 * way: the CSRF token travels in the {@code XSRF-TOKEN} cookie and the {@code X-XSRF-TOKEN} header,
 * and an anonymous call is answered with 401.
 *
 * <p>Applies only when the application has no {@link SecurityFilterChain} of its own, so core, which
 * signs people in, keeps its own configuration. It runs before Spring Boot's default web security,
 * which would otherwise define a chain first.
 */
@AutoConfiguration(before = {ManagementWebSecurityAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnMissingBean(SecurityFilterChain.class)
public class ServiceSecurityAutoConfiguration {

	@Bean
	SecurityFilterChain serviceSecurityFilterChain(HttpSecurity http) throws Exception {
		CsrfTokenRequestAttributeHandler csrfTokenRequestHandler = new CsrfTokenRequestAttributeHandler();
		csrfTokenRequestHandler.setCsrfRequestAttributeName(null);
		http
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
						// Calls between services. The Ingress does not route /internal, so only the
						// services inside the cluster can reach these endpoints.
						.requestMatchers("/internal/**").permitAll()
						.anyRequest().authenticated())
				.csrf(csrf -> csrf
						.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
						.csrfTokenRequestHandler(csrfTokenRequestHandler)
						.ignoringRequestMatchers("/internal/**"))
				// A service never starts a session; only core does, at sign-in. Without a request
				// cache, a rejected anonymous call does not create one either.
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.NEVER))
				.requestCache(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable)
				.exceptionHandling(ex -> ex.authenticationEntryPoint(
						(request, response, authException) -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED)));
		return http.build();
	}

	/** A service checks no passwords. Without this, Spring Boot would create an in-memory user. */
	@Bean
	@ConditionalOnMissingBean
	AuthenticationManager signInHappensInCore() {
		return authentication -> {
			throw new ProviderNotFoundException("Sign-in happens in core");
		};
	}

	@Bean
	@ConditionalOnMissingBean
	SignedInUsers signedInUsers() {
		return new SignedInUsers();
	}

}
