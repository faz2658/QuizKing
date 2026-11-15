package com.fazeel.maverick.config;

import static com.fazeel.maverick.auth.ApiSecurity.ADMIN;
import static com.fazeel.maverick.auth.ApiSecurity.selfOrAdmin;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

import com.fazeel.maverick.auth.ApiSecurity;
import com.fazeel.maverick.auth.JwtSupport;

/** Players can only join, check or leave a lobby as themselves; private rooms take the player from the token. */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@Import(JwtSupport.class)
public class SecurityConfig {

	@Bean
	SecurityFilterChain security(HttpSecurity http) throws Exception {
		return ApiSecurity.build(http, auth -> auth
				.requestMatchers("/api/v1/matching/rooms", "/api/v1/matching/rooms/**").authenticated()
				.requestMatchers("/api/v1/matching/{gameId}/players/{userId}").access(selfOrAdmin())
				.anyRequest().hasRole(ADMIN));
	}
}
