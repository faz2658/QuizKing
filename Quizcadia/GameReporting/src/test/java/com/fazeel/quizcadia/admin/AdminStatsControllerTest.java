package com.fazeel.quizcadia.admin;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fazeel.quizcadia.config.SecurityConfig;

/** Retention covers every player, so only admins may read it. */
@Import(SecurityConfig.class)
@WebMvcTest(AdminStatsController.class)
class AdminStatsControllerTest {

	@Autowired
	MockMvc mvc;

	@MockitoBean
	ActivityStats stats;

	@MockitoBean
	com.fazeel.quizcadia.analytics.WebsiteAnalytics analytics;

	@Test
	void adminsSeeRetentionAndTheWeeksAreClamped() throws Exception {
		when(stats.retention(26)).thenReturn(new Retention.View(LocalDate.parse("2026-11-18"), 3,
				List.of(new Retention.DayN(1, 2, 1, 0.5)), List.of(), List.of()));

		mvc.perform(get("/api/v1/reports/admin/retention").param("weeks", "500")
				.with(jwt().jwt(j -> j.subject("1")).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.players").value(3))
				.andExpect(jsonPath("$.everyone[0].rate").value(0.5));
	}

	@Test
	void playersCannot() throws Exception {
		mvc.perform(get("/api/v1/reports/admin/retention")
				.with(jwt().jwt(j -> j.subject("7")).authorities(new SimpleGrantedAuthority("ROLE_USER"))))
				.andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/reports/admin/retention")).andExpect(status().isUnauthorized());
	}
}
