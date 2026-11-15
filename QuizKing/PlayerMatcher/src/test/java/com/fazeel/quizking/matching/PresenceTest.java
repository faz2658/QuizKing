package com.fazeel.quizking.matching;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fazeel.quizking.domain.Match;

class PresenceTest {

	QuickMatchTest.MutableClock clock = new QuickMatchTest.MutableClock();
	Presence presence = new Presence(clock);

	@Test
	void countsWhoWasSeenRecentlyByCountry() {
		presence.seen(1, "Ada", "ca");
		presence.seen(2, "Bo", "CA");
		presence.seen(3, "Cy", "IN");
		presence.seen(4, "Di", "not-a-country");

		assertThat(presence.online()).isEqualTo(4);
		assertThat(presence.byCountry()).containsExactly(new Presence.CountryCount("CA", 2), new Presence.CountryCount("IN", 1));

		clock.now = clock.now.plus(Duration.ofMinutes(3));
		presence.seen(3, "Cy", null); // a heartbeat without a country keeps the old one
		assertThat(presence.online()).isEqualTo(1);
		assertThat(presence.byCountry()).containsExactly(new Presence.CountryCount("IN", 1));
	}

	@Test
	void matchesGetNamesAndCountriesButBotsKeepTheirs() {
		presence.seen(1, "Ada", "CA");
		Match m = new Match("m", 7, List.of(1, -2), clock.now, java.util.Map.of(-2, "🤖 Turing"), java.util.Map.of(-2, "JP"));

		Match enriched = presence.enrich(m);
		assertThat(enriched.names()).containsEntry(1, "Ada").containsEntry(-2, "🤖 Turing");
		assertThat(enriched.countries()).containsEntry(1, "CA").containsEntry(-2, "JP");
	}
}
