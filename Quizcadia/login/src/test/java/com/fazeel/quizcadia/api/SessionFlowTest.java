package com.fazeel.quizcadia.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

import com.fazeel.quizcadia.account.AccountEvents;
import com.fazeel.quizcadia.mail.Mailer;
import com.fazeel.quizcadia.session.SessionCookie;

/** Short access tokens, the rotating refresh cookie, signing out, and what ends every sign-in of an account. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "quizcadia.public-url=https://quiz.example.test")
class SessionFlowTest {

	/** Starts at the real time (the JWT check uses it) and can be moved forward. */
	static class MovableClock extends Clock {
		Instant now = Instant.now();

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}
	}

	@TestConfiguration
	static class Clocks {
		@Bean
		@Primary
		MovableClock movableClock() {
			return new MovableClock();
		}
	}

	@LocalServerPort
	int port;

	@Autowired
	MovableClock clock;

	@MockitoBean
	Mailer mailer;

	@MockitoBean
	AccountEvents events;

	@BeforeEach
	void realTime() {
		clock.now = Instant.now();
	}

	RestClient http() {
		return RestClient.builder().baseUrl("http://localhost:" + port).defaultStatusHandler(HttpStatusCode::isError, (q, r) -> {
		}).build();
	}

	@SuppressWarnings("rawtypes")
	ResponseEntity<Map> post(String path, Object body, String refresh, String bearer) {
		return http().post().uri(path).contentType(MediaType.APPLICATION_JSON).headers(h -> {
			if (refresh != null) {
				h.add(HttpHeaders.COOKIE, SessionCookie.NAME + "=" + refresh);
			}
			if (bearer != null) {
				h.setBearerAuth(bearer);
			}
		}).body(body == null ? Map.of() : body).retrieve().toEntity(Map.class);
	}

	/** The refresh cookie's Set-Cookie header, or null. */
	static String setCookie(ResponseEntity<?> r) {
		List<String> all = r.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE);
		return all.stream().filter(c -> c.startsWith(SessionCookie.NAME + "=")).findFirst().orElse(null);
	}

	static String cookieValue(ResponseEntity<?> r) {
		String c = setCookie(r);
		assertThat(c).as("refresh cookie").isNotNull();
		return c.substring(SessionCookie.NAME.length() + 1, c.indexOf(';'));
	}

	String register(String name, String email) {
		return cookieValue(post("/api/v1/auth/register", Map.of("userName", name, "email", email, "password", "long enough"),
				null, null));
	}

	@Test
	void signingInSetsAnHttpOnlyStrictCookieAndAShortAccessToken() {
		var r = post("/api/v1/auth/register", Map.of("userName", "Ada", "email", "ada-s@example.test", "password", "long enough"),
				null, null);
		assertThat(r.getStatusCode().value()).isEqualTo(201);
		String c = setCookie(r);
		assertThat(c).contains("HttpOnly", "SameSite=Strict", "Path=/api/v1/auth", "Secure", "Max-Age=");
		Instant expires = Instant.parse((String) r.getBody().get("expiresAt"));
		assertThat(Duration.between(clock.now, expires)).isEqualTo(Duration.ofMinutes(15));
		Instant session = Instant.parse((String) r.getBody().get("sessionExpiresAt"));
		assertThat(Duration.between(clock.now, session)).isEqualTo(Duration.ofDays(30));
	}

	@Test
	@SuppressWarnings("unchecked")
	void refreshingSwapsTheCookieAndKeepsTheSamePlayer() {
		String first = register("Bea", "bea-s@example.test");
		var r = post("/api/v1/auth/refresh", null, first, null);
		assertThat(r.getStatusCode().value()).isEqualTo(200);
		assertThat((String) r.getBody().get("token")).isNotBlank();
		assertThat(((Map<String, Object>) r.getBody().get("user")).get("email")).isEqualTo("bea-s@example.test");
		String second = cookieValue(r);
		assertThat(second).isNotEqualTo(first);

		// the access token works on protected endpoints
		String access = (String) r.getBody().get("token");
		assertThat(http().get().uri("/api/v1/auth/me").headers(h -> h.setBearerAuth(access)).retrieve()
				.toBodilessEntity().getStatusCode().value()).isEqualTo(200);

		// a second tab sending the old cookie a moment later gets an access token but no new cookie
		var tab2 = post("/api/v1/auth/refresh", null, first, null);
		assertThat(tab2.getStatusCode().value()).isEqualTo(200);
		assertThat(setCookie(tab2)).isNull();
		assertThat(post("/api/v1/auth/refresh", null, second, null).getStatusCode().value()).isEqualTo(200);
	}

	@Test
	void aUsedTokenComingBackLaterEndsThatWholeSignIn() {
		String first = register("Cal", "cal-s@example.test");
		String second = cookieValue(post("/api/v1/auth/refresh", null, first, null));
		String other = cookieValue(post("/api/v1/auth/login", Map.of("email", "cal-s@example.test", "password", "long enough"),
				null, null));

		clock.now = clock.now.plusSeconds(31); // past the two-tabs grace: someone copied the first token
		var stolen = post("/api/v1/auth/refresh", null, first, null);
		assertThat(stolen.getStatusCode().value()).isEqualTo(401);
		assertThat(stolen.getBody().get("detail")).isEqualTo("Your session has ended. Please sign in again.");
		assertThat(setCookie(stolen)).contains("Max-Age=0");
		assertThat(post("/api/v1/auth/refresh", null, second, null).getStatusCode().value()).isEqualTo(401);
		// another browser of the same account is a different sign-in and keeps working
		assertThat(post("/api/v1/auth/refresh", null, other, null).getStatusCode().value()).isEqualTo(200);
	}

	@Test
	void signingOutEndsTheSignInAndRemovesTheCookie() {
		String token = register("Dee", "dee-s@example.test");
		var out = post("/api/v1/auth/logout", null, token, null);
		assertThat(out.getStatusCode().value()).isEqualTo(204);
		assertThat(setCookie(out)).contains("Max-Age=0");
		assertThat(post("/api/v1/auth/refresh", null, token, null).getStatusCode().value()).isEqualTo(401);
		// signing out without a cookie (or twice) still works
		assertThat(post("/api/v1/auth/logout", null, null, null).getStatusCode().value()).isEqualTo(204);
		assertThat(post("/api/v1/auth/logout", null, token, null).getStatusCode().value()).isEqualTo(204);
	}

	@Test
	void signOutEverywhereEndsEverySignInOfTheAccount() {
		String laptop = register("Eve", "eve-s@example.test");
		var phone = post("/api/v1/auth/login", Map.of("email", "eve-s@example.test", "password", "long enough"), null, null);
		String access = (String) phone.getBody().get("token");
		assertThat(post("/api/v1/auth/logout/all", null, cookieValue(phone), access).getStatusCode().value()).isEqualTo(204);
		assertThat(post("/api/v1/auth/refresh", null, laptop, null).getStatusCode().value()).isEqualTo(401);
		assertThat(post("/api/v1/auth/logout/all", null, null, null).getStatusCode().value()).isEqualTo(401);
	}

	@Test
	void aPasswordResetSignsTheAccountOutEverywhereElse() {
		String before = register("Fay", "fay-s@example.test");
		post("/api/v1/auth/password/forgot", Map.of("email", "fay-s@example.test"), null, null);
		ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
		verify(mailer).send(eq("fay-s@example.test"), anyString(), body.capture());
		Matcher m = Pattern.compile("token=([A-Za-z0-9_-]+)").matcher(body.getValue());
		assertThat(m.find()).isTrue();

		var reset = post("/api/v1/auth/password/reset", Map.of("token", m.group(1), "password", "new password"), null, null);
		assertThat(reset.getStatusCode().value()).isEqualTo(200);
		assertThat(post("/api/v1/auth/refresh", null, before, null).getStatusCode().value()).isEqualTo(401);
		assertThat(post("/api/v1/auth/refresh", null, cookieValue(reset), null).getStatusCode().value()).isEqualTo(200);
	}

	@Test
	@SuppressWarnings("unchecked")
	void guestsKeepTheirIdAndNameForADay() {
		var g = post("/api/v1/auth/guest", Map.of("userName", "Visitor"), null, null);
		Map<String, Object> user = (Map<String, Object>) g.getBody().get("user");
		String token = cookieValue(g);

		var r = post("/api/v1/auth/refresh", null, token, null);
		assertThat(r.getStatusCode().value()).isEqualTo(200);
		assertThat((Map<String, Object>) r.getBody().get("user")).containsEntry("userId", user.get("userId"))
				.containsEntry("userName", "Visitor").containsEntry("role", "GUEST");

		String next = cookieValue(r);
		clock.now = clock.now.plus(Duration.ofDays(1)).plusSeconds(1);
		assertThat(post("/api/v1/auth/refresh", null, next, null).getStatusCode().value()).isEqualTo(401);
	}

	@Test
	void anUnusedSignInEndsAfter30DaysAndNoneLastsPast90() {
		String idle = register("Gus", "gus-s@example.test");
		clock.now = clock.now.plus(Duration.ofDays(30)).plusSeconds(1);
		assertThat(post("/api/v1/auth/refresh", null, idle, null).getStatusCode().value()).isEqualTo(401);

		clock.now = Instant.now();
		String token = cookieValue(post("/api/v1/auth/login", Map.of("email", "gus-s@example.test", "password", "long enough"),
				null, null));
		for (int day = 20; day < 90; day += 20) { // used every 20 days: still signed in
			clock.now = Instant.now().plus(Duration.ofDays(day));
			token = cookieValue(post("/api/v1/auth/refresh", null, token, null));
		}
		clock.now = Instant.now().plus(Duration.ofDays(90)).plusSeconds(1);
		assertThat(post("/api/v1/auth/refresh", null, token, null).getStatusCode().value()).isEqualTo(401);
	}

	@Test
	void refreshingWithoutACookieIsA401WithAReadableDetail() {
		var r = post("/api/v1/auth/refresh", null, null, null);
		assertThat(r.getStatusCode().value()).isEqualTo(401);
		assertThat(r.getBody().get("detail")).isEqualTo("Your session has ended. Please sign in again.");
		assertThat(post("/api/v1/auth/refresh", null, "made-up", null).getStatusCode().value()).isEqualTo(401);
	}
}
