package com.stackroute.maverick.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.stackroute.maverick.report.GameReport;
import com.stackroute.maverick.report.GameReport.GameMode;
import com.stackroute.maverick.report.ReportService;

@WebMvcTest(ReportController.class)
class ReportControllerTest {

	@Autowired
	MockMvc mvc;

	@MockitoBean
	ReportService service;

	@Test
	void listsReportsOfOneMode() throws Exception {
		when(service.reports(1, GameMode.MULTI)).thenReturn(List.of(new GameReport("x", GameMode.MULTI, 1, 2, "Race",
				3, 20, null, 2, 2, 1, 2, null, Instant.EPOCH, List.of())));

		mvc.perform(get("/api/v1/reports/users/1").param("mode", "MULTI")).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].rank").value(1)).andExpect(jsonPath("$[0].gameName").value("Race"));
	}
}
