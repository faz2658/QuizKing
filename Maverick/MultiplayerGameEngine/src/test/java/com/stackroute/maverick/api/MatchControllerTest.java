package com.stackroute.maverick.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.stackroute.maverick.match.MatchQuestion;
import com.stackroute.maverick.match.MatchService;
import com.stackroute.maverick.match.MultiplayerMatch;
import com.stackroute.maverick.match.NotFoundException;

@WebMvcTest(MatchController.class)
class MatchControllerTest {

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
	MatchService service;

	@Test
	void openQuestionNeverRevealsItsAnswer() throws Exception {
		MultiplayerMatch m = new MultiplayerMatch("m1", 7, "Race", 1, List.of(1, 2),
				List.of(new MatchQuestion(5, "Capital of Peru?", List.of("Lima", "Quito"), "Lima")), 10, 10, NOW);
		when(service.get("m1", 1)).thenReturn(m);

		mvc.perform(get("/api/v1/matches/m1").param("userId", "1")).andExpect(status().isOk())
				.andExpect(jsonPath("$.currentQuestion.stem").value("Capital of Peru?"))
				.andExpect(jsonPath("$.standings.length()").value(2))
				.andExpect(content().string(not(containsString("correctAnswer"))));
	}

	@Test
	void matchNotStartedYetIs404WithRetryHint() throws Exception {
		when(service.get("m1", 1)).thenThrow(new NotFoundException("No match m1"));

		mvc.perform(get("/api/v1/matches/m1").param("userId", "1")).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.retryAfterSeconds").value(1));
	}
}
