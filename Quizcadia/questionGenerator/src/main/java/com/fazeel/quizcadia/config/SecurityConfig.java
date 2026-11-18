package com.fazeel.quizcadia.config;

import static com.fazeel.quizcadia.auth.ApiSecurity.ADMIN;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

import com.fazeel.quizcadia.auth.ApiSecurity;
import com.fazeel.quizcadia.auth.JwtSupport;

/** The question bank holds every answer, so it is for admins only; the welcome page may show topic counts and
 * every player may rate or flag a question, and make and play AI quizzes on any topic. */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@Import(JwtSupport.class)
public class SecurityConfig {

	@Bean
	SecurityFilterChain security(HttpSecurity http) throws Exception {
		return ApiSecurity.build(http, auth -> auth
				.requestMatchers(HttpMethod.GET, "/api/v1/question-generator/stats").permitAll()
				// the game engines load a topic's questions here; nginx keeps /internal/ off the public site
				.requestMatchers(HttpMethod.GET, "/internal/topics/**", "/internal/questions/**").permitAll()
				.requestMatchers(HttpMethod.POST, "/api/v1/question-generator/feedback").authenticated()
				.requestMatchers("/api/v1/question-generator/topic-quizzes", "/api/v1/question-generator/topic-quizzes/**").authenticated()
				.anyRequest().hasRole(ADMIN));
	}
}
