package com.fazeel.quizcadia.inquiry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.fazeel.quizcadia.config.SecurityConfig;
import com.fazeel.quizcadia.inquiry.Inquiry.Kind;
import com.fazeel.quizcadia.inquiry.Inquiry.Status;

@Import(SecurityConfig.class)
@WebMvcTest(InquiryController.class)
class InquiryControllerTest {

	static final Instant NOW = Instant.parse("2026-11-16T12:00:00Z");

	static RequestPostProcessor admin() {
		return jwt().jwt(j -> j.subject("1")).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
	}

	static RequestPostProcessor player() {
		return jwt().jwt(j -> j.subject("2")).authorities(new SimpleGrantedAuthority("ROLE_USER"));
	}

	@Autowired
	MockMvc mvc;

	@MockitoBean
	InquiryRepository repo;

	@MockitoBean
	InquiryLimiter limiter;

	@MockitoBean
	Clock clock;

	@BeforeEach
	void fixedTime() {
		when(clock.instant()).thenReturn(NOW);
	}

	static final String EVENT = """
			{"kind":"EVENT","name":" Ada ","email":"ada@example.test","organisation":"Acme","occasion":"Team event",
			 "date":"2026-12-12","groupSize":25,"city":"Toronto","message":"Holiday party","website":""}""";

	@Test
	void anyoneCanSendARequestWithoutAnAccount() throws Exception {
		when(limiter.allow(anyString())).thenReturn(true);
		mvc.perform(post("/api/v1/reports/inquiries").contentType(MediaType.APPLICATION_JSON).content(EVENT)
				.header("X-Forwarded-For", "203.0.113.9, 10.0.0.2"))
				.andExpect(status().isAccepted()).andExpect(jsonPath("$.received").value(true));

		ArgumentCaptor<Inquiry> saved = ArgumentCaptor.forClass(Inquiry.class);
		verify(repo).save(saved.capture());
		assertThat(saved.getValue()).extracting(Inquiry::kind, Inquiry::name, Inquiry::groupSize, Inquiry::status, Inquiry::createdAt)
				.containsExactly(Kind.EVENT, "Ada", 25, Status.NEW, NOW);
		verify(limiter).allow("203.0.113.9");
	}

	@Test
	void botsFillingTheHiddenFieldAreIgnoredQuietly() throws Exception {
		mvc.perform(post("/api/v1/reports/inquiries").contentType(MediaType.APPLICATION_JSON)
				.content(EVENT.replace("\"website\":\"\"", "\"website\":\"http://spam.example\"")))
				.andExpect(status().isAccepted());
		verify(repo, never()).save(any());
	}

	@Test
	void tooManyRequestsAreRefused() throws Exception {
		when(limiter.allow(anyString())).thenReturn(false);
		mvc.perform(post("/api/v1/reports/inquiries").contentType(MediaType.APPLICATION_JSON).content(EVENT))
				.andExpect(status().isTooManyRequests());
		verify(repo, never()).save(any());
	}

	@Test
	void badInputIsRejected() throws Exception {
		when(limiter.allow(anyString())).thenReturn(true);
		for (String bad : List.of(EVENT.replace("ada@example.test", "not-an-email"), EVENT.replace("\" Ada \"", "\"\""),
				EVENT.replace("2026-12-12", "next friday"), EVENT.replace("25", "0"), EVENT.replace("\"EVENT\"", "\"PARTY\""))) {
			mvc.perform(post("/api/v1/reports/inquiries").contentType(MediaType.APPLICATION_JSON).content(bad))
					.andExpect(status().isBadRequest());
		}
		verify(repo, never()).save(any());
	}

	@Test
	void onlyAdminsCanReadOrUpdateRequests() throws Exception {
		Inquiry i = new Inquiry("abc", Kind.VENUE, "Bob", "bob@example.test", "The Fox", null, null, null, "Ottawa", null,
				Status.NEW, NOW, NOW);
		when(repo.findTop200ByOrderByCreatedAtDesc()).thenReturn(List.of(i));
		when(repo.findById("abc")).thenReturn(Optional.of(i));
		when(repo.save(any())).thenAnswer(a -> a.getArgument(0));

		mvc.perform(get("/api/v1/reports/inquiries")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/reports/inquiries").with(player())).andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/reports/inquiries").with(admin())).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].organisation").value("The Fox"));

		mvc.perform(patch("/api/v1/reports/inquiries/abc").with(player()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"BOOKED\"}")).andExpect(status().isForbidden());
		mvc.perform(patch("/api/v1/reports/inquiries/abc").with(admin()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"BOOKED\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("BOOKED"));
	}
}
