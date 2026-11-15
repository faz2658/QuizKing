package com.fazeel.maverick.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fazeel.maverick.matching.LobbyStatus;
import com.fazeel.maverick.matching.LobbyStatus.State;
import com.fazeel.maverick.matching.Matchmaker;

@WebMvcTest(MatchingController.class)
class MatchingControllerTest {

	@Autowired
	MockMvc mvc;

	@MockitoBean
	Matchmaker matchmaker;

	@Test
	void joiningReturnsTheLobby() throws Exception {
		when(matchmaker.join(3, 7)).thenReturn(new LobbyStatus(State.WAITING, 3, List.of(7), null, null));

		mvc.perform(post("/api/v1/matching/3/players/7")).andExpect(status().isOk())
				.andExpect(jsonPath("$.state").value("WAITING")).andExpect(jsonPath("$.players[0]").value(7));
	}
}
