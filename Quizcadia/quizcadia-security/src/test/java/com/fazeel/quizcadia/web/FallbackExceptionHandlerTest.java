package com.fazeel.quizcadia.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.core.annotation.Order;

class FallbackExceptionHandlerTest {

	record Body(String name) {
	}

	static class DomainException extends RuntimeException {
		DomainException(String m) {
			super(m);
		}
	}

	@RestController
	static class Demo {
		@GetMapping("/boom")
		String boom() {
			throw new IllegalStateException("secret internals: password=hunter2");
		}

		@GetMapping("/db")
		String db() {
			throw new DataAccessResourceFailureException("Timed out connecting to mongo:27017");
		}

		@GetMapping("/peer")
		String peer() {
			throw new ResourceAccessException("I/O error on GET request for http://game-manager:9091");
		}

		@GetMapping("/items/{id}")
		String item(@PathVariable int id) {
			return "item " + id;
		}

		@PostMapping("/items")
		String create(@RequestBody Body body) {
			return body.name();
		}

		@GetMapping("/domain")
		String domain() {
			throw new DomainException("That quiz has ended");
		}

		@GetMapping("/denied")
		String denied() {
			throw new AccessDeniedException("no");
		}

		@GetMapping("/gone")
		String gone() throws IOException {
			throw new IOException("Broken pipe");
		}
	}

	/** Stands in for a service's own handler, which must win over the fallback. */
	@RestControllerAdvice
	@Order(0)
	static class ServiceHandler {
		@ExceptionHandler(DomainException.class)
		ProblemDetail domain(DomainException e) {
			return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
		}
	}

	final MockMvc mvc = MockMvcBuilders.standaloneSetup(new Demo())
			.setControllerAdvice(new FallbackExceptionHandler(), new ServiceHandler())
			.build();

	@Test
	void unexpectedErrorsAreA500ProblemWithAnErrorIdAndNoInternals() throws Exception {
		String body = mvc.perform(get("/boom"))
				.andExpect(status().isInternalServerError())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.errorId").isString())
				.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.startsWith(FallbackExceptionHandler.UNEXPECTED)))
				.andReturn().getResponse().getContentAsString();
		assertThat(body).doesNotContain("hunter2", "IllegalStateException", "secret");
	}

	@Test
	void unreachableDatabaseOrServiceIsA503MaintenanceAnswer() throws Exception {
		for (String path : new String[] { "/db", "/peer" }) {
			String body = mvc.perform(get(path))
					.andExpect(status().isServiceUnavailable())
					.andExpect(header().string("Retry-After", "15"))
					.andExpect(header().string("X-Maintenance", "1"))
					.andExpect(jsonPath("$.maintenance").value(true))
					.andExpect(jsonPath("$.retryAfterSeconds").value(15))
					.andExpect(jsonPath("$.detail").value(FallbackExceptionHandler.UNAVAILABLE))
					.andReturn().getResponse().getContentAsString();
			assertThat(body).doesNotContain("mongo", "game-manager");
		}
	}

	@Test
	void badRequestsAreClientErrorProblems() throws Exception {
		mvc.perform(get("/items/abc")).andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		mvc.perform(post("/items").contentType(MediaType.APPLICATION_JSON).content("{not json"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		mvc.perform(post("/items").contentType(MediaType.TEXT_PLAIN).content("x"))
				.andExpect(status().isUnsupportedMediaType());
		mvc.perform(post("/boom")).andExpect(status().isMethodNotAllowed())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void theServicesOwnHandlerWins() throws Exception {
		mvc.perform(get("/domain")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value("That quiz has ended"));
	}

	@Test
	void securityFailuresAreLeftToSpringSecurity() {
		assertThatThrownBy(() -> mvc.perform(get("/denied"))).hasRootCauseInstanceOf(AccessDeniedException.class);
	}

	@Test
	void aClientThatWentAwayGetsNoErrorBody() throws Exception {
		mvc.perform(get("/gone")).andExpect(content().string(""));
	}
}
