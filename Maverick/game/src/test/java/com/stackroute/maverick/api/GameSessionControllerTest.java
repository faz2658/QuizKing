package com.stackroute.maverick.api;

import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.stackroute.maverick.service.GameNotReadyException;
import com.stackroute.maverick.service.GameSessionService;
import com.stackroute.maverick.session.Difficulty;
import com.stackroute.maverick.session.GameSession;
import com.stackroute.maverick.session.QuizRuleException;
import com.stackroute.maverick.session.SessionQuestion;

@WebMvcTest(GameSessionController.class)
class GameSessionControllerTest {

	static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

	@TestConfiguration
	static class Config {
		@Bean
		@org.springframework.context.annotation.Primary
		Clock fixedClock() {
			return Clock.fixed(NOW, ZoneOffset.UTC);
		}
	}

	@Autowired
	MockMvc mvc;

	@MockitoBean
	GameSessionService service;

	private GameSession session() {
		return new GameSession("abc", 5, 42, 7, "Capitals", List.of(
				new SessionQuestion(1, Difficulty.EASY, "Capital of France?", List.of("Lyon", "Paris"), "Paris", 10, 10)),
				NOW);
	}

	@Test
	void startingAGameReturnsTheFirstQuestionWithoutItsAnswer() throws Exception {
		when(service.start(5, 42)).thenReturn(session());

		mvc.perform(post("/api/v1/sessions").contentType(MediaType.APPLICATION_JSON)
				.content("{\"gameId\":5,\"userId\":42}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.sessionId").value("abc"))
				.andExpect(jsonPath("$.currentQuestion.stem").value("Capital of France?"))
				.andExpect(jsonPath("$.currentQuestion.secondsRemaining").value(10))
				.andExpect(content().string(not(org.hamcrest.Matchers.containsString("correctAnswer"))));
	}

	@Test
	void invalidStartRequestIsRejected() throws Exception {
		mvc.perform(post("/api/v1/sessions").contentType(MediaType.APPLICATION_JSON).content("{\"gameId\":0}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void gameStillLoadingAnswers503WithRetryHint() throws Exception {
		when(service.start(anyInt(), anyInt())).thenThrow(new GameNotReadyException(5));

		mvc.perform(post("/api/v1/sessions").contentType(MediaType.APPLICATION_JSON)
				.content("{\"gameId\":5,\"userId\":42}"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.retryAfterSeconds").value(2));
	}

	@Test
	void answeringRevealsTheCorrectAnswer() throws Exception {
		GameSession s = session();
		var outcome = s.answer(1, "Lyon", NOW.plusSeconds(1));
		when(service.answer(eq("abc"), eq(1), eq("Lyon")))
				.thenReturn(new GameSessionService.AnswerResult(outcome, s));

		mvc.perform(post("/api/v1/sessions/abc/answers").contentType(MediaType.APPLICATION_JSON)
				.content("{\"questionId\":1,\"answer\":\"Lyon\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.correct").value(false))
				.andExpect(jsonPath("$.correctAnswer").value("Paris"))
				.andExpect(jsonPath("$.session.status").value("COMPLETED"));
	}

	@Test
	void breakingTheRulesIsAConflict() throws Exception {
		when(service.result("abc")).thenThrow(new QuizRuleException("The game is still in progress"));

		mvc.perform(get("/api/v1/sessions/abc/result"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value("The game is still in progress"));
	}
}
