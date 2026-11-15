package com.stackroute.maverick.generator;

import java.util.List;

public interface FactSource {

	List<Fact> facts(QuestionTemplate template);
}
