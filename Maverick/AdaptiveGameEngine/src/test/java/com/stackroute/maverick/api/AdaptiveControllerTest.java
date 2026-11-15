package com.stackroute.maverick.api;

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

import com.stackroute.maverick.adaptive.AdaptiveRules;
import com.stackroute.maverick.adaptive.AdaptiveService;
import com.stackroute.maverick.adaptive.AdaptiveSession;
import com.stackroute.maverick.adaptive.PooledQuestion;

@WebMvcTest(AdaptiveController.class)
class AdaptiveControllerTest {

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

		mvc.perform(post("/api/v1/adaptive/sessions").contentType(MediaType.APPLICATION_JSON)
				.content("{\"gameId\":5,\"userId\":9}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.currentQuestion.stem").value("2 + 2?"))
				.andExpect(jsonPath("$.currentQuestion.points").value(10))
				.andExpect(content().string(not(containsString("correctAnswer"))));
	}
}
