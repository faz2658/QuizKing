package com.fazeel.quizcadia.admin;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Activity numbers for admins (the security config limits everything not listed to ADMIN). */
@RestController
@RequestMapping("/api/v1/reports/admin")
public class AdminStatsController {

	private final ActivityStats stats;
	private final com.fazeel.quizcadia.analytics.WebsiteAnalytics analytics;

	public AdminStatsController(ActivityStats stats, com.fazeel.quizcadia.analytics.WebsiteAnalytics analytics) {
		this.stats = stats;
		this.analytics = analytics;
	}

	/** Google Analytics numbers (visitors, pages, countries, sources); a setup or access problem comes back as error. */
	@GetMapping("/analytics")
	public com.fazeel.quizcadia.analytics.WebsiteAnalytics.View analytics(@RequestParam(defaultValue = "28") int days) {
		int d = Math.max(1, Math.min(days, 90));
		try {
			return analytics.view(d);
		}
		catch (com.fazeel.quizcadia.analytics.WebsiteAnalytics.AnalyticsUnavailable e) {
			return new com.fazeel.quizcadia.analytics.WebsiteAnalytics.View(true, e.getMessage(), null, d, 0, null,
					java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(),
					java.util.List.of());
		}
	}

	/** Day-1/7/30 retention and weekly cohorts of new players ({@code weeks}: 4 to 26 start weeks). */
	@GetMapping("/retention")
	public Retention.View retention(@RequestParam(defaultValue = "8") int weeks) {
		return stats.retention(Math.max(4, Math.min(weeks, 26)));
	}

	@GetMapping("/stats")
	public ActivityStats.View stats(@RequestParam(defaultValue = "30") int days) {
		return stats.view(Math.max(1, Math.min(days, 90)));
	}
}
