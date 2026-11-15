package com.stackroute.maverick.api;

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

import com.stackroute.maverick.catalog.GameCatalogService;
import com.stackroute.maverick.catalog.NotEnoughQuestionsException;
import com.stackroute.maverick.catalog.NotFoundException;
import com.stackroute.maverick.domain.Game;

@WebMvcTest(GameManagerController.class)
class GameManagerControllerTest {

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

		mvc.perform(post("/api/game/category/1/Geography").contentType(MediaType.APPLICATION_JSON)
				.content("{\"gameName\":\"Capitals\",\"questionLevels\":{\"easyLevel\":2}}"))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.gameId").value(2_000_001));
	}

	@Test
	void notEnoughQuestionsIsUnprocessable() throws Exception {
		when(catalog.createGame(eq(1), eq("Geography"), any())).thenThrow(new NotEnoughQuestionsException(1, 5, 2));

		mvc.perform(post("/api/game/category/1/Geography").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.detail").value("Asked for 5 easy questions but the topic only has 2"));
	}

	@Test
	void unknownGameIs404() throws Exception {
		when(catalog.game(7)).thenThrow(new NotFoundException("No game 7"));

		mvc.perform(get("/api/game/game1/7")).andExpect(status().isNotFound());
	}

	@Test
	void deleteNeedsNoRequestBody() throws Exception {
		mvc.perform(delete("/api/game/deletegame/7")).andExpect(status().isNoContent());
		verify(catalog).deleteGame(7);
	}
}
