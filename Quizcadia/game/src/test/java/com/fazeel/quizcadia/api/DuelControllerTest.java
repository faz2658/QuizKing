package com.fazeel.quizcadia.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.fazeel.quizcadia.challenge.FriendDuel;
import com.fazeel.quizcadia.challenge.FriendDuel.Side;
import com.fazeel.quizcadia.challenge.FriendDuelService;
import com.fazeel.quizcadia.challenge.FriendDuelService.Attempt;
import com.fazeel.quizcadia.challenge.FriendDuelService.Standing;
import com.fazeel.quizcadia.challenge.FriendDuelService.State;
import com.fazeel.quizcadia.config.SecurityConfig;
import com.fazeel.quizcadia.session.Difficulty;
import com.fazeel.quizcadia.session.GameSession;
import com.fazeel.quizcadia.session.SessionQuestion;

@Import(SecurityConfig.class)
@WebMvcTest(DuelController.class)
class DuelControllerTest {

	static final Instant NOW = Instant.parse("2026-11-17T12:00:00Z");

	static RequestPostProcessor player(int userId) {
		return jwt().jwt(j -> j.subject(String.valueOf(userId)).claim("name", "P" + userId))
				.authorities(new SimpleGrantedAuthority("ROLE_USER"));
	}

	static RequestPostProcessor guest(int userId) {
		return jwt().jwt(j -> j.subject(String.valueOf(userId))).authorities(new SimpleGrantedAuthority("ROLE_GUEST"));
	}

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
	FriendDuelService service;

	static List<SessionQuestion> questions() {
		return List.of(new SessionQuestion(1, Difficulty.EASY, "Capital of India?", List.of("Delhi", "Agra"), "Delhi", 10, 10));
	}

	static GameSession attempt(String id, int userId, boolean finished) {
		GameSession s = new GameSession(id, 5, userId, 7, "Capitals", questions(), NOW);
		if (finished) {
			s.answer(1, "Delhi", NOW.plusSeconds(1));
		}
		return s;
	}

	/** Ada (IN) challenged Bo (IN); Bo has finished, Ada is halfway. */
	static Standing halfway() {
		FriendDuel d = new FriendDuel("abcd2345", 1L, 5, "Capitals", 7, false, new Side(1, "Ada", "IN", "s1"),
				new Side(2, "Bo", "IN", "s2"), FriendDuel.Status.ACCEPTED, questions(), NOW, NOW, NOW.plusSeconds(3600));
		return new Standing(d, State.PLAYING, new Attempt(d.challenger(), attempt("s1", 1, false)),
				new Attempt(d.opponent(), attempt("s2", 2, true)), null, false);
	}

	@Test
	void guestsCannotSendChallenges() throws Exception {
		mvc.perform(post("/api/v1/challenges/duels").with(guest(9)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"gameId\":5}")).andExpect(status().isForbidden());
	}

	@Test
	void aSignedInPlayerGetsAChallengeToShare() throws Exception {
		FriendDuel d = new FriendDuel("abcd2345", 0L, 5, "Capitals", 7, false, new Side(1, "P1", "IN", null), null,
				FriendDuel.Status.PENDING, List.of(), NOW, null, NOW.plusSeconds(60));
		when(service.create(1, "P1", 5, false, "IN")).thenReturn(d);
		when(service.standing(d)).thenReturn(new Standing(d, State.WAITING, new Attempt(d.challenger(), null), null, null, false));

		mvc.perform(post("/api/v1/challenges/duels").with(player(1)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"gameId\":5,\"country\":\"IN\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value("abcd2345"))
				.andExpect(jsonPath("$.state").value("WAITING"))
				.andExpect(jsonPath("$.role").value("CHALLENGER"))
				.andExpect(content().string(not(containsString("IN\""))));
	}

	@Test
	void theFriendsScoreStaysHiddenUntilYouHaveFinishedAndCountriesAreNeverShown() throws Exception {
		Standing st = halfway();
		when(service.get("abcd2345")).thenReturn(st.duel());
		when(service.standing(any())).thenReturn(st);

		mvc.perform(get("/api/v1/challenges/duels/abcd2345").with(player(1)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.you.status").value("IN_PROGRESS"))
				.andExpect(jsonPath("$.you.sessionId").value("s1"))
				.andExpect(jsonPath("$.friend.status").value("COMPLETED"))
				.andExpect(jsonPath("$.friend.score").doesNotExist())
				.andExpect(jsonPath("$.friend.sessionId").doesNotExist())
				.andExpect(content().string(not(containsString("country"))))
				.andExpect(content().string(not(containsString("\"IN\""))));

		mvc.perform(get("/api/v1/challenges/duels/abcd2345").with(player(2)))
				.andExpect(jsonPath("$.role").value("OPPONENT"))
				.andExpect(jsonPath("$.you.score").value(10))
				.andExpect(jsonPath("$.friend.status").value("IN_PROGRESS"));
	}

	@Test
	void theResultShowsTheWinnerAndBothScores() throws Exception {
		FriendDuel d = halfway().duel();
		Standing done = new Standing(d, State.FINISHED, new Attempt(d.challenger(), attempt("s1", 1, false)),
				new Attempt(d.opponent(), attempt("s2", 2, true)), 2, false);
		when(service.get("abcd2345")).thenReturn(d);
		when(service.standing(any())).thenReturn(done);

		mvc.perform(get("/api/v1/challenges/duels/abcd2345").with(player(1)))
				.andExpect(jsonPath("$.outcome").value("LOST"))
				.andExpect(jsonPath("$.friend.name").value("Bo"))
				.andExpect(jsonPath("$.friend.score").value(10))
				.andExpect(jsonPath("$.expiresAt").doesNotExist());
		mvc.perform(get("/api/v1/challenges/duels/abcd2345").with(player(2)))
				.andExpect(jsonPath("$.outcome").value("WON"));
	}

	@Test
	void acceptingPassesThePlayersCountry() throws Exception {
		Standing st = halfway();
		when(service.accept("abcd2345", 2, "P2", "IN")).thenReturn(st.duel());
		when(service.standing(any())).thenReturn(st);

		mvc.perform(post("/api/v1/challenges/duels/abcd2345/accept").with(player(2))
				.contentType(MediaType.APPLICATION_JSON).content("{\"country\":\"IN\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.role").value("OPPONENT"));
	}

	@Test
	void playingStartsAnOrdinarySession() throws Exception {
		when(service.play("abcd2345", 2, "P2")).thenReturn(attempt("s2", 2, false));

		mvc.perform(post("/api/v1/challenges/duels/abcd2345/sessions").with(player(2)))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.sessionId").value("s2"))
				.andExpect(jsonPath("$.currentQuestion.options").isArray());
	}

	@Test
	void bothSidesSeeADeclinedChallenge() throws Exception {
		FriendDuel d = new FriendDuel("abcd2345", 1L, 5, "Capitals", 7, false, new Side(1, "Ada", "IN", null),
				new Side(2, "Bo", null, null), FriendDuel.Status.DECLINED, List.of(), NOW, null, NOW.plusSeconds(60));
		Standing st = new Standing(d, State.DECLINED, new Attempt(d.challenger(), null), new Attempt(d.opponent(), null),
				null, false);
		when(service.get("abcd2345")).thenReturn(d);
		when(service.standing(any())).thenReturn(st);

		mvc.perform(get("/api/v1/challenges/duels/abcd2345").with(player(1)))
				.andExpect(jsonPath("$.state").value("DECLINED"))
				.andExpect(jsonPath("$.friend.name").value("Bo"))
				.andExpect(jsonPath("$.friend.status").value("DECLINED"));
		mvc.perform(get("/api/v1/challenges/duels/abcd2345").with(player(2)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role").value("OPPONENT"))
				.andExpect(jsonPath("$.you.status").value("DECLINED"));
	}

	@Test
	void outsidersOnlySeeWhoSentIt() throws Exception {
		Standing st = halfway();
		when(service.get("abcd2345")).thenReturn(st.duel());
		when(service.standing(any())).thenReturn(st);

		mvc.perform(get("/api/v1/challenges/duels/abcd2345").with(player(3)))
				.andExpect(jsonPath("$.role").value("OUTSIDER"))
				.andExpect(jsonPath("$.you").doesNotExist())
				.andExpect(jsonPath("$.friend").doesNotExist());
	}
}
