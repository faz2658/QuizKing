package com.fazeel.quizcadia.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

import com.fazeel.quizcadia.account.AccountEvents;
import com.fazeel.quizcadia.account.AccountRepository;
import com.fazeel.quizcadia.account.AccountService;
import com.fazeel.quizcadia.account.UserRegistered;
import com.fazeel.quizcadia.google.GoogleIdentity;
import com.fazeel.quizcadia.google.GoogleSignInException;
import com.fazeel.quizcadia.google.GoogleTokenVerifier;
import com.fazeel.quizcadia.mail.Mailer;

/** Forgot/reset password and "Sign in with Google", over HTTP. Email and Google's token check are stubbed. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = { "quizcadia.public-url=https://quiz.example.test/", "quizcadia.admin-emails=chief@quizcadia.test" })
class RecoveryAndGoogleFlowTest {

	@LocalServerPort
	int port;

	@Autowired
	AccountRepository accounts;

	@MockitoBean
	Mailer mailer;

	@MockitoBean
	GoogleTokenVerifier google;

	@MockitoBean
	AccountEvents events;

	RestClient http() {
		return RestClient.builder().baseUrl("http://localhost:" + port)
				.defaultStatusHandler(HttpStatusCode::isError, (req, res) -> {
				}).build();
	}

	int post(String path, Object body) {
		return http().post().uri(path).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toBodilessEntity()
				.getStatusCode().value();
	}

	@SuppressWarnings("unchecked")
	Map<String, Object> postForMap(String path, Object body) {
		return http().post().uri(path).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(Map.class);
	}

	String emailedToken(String to) {
		ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
		verify(mailer).send(eq(to), anyString(), body.capture());
		Matcher m = Pattern.compile("https://quiz\\.example\\.test/reset-password\\?token=([A-Za-z0-9_-]+)")
				.matcher(body.getValue());
		assertThat(m.find()).as("reset link in: " + body.getValue()).isTrue();
		return m.group(1);
	}

	@Test
	void forgotPasswordEmailsALinkThatWorksOnce() {
		post("/api/v1/auth/register", Map.of("userName", "Rae", "email", "rae@example.test", "password", "old-password"));

		assertThat(post("/api/v1/auth/password/forgot", Map.of("email", " RAE@example.test "))).isEqualTo(202);
		String token = emailedToken("rae@example.test");

		Map<String, Object> reset = postForMap("/api/v1/auth/password/reset", Map.of("token", token, "password", "new-password"));
		assertThat((String) reset.get("token")).isNotBlank();
		assertThat(post("/api/v1/auth/login", Map.of("email", "rae@example.test", "password", "new-password"))).isEqualTo(200);
		assertThat(post("/api/v1/auth/login", Map.of("email", "rae@example.test", "password", "old-password"))).isEqualTo(401);

		assertThat(post("/api/v1/auth/password/reset", Map.of("token", token, "password", "third-password"))).isEqualTo(400);
		assertThat(accounts.findByEmailIgnoreCase("rae@example.test").orElseThrow().isEmailVerified()).isTrue();
	}

	@Test
	void unknownEmailsLookTheSameAndSendNothing() {
		assertThat(post("/api/v1/auth/password/forgot", Map.of("email", "nobody@example.test"))).isEqualTo(202);
		verify(mailer, never()).send(anyString(), anyString(), anyString());
	}

	@Test
	void resetEmailsAreLimitedPerHourAndBadTokensFail() {
		post("/api/v1/auth/register", Map.of("userName", "Sol", "email", "sol@example.test", "password", "password123"));
		for (int i = 0; i < 5; i++) {
			post("/api/v1/auth/password/forgot", Map.of("email", "sol@example.test"));
		}
		verify(mailer, times(3)).send(eq("sol@example.test"), anyString(), anyString());

		assertThat(post("/api/v1/auth/password/reset", Map.of("token", "made-up", "password", "password123"))).isEqualTo(400);
		assertThat(post("/api/v1/auth/password/reset", Map.of("token", "x", "password", "short"))).isEqualTo(400);
	}

	@Test
	@SuppressWarnings("unchecked")
	void googleCreatesAnAccountOnceAndSignsInAfterwards() {
		when(google.verify("cred-1")).thenReturn(new GoogleIdentity("g-111", "Gail@Example.test", true, "Gail G"));

		Map<String, Object> first = postForMap("/api/v1/auth/google", Map.of("credential", "cred-1"));
		Map<String, Object> user = (Map<String, Object>) first.get("user");
		assertThat(user.get("email")).isEqualTo("gail@example.test");
		assertThat(user.get("userName")).isEqualTo("Gail G");
		long count = accounts.count();

		Map<String, Object> again = postForMap("/api/v1/auth/google", Map.of("credential", "cred-1"));
		assertThat(((Map<String, Object>) again.get("user")).get("userId")).isEqualTo(user.get("userId"));
		assertThat(accounts.count()).isEqualTo(count);
		// a Google-only account has no password anyone could know
		assertThat(post("/api/v1/auth/login", Map.of("email", "gail@example.test", "password", ""))).isEqualTo(400);
	}

	@Test
	@SuppressWarnings("unchecked")
	void googleLinksAnUnverifiedPasswordAccountAndDropsItsPassword() {
		Map<String, Object> reg = postForMap("/api/v1/auth/register",
				Map.of("userName", "Hal", "email", "hal@example.test", "password", "squatter-pass"));
		when(google.verify("cred-2")).thenReturn(new GoogleIdentity("g-222", "hal@example.test", true, "Hal"));

		Map<String, Object> signedIn = postForMap("/api/v1/auth/google", Map.of("credential", "cred-2"));
		assertThat(((Map<String, Object>) signedIn.get("user")).get("userId"))
				.isEqualTo(((Map<String, Object>) reg.get("user")).get("userId"));
		assertThat(post("/api/v1/auth/login", Map.of("email", "hal@example.test", "password", "squatter-pass"))).isEqualTo(401);
	}

	@Test
	void googleKeepsTheVerifiedAccountsPasswordAndAdminRole() {
		post("/api/v1/auth/register", Map.of("userName", "Chief", "email", "chief@quizcadia.test", "password", "chief-password"));
		post("/api/v1/auth/password/forgot", Map.of("email", "chief@quizcadia.test"));
		post("/api/v1/auth/password/reset", Map.of("token", emailedToken("chief@quizcadia.test"), "password", "chief-password-2"));
		when(google.verify("cred-3")).thenReturn(new GoogleIdentity("g-333", "chief@quizcadia.test", true, "Chief"));

		assertThat(postForMap("/api/v1/auth/google", Map.of("credential", "cred-3")).get("user").toString()).contains("ADMIN");
		assertThat(post("/api/v1/auth/login", Map.of("email", "chief@quizcadia.test", "password", "chief-password-2"))).isEqualTo(200);
	}

	@Test
	void unverifiedGoogleEmailsAndBadTokensAreRefused() {
		when(google.verify("cred-4")).thenReturn(new GoogleIdentity("g-444", "ivy@example.test", false, "Ivy"));
		when(google.verify("forged")).thenThrow(new GoogleSignInException("Google sign-in failed, please try again"));

		assertThat(post("/api/v1/auth/google", Map.of("credential", "cred-4"))).isEqualTo(401);
		assertThat(post("/api/v1/auth/google", Map.of("credential", "forged"))).isEqualTo(401);
		assertThat(accounts.findByEmailIgnoreCase("ivy@example.test")).isEmpty();
	}

	@Test
	@SuppressWarnings("unchecked")
	void providersTellTheBrowserWhetherGoogleIsOn() {
		when(google.enabled()).thenReturn(true);
		when(google.clientId()).thenReturn("123-abc.apps.googleusercontent.com");

		Map<String, Object> p = http().get().uri("/api/v1/auth/providers").retrieve().body(Map.class);
		assertThat(p.get("googleClientId")).isEqualTo("123-abc.apps.googleusercontent.com");
	}

	@Test
	@SuppressWarnings("unchecked")
	void anInviteCodeNamesTheFriendWhoSentTheLink() {
		Map<String, Object> host = postForMap("/api/v1/auth/register",
				Map.of("userName", "Host", "email", "host@example.test", "password", "password123"));
		int hostId = (Integer) ((Map<String, Object>) host.get("user")).get("userId");
		String code = AccountService.inviteCode(hostId);

		postForMap("/api/v1/auth/register", Map.of("userName", "Guest", "email", "invited@example.test", "password",
				"password123", "invite", code));
		postForMap("/api/v1/auth/register", Map.of("userName", "Other", "email", "bogus@example.test", "password",
				"password123", "invite", "zzzzzz"));

		ArgumentCaptor<UserRegistered> sent = ArgumentCaptor.forClass(UserRegistered.class);
		verify(events, times(3)).registered(sent.capture());
		assertThat(sent.getAllValues()).extracting(UserRegistered::referredBy)
				.containsExactly(null, hostId, null);
	}

	/** Google's redirect sign-in: a form post from accounts.google.com with its double-submit token. */
	ResponseEntity<Void> googleForm(String credential, String formToken, String cookies) {
		return googleForm(credential, formToken, cookies, "https://accounts.google.com");
	}

	ResponseEntity<Void> googleForm(String credential, String formToken, String cookies, String origin) {
		LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		if (credential != null) form.add("credential", credential);
		if (formToken != null) form.add("g_csrf_token", formToken);
		// a client that shows the 303 instead of following it to the app (which this service doesn't serve)
		RestClient noRedirects = RestClient.builder().baseUrl("http://localhost:" + port)
				.requestFactory(new JdkClientHttpRequestFactory(HttpClient.newBuilder()
						.followRedirects(HttpClient.Redirect.NEVER).build()))
				.defaultStatusHandler(HttpStatusCode::isError, (rq, rs) -> {
				}).build();
		var req = noRedirects.post().uri("/api/v1/auth/google/redirect").contentType(MediaType.APPLICATION_FORM_URLENCODED);
		if (origin != null) req = req.header("Origin", origin);
		if (cookies != null) req = req.header("Cookie", cookies);
		return req.body(form).retrieve().toBodilessEntity();
	}

	@Test
	void googleRedirectSignsInWithTheRefreshCookieAndGoesBackToTheApp() {
		when(google.verify("cred-r")).thenReturn(new GoogleIdentity("g-redirect-rio", "rio@example.test", true, "Rio R"));
		var res = googleForm("cred-r", "tok-123", "g_csrf_token=tok-123");

		assertThat(res.getStatusCode().value()).isEqualTo(303);
		assertThat(res.getHeaders().getLocation()).hasToString("/welcome?google=ok");
		assertThat(res.getHeaders().get("Set-Cookie")).anyMatch(c -> c.startsWith("qc_refresh=") && !c.startsWith("qc_refresh=;"));
		// the app then swaps that cookie for a session, as after any sign-in
		String refresh = res.getHeaders().get("Set-Cookie").stream().filter(c -> c.startsWith("qc_refresh=")).findFirst()
				.orElseThrow().split(";", 2)[0];
		@SuppressWarnings("unchecked")
		Map<String, Object> session = http().post().uri("/api/v1/auth/refresh").header("Cookie", refresh)
				.contentType(MediaType.APPLICATION_JSON).body(Map.of()).retrieve().body(Map.class);
		assertThat(((Map<String, Object>) session.get("user")).get("email")).isEqualTo("rio@example.test");
	}

	@Test
	void googleRedirectRefusesAMissingOrForgedAntiForgeryToken() {
		when(google.verify("cred-x")).thenReturn(new GoogleIdentity("g-redirect-eve", "eve@example.test", true, "Eve"));
		long before = accounts.count();

		for (var res : List.of(googleForm("cred-x", "tok-a", "g_csrf_token=tok-b"), googleForm("cred-x", "tok-a", null),
				googleForm("cred-x", null, "g_csrf_token=tok-a"), googleForm(null, "tok-a", "g_csrf_token=tok-a"))) {
			assertThat(res.getStatusCode().value()).isEqualTo(303);
			assertThat(res.getHeaders().getLocation()).hasToString("/welcome?google=failed");
			assertThat(res.getHeaders().get("Set-Cookie")).noneMatch(c -> c.startsWith("qc_refresh=") && !c.startsWith("qc_refresh=;"));
		}
		assertThat(accounts.count()).isEqualTo(before);
	}

	@Test
	void googleRedirectWithABadTokenGoesBackWithAFailure() {
		when(google.verify("bad")).thenThrow(new GoogleSignInException("Google sign-in failed, please try again"));

		var res = googleForm("bad", "t", "g_csrf_token=t");

		assertThat(res.getHeaders().getLocation()).hasToString("/welcome?google=failed");
	}

	@Test
	void googleRedirectWorksWhateverOriginSafariSends() {
		// Safari posts Google's form with "Origin: null"; that used to be refused as "Invalid CORS request"
		when(google.verify("cred-n")).thenReturn(new GoogleIdentity("g-redirect-nia", "nia@example.test", true, "Nia"));

		for (String origin : new String[] { "null", null, "https://accounts.google.com" }) {
			var res = googleForm("cred-n", "t", "g_csrf_token=t", origin);
			assertThat(res.getStatusCode().value()).as("Origin %s", origin).isEqualTo(303);
			assertThat(res.getHeaders().getLocation()).as("Origin %s", origin).hasToString("/welcome?google=ok");
		}
	}

	@Test
	void theRestOfTheApiStillRefusesOtherSites() {
		int status = http().post().uri("/api/v1/auth/login").header("Origin", "https://evil.example")
				.contentType(MediaType.APPLICATION_JSON).body(Map.of("email", "a@b.test", "password", "x")).retrieve()
				.toBodilessEntity().getStatusCode().value();
		assertThat(status).isEqualTo(403);
	}
}
