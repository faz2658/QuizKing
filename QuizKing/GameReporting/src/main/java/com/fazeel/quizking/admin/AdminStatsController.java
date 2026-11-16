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
	private final com.fazeel.quizking.analytics.WebsiteAnalytics analytics;

	public AdminStatsController(ActivityStats stats, com.fazeel.quizking.analytics.WebsiteAnalytics analytics) {
		this.stats = stats;
		this.analytics = analytics;
	}

	/** Google Analytics numbers (visitors, pages, countries, sources); a setup or access problem comes back as error. */
	@GetMapping("/analytics")
	public com.fazeel.quizking.analytics.WebsiteAnalytics.View analytics(@RequestParam(defaultValue = "28") int days) {
		int d = Math.max(1, Math.min(days, 90));
		try {
			return analytics.view(d);
		}
		catch (com.fazeel.quizking.analytics.WebsiteAnalytics.AnalyticsUnavailable e) {
			return new com.fazeel.quizking.analytics.WebsiteAnalytics.View(true, e.getMessage(), null, d, 0, null,
					java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(),
					java.util.List.of());
		}
	}

	@GetMapping("/stats")
	public ActivityStats.View stats(@RequestParam(defaultValue = "30") int days) {
		return stats.view(Math.max(1, Math.min(days, 90)));
	}
}
