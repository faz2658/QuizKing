package com.fazeel.quizcadia.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import com.fazeel.quizcadia.config.SecurityConfig;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fazeel.quizcadia.catalog.GameCatalogService;
import com.fazeel.quizcadia.catalog.NotEnoughQuestionsException;
import com.fazeel.quizcadia.catalog.NotFoundException;
import com.fazeel.quizcadia.domain.Game;

@Import(SecurityConfig.class)
@WebMvcTest(GameManagerController.class)
class GameManagerControllerTest {

	/** A signed-in player whose token subject is {@code userId}. */
	static RequestPostProcessor player(int userId) {
		return jwt().jwt(j -> j.subject(String.valueOf(userId))).authorities(new SimpleGrantedAuthority("ROLE_USER"));
	}

	static RequestPostProcessor admin() {
		return jwt().jwt(j -> j.subject("1")).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
	}

	@Autowired
	MockMvc mvc;

	@MockitoBean
	GameCatalogService catalog;

	@Test
	void createsAGame() throws Exception {
		Game g = new Game();
		g.setGameId(2_000_001);
		g.setGameName("Capitals");
		when(catalog.createGame(eq(1), eq("Geography"), any())).thenReturn(g);

		mvc.perform(post("/api/game/category/1/Geography").with(admin()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"gameName\":\"Capitals\",\"questionLevels\":{\"easyLevel\":2}}"))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.gameId").value(2_000_001));
	}

	@Test
	void gameListsNeverIncludeQuestionsOrAnswers() throws Exception {
		Game g = new Game();
		g.setGameId(5);
		g.setGameName("Film");
		com.fazeel.quizcadia.domain.Questions q = new com.fazeel.quizcadia.domain.Questions();
		q.setQuestionStem("Secret?");
		q.setCorrectAnswer("SECRET-ANSWER");
		g.setQuestions(java.util.List.of(q, q, q));
		when(catalog.version()).thenReturn(System.nanoTime()); // each test sees a fresh catalog
		when(catalog.games()).thenReturn(java.util.List.of(g));

		mvc.perform(get("/api/game/games").with(admin())).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].questionCount").value(3))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
						.string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SECRET-ANSWER"))));
	}

	@Test
	void notEnoughQuestionsIsUnprocessable() throws Exception {
		when(catalog.createGame(eq(1), eq("Geography"), any())).thenThrow(new NotEnoughQuestionsException(1, 5, 2));

		mvc.perform(post("/api/game/category/1/Geography").with(admin()).contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.detail").value("Asked for 5 easy questions but the topic only has 2"));
	}

	@Test
	void fullGameDefinitionsAreNotOnThePublicApi() throws Exception {
		// they hold every answer: only the engines read them, on /internal/ (which the public proxy never forwards)
		for (String path : new String[] { "/api/game/game1/7", "/api/game/games/mp/7", "/api/game/games/mp/+7", "/api/game/game1/+7" }) {
			mvc.perform(get(path)).andExpect(result -> {
				String body = result.getResponse().getContentAsString();
				org.assertj.core.api.Assertions.assertThat(body).as(path).doesNotContain("correctAnswer").doesNotContain("\"questions\"");
			});
		}
		org.mockito.Mockito.verify(catalog, org.mockito.Mockito.never()).game(org.mockito.ArgumentMatchers.anyInt());
		org.mockito.Mockito.verify(catalog, org.mockito.Mockito.never()).multiPlayerGame(org.mockito.ArgumentMatchers.anyInt());
	}

	@Test
	void deleteNeedsNoRequestBody() throws Exception {
		mvc.perform(delete("/api/game/deletegame/7").with(admin())).andExpect(status().isNoContent());
		verify(catalog).deleteGame(7);
	}

	@Test
	void anyoneMayBrowseButOnlyAdminsChangeGames() throws Exception {
		when(catalog.version()).thenReturn(System.nanoTime()); // each test sees a fresh catalog
		when(catalog.games()).thenReturn(java.util.List.of());

		mvc.perform(get("/api/game/games")).andExpect(status().isOk());
		mvc.perform(post("/api/game/category/1/Geography").with(player(5)).contentType(MediaType.APPLICATION_JSON)
				.content("{}")).andExpect(status().isForbidden());
		mvc.perform(delete("/api/game/deletegame/7").with(player(5))).andExpect(status().isForbidden());
		mvc.perform(delete("/api/game/deletegame/7")).andExpect(status().isUnauthorized());
	}

	@Test
	void theGamesListIsBuiltOnceUntilTheCatalogChanges() throws Exception {
		long v = System.nanoTime();
		when(catalog.version()).thenReturn(v);
		when(catalog.games()).thenReturn(java.util.List.of());
		for (int i = 0; i < 5; i++) {
			mvc.perform(get("/api/game/games").with(admin())).andExpect(status().isOk());
		}
		org.mockito.Mockito.verify(catalog, org.mockito.Mockito.times(1)).games();

		when(catalog.version()).thenReturn(v + 1); // someone created or changed a game
		mvc.perform(get("/api/game/games").with(admin())).andExpect(status().isOk());
		org.mockito.Mockito.verify(catalog, org.mockito.Mockito.times(2)).games();
	}
}
