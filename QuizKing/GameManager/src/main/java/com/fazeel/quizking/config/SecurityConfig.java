package com.fazeel.quizking.config;

import static com.fazeel.quizking.auth.ApiSecurity.ADMIN;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

import com.fazeel.quizking.auth.ApiSecurity;
import com.fazeel.quizking.auth.JwtSupport;

/** Anyone may browse games (the engines and search read them without a token); only admins change them. */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@Import(JwtSupport.class)
public class SecurityConfig {

	@Bean
	SecurityFilterChain security(HttpSecurity http) throws Exception {
		return ApiSecurity.build(http, auth -> auth
				.requestMatchers(HttpMethod.GET, "/api/game/**").permitAll()
				.anyRequest().hasRole(ADMIN));
	}
}
