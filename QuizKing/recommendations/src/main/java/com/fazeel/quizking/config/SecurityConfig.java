package com.fazeel.quizking.config;

import static com.fazeel.quizking.auth.ApiSecurity.selfOrAdmin;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

import com.fazeel.quizking.auth.ApiSecurity;
import com.fazeel.quizking.auth.JwtSupport;

/** A player's recommendations and favourites are theirs alone; trending is for any player. */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@Import(JwtSupport.class)
public class SecurityConfig {

	@Bean
	SecurityFilterChain security(HttpSecurity http) throws Exception {
		return ApiSecurity.build(http, auth -> auth
				.requestMatchers("/api/v1/recommendation/users/{userId}", "/api/v1/recommendation/users/{userId}/**").access(selfOrAdmin())
				.anyRequest().authenticated());
	}
}
