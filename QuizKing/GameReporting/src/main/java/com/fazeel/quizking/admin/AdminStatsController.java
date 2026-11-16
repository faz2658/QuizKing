package com.fazeel.quizking.admin;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Activity numbers for admins (the security config limits everything not listed to ADMIN). */
@RestController
@RequestMapping("/api/v1/reports/admin")
public class AdminStatsController {

	private final ActivityStats stats;

	public AdminStatsController(ActivityStats stats) {
		this.stats = stats;
	}

	@GetMapping("/stats")
	public ActivityStats.View stats(@RequestParam(defaultValue = "30") int days) {
		return stats.view(Math.max(1, Math.min(days, 90)));
	}
}
