package sg.nus.carelink.platform;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.savedrequest.NullRequestCache;

/**
 * Security for core's internal API ({@code /internal/v1}), which the other services call without a
 * signed-in user: a call carries no session and no CSRF token. The Ingress routes only the public
 * paths ({@code /api/**}), never {@code /internal}, so only callers inside the cluster reach it
 * (docs/platform/service-boundaries.md, section 3).
 *
 * <p>A chain of its own, ordered before core's main chain, so the main security configuration
 * stays as it is.
 */
@Configuration(proxyBeanMethods = false)
class InternalApiSecurity {

	static final String INTERNAL_API = "/internal/**";

	@Bean
	@Order(1)
	SecurityFilterChain internalApiSecurityFilterChain(HttpSecurity http) throws Exception {
		http
				.securityMatcher(INTERNAL_API)
				.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
				.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.requestCache(cache -> cache.requestCache(new NullRequestCache()))
				.formLogin(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable);
		return http.build();
	}

}
