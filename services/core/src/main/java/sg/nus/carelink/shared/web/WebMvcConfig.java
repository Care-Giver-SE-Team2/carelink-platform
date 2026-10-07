package sg.nus.carelink.shared.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the cross-cutting interceptors. Kept separate from SecurityConfig
 * on purpose: that class decides who may get in, this one decides what happens
 * around every request that did.
 */
@Configuration
class WebMvcConfig implements WebMvcConfigurer {

	/** Every API path. The front-end static files are not interesting to log or guard. */
	static final String API_PATHS = "/api/**";
	/** Logging in cannot itself require a login. Mirrors the permitAll in SecurityConfig. */
	static final String LOGIN_PATH = "/api/auth/login";
	/** CSRF token initialization is accessible before login. */
	static final String CSRF_PATH = "/api/auth/csrf";

	private final RequestContextInterceptor requestContext;

	WebMvcConfig(RequestContextInterceptor requestContext) {
		this.requestContext = requestContext;
	}

	/**
	 * Register API request context handling, excluding login and CSRF initialization.
	 *
	 * @param registry Spring MVC interceptor registry
	 *
	 * @author Wang Zhili
	 */
	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(requestContext)
				.addPathPatterns(API_PATHS)
				.excludePathPatterns(LOGIN_PATH, CSRF_PATH);
	}
}
