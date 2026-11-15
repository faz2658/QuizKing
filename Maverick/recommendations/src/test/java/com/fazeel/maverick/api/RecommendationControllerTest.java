package com.fazeel.maverick.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fazeel.maverick.recommend.GameMode;
import com.fazeel.maverick.recommend.Recommendation;
import com.fazeel.maverick.recommend.Recommendation.Reason;
import com.fazeel.maverick.recommend.RecommendationService;

@WebMvcTest(RecommendationController.class)
class RecommendationControllerTest {

	@Autowired
	MockMvc mvc;

	@MockitoBean
	RecommendationService service;

	@Test
	void returnsTheFeedWithReasons() throws Exception {
		when(service.forUser(5, 12)).thenReturn(
				List.of(new Recommendation(1, "Capitals", null, 10, GameMode.SINGLE, Reason.FAVORITE_CATEGORY, 3.0)));

		mvc.perform(get("/api/v1/recommendation/users/5")).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].reason").value("FAVORITE_CATEGORY"));
	}
}
