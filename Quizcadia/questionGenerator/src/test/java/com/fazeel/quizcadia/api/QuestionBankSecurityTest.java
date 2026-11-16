package com.fazeel.quizcadia.api;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.fazeel.quizcadia.ai.DraftService;
import com.fazeel.quizcadia.bank.QuestionBankService;
import com.fazeel.quizcadia.config.SecurityConfig;
import com.fazeel.quizcadia.opentdb.OpenTriviaImporter;

/** The question bank holds every answer: only admins may read or change it. */
@Import(SecurityConfig.class)
@WebMvcTest(QuestionBankController.class)
class QuestionBankSecurityTest {

	@Autowired
	MockMvc mvc;

	@MockitoBean
	QuestionBankService bank;

	@MockitoBean
	OpenTriviaImporter importer;

	@MockitoBean
	DraftService drafts;

	static RequestPostProcessor as(String role) {
		return jwt().jwt(j -> j.subject("5")).authorities(new SimpleGrantedAuthority("ROLE_" + role));
	}

	@Test
	void playersAndGuestsCannotReadAnswersOrChangeTheBank() throws Exception {
		for (String role : List.of("USER", "GUEST")) {
			mvc.perform(get("/api/v1/question-generator/category").with(as(role))).andExpect(status().isForbidden());
			mvc.perform(get("/api/v1/question-generator/ai/drafts").with(as(role))).andExpect(status().isForbidden());
			mvc.perform(post("/api/v1/question-generator/ai/drafts").with(as(role))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"categoryId\":1,\"topicName\":\"Art\",\"level\":1,\"trueFalse\":false,\"count\":1}"))
					.andExpect(status().isForbidden());
			mvc.perform(post("/api/v1/question-generator/ai/drafts/d1/approve").with(as(role)))
					.andExpect(status().isForbidden());
			mvc.perform(delete("/api/v1/question-generator/ai/drafts/d1").with(as(role)))
					.andExpect(status().isForbidden());
			mvc.perform(post("/api/v1/question-generator/import/opentdb").with(as(role)))
					.andExpect(status().isForbidden());
		}
		mvc.perform(get("/api/v1/question-generator/category")).andExpect(status().isUnauthorized());
	}

	@Test
	void topicCountsArePublicForTheWelcomePage() throws Exception {
		mvc.perform(get("/api/v1/question-generator/stats")).andExpect(status().isOk());
	}

	@Test
	void adminsCanUseIt() throws Exception {
		when(drafts.available()).thenReturn(true);
		when(bank.categories()).thenReturn(List.of());

		mvc.perform(get("/api/v1/question-generator/ai/status").with(as("ADMIN"))).andExpect(status().isOk());
		mvc.perform(get("/api/v1/question-generator/category").with(as("ADMIN"))).andExpect(status().isOk());
	}
}
