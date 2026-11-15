package com.fazeel.quizking.generator;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Runs a template's query against the Wikidata SPARQL endpoint (standard JSON results format). */
@Component
public class WikidataFactSource implements FactSource {

	private final RestClient http;

	public WikidataFactSource(RestClient.Builder builder,
			@Value("${quizking.wikidata.endpoint:https://query.wikidata.org/sparql}") String endpoint) {
		// Wikidata asks automated clients to identify themselves.
		this.http = builder.baseUrl(endpoint).defaultHeader("User-Agent", "QuizKing/2.0 (question generator)")
				.build();
	}

	@Override
	public List<Fact> facts(QuestionTemplate template) {
		SparqlResponse response = http.get().uri(u -> u.queryParam("query", "{q}").build(template.sparql()))
				.accept(MediaType.parseMediaType("application/sparql-results+json")).retrieve()
				.body(SparqlResponse.class);
		if (response == null || response.results() == null) {
			return List.of();
		}
		return Objects.requireNonNullElse(response.results().bindings(), List.<Map<String, Binding>>of()).stream()
				.map(b -> new Fact(text(b.get("subjectLabel")), text(b.get("answerLabel"))))
				// unlabeled entities come back as their Q-id; they make bad questions
				.filter(f -> f.subject() != null && f.answer() != null && !f.subject().matches("Q\\d+")
						&& !f.answer().matches("Q\\d+"))
				.toList();
	}

	private static String text(Binding v) {
		return v == null ? null : v.value();
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record SparqlResponse(Results results) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Results(List<Map<String, Binding>> bindings) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Binding(String value) {
	}
}
