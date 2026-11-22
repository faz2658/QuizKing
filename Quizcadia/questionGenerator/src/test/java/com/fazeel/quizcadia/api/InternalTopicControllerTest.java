package com.fazeel.quizcadia.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fazeel.quizcadia.ai.DraftService;
import com.fazeel.quizcadia.bank.NotFoundException;
import com.fazeel.quizcadia.bank.QuestionBankService;
import com.fazeel.quizcadia.config.SecurityConfig;
import com.fazeel.quizcadia.domain.Questions;
import com.fazeel.quizcadia.opentdb.OpenTriviaImporter;

/** The engines load a topic's questions here, from inside the Docker network, without a token. */
@Import(SecurityConfig.class)
@WebMvcTest(InternalTopicController.class)
class InternalTopicControllerTest {

	@Autowired
	MockMvc mvc;

	@MockitoBean
	QuestionBankService bank;

	// declared by the application class, so every web test mocks them
	@MockitoBean
	OpenTriviaImporter importer;

	@MockitoBean
	DraftService drafts;

	@Test
	void servesATopicsQuestionsWithTheirCountry() throws Exception {
		Questions q = new Questions(5, 1, "Capital of India?", "pack", "Delhi", "Mumbai", "Pune", "Agra", "Delhi");
		q.setCountry("IN");
		when(bank.topicQuestions(7)).thenReturn(List.of(q));

		mvc.perform(get("/internal/topics/7/questions"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].questionId").value(5))
				.andExpect(jsonPath("$[0].country").value("IN"));
	}

	@Test
	void anUnknownTopicIs404() throws Exception {
		when(bank.topicQuestions(9)).thenThrow(new NotFoundException("No topic 9"));

		mvc.perform(get("/internal/topics/9/questions")).andExpect(status().isNotFound());
	}
}
