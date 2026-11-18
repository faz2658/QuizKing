package com.fazeel.quizcadia.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class IdempotencyFilterTest {

	static final AtomicInteger calls = new AtomicInteger();
	static volatile CountDownLatch slow;

	record Answer(String option) {
	}

	@RestController
	static class Demo {
		@PostMapping("/answers")
		ResponseEntity<String> answer(@RequestBody Answer a) throws InterruptedException {
			int n = calls.incrementAndGet();
			if (slow != null) {
				slow.await(5, TimeUnit.SECONDS);
			}
			return ResponseEntity.status(HttpStatus.CREATED).header("Set-Cookie", "c=" + n)
					.contentType(MediaType.APPLICATION_JSON).body("{\"call\":" + n + ",\"option\":\"" + a.option() + "\"}");
		}

		@PostMapping("/broken")
		ResponseEntity<String> broken() {
			calls.incrementAndGet();
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("down");
		}
	}

	final IdempotencyFilter filter = new IdempotencyFilter();
	final MockMvc mvc = MockMvcBuilders.standaloneSetup(new Demo()).addFilters(filter).build();

	@AfterEach
	void reset() {
		calls.set(0);
		slow = null;
		SecurityContextHolder.clearContext();
	}

	MvcResult send(String path, String key, String body) throws Exception {
		var req = post(path).contentType(MediaType.APPLICATION_JSON).content(body);
		if (key != null) {
			req.header(IdempotencyFilter.HEADER, key);
		}
		return mvc.perform(req).andReturn();
	}

	@Test
	void aRepeatedRequestGetsTheFirstAnswerInsteadOfRunningAgain() throws Exception {
		MvcResult first = send("/answers", "key-00000001", "{\"option\":\"Lima\"}");
		mvc.perform(post("/answers").contentType(MediaType.APPLICATION_JSON).content("{\"option\":\"Lima\"}")
				.header(IdempotencyFilter.HEADER, "key-00000001"))
				.andExpect(status().isCreated())
				.andExpect(content().string(first.getResponse().getContentAsString()))
				.andExpect(header().string("Set-Cookie", "c=1"))
				.andExpect(header().string("Idempotent-Replayed", "true"));
		assertThat(calls).hasValue(1);
		assertThat(first.getResponse().getContentAsString()).isEqualTo("{\"call\":1,\"option\":\"Lima\"}");
	}

	@Test
	void withoutAKeyEveryRequestRuns() throws Exception {
		send("/answers", null, "{\"option\":\"Lima\"}");
		send("/answers", null, "{\"option\":\"Lima\"}");
		assertThat(calls).hasValue(2);
	}

	@Test
	void aKeyCannotBeReusedForADifferentRequest() throws Exception {
		send("/answers", "key-00000002", "{\"option\":\"Lima\"}");
		assertThat(send("/answers", "key-00000002", "{\"option\":\"Quito\"}").getResponse().getStatus()).isEqualTo(422);
		assertThat(calls).hasValue(1);
	}

	@Test
	void serverErrorsAreNotRememberedSoARetryRunsAgain() throws Exception {
		send("/broken", "key-00000003", "{}");
		send("/broken", "key-00000003", "{}");
		assertThat(calls).hasValue(2);
	}

	@Test
	void eachPlayerHasTheirOwnKeys() throws Exception {
		SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated("5", null, java.util.List.of()));
		send("/answers", "key-00000004", "{\"option\":\"Lima\"}");
		SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated("6", null, java.util.List.of()));
		send("/answers", "key-00000004", "{\"option\":\"Lima\"}");
		assertThat(calls).hasValue(2);
	}

	@Test
	void malformedKeysAreRefused() throws Exception {
		assertThat(send("/answers", "short", "{\"option\":\"Lima\"}").getResponse().getStatus()).isEqualTo(400);
		assertThat(calls).hasValue(0);
	}

	@Test
	void aRetryWhileTheFirstIsStillRunningWaitsForItsAnswer() throws Exception {
		slow = new CountDownLatch(1);
		var pool = Executors.newFixedThreadPool(2);
		try {
			Future<MvcResult> first = pool.submit(() -> send("/answers", "key-00000005", "{\"option\":\"Lima\"}"));
			while (calls.get() == 0) {
				Thread.onSpinWait();
			}
			Future<MvcResult> retry = pool.submit(() -> send("/answers", "key-00000005", "{\"option\":\"Lima\"}"));
			Thread.sleep(100);
			assertThat(retry.isDone()).isFalse();
			slow.countDown();
			assertThat(retry.get(5, TimeUnit.SECONDS).getResponse().getContentAsString())
					.isEqualTo(first.get(5, TimeUnit.SECONDS).getResponse().getContentAsString());
			assertThat(calls).hasValue(1);
		}
		finally {
			pool.shutdownNow();
		}
	}
}
