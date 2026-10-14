package sg.nus.carelink.platform.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.savedrequest.NullRequestCache;

/**
 * The Open Platform's paths, {@code /api/open/**}, for partners rather than the CareLink app
 * (docs/section5/decisions.md, D5 and D15). A request there carries a bearer token that notification
 * issued, not core's session: a portal user's on {@code /api/open/v1/portal/**}, a provider server's on
 * {@code /api/open/v1/jobs/**}, with the scope the operation needs. They are under {@code /api} because
 * that is what CloudFront sends to the services. Which service serves which path is the ingress's
 * routing; the rules are the same for each.
 *
 * <p>Open to anyone: applying, and getting a token and the keys to check it with (signing in to the
 * portal, the client-credentials grant, the key set). shared's request interceptor leaves the same four
 * alone.
 *
 * <p>A chain of its own, before the service's session chain, and only for {@code /api/open/**}: the rest
 * of the service keeps the session, the CSRF cookie and every rule it had. Nothing here is a session,
 * so nothing here needs CSRF protection. Turned on by {@code carelink.open-platform.jwk-set-uri}; a
 * service that does not set it has no such chain, and {@code /api/open/**} is refused by its session
 * chain like any other path.
 *
 * <p>Imported by {@link ServiceSecurityAutoConfiguration}, under its conditions, and deliberately not a
 * {@code @Configuration}: the services' component scan covers this package, and would otherwise register
 * this chain first, so that the auto-configuration found a chain and backed off.
 */
@ConditionalOnProperty(prefix = "carelink.open-platform", name = "jwk-set-uri")
@EnableConfigurationProperties(OpenPlatformProperties.class)
class OpenPlatformSecurityConfiguration {

	@Bean
	@Order(Ordered.HIGHEST_PRECEDENCE)
	SecurityFilterChain openPlatformSecurityFilterChain(HttpSecurity http, JwtDecoder openPlatformJwtDecoder)
			throws Exception {
		http
				.securityMatcher("/api/open/**")
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.POST, "/api/open/v1/applications", "/api/open/v1/portal/sessions",
								"/api/open/v1/oauth2/token").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/open/v1/.well-known/jwks.json").permitAll()
						.requestMatchers("/api/open/v1/portal/**").hasAuthority(OpenPlatformTokens.PORTAL_USER)
						.requestMatchers(HttpMethod.GET, "/api/open/v1/jobs", "/api/open/v1/jobs/**")
						.hasAuthority("SCOPE_" + OpenPlatformTokens.JOBS_READ)
						.requestMatchers("/api/open/v1/jobs/**").hasAuthority("SCOPE_" + OpenPlatformTokens.JOBS_WRITE)
						.anyRequest().denyAll())
				.oauth2ResourceServer(oauth -> oauth
						.jwt(jwt -> jwt.decoder(openPlatformJwtDecoder)
								.jwtAuthenticationConverter(OpenPlatformTokens::authentication))
						.authenticationEntryPoint(OpenPlatformProblems.invalidToken())
						.accessDeniedHandler(OpenPlatformProblems.insufficientScope()))
				.exceptionHandling(ex -> ex
						.authenticationEntryPoint(OpenPlatformProblems.invalidToken())
						.accessDeniedHandler(OpenPlatformProblems.insufficientScope()))
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.requestCache(cache -> cache.requestCache(new NullRequestCache()))
				.csrf(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable);
		return http.build();
	}

	/**
	 * Checks a token's signature against notification's published keys, its expiry, its issuer when one is set,
	 * and that it is an access token. A test may define its own decoder instead.
	 */
	@Bean
	@ConditionalOnMissingBean(JwtDecoder.class)
	JwtDecoder openPlatformJwtDecoder(OpenPlatformProperties properties) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri()).build();
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
				properties.issuer() == null || properties.issuer().isBlank()
						? JwtValidators.createDefault()
						: JwtValidators.createDefaultWithIssuer(properties.issuer()),
				OpenPlatformTokens.accessTokensOnly()));
		return decoder;
	}
}
