package com.fazeel.quizcadia.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import com.fazeel.quizcadia.config.SecurityConfig;
import com.fazeel.quizcadia.match.MatchQuestion;
import com.fazeel.quizcadia.match.MatchService;
import com.fazeel.quizcadia.match.MatchUpdates;
import com.fazeel.quizcadia.match.MultiplayerMatch;
import com.fazeel.quizcadia.match.NotFoundException;

@Import({ SecurityConfig.class, MatchUpdates.class })
@WebMvcTest(MatchController.class)
class MatchControllerTest {

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
	MatchService service;

	@Autowired
	MatchUpdates updates;

	static MultiplayerMatch race() {
		return new MultiplayerMatch("m1", 7, "Race", 1, List.of(1, 2),
				List.of(new MatchQuestion(5, "Capital of Peru?", List.of("Lima", "Quito"), "Lima")), 10, 10, NOW);
	}

	@Test
	void nextAnswersAtOnceWhenTheScreenIsOutOfDate() throws Exception {
		when(service.get("m1", 1)).thenReturn(race());
		var started = mvc.perform(get("/api/v1/matches/m1/next").with(player(1)).param("userId", "1").param("after", "stale"))
				.andExpect(MockMvcResultMatchers.request().asyncStarted()).andReturn();
		mvc.perform(MockMvcRequestBuilders.asyncDispatch(started))
				.andExpect(status().isOk()).andExpect(jsonPath("$.version").isNotEmpty())
				.andExpect(jsonPath("$.currentQuestion.stem").value("Capital of Peru?"));
	}

	@Test
	void nextWaitsUntilTheMatchChanges() throws Exception {
		MultiplayerMatch m = race();
		when(service.get("m1", 1)).thenReturn(m);
		// the current version, as the screen would have it from its last answer
		String current = Views_version(m);
		var waiting = mvc.perform(get("/api/v1/matches/m1/next").with(player(1)).param("userId", "1").param("after", current))
				.andExpect(MockMvcResultMatchers.request().asyncStarted()).andReturn();
		Thread.sleep(100);
		Assertions.assertThat(updates.waitingFor("m1")).isEqualTo(1); // still waiting: nothing changed

		m.answer(2, 5, "Lima", NOW.plusSeconds(3)); // the other player answers
		updates.changed("m1");
		mvc.perform(MockMvcRequestBuilders.asyncDispatch(waiting))
				.andExpect(status().isOk()).andExpect(jsonPath("$.standings[0].userId").value(2))
				.andExpect(jsonPath("$.standings[0].score").value(Matchers.greaterThan(0)))
				.andExpect(jsonPath("$.version").value(Matchers.not(current)));
	}

	static String Views_version(MultiplayerMatch m) {
		return Views.MatchView.of(m, 1, NOW).version();
	}

	@Test
	void openQuestionNeverRevealsItsAnswer() throws Exception {
		MultiplayerMatch m = new MultiplayerMatch("m1", 7, "Race", 1, List.of(1, 2),
				List.of(new MatchQuestion(5, "Capital of Peru?", List.of("Lima", "Quito"), "Lima")), 10, 10, NOW);
		when(service.get("m1", 1)).thenReturn(m);

		mvc.perform(get("/api/v1/matches/m1").with(player(1)).param("userId", "1")).andExpect(status().isOk())
				.andExpect(jsonPath("$.currentQuestion.stem").value("Capital of Peru?"))
				.andExpect(jsonPath("$.standings.length()").value(2))
				.andExpect(content().string(not(containsString("correctAnswer"))));
	}

	@Test
	void matchNotStartedYetIs404WithRetryHint() throws Exception {
		when(service.get("m1", 1)).thenThrow(new NotFoundException("No match m1"));

		mvc.perform(get("/api/v1/matches/m1").with(player(1)).param("userId", "1")).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.retryAfterSeconds").value(1));
	}

	@Test
	void playersOnlySeeAndAnswerMatchesAsThemselves() throws Exception {
		mvc.perform(get("/api/v1/matches/m1").with(player(2)).param("userId", "1")).andExpect(status().isForbidden());
		mvc.perform(post("/api/v1/matches/m1/answers").with(player(2)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"userId\":1,\"questionId\":5,\"answer\":\"Lima\"}")).andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/matches/m1").param("userId", "1")).andExpect(status().isUnauthorized());
	}

	static MultiplayerMatch roomQuiz() {
		return MultiplayerMatch.roomQuiz("q1", List.of(1, 2), Map.of(1, "Ada", 2, "Bo"),
				List.of(new MatchQuestion(5, "Capital of Peru?", List.of("Lima", "Quito"), "Lima"),
						new MatchQuestion(6, "Capital of Chile?", List.of("Santiago", "Lima"), "Santiago")),
				20, 0, 50, NOW.minus(MultiplayerMatch.LEAD_IN));
	}

	@Test
	void aPhoneInARoomQuizKeepsWaitingThroughOtherPlayersAnswers() throws Exception {
		MultiplayerMatch m = roomQuiz();
		when(service.get("q1", 1)).thenReturn(m);
		String current = Views.MatchView.of(m, 1, NOW).version();
		var waiting = mvc.perform(get("/api/v1/matches/q1/next").with(player(1)).param("userId", "1").param("after", current))
				.andExpect(MockMvcResultMatchers.request().asyncStarted()).andReturn();
		Thread.sleep(100);

		m.answer(2, 5, "Lima", NOW.plusSeconds(1)); // someone else answers: nothing new on this phone
		updates.changed("q1");
		Thread.sleep(50);
		Assertions.assertThat(updates.waitingFor("q1")).isEqualTo(1);

		m.answer(1, 5, "Quito", NOW.plusSeconds(2)); // its own answer, the last one: the answer shows
		updates.changed("q1");
		mvc.perform(MockMvcRequestBuilders.asyncDispatch(waiting))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.phase").value("REVEAL"))
				.andExpect(jsonPath("$.format").value("ROOM_QUIZ"))
				.andExpect(jsonPath("$.lastQuestion.correctAnswer").value("Lima"))
				.andExpect(jsonPath("$.lastQuestion.counts[0]").value(1))
				.andExpect(jsonPath("$.lastQuestion.counts[1]").value(1))
				.andExpect(jsonPath("$.you.rank").value(2))
				.andExpect(jsonPath("$.you.lastCorrect").value(false))
				.andExpect(jsonPath("$.you.lastAnswer").value("Quito"));
	}

	@Test
	void theBigScreenSeesHowManyAnswered() throws Exception {
		MultiplayerMatch m = roomQuiz();
		m.answer(2, 5, "Lima", NOW.plusSeconds(1));
		when(service.get("q1", 50)).thenReturn(m);

		mvc.perform(get("/api/v1/matches/q1").with(player(50)).param("userId", "50")).andExpect(status().isOk())
				.andExpect(jsonPath("$.phase").value("QUESTION"))
				.andExpect(jsonPath("$.answered").value(1))
				.andExpect(jsonPath("$.playerCount").value(2))
				.andExpect(jsonPath("$.you").doesNotExist())
				.andExpect(content().string(not(containsString("correctAnswer\":\"Lima"))));
	}

	@Test
	void theHostPacesTheQuizAsTheCaller() throws Exception {
		MultiplayerMatch m = roomQuiz();
		when(service.host("q1", 50, MultiplayerMatch.HostAction.REVEAL)).thenReturn(m);

		mvc.perform(post("/api/v1/matches/q1/host").with(player(50)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"action\":\"REVEAL\"}")).andExpect(status().isOk())
				.andExpect(jsonPath("$.matchId").value("q1"));
		mvc.perform(post("/api/v1/matches/q1/host").with(player(50)).contentType(MediaType.APPLICATION_JSON)
				.content("{}")).andExpect(status().isBadRequest());
	}
}
