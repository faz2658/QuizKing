package com.fazeel.quizcadia.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

import com.fazeel.quizcadia.bank.QuestionBankService;
import com.fazeel.quizcadia.config.SecurityConfig;
import com.fazeel.quizcadia.domain.Questions;

/** The multiplayer engine loads a room quiz's questions here, from inside the Docker network, without a token. */
@Import(SecurityConfig.class)
@WebMvcTest(InternalQuestionController.class)
class InternalQuestionControllerTest {

	@Autowired
	MockMvc mvc;

	@MockitoBean
	QuestionBankService bank;

	@MockitoBean
	com.fazeel.quizcadia.opentdb.OpenTriviaImporter importer;

	@MockitoBean
	com.fazeel.quizcadia.ai.DraftService drafts;

	@Test
	void servesAMediumMixByDefault() throws Exception {
		when(bank.mixedQuestions(eq(2), eq(20), any()))
				.thenReturn(List.of(new Questions(5, 2, "Q?", "pack", "A", "B", "C", "D", "A")));

		mvc.perform(get("/internal/questions/mix"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].questionId").value(5))
				.andExpect(jsonPath("$[0].questionLevel").value(2));
	}

	@Test
	void clampsSillyValues() throws Exception {
		when(bank.mixedQuestions(eq(3), eq(50), any()))
				.thenReturn(List.of(new Questions(6, 3, "Q?", "pack", "A", "B", "C", "D", "A")));

		mvc.perform(get("/internal/questions/mix").param("count", "500").param("level", "9"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].questionId").value(6));
	}
}
