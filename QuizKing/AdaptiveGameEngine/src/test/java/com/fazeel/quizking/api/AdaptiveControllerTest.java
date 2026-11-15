package com.fazeel.quizking.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import com.fazeel.quizking.config.SecurityConfig;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fazeel.quizking.adaptive.AdaptiveRules;
import com.fazeel.quizking.adaptive.AdaptiveService;
import com.fazeel.quizking.adaptive.AdaptiveSession;
import com.fazeel.quizking.adaptive.PooledQuestion;

@Import(SecurityConfig.class)
@WebMvcTest(AdaptiveController.class)
class AdaptiveControllerTest {

	/** A signed-in player whose token subject is {@code userId}. */
	static RequestPostProcessor player(int userId) {
		return jwt().jwt(j -> j.subject(String.valueOf(userId))).authorities(new SimpleGrantedAuthority("ROLE_USER"));
	}

	static RequestPostProcessor admin() {
		return jwt().jwt(j -> j.subject("1")).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
	}

	static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

	@TestConfiguration
	static class Config {
		@Bean
		@Primary
		Clock fixedClock() {
			return Clock.fixed(NOW, ZoneOffset.UTC);
		}
	}

	@Autowired
	MockMvc mvc;

	@MockitoBean
	AdaptiveService service;

	@Test
	void startReturnsFirstQuestionWithoutAnswer() throws Exception {
		AdaptiveSession s = new AdaptiveSession("s1", 9, 5, "Adaptive", 1, 7,
				List.of(new PooledQuestion(1, 1, "2 + 2?", List.of("4", "5"), "4")), AdaptiveRules.DEFAULT, NOW);
		when(service.start(5, 9)).thenReturn(s);

		mvc.perform(post("/api/v1/adaptive/sessions").with(player(9)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"gameId\":5,\"userId\":9}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.currentQuestion.stem").value("2 + 2?"))
				.andExpect(jsonPath("$.currentQuestion.points").value(10))
				.andExpect(content().string(not(containsString("correctAnswer"))));
	}

	@Test
	void nobodyCanPlayAsSomeoneElse() throws Exception {
		AdaptiveSession s = new AdaptiveSession("s1", 9, 5, "Adaptive", 1, 7,
				List.of(new PooledQuestion(1, 1, "2 + 2?", List.of("4", "5"), "4")), AdaptiveRules.DEFAULT, NOW);
		when(service.get("s1")).thenReturn(s); // player 9's session

		mvc.perform(post("/api/v1/adaptive/sessions").with(player(8)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"gameId\":5,\"userId\":9}")).andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/adaptive/sessions/s1").with(player(8))).andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/adaptive/sessions/s1")).andExpect(status().isUnauthorized());
	}
}
