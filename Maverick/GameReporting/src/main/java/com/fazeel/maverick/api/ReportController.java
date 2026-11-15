package com.fazeel.maverick.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fazeel.maverick.progress.ProgressService;
import com.fazeel.maverick.progress.ProgressService.Progress;
import com.fazeel.maverick.report.GameReport;
import com.fazeel.maverick.report.GameReport.GameMode;
import com.fazeel.maverick.report.ReportService;
import com.fazeel.maverick.report.ReportService.UserSummary;

@RestController
@RequestMapping("/api/v1/reports/users/{userId}")
public class ReportController {

	private final ReportService service;
	private final ProgressService progress;

	private final com.fazeel.maverick.report.PlayerNames names;

	public ReportController(ReportService service, ProgressService progress,
			com.fazeel.maverick.report.PlayerNames names) {
		this.service = service;
		this.progress = progress;
		this.names = names;
	}

	/** Newest first, optionally only one mode (SINGLE, MULTI or ADAPTIVE). */
	@GetMapping
	public List<GameReport> reports(@PathVariable int userId, @RequestParam(required = false) GameMode mode) {
		return service.reports(userId, mode);
	}

	/** XP, level and streak. */
	@GetMapping("/progress")
	public Progress progress(@PathVariable int userId) {
		if (userId == com.fazeel.maverick.auth.Callers.currentUserId()) {
			names.remember(userId, com.fazeel.maverick.auth.Callers.currentUserName());
		}
		return progress.progress(userId);
	}

	@GetMapping("/summary")
	public UserSummary summary(@PathVariable int userId) {
		return service.summary(userId);
	}
}
