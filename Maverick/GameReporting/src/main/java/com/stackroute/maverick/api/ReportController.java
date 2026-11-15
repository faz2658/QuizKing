package com.stackroute.maverick.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.stackroute.maverick.report.GameReport;
import com.stackroute.maverick.report.GameReport.GameMode;
import com.stackroute.maverick.report.ReportService;
import com.stackroute.maverick.report.ReportService.UserSummary;

@RestController
@RequestMapping("/api/v1/reports/users/{userId}")
public class ReportController {

	private final ReportService service;

	public ReportController(ReportService service) {
		this.service = service;
	}

	/** Newest first, optionally only one mode (SINGLE, MULTI or ADAPTIVE). */
	@GetMapping
	public List<GameReport> reports(@PathVariable int userId, @RequestParam(required = false) GameMode mode) {
		return service.reports(userId, mode);
	}

	@GetMapping("/summary")
	public UserSummary summary(@PathVariable int userId) {
		return service.summary(userId);
	}
}
