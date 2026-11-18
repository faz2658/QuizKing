package com.fazeel.quizcadia.analytics;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fazeel.quizcadia.board.BoardCache;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.fazeel.quizcadia.web.InternalHttp;

/**
 * Website analytics for the admin page, read from the Google Analytics 4 Data API with a read-only service account.
 * Off (configured = false) until GA_PROPERTY_ID and GA_CREDENTIALS_FILE are set. Results are kept for 5 minutes:
 * the API has daily quotas and GA's numbers only move every few minutes anyway.
 */
@Service
public class WebsiteAnalytics {

	private static final Logger log = LoggerFactory.getLogger(WebsiteAnalytics.class);
	static final Duration TTL = Duration.ofMinutes(5);
	static final String API = "https://analyticsdata.googleapis.com/v1beta/properties/";

	public record Totals(long activeUsers, long newUsers, long sessions, long pageViews, double avgSessionSeconds,
			double engagementRate) {
	}

	public record Day(LocalDate day, long activeUsers, long pageViews) {
	}

	public record Row(String name, long value) {
	}

	public record View(boolean configured, String error, String propertyId, int days, long activeNow, Totals totals,
			List<Day> byDay, List<Row> pages, List<Row> countries, List<Row> devices, List<Row> channels,
			List<Row> events) {

		static View off(String why) {
			return new View(false, why, null, 0, 0, null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
		}
	}

	private final String propertyId;
	private final GoogleCredentials credentials;
	private final String setupProblem;
	private final RestClient http;
	private final BoardCache cache;
	private final ObjectMapper json = new ObjectMapper();
	private final Clock clock;

	@org.springframework.beans.factory.annotation.Autowired
	public WebsiteAnalytics(@Value("${quizcadia.analytics.property-id:}") String propertyId,
			@Value("${quizcadia.analytics.credentials-file:}") String credentialsFile, BoardCache cache, Clock clock) {
		this(propertyId, readKey(credentialsFile),
				RestClient.builder().requestFactory(InternalHttp.requestFactory()).build(), cache, clock);
	}

	WebsiteAnalytics(String propertyId, String keyJson, RestClient http, BoardCache cache, Clock clock) {
		this.http = http;
		this.cache = cache;
		this.clock = clock;
		String id = propertyId == null ? "" : propertyId.trim().replaceFirst("^properties/", "");
		GoogleCredentials creds = null;
		String problem = null;
		if (!id.matches("\\d{4,15}")) {
			problem = "Set GA_PROPERTY_ID to the property's number (Admin > Property details in Google Analytics).";
		}
		else if (keyJson == null) {
			problem = "Set GA_CREDENTIALS_FILE to the service account's JSON key file.";
		}
		else {
			try {
				creds = new GoogleCredentials(keyJson, http, clock);
			}
			catch (RuntimeException e) {
				problem = "The service account key can't be used: " + e.getMessage();
			}
		}
		this.propertyId = id;
		this.credentials = creds;
		this.setupProblem = problem;
		if (problem != null && !id.isEmpty()) {
			log.warn("Website analytics is off: {}", problem);
		}
	}

	private static String readKey(String file) {
		if (file == null || file.isBlank()) {
			return null;
		}
		try {
			return Files.readString(Path.of(file));
		}
		catch (java.io.IOException e) {
			log.warn("Can't read the Google Analytics key file {}: {}", file, e.getMessage());
			return null;
		}
	}

	/** The service account's email, which must be added as a Viewer on the GA property; null when not set up. */
	public String serviceAccount() {
		return credentials == null ? null : credentials.clientEmail();
	}

	public View view(int days) {
		if (credentials == null) {
			return View.off(setupProblem);
		}
		return cache.get("ga:" + days, TTL, () -> {
			try {
				return fetch(days);
			}
			catch (RestClientException | IllegalStateException e) {
				log.warn("Google Analytics request failed: {}", e.getMessage());
				String hint = e.getMessage() != null && e.getMessage().contains("403")
						? "Google refused access. Add " + credentials.clientEmail()
								+ " as a Viewer in Google Analytics (Admin > Property access management)."
						: "Google Analytics didn't answer: " + e.getMessage();
				throw new AnalyticsUnavailable(hint);
			}
		});
	}

	/** Thrown (and not cached) when Google can't be reached or refuses; the controller turns it into a message. */
	public static class AnalyticsUnavailable extends RuntimeException {
		public AnalyticsUnavailable(String message) {
			super(message);
		}
	}

	private View fetch(int days) {
		Map<String, Object> range = Map.of("startDate", days + "daysAgo", "endDate", "today");
		JsonNode totals = report(Map.of("dateRanges", List.of(range), "metrics",
				metrics("activeUsers", "newUsers", "sessions", "screenPageViews", "averageSessionDuration", "engagementRate")));
		JsonNode t = totals.path("rows").path(0).path("metricValues");
		Totals sum = new Totals(lng(t.path(0)), lng(t.path(1)), lng(t.path(2)), lng(t.path(3)), dbl(t.path(4)), dbl(t.path(5)));

		JsonNode daily = report(Map.of("dateRanges", List.of(range), "dimensions", dims("date"), "metrics",
				metrics("activeUsers", "screenPageViews"), "orderBys", List.of(Map.of("dimension", Map.of("dimensionName", "date")))));
		List<Day> byDay = new ArrayList<>();
		for (JsonNode r : daily.path("rows")) {
			String d = r.path("dimensionValues").path(0).path("value").asString("");
			if (d.matches("\\d{8}")) {
				byDay.add(new Day(LocalDate.of(Integer.parseInt(d.substring(0, 4)), Integer.parseInt(d.substring(4, 6)),
						Integer.parseInt(d.substring(6))), lng(r.path("metricValues").path(0)), lng(r.path("metricValues").path(1))));
			}
		}

		List<Row> pages = top(range, "pagePath", "screenPageViews", 10);
		List<Row> countries = top(range, "country", "activeUsers", 10);
		List<Row> devices = top(range, "deviceCategory", "activeUsers", 5);
		List<Row> channels = top(range, "sessionDefaultChannelGroup", "sessions", 8);
		List<Row> events = top(range, "eventName", "eventCount", 10);

		JsonNode realtime = call(API + propertyId + ":runRealtimeReport", Map.of("metrics", metrics("activeUsers")));
		long activeNow = lng(realtime.path("rows").path(0).path("metricValues").path(0));
		return new View(true, null, propertyId, days, activeNow, sum, byDay, pages, countries, devices, channels, events);
	}

	private List<Row> top(Map<String, Object> range, String dimension, String metric, int limit) {
		JsonNode r = report(Map.of("dateRanges", List.of(range), "dimensions", dims(dimension), "metrics", metrics(metric),
				"orderBys", List.of(Map.of("metric", Map.of("metricName", metric), "desc", true)), "limit", limit));
		List<Row> rows = new ArrayList<>();
		for (JsonNode row : r.path("rows")) {
			rows.add(new Row(row.path("dimensionValues").path(0).path("value").asString(""), lng(row.path("metricValues").path(0))));
		}
		return rows;
	}

	private JsonNode report(Map<String, Object> body) {
		return call(API + propertyId + ":runReport", body);
	}

	private JsonNode call(String url, Map<String, Object> body) {
		String raw = http.post().uri(url).header("Authorization", "Bearer " + credentials.accessToken())
				.contentType(MediaType.APPLICATION_JSON).body(json.writeValueAsString(body)).retrieve().body(String.class);
		return json.readTree(raw == null ? "{}" : raw);
	}

	private static List<Map<String, String>> metrics(String... names) {
		return java.util.Arrays.stream(names).map(n -> Map.of("name", n)).toList();
	}

	private static List<Map<String, String>> dims(String... names) {
		return java.util.Arrays.stream(names).map(n -> Map.of("name", n)).toList();
	}

	private static long lng(JsonNode v) {
		return Math.round(dbl(v));
	}

	private static double dbl(JsonNode v) {
		String s = v.path("value").asString("0");
		try {
			return Double.parseDouble(s);
		}
		catch (NumberFormatException e) {
			return 0;
		}
	}
}
