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

/** A player's reports are theirs alone (admins may read any). */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@Import(JwtSupport.class)
public class SecurityConfig {

	@Bean
	SecurityFilterChain security(HttpSecurity http) throws Exception {
		return ApiSecurity.build(http, auth -> auth
				.requestMatchers("/api/v1/reports/users/{userId}", "/api/v1/reports/users/{userId}/**").access(selfOrAdmin())
				.anyRequest().hasRole(ADMIN));
	}
}
