package com.fazeel.quizcadia.generator;

import java.util.List;

/** Where facts for a question template come from (Wikidata in production). */
public interface FactSource {

	List<Fact> facts(QuestionTemplate template);
}
