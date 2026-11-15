package com.fazeel.maverick.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import com.fazeel.maverick.account.AccountRepository;

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
				Map.of("userName", "Boss", "email", "boss@maverick.test", "password", "password123"));

		assertThat(((Map<String, Object>) r.get("user")).get("role")).isEqualTo("ADMIN");
	}
}
