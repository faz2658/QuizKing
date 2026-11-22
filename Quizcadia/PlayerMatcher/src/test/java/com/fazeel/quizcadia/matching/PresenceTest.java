package com.fazeel.quizcadia.matching;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fazeel.quizcadia.domain.Match;

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
		Match m = new Match("m", 7, List.of(1, -2), clock.now, Map.of(-2, "🤖 Turing"), Map.of(-2, "JP"));

		Match enriched = presence.enrich(m);
		assertThat(enriched.names()).containsEntry(1, "Ada").containsEntry(-2, "🤖 Turing");
		assertThat(enriched.countries()).containsEntry(1, "CA").containsEntry(-2, "JP");
	}

	@Test
	void adminsSeeWhoIsOnlineAccountsFirstWithTheTimeTheyCameOnline() {
		Instant start = clock.now;
		presence.seen(Presence.GUEST_ID_BASE + 5, "zed (guest)", null);
		presence.seen(2, "bo", "IN");
		presence.seen(1, "Ada", "ca");
		clock.now = clock.now.plus(Duration.ofSeconds(60));
		presence.seen(1, "Ada", null); // still online: keeps the time she came online
		presence.seen(3, "Cy", "IN");

		List<Presence.OnlinePlayer> online = presence.whoIsOnline();
		assertThat(online).extracting(Presence.OnlinePlayer::name).containsExactly("Ada", "bo", "Cy", "zed (guest)");
		assertThat(online.get(0).onlineSince()).isEqualTo(start);
		assertThat(online.get(0).lastSeen()).isEqualTo(clock.now);
		assertThat(online.get(0).country()).isEqualTo("CA");
		assertThat(online.get(3).guest()).isTrue();

		clock.now = clock.now.plus(Duration.ofMinutes(5)); // everyone left; Ada comes back later
		presence.seen(1, "Ada", null);
		assertThat(presence.whoIsOnline()).extracting(Presence.OnlinePlayer::name).containsExactly("Ada");
		assertThat(presence.whoIsOnline().get(0).onlineSince()).isEqualTo(clock.now);
	}
}
