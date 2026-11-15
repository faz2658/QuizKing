package com.fazeel.maverick.matching;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fazeel.maverick.domain.Match;
import com.fazeel.maverick.matching.QuickMatch.State;

class QuickMatchTest {

	static class MutableClock extends Clock {
		Instant now = Instant.parse("2026-11-15T12:00:00Z");

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}
	}

	MutableClock clock = new MutableClock();
	List<Match> started = new ArrayList<>();
	List<Integer> games = new ArrayList<>(List.of(70, 71));
	QuickMatch quick;

	@BeforeEach
	void setUp() {
		quick = new QuickMatch(clock, new Random(3), () -> games, started::add);
	}

	@Test
	void twoPlayersAreMatchedAfterAShortGather() {
		assertThat(quick.join(1).state()).isEqualTo(State.WAITING);
		quick.join(2);
		quick.tick();
		assertThat(started).isEmpty(); // still gathering

		clock.now = clock.now.plus(QuickMatch.GATHER);
		quick.tick();
		assertThat(started).singleElement().satisfies(m -> {
			assertThat(m.players()).containsExactly(1, 2);
			assertThat(m.gameId()).isIn(70, 71);
		});
		assertThat(quick.status(2).state()).isEqualTo(State.MATCHED);
		assertThat(quick.status(2).matchId()).isEqualTo(started.get(0).matchId());
		assertThat(quick.status(2).withBot()).isFalse();
	}

	@Test
	void aFullQueueStartsAtOnce() {
		for (int id = 1; id <= QuickMatch.MAX_PLAYERS; id++) {
			quick.join(id);
		}
		assertThat(started).singleElement().satisfies(m -> assertThat(m.players()).hasSize(QuickMatch.MAX_PLAYERS));
	}

	@Test
	void aLonelyPlayerGetsABot() {
		quick.join(1);
		clock.now = clock.now.plus(Duration.ofSeconds(5));
		assertThat(quick.status(1).botInSeconds()).isEqualTo(7);

		clock.now = clock.now.plus(QuickMatch.BOT_AFTER);
		quick.tick();
		Match m = started.get(0);
		int bot = m.players().get(1);
		assertThat(bot).isNegative();
		assertThat(m.names().get(bot)).startsWith("🤖");
		assertThat(m.countries()).containsKey(bot);
		assertThat(quick.status(1).withBot()).isTrue();
	}

	@Test
	void noMultiplayerGamesYetIsReported() {
		games.clear();
		quick.join(1);
		clock.now = clock.now.plus(QuickMatch.BOT_AFTER);
		quick.tick();
		assertThat(quick.status(1).state()).isEqualTo(State.NO_GAMES);
	}

	@Test
	void leavingTakesYouOutOfTheQueue() {
		quick.join(1);
		quick.leave(1);
		assertThat(quick.status(1).state()).isEqualTo(State.NOT_IN_QUEUE);
		assertThat(quick.waiting()).isZero();
	}
}
