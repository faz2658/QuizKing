package com.fazeel.quizcadia.generator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class WikidataFactSourceTest {

	@Test
	void parsesSparqlJsonAndSkipsUnlabeledEntities() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(queryParam("query", Matchers.notNullValue()))
				.andExpect(header("User-Agent", Matchers.startsWith("Quizcadia")))
				.andRespond(withSuccess("""
						{"head":{"vars":["subjectLabel","answerLabel"]},"results":{"bindings":[
						 {"subjectLabel":{"type":"literal","value":"France"},"answerLabel":{"type":"literal","value":"Paris"}},
						 {"subjectLabel":{"type":"literal","value":"Q123"},"answerLabel":{"type":"literal","value":"X"}},
						 {"subjectLabel":{"type":"literal","value":"Japan"}}
						]}}""", MediaType.parseMediaType("application/sparql-results+json")));

		var facts = new WikidataFactSource(builder, "https://query.wikidata.org/sparql")
				.facts(QuestionTemplate.CAPITALS);

		assertThat(facts).containsExactly(new Fact("France", "Paris"));
		server.verify();
	}
}
