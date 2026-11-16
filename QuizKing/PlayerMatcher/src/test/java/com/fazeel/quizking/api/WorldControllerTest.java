package com.fazeel.quizking.api;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fazeel.quizking.config.SecurityConfig;
import com.fazeel.quizking.matching.Matchmaker;
import com.fazeel.quizking.matching.Presence;
import com.fazeel.quizking.matching.QuickMatch;

@Import(SecurityConfig.class)
@WebMvcTest(WorldController.class)
class WorldControllerTest {

	@Autowired
	MockMvc mvc;

	@MockitoBean
	Presence presence;

	@MockitoBean
	Matchmaker matchmaker;

	@MockitoBean
	QuickMatch quick;

	@MockitoBean
	com.fazeel.quizking.matching.PrivateRooms rooms;

	@MockitoBean(name = "matchPublisher")
	java.util.function.Consumer<com.fazeel.quizking.domain.Match> matchPublisher;

	@Test
	void onlyAdminsSeeTheNamesOfWhoIsOnline() throws Exception {
		Instant t = Instant.parse("2026-11-16T05:00:00Z");
		when(presence.whoIsOnline()).thenReturn(List.of(new Presence.OnlinePlayer(7, "Ada", "CA", false, t, t)));

		mvc.perform(get("/api/v1/matching/admin/online")
				.with(jwt().jwt(j -> j.subject("2")).authorities(new SimpleGrantedAuthority("ROLE_USER"))))
				.andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/matching/admin/online")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/matching/admin/online")
				.with(jwt().jwt(j -> j.subject("1")).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
				.andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("Ada"))
				.andExpect(jsonPath("$[0].guest").value(false));
	}
}
