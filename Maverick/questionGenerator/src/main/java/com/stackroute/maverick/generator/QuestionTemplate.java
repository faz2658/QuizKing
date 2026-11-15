package com.stackroute.maverick.generator;

/**
 * A kind of question that can be generated from facts.
 *
 * @param key stable identifier used in the API and in generated question ids
 * @param stemFormat question text; {@code %s} is replaced by the fact's subject
 * @param sparql Wikidata query selecting {@code ?subjectLabel} and {@code ?answerLabel}
 */
public record QuestionTemplate(String key, String stemFormat, int level, String sparql) {

	public static final QuestionTemplate CAPITALS = new QuestionTemplate("capitals", "What is the capital of %s?", 1,
			"""
					SELECT DISTINCT ?subjectLabel ?answerLabel WHERE {
					  ?subject wdt:P31 wd:Q3624078; wdt:P36 ?answer .
					  FILTER NOT EXISTS { ?subject wdt:P576 [] }
					  SERVICE wikibase:label { bd:serviceParam wikibase:language "en". }
					}""");

	public static final QuestionTemplate CURRENCIES = new QuestionTemplate("currencies",
			"What is the official currency of %s?", 2, """
					SELECT DISTINCT ?subjectLabel ?answerLabel WHERE {
					  ?subject wdt:P31 wd:Q3624078; wdt:P38 ?answer .
					  FILTER NOT EXISTS { ?subject wdt:P576 [] }
					  SERVICE wikibase:label { bd:serviceParam wikibase:language "en". }
					}""");

	public static final QuestionTemplate US_PRESIDENT_BIRTH_YEAR = new QuestionTemplate("us-presidents",
			"In which year was US President %s born?", 3, """
					SELECT DISTINCT ?subjectLabel ?answerLabel WHERE {
					  ?subject wdt:P39 wd:Q11696; wdt:P569 ?dob .
					  BIND(STR(YEAR(?dob)) AS ?answerLabel)
					  SERVICE wikibase:label { bd:serviceParam wikibase:language "en". }
					}""");

	public static QuestionTemplate byKey(String key) {
		for (QuestionTemplate t : new QuestionTemplate[] { CAPITALS, CURRENCIES, US_PRESIDENT_BIRTH_YEAR }) {
			if (t.key().equalsIgnoreCase(key)) {
				return t;
			}
		}
		throw new IllegalArgumentException("Unknown question template '" + key
				+ "' (known: capitals, currencies, us-presidents)");
	}
}
