package com.fazeel.quizcadia.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fazeel.quizcadia.progress.Achievements.Achievement;
import com.fazeel.quizcadia.progress.ProgressService;
import com.fazeel.quizcadia.progress.ProgressService.Progress;
import com.fazeel.quizcadia.report.GameReport;
import com.fazeel.quizcadia.report.GameReport.GameMode;
import com.fazeel.quizcadia.report.ReportService;
import com.fazeel.quizcadia.report.ReportService.UserSummary;

@RestController
@RequestMapping("/api/v1/reports/users/{userId}")
public class ReportController {

	private final ReportService service;
	private final ProgressService progress;

	private final com.fazeel.quizcadia.report.PlayerNames names;

	public ReportController(ReportService service, ProgressService progress,
			com.fazeel.quizcadia.report.PlayerNames names) {
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
		if (userId == com.fazeel.quizcadia.auth.Callers.currentUserId()) {
			names.remember(userId, com.fazeel.quizcadia.auth.Callers.currentUserName());
		}
		return progress.progress(userId);
	}

	/** Every badge, earned or not. */
	@GetMapping("/achievements")
	public List<Achievement> achievements(@PathVariable int userId) {
		return progress.achievements(userId);
	}

	@GetMapping("/summary")
	public UserSummary summary(@PathVariable int userId) {
		return service.summary(userId);
	}
}
