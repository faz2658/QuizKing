package com.fazeel.quizking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

import com.fazeel.quizking.auth.ApiSecurity;
import com.fazeel.quizking.auth.JwtSupport;

/** Searching games needs a player token. */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@Import(JwtSupport.class)
public class SecurityConfig {

	@Bean
	SecurityFilterChain security(HttpSecurity http) throws Exception {
		return ApiSecurity.build(http, auth -> auth
				.anyRequest().authenticated());
	}
}
