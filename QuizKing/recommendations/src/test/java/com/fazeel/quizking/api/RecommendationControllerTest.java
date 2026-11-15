package com.fazeel.quizking.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import com.fazeel.quizking.config.SecurityConfig;
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

import com.fazeel.quizking.recommend.GameMode;
import com.fazeel.quizking.recommend.Recommendation;
import com.fazeel.quizking.recommend.Recommendation.Reason;
import com.fazeel.quizking.recommend.RecommendationService;

@Import(SecurityConfig.class)
@WebMvcTest(RecommendationController.class)
class RecommendationControllerTest {

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
	RecommendationService service;

	@Test
	void returnsTheFeedWithReasons() throws Exception {
		when(service.forUser(5, 12)).thenReturn(
				List.of(new Recommendation(1, "Capitals", null, 10, GameMode.SINGLE, Reason.FAVORITE_CATEGORY, 3.0)));

		mvc.perform(get("/api/v1/recommendation/users/5").with(player(5))).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].reason").value("FAVORITE_CATEGORY"));
	}

	@Test
	void recommendationsAreForTheirOwnerOnly() throws Exception {
		mvc.perform(get("/api/v1/recommendation/users/5").with(player(6))).andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/recommendation/users/5/favorite-categories").with(player(6)))
				.andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/recommendation/users/5")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/recommendation/users/5").with(admin())).andExpect(status().isOk());
	}
}
