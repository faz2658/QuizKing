package com.fazeel.quizcadia.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OpenAiCompatibleQuestionModelTest {

	@Test
	void sendsTheTrainingPromptAndParsesTheReply() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo("http://model:8080/v1/chat/completions"))
				.andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.header("Content-Length",
						org.hamcrest.Matchers.notNullValue()))
				.andExpect(content().string(org.hamcrest.Matchers.containsString(
						"Topic: Geography\\nDifficulty: hard\\nFormat: multiple choice\\nWrite one new question. /no_think")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("You write trivia questions")))
				.andRespond(withSuccess("""
						{"choices":[{"message":{"role":"assistant","content":"<think>\\n\\n</think>\\n\\n{\\"question\\": \\"Capital of Peru?\\", \\"options\\": [\\"Lima\\", \\"Quito\\", \\"Cusco\\", \\"Arequipa\\"], \\"answer\\": \\"Lima\\"}"}}]}""",
						MediaType.APPLICATION_JSON));

		var model = new OpenAiCompatibleQuestionModel(builder, "http://model:8080", "quizcadia");
		var q = model.write("Geography", "hard", "multiple choice");

		assertThat(q).get().satisfies(g -> {
			assertThat(g.question()).isEqualTo("Capital of Peru?");
			assertThat(g.options()).hasSize(4);
			assertThat(g.answer()).isEqualTo("Lima");
		});
		server.verify();
	}

	@Test
	void parsesFencedJsonAndRejectsJunk() {
		var model = new OpenAiCompatibleQuestionModel(RestClient.builder(), "http://x", "m");

		assertThat(model.parse("```json\n{\"question\":\"Q?\",\"options\":[\"True\",\"False\"],\"answer\":\"True\"}\n```")).isPresent();
		assertThat(model.parse("I cannot help with that.")).isEmpty();
		assertThat(model.parse("{\"question\": \"Q?\"}")).isEmpty();
		assertThat(model.parse("{not json}")).isEmpty();
		assertThat(model.parse(null)).isEmpty();
	}

	@Test
	void serverErrorsBecomeUnavailable() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo("http://model:8080/v1/chat/completions")).andRespond(withServerError());

		var model = new OpenAiCompatibleQuestionModel(builder, "http://model:8080", "m");

		assertThatThrownBy(() -> model.write("X", "easy", "true/false")).isInstanceOf(AiUnavailableException.class);
	}

	@Test
	void notConfiguredMeansUnavailable() {
		assertThat(new OpenAiCompatibleQuestionModel(RestClient.builder(), "", "m").available()).isFalse();
	}

	@Test
	void servingPromptIsIdenticalToTheTrainingPrompt() throws Exception {
		Path training = Path.of("..", "ai", "question-model", "prompt.json");
		Path serving = Path.of("src", "main", "resources", "ai", "prompt.json");

		assertThat(Files.readString(serving)).as("copy ai/question-model/prompt.json into resources after editing it")
				.isEqualTo(Files.readString(training));
	}
}
