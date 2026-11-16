package com.fazeel.quizcadia.profile;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.fazeel.quizcadia.config.SecurityConfig;

/** A profile is its owner's; only admins may list or search profiles. */
@Import(SecurityConfig.class)
@WebMvcTest(ProfileController.class)
class ProfileSecurityTest {

	@Autowired
	MockMvc mvc;

	@MockitoBean
	ProfileService service;

	static RequestPostProcessor as(int userId, String role) {
		return jwt().jwt(j -> j.subject(String.valueOf(userId))).authorities(new SimpleGrantedAuthority("ROLE_" + role));
	}

	@Test
	void playersSeeOnlyTheirOwnProfile() throws Exception {
		when(service.get(7)).thenReturn(new UserProfile(7, "Ada", "ada@example.test", null, null, null, List.of(),
				Instant.EPOCH));

		mvc.perform(get("/api/v1/users/7").with(as(7, "USER"))).andExpect(status().isOk());
		mvc.perform(get("/api/v1/users/7").with(as(8, "USER"))).andExpect(status().isForbidden());
		mvc.perform(delete("/api/v1/users/7").with(as(8, "USER"))).andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/users/7")).andExpect(status().isUnauthorized());
	}

	@Test
	void onlyAdminsListOrSearchProfiles() throws Exception {
		mvc.perform(get("/api/v1/users").with(as(7, "USER"))).andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/users").param("email", "ada@example.test").with(as(7, "USER")))
				.andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/users").with(as(1, "ADMIN"))).andExpect(status().isOk());
	}
}
