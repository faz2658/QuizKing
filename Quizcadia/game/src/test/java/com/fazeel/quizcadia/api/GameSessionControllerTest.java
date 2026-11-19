package com.fazeel.quizcadia.api;

import static org.mockito.ArgumentMatchers.any;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import com.fazeel.quizcadia.config.SecurityConfig;
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

import com.fazeel.quizcadia.service.GameNotReadyException;
import com.fazeel.quizcadia.service.GameSessionService;
import com.fazeel.quizcadia.session.Difficulty;
import com.fazeel.quizcadia.session.GameSession;
import com.fazeel.quizcadia.session.QuizRuleException;
import com.fazeel.quizcadia.session.SessionQuestion;

@Import(SecurityConfig.class)
@WebMvcTest(GameSessionController.class)
class GameSessionControllerTest {

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
	void anInternationalQuizAsksForQuestionsFromEveryCountry() throws Exception {
		when(service.start(eq(5), eq(42), any(), eq(com.fazeel.quizcadia.session.QuizBuilder.EVERYWHERE))).thenReturn(session());

		mvc.perform(post("/api/v1/sessions").with(player(42)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"gameId\":5,\"userId\":42,\"country\":\"IN\",\"international\":true}"))
				.andExpect(status().isCreated());
	}

	@Test
	void aCountryThatIsNotATwoLetterCodeIsRejected() throws Exception {
		mvc.perform(post("/api/v1/sessions").with(player(42)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"gameId\":5,\"userId\":42,\"country\":\"India\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void startingAGameReturnsTheFirstQuestionWithoutItsAnswer() throws Exception {
		when(service.start(eq(5), eq(42), any(), eq("IN"))).thenReturn(session());

		mvc.perform(post("/api/v1/sessions").with(player(42)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"gameId\":5,\"userId\":42,\"country\":\"IN\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.sessionId").value("abc"))
				.andExpect(jsonPath("$.currentQuestion.stem").value("Capital of France?"))
				.andExpect(jsonPath("$.currentQuestion.secondsRemaining").value(10))
				.andExpect(content().string(not(org.hamcrest.Matchers.containsString("correctAnswer"))));
	}

	@Test
	void invalidStartRequestIsRejected() throws Exception {
		mvc.perform(post("/api/v1/sessions").with(player(42)).contentType(MediaType.APPLICATION_JSON).content("{\"gameId\":0}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void gameStillLoadingAnswers503WithRetryHint() throws Exception {
		when(service.start(anyInt(), anyInt(), any(), any())).thenThrow(new GameNotReadyException(5));

		mvc.perform(post("/api/v1/sessions").with(player(42)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"gameId\":5,\"userId\":42}"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.retryAfterSeconds").value(2));
	}

	@Test
	void answeringRevealsTheCorrectAnswer() throws Exception {
		GameSession s = session();
		when(service.get("abc")).thenReturn(s);
		var outcome = s.answer(1, "Lyon", NOW.plusSeconds(1));
		when(service.answer(eq("abc"), eq(1), eq("Lyon")))
				.thenReturn(new GameSessionService.AnswerResult(outcome, s));

		mvc.perform(post("/api/v1/sessions/abc/answers").with(player(42)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"questionId\":1,\"answer\":\"Lyon\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.correct").value(false))
				.andExpect(jsonPath("$.correctAnswer").value("Paris"))
				.andExpect(jsonPath("$.session.status").value("COMPLETED"));
	}

	@Test
	void breakingTheRulesIsAConflict() throws Exception {
		when(service.get("abc")).thenReturn(session());
		when(service.result("abc")).thenThrow(new QuizRuleException("The game is still in progress"));

		mvc.perform(get("/api/v1/sessions/abc/result").with(player(42)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value("The game is still in progress"));
	}

	@Test
	void playingNeedsAToken() throws Exception {
		mvc.perform(post("/api/v1/sessions").contentType(MediaType.APPLICATION_JSON)
				.content("{\"gameId\":5,\"userId\":42}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void nobodyCanStartAGameAsSomeoneElse() throws Exception {
		mvc.perform(post("/api/v1/sessions").with(player(43)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"gameId\":5,\"userId\":42}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void anotherPlayersSessionIsOffLimits() throws Exception {
		when(service.get("abc")).thenReturn(session()); // started by player 42

		mvc.perform(get("/api/v1/sessions/abc").with(player(43))).andExpect(status().isForbidden());
		mvc.perform(post("/api/v1/sessions/abc/answers").with(player(43)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"questionId\":1,\"answer\":\"Paris\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void theAppReportsAShownQuestionOnlyForItsOwnGame() throws Exception {
		org.mockito.Mockito.when(service.get("abc")).thenReturn(session()); // the player is user 42

		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/sessions/abc/questions/1/shown")
				.with(player(42))).andExpect(status().isNoContent());
		org.mockito.Mockito.verify(service).shown("abc", 1);

		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/sessions/abc/questions/1/shown")
				.with(player(7))).andExpect(status().isForbidden());
		org.mockito.Mockito.verify(service, org.mockito.Mockito.times(1)).shown("abc", 1);
	}
}
