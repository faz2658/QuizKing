package com.fazeel.quizcadia.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fazeel.quizcadia.config.SecurityConfig;
import com.fazeel.quizcadia.matching.Matchmaker;
import com.fazeel.quizcadia.matching.Presence;
import com.fazeel.quizcadia.matching.QuickMatch;

@Import(SecurityConfig.class)
@WebMvcTest(WorldController.class)
class WorldControllerTest {

	@Autowired
	MockMvc mvc;

	// The real Presence, not a mock: the app's scheduled task calls presence.tick() as the context starts, and on a
	// mock that background call can steal a when(...) stubbing made at the same moment (a flaky CI failure).
	@Autowired
	Presence presence;

	@MockitoBean
	Matchmaker matchmaker;

	@MockitoBean
	QuickMatch quick;

	@MockitoBean
	com.fazeel.quizcadia.matching.PrivateRooms rooms;

	@MockitoBean(name = "matchPublisher")
	java.util.function.Consumer<com.fazeel.quizcadia.domain.Match> matchPublisher;

	@Test
	void onlyAdminsSeeTheNamesOfWhoIsOnline() throws Exception {
		presence.seen(7, "Ada", "CA"); // a heartbeat

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
