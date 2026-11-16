package com.fazeel.quizcadia.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fazeel.quizcadia.catalog.GameCatalogService;
import com.fazeel.quizcadia.config.SecurityConfig;
import com.fazeel.quizcadia.domain.Game;
import com.fazeel.quizcadia.domain.MultiPlayerGame;

/** The engines read full game definitions without a token, on the internal path the public proxy never forwards. */
@Import(SecurityConfig.class)
@WebMvcTest(InternalGameController.class)
class InternalGameControllerTest {

	@Autowired
	MockMvc mvc;

	@MockitoBean
	GameCatalogService catalog;

	@Test
	void enginesCanReadFullGamesWithoutAToken() throws Exception {
		Game g = new Game();
		g.setGameId(2_000_001);
		g.setGameName("Video Games");
		when(catalog.game(2_000_001)).thenReturn(g);
		MultiPlayerGame mp = new MultiPlayerGame();
		mp.setGameId(2_000_002);
		when(catalog.multiPlayerGame(2_000_002)).thenReturn(mp);

		mvc.perform(get("/internal/game/2000001")).andExpect(status().isOk()).andExpect(jsonPath("$.gameName").value("Video Games"));
		mvc.perform(get("/internal/game/mp/2000002")).andExpect(status().isOk()).andExpect(jsonPath("$.gameId").value(2_000_002));
	}
}
