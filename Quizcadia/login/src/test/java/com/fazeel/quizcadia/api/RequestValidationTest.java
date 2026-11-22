package com.fazeel.quizcadia.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import com.fazeel.quizcadia.account.AccountRepository;
import com.fazeel.quizcadia.api.AuthController.ForgotRequest;
import com.fazeel.quizcadia.api.AuthController.GuestRequest;
import com.fazeel.quizcadia.api.AuthController.LoginRequest;
import com.fazeel.quizcadia.api.AuthController.RegisterRequest;
import com.fazeel.quizcadia.api.AuthController.ResetRequest;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/** The sign-up and sign-in rules, and the 400 problem the app shows when one is broken. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RequestValidationTest {

	static ValidatorFactory factory;
	static Validator validator;

	@BeforeAll
	static void start() {
		factory = Validation.buildDefaultValidatorFactory();
		validator = factory.getValidator();
	}

	@AfterAll
	static void stop() {
		factory.close();
	}

	static Set<String> messages(Object request) {
		return validator.validate(request).stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
	}

	static RegisterRequest register(String name, String email, String password) {
		return new RegisterRequest(name, email, password, null);
	}

	@Test
	void namesAllowLettersOfAnyLanguageNumbersSpacesAndAFewMarks() {
		for (String ok : new String[] { "D", "Ada", "Ada Lovelace", "José Álvarez", "O'Brien", "anne-marie", "player_1",
				"Dr. Who", "Zoë", "李小龍", "محمد", "2fast" }) {
			assertThat(messages(new GuestRequest(ok))).as(ok).isEmpty();
			assertThat(messages(register(ok, "a@example.test", "long enough"))).as(ok).isEmpty();
		}
		for (String bad : new String[] { "<script>", "ada@home", "-ada", ".", "a/b", "tab\tname", "🦊 Fox" }) {
			assertThat(messages(new GuestRequest(bad))).as(bad).containsExactly(InputRules.NAME_CHARS);
		}
		assertThat(messages(new GuestRequest("a".repeat(60)))).isEmpty();
		assertThat(messages(new GuestRequest("a".repeat(61)))).containsExactly(InputRules.NAME_SIZE);
		assertThat(messages(new GuestRequest("   "))).contains(InputRules.NAME_REQUIRED);
		assertThat(messages(new GuestRequest(null))).containsExactly(InputRules.NAME_REQUIRED);
		assertThat(new GuestRequest("  Visitor  ").userName()).isEqualTo("Visitor"); // trimmed before checking
	}

	@Test
	void newAccountsNeedARealLookingEmailAndAPasswordOf8To72() {
		assertThat(messages(register("Ada", "ada@example.test", "12345678"))).isEmpty();
		assertThat(messages(register("Ada", "ada@example.test", "x".repeat(72)))).isEmpty();
		assertThat(messages(register("Ada", "ada@example.test", "1234567"))).containsExactly(InputRules.PASSWORD_SIZE);
		assertThat(messages(register("Ada", "ada@example.test", "x".repeat(73)))).containsExactly(InputRules.PASSWORD_SIZE);
		assertThat(messages(register("Ada", "ada@example.test", null))).containsExactly(InputRules.PASSWORD_REQUIRED);
		for (String bad : new String[] { "not-an-email", "ada@localhost", "ada example@x.test", "@x.test", "ada@" }) {
			assertThat(messages(register("Ada", bad, "long enough"))).as(bad).containsExactly(InputRules.EMAIL_INVALID);
		}
		assertThat(messages(register("Ada", "a".repeat(250) + "@x.test", "long enough")))
				.contains(InputRules.EMAIL_SIZE);
		assertThat(messages(new RegisterRequest("Ada", "ada@x.test", "long enough", "z".repeat(17)))).hasSize(1);
		assertThat(messages(new RegisterRequest("Ada", "ada@x.test", "long enough", "not!valid"))).isEmpty();
	}

	@Test
	void signingInOnlyChecksTheShapeSoOlderAccountsStillWork() {
		assertThat(messages(new LoginRequest("admin@localhost", "anything"))).isEmpty();
		assertThat(messages(new LoginRequest("short", "x"))).containsExactly(InputRules.EMAIL_INVALID);
		assertThat(messages(new LoginRequest(" ", "x"))).contains(InputRules.EMAIL_REQUIRED);
		assertThat(messages(new LoginRequest("ada@x.test", ""))).containsExactly("Enter your password.");
		assertThat(messages(new LoginRequest("ada@x.test", "x".repeat(73)))).containsExactly(InputRules.PASSWORD_TOO_LONG);
		assertThat(messages(new ForgotRequest("admin@localhost"))).isEmpty();
		assertThat(messages(new ForgotRequest("nope"))).containsExactly(InputRules.EMAIL_INVALID);
		assertThat(messages(new ResetRequest("tok", "1234567"))).containsExactly(InputRules.PASSWORD_SIZE);
		assertThat(messages(new ResetRequest("", "12345678"))).hasSize(1);
	}

	@LocalServerPort
	int port;

	@Autowired
	AccountRepository accounts;

	@SuppressWarnings("unchecked")
	Map<String, Object> problem(String path, Object body) {
		return RestClient.builder().baseUrl("http://localhost:" + port).defaultStatusHandler(HttpStatusCode::isError, (q, r) -> {
		}).build().post().uri(path).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(Map.class);
	}

	@Test
	@SuppressWarnings("unchecked")
	void aBrokenSignUpIsA400ProblemWithOneSentencePerField() {
		Map<String, Object> p = problem("/api/v1/auth/register",
				Map.of("userName", " ", "email", "not-an-email", "password", "short"));
		assertThat(p.get("status")).isEqualTo(400);
		assertThat(p.get("title")).isEqualTo("Invalid input");
		assertThat(p.get("detail")).isEqualTo(
				InputRules.NAME_REQUIRED + " " + InputRules.EMAIL_INVALID + " " + InputRules.PASSWORD_SIZE);
		assertThat((Map<String, String>) p.get("errors")).containsOnlyKeys("userName", "email", "password");
		assertThat(accounts.findByEmailIgnoreCase("not-an-email")).isEmpty();
	}

	@Test
	void guestNamesAndBodiesThatArentJsonGetAReadableDetailToo() {
		assertThat(problem("/api/v1/auth/guest", Map.of("userName", "<b>hi</b>")).get("detail"))
				.isEqualTo(InputRules.NAME_CHARS);
		Map<String, Object> p = RestClient.builder().baseUrl("http://localhost:" + port)
				.defaultStatusHandler(HttpStatusCode::isError, (q, r) -> {
				}).build().post().uri("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).body("{oops")
				.retrieve().body(Map.class);
		assertThat(p.get("status")).isEqualTo(400);
		assertThat(p.get("detail")).isEqualTo(ValidationProblems.UNREADABLE);
	}
}
