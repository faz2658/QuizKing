package com.fazeel.quizcadia.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.fazeel.quizcadia.domain.Match;
import com.fazeel.quizcadia.matching.LobbyStatus.State;

class MatchmakerTest {

	final List<Match> matches = new ArrayList<>();
	final TestClock clock = new TestClock();
	final Matchmaker matchmaker = new Matchmaker(new MatchmakingRules(2, 4, Duration.ofSeconds(10)), clock,
			matches::add);

	@Test
	void fullLobbyStartsImmediately() {
		matchmaker.join(1, 10);
		matchmaker.join(1, 11);
		matchmaker.join(1, 12);
		LobbyStatus last = matchmaker.join(1, 13);

		assertThat(last.state()).isEqualTo(State.MATCHED);
		assertThat(matches).singleElement().satisfies(m -> {
			assertThat(m.gameId()).isEqualTo(1);
			assertThat(m.players()).containsExactly(10, 11, 12, 13);
			assertThat(m.matchId()).isEqualTo(last.matchId());
		});
		assertThat(matchmaker.status(1, 10).matchId()).isEqualTo(last.matchId());
	}

	@Test
	void lobbyWithEnoughPlayersStartsWhenTheWaitIsOver() {
		matchmaker.join(1, 10);
		matchmaker.join(1, 11);
		clock.advance(9);
		matchmaker.tick();
		assertThat(matches).isEmpty();
		assertThat(matchmaker.status(1, 10).state()).isEqualTo(State.WAITING);

		clock.advance(1);
		matchmaker.tick();

		assertThat(matches).singleElement().extracting(Match::players).isEqualTo(List.of(10, 11));
		assertThat(matchmaker.status(1, 11).state()).isEqualTo(State.MATCHED);
	}

	@Test
	void lonelyPlayerExpiresAndCanTryAgain() {
		matchmaker.join(1, 10);
		clock.advance(10);
		matchmaker.tick();

		assertThat(matches).isEmpty();
		assertThat(matchmaker.status(1, 10).state()).isEqualTo(State.EXPIRED);
		assertThat(matchmaker.join(1, 10).state()).isEqualTo(State.WAITING);
	}

	@Test
	void playersOfDifferentGamesAreNeverMixed() {
		matchmaker.join(1, 10);
		matchmaker.join(2, 20);
		matchmaker.join(1, 11);
		matchmaker.join(2, 21);
		clock.advance(10);
		matchmaker.tick();

		assertThat(matches).hasSize(2);
		assertThat(matches).allSatisfy(m -> assertThat(m.players())
				.allMatch(p -> p / 10 == m.gameId()));
	}

	@Test
	void joiningTwiceDoesNotCountTwice() {
		matchmaker.join(1, 10);
		matchmaker.join(1, 10);
		clock.advance(10);
		matchmaker.tick();

		assertThat(matches).isEmpty();
	}

	@Test
	void switchingGameLeavesThePreviousLobby() {
		matchmaker.join(1, 10);
		matchmaker.join(1, 11);
		matchmaker.join(2, 11);
		clock.advance(10);
		matchmaker.tick();

		assertThat(matches).isEmpty();
		assertThat(matchmaker.status(1, 10).state()).isEqualTo(State.EXPIRED);
	}

	@Test
	void leavingTheLobbyWorks() {
		matchmaker.join(1, 10);
		matchmaker.join(1, 11);
		matchmaker.leave(1, 11);
		clock.advance(10);
		matchmaker.tick();

		assertThat(matches).isEmpty();
	}

	@Test
	void unknownPlayerIsReportedAsExpired() {
		assertThat(matchmaker.status(1, 99).state()).isEqualTo(State.EXPIRED);
	}

	@Test
	void concurrentJoinsProduceCompleteNonOverlappingMatches() throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(8);
		for (int u = 1; u <= 400; u++) {
			int user = u;
			pool.submit(() -> matchmaker.join(1, user));
		}
		pool.shutdown();
		assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

		assertThat(matches).hasSize(100);
		assertThat(matches.stream().flatMap(m -> m.players().stream()).distinct().count()).isEqualTo(400);
	}

	@Test
	void rulesAreValidated() {
		assertThatThrownBy(() -> new MatchmakingRules(1, 4, Duration.ofSeconds(1)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new MatchmakingRules(3, 2, Duration.ofSeconds(1)))
				.isInstanceOf(IllegalArgumentException.class);
	}

	static final class TestClock extends Clock {
		Instant now = Instant.parse("2026-01-01T00:00:00Z");

		void advance(long seconds) {
			now = now.plusSeconds(seconds);
		}

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
}
