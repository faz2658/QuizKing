package com.fazeel.quizcadia.security;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

	private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

	@Bean
	SecurityFilterChain security(HttpSecurity http) throws Exception {
		http.csrf(csrf -> csrf.disable()) // bearer tokens; the one cookie (refresh) is SameSite=Strict, HttpOnly
				.cors(Customizer.withDefaults())
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/guest",
								"/api/v1/auth/google", "/api/v1/auth/google/redirect", "/api/v1/auth/password/forgot", "/api/v1/auth/password/reset",
								"/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/v1/auth/providers").permitAll()
						// recordings and dumps: only from inside this container (they can hold secrets)
						.requestMatchers(com.fazeel.quizcadia.diagnostics.Diagnostics.PATHS)
						.access(com.fazeel.quizcadia.diagnostics.Diagnostics.fromInsideTheContainer())
						.requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus", "/error").permitAll()
						.anyRequest().authenticated())
				.oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()));
		return http.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	SecretKey jwtKey(@Value("${quizcadia.jwt.secret:}") String secret) {
		byte[] bytes;
		if (secret == null || secret.isBlank()) {
			bytes = new byte[32];
			new SecureRandom().nextBytes(bytes);
			log.warn("QUIZCADIA_JWT_SECRET is not set: using a random key, tokens will not survive a restart");
		} else {
			bytes = decode(secret);
			if (bytes.length < 32) {
				throw new IllegalStateException("QUIZCADIA_JWT_SECRET must be at least 32 bytes");
			}
		}
		return new SecretKeySpec(bytes, "HmacSHA256");
	}

	private static byte[] decode(String secret) {
		try {
			return Base64.getDecoder().decode(secret);
		}
		catch (IllegalArgumentException notBase64) {
			return secret.getBytes(StandardCharsets.UTF_8);
		}
	}

	@Bean
	JwtEncoder jwtEncoder(SecretKey key) {
		return NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build();
	}

	@Bean
	JwtDecoder jwtDecoder(SecretKey key) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(),
				new JwtClaimValidator<Object>(JwtClaimNames.ISS, iss -> iss != null && TokenService.ISSUER.equals(iss.toString()))));
		return decoder;
	}

	@Bean
	TokenService tokenService(JwtEncoder encoder, @Value("${quizcadia.jwt.lifetime:15m}") Duration lifetime,
			Clock clock) {
		return new TokenService(encoder, lifetime, clock);
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(@Value("${quizcadia.cors.allowed-origins}") List<String> origins) {
		CorsConfiguration cors = new CorsConfiguration();
		cors.setAllowedOrigins(origins);
		cors.setAllowedMethods(List.of("GET", "POST"));
		cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", cors);
		// Google's redirect sign-in is a full-page form post (a navigation: no script ever reads the answer), so CORS
		// guards nothing there. Safari sends it with "Origin: null" (Google's page hides where it came from), which the
		// filter would refuse as "Invalid CORS request". Its real protection is Google's signed ID token plus the
		// g_csrf_token double-submit check (AuthController#googleRedirect), so the path is left out of CORS entirely.
		return request -> GOOGLE_REDIRECT.equals(request.getRequestURI()) ? null : source.getCorsConfiguration(request);
	}

	static final String GOOGLE_REDIRECT = "/api/v1/auth/google/redirect";
}
