package com.fazeel.quizcadia.api;

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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.fazeel.quizcadia.config.SecurityConfig;
import com.fazeel.quizcadia.progress.ProgressService;
import com.fazeel.quizcadia.report.GameReport;
import com.fazeel.quizcadia.report.GameReport.GameMode;
import com.fazeel.quizcadia.report.PlayerNames;
import com.fazeel.quizcadia.report.ReportService;

@Import(SecurityConfig.class)
@WebMvcTest(ReportController.class)
class ReportControllerTest {

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
	ReportService service;

	@MockitoBean
	ProgressService progress;

	@MockitoBean
	PlayerNames names;

	@Test
	void listsReportsOfOneMode() throws Exception {
		when(service.reports(1, GameMode.MULTI)).thenReturn(List.of(new GameReport("x", GameMode.MULTI, 1, 2, "Race",
				3, 20, null, 2, 2, 1, 2, null, Instant.EPOCH, List.of())));

		mvc.perform(get("/api/v1/reports/users/1").with(player(1)).param("mode", "MULTI")).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].rank").value(1)).andExpect(jsonPath("$[0].gameName").value("Race"));
	}

	@Test
	void reportsAreForTheirOwnerOnly() throws Exception {
		mvc.perform(get("/api/v1/reports/users/1").with(player(2))).andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/reports/users/1/summary").with(player(2))).andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/reports/users/1")).andExpect(status().isUnauthorized());
	}
}
