package com.fazeel.quizcadia.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.client.RestClient;

import com.fazeel.quizcadia.account.AccountRepository;

/** Full HTTP round trip through Spring Security, JPA (H2) and JWT. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthFlowTest {

	@LocalServerPort
	int port;

	@Autowired
	AccountRepository accounts;

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

	@Autowired
	JwtEncoder jwtEncoder;

	String signedToken(String issuer) {
		JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).subject("1").claim("roles", List.of("USER"))
				.issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
		return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
	}

	int getStatus(String path, String token) {
		return http().get().uri(path).header("Authorization", "Bearer " + token).retrieve().toBodilessEntity()
				.getStatusCode().value();
	}

	@Test
	void tokensFromBeforeTheRenameStillWork() {
		// a browser signed in before the rename sends its old token with every request, public ones included
		assertThat(getStatus("/api/v1/auth/providers", signedToken("quiz-king-login"))).isEqualTo(200);
		assertThat(getStatus("/api/v1/auth/providers", signedToken("someone-else"))).isEqualTo(401);
	}

	@Test
	@SuppressWarnings("unchecked")
	void registerLoginAndCallAProtectedEndpoint() {
		Map<String, Object> registered = postForMap("/api/v1/auth/register",
				Map.of("userName", "Ada", "email", " Ada@Example.test ", "password", "correct horse"));
		assertThat(((Map<String, Object>) registered.get("user")).get("email")).isEqualTo("ada@example.test");
		assertThat(accounts.findByEmailIgnoreCase("ada@example.test").orElseThrow().getPasswordHash())
				.startsWith("$2").doesNotContain("correct horse");

		Map<String, Object> login = postForMap("/api/v1/auth/login",
				Map.of("email", "ADA@example.test", "password", "correct horse"));
		String token = (String) login.get("token");

		Map<String, Object> me = http().get().uri("/api/v1/auth/me").header("Authorization", "Bearer " + token)
				.retrieve().body(Map.class);
		assertThat(me.get("userName")).isEqualTo("Ada");
		assertThat(me.get("role")).isEqualTo("USER");
	}

	@Test
	void passwordGuessingLocksThatEmailOnly() {
		post("/api/v1/auth/register", Map.of("userName", "Cleo", "email", "cleo@example.test", "password", "right-password-1"));
		post("/api/v1/auth/register", Map.of("userName", "Dan", "email", "dan@example.test", "password", "right-password-2"));
		for (int i = 0; i < 10; i++) {
			assertThat(post("/api/v1/auth/login", Map.of("email", "cleo@example.test", "password", "guess-" + i))).isEqualTo(401);
		}
		// locked now, even with the right password, and whatever capitals are used. (A plain JDK client: the default
		// one would honour Retry-After and wait out the whole lock before returning.)
		var noRetry = RestClient.builder().baseUrl("http://localhost:" + port)
				.requestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory())
				.defaultStatusHandler(HttpStatusCode::isError, (req, res) -> {
				}).build();
		var locked = noRetry.post().uri("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("email", "Cleo@Example.test", "password", "right-password-1")).retrieve().toBodilessEntity();
		assertThat(locked.getStatusCode().value()).isEqualTo(429);
		assertThat(locked.getHeaders().getFirst("Retry-After")).isNotBlank();
		// other accounts are unaffected
		assertThat(post("/api/v1/auth/login", Map.of("email", "dan@example.test", "password", "right-password-2"))).isEqualTo(200);
	}

	@Test
	void wrongPasswordAndUnknownEmailAreBoth401() {
		post("/api/v1/auth/register", Map.of("userName", "Bob", "email", "bob@example.test", "password", "password123"));

		assertThat(post("/api/v1/auth/login", Map.of("email", "bob@example.test", "password", "nope-nope")))
				.isEqualTo(401);
		assertThat(post("/api/v1/auth/login", Map.of("email", "nobody@example.test", "password", "password123")))
				.isEqualTo(401);
	}

	@Test
	void duplicateEmailIsAConflict() {
		post("/api/v1/auth/register", Map.of("userName", "Cy", "email", "cy@example.test", "password", "password123"));

		assertThat(post("/api/v1/auth/register",
				Map.of("userName", "Cy2", "email", "CY@example.test", "password", "password123"))).isEqualTo(409);
	}

	@Test
	void weakOrMissingInputIsRejected() {
		assertThat(post("/api/v1/auth/register", Map.of("userName", "D", "email", "d@example.test", "password", "short")))
				.isEqualTo(400);
		assertThat(post("/api/v1/auth/register", Map.of("userName", "D", "email", "not-an-email", "password",
				"password123"))).isEqualTo(400);
	}

	@Test
	void protectedEndpointsNeedAValidToken() {
		assertThat(http().get().uri("/api/v1/auth/me").retrieve().toBodilessEntity().getStatusCode().value())
				.isEqualTo(401);
		assertThat(http().get().uri("/api/v1/auth/me").header("Authorization", "Bearer forged.token.value").retrieve()
				.toBodilessEntity().getStatusCode().value()).isEqualTo(401);
	}

	@Test
	@SuppressWarnings("unchecked")
	void configuredAdminEmailGetsTheAdminRole() {
		Map<String, Object> r = postForMap("/api/v1/auth/register",
				Map.of("userName", "Boss", "email", "boss@quizcadia.test", "password", "password123"));

		assertThat(((Map<String, Object>) r.get("user")).get("role")).isEqualTo("ADMIN");
	}

	@Autowired
	org.springframework.security.crypto.password.PasswordEncoder encoder;

	@Test
	@SuppressWarnings("unchecked")
	void onlyAdminsSeeAccountStats() {
		accounts.saveAndFlush(new com.fazeel.quizcadia.account.Account("Stats", "stats-admin@quizcadia.test",
				encoder.encode("password123"), com.fazeel.quizcadia.account.Account.Role.ADMIN, java.time.Instant.now()));
		String admin = (String) postForMap("/api/v1/auth/login",
				Map.of("email", "stats-admin@quizcadia.test", "password", "password123")).get("token");
		String player = (String) postForMap("/api/v1/auth/register",
				Map.of("userName", "Pat", "email", "pat-stats@example.test", "password", "password123")).get("token");

		var asPlayer = http().get().uri("/api/v1/auth/admin/stats").header("Authorization", "Bearer " + player)
				.retrieve().toBodilessEntity();
		assertThat(asPlayer.getStatusCode().value()).isEqualTo(403);
		assertThat(http().get().uri("/api/v1/auth/admin/stats").retrieve().toBodilessEntity().getStatusCode().value())
				.isEqualTo(401);

		Map<String, Object> stats = http().get().uri("/api/v1/auth/admin/stats?days=7")
				.header("Authorization", "Bearer " + admin).retrieve().body(Map.class);
		assertThat((Integer) stats.get("total")).isGreaterThanOrEqualTo(2);
		assertThat((Integer) stats.get("today")).isGreaterThanOrEqualTo(2);
		assertThat((java.util.List<?>) stats.get("signupsByDay")).hasSize(7);
		assertThat(stats.get("recent").toString()).contains("pat-stats@example.test");
	}

	@Test
	void providersListsTheAnalyticsIdOnlyWhenItLooksRight() {
		assertThat(http().get().uri("/api/v1/auth/providers").retrieve().body(Map.class)).containsKey("analyticsId");
	}

	@Test
	void passwordResetIsOffUntilEmailIsSetUp() {
		// the test configuration has no MAIL_HOST, so reset links can't be emailed and the app hides the option
		assertThat(http().get().uri("/api/v1/auth/providers").retrieve().body(Map.class)).containsEntry("passwordReset", false);
	}

	@Test
	@SuppressWarnings("unchecked")
	void meDescribesAGuestFromTheirToken() {
		String token = (String) postForMap("/api/v1/auth/guest", Map.of("userName", "Visitor")).get("token");
		Map<String, Object> me = http().get().uri("/api/v1/auth/me").header("Authorization", "Bearer " + token)
				.retrieve().body(Map.class);
		assertThat(me.get("userName")).isEqualTo("Visitor");
		assertThat(me.get("role")).isEqualTo("GUEST");
		assertThat((Integer) me.get("userId")).isGreaterThanOrEqualTo(AuthController.GUEST_ID_BASE);
	}

	@Test
	@SuppressWarnings("unchecked")
	void guestsGetATokenButNoAccount() {
		long before = accounts.count();
		Map<String, Object> guest = postForMap("/api/v1/auth/guest", Map.of("userName", "  Visitor  "));
		Map<String, Object> user = (Map<String, Object>) guest.get("user");

		assertThat((String) guest.get("token")).isNotBlank();
		assertThat(user.get("role")).isEqualTo("GUEST");
		assertThat(user.get("userName")).isEqualTo("Visitor");
		assertThat((Integer) user.get("userId")).isGreaterThanOrEqualTo(AuthController.GUEST_ID_BASE);
		assertThat(accounts.count()).isEqualTo(before);
		assertThat(post("/api/v1/auth/guest", Map.of("userName", " "))).isEqualTo(400);
	}
}
