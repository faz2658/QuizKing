package com.fazeel.quizcadia.auth;

import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import com.fazeel.quizcadia.diagnostics.Diagnostics;

/** The common shape of every service's security: stateless bearer tokens plus the service's own rules. */
public final class ApiSecurity {

	public static final String ADMIN = "ADMIN";

	private ApiSecurity() {
	}

	/**
	 * The service's security filter chain: no CSRF or sessions, JWT bearer tokens, diagnostics only from inside
	 * the container, Actuator open (nginx keeps it off the public site), then the service's own rules.
	 */
	public static SecurityFilterChain build(HttpSecurity http,
			Customizer<AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry> rules)
			throws Exception {
		http.csrf(csrf -> csrf.disable()) // bearer tokens, no cookies
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> {
					// Recordings and dumps: only from inside the service's own container (they can hold secrets)
					auth.requestMatchers(Diagnostics.PATHS).access(Diagnostics.fromInsideTheContainer());
					// The rest of Actuator is only reachable inside the Docker network (nginx serves /api/** and the UI)
					auth.requestMatchers("/actuator/**", "/error").permitAll();
					rules.customize(auth);
				})
				.oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()));
		return http.build();
	}

	/** Allows the request when the {userId} path variable is the caller's own id, or the caller is an admin. */
	public static AuthorizationManager<RequestAuthorizationContext> selfOrAdmin() {
		return (authentication, context) -> {
			Authentication auth = authentication.get();
			String userId = context.getVariables().get("userId");
			boolean allowed = auth != null && auth.isAuthenticated()
					&& (Callers.isAdmin(auth) || (userId != null && userId.equals(auth.getName())));
			return new AuthorizationDecision(allowed);
		};
	}
}
