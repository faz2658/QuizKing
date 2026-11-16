package com.fazeel.quizcadia.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class SearchServiceTest {

	static GameCard card(String name) {
		return new GameCard(name.hashCode(), name, null, null, 1, null, null);
	}

	@Test
	void exactMatchComesFirstThenAlphabetical() {
		SearchService s = new SearchService(p -> List.of(card("Capitals of Asia"), card("capitals"), card("Capes")));

		assertThat(s.search(" Capitals ")).extracting(GameCard::gameName).containsExactly("capitals", "Capes",
				"Capitals of Asia");
	}

	@Test
	void blankQueryFindsNothingWithoutCallingTheCatalog() {
		SearchService s = new SearchService(p -> {
			throw new AssertionError("should not be called");
		});

		assertThat(s.search("  ")).isEmpty();
		assertThat(s.search(null)).isEmpty();
	}

	@Test
	void resultsAreCapped() {
		SearchService s = new SearchService(p -> IntStream.range(0, 50).mapToObj(i -> card("Game " + i)).toList());

		assertThat(s.search("Game")).hasSize(SearchService.MAX_RESULTS);
	}
}
