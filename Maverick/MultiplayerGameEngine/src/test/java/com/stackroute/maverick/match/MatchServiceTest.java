package com.stackroute.maverick.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.stackroute.maverick.domain.Match;
import com.stackroute.maverick.domain.MultiPlayerGame;
import com.stackroute.maverick.domain.MultiPlayerResult;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MatchServiceTest {

	@Mock
	MatchRepository matches;
	@Mock
	GameRepository games;
	@Mock
	GameSource source;
	@Mock
	MatchEvents events;

	Map<String, MultiplayerMatch> store = new HashMap<>();
	Instant now = Instant.parse("2026-01-01T10:00:00Z");
	MatchService service;

	static MultiPlayerGame game() {
		return new MultiPlayerGame(7, "Race", 3, 10, 10,
				List.of(new MultiPlayerGame.Question(1, 1, "Q1", "a", "b", "", null, "a"),
						new MultiPlayerGame.Question(2, 1, "broken", "a", "b", null, null, "zzz")));
	}

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(now, ZoneOffset.UTC);
		service = new MatchService(matches, games, source, new MatchFactory(new Random(2)), events, clock);
		when(matches.save(any())).thenAnswer(i -> {
			MultiplayerMatch m = i.getArgument(0);
			store.put(m.getMatchId(), m);
			return m;
		});
		when(matches.findById(any())).thenAnswer(i -> Optional.ofNullable(store.get(i.getArgument(0))));
		when(games.save(any())).thenAnswer(i -> i.getArgument(0));
	}

	@Test
	void startsAMatchFetchingTheGameWhenNotCachedAndDropsBrokenQuestions() {
		when(games.findById(7)).thenReturn(Optional.empty());
		when(source.fetch(7)).thenReturn(Optional.of(game()));

		MultiplayerMatch m = service.start(new Match("m1", 7, List.of(1, 2), now));

		assertThat(m.getQuestions()).hasSize(1);
		verify(games).save(any());
	}

	@Test
	void replayedMatchEventDoesNotRestartTheMatch() {
		when(games.findById(7)).thenReturn(Optional.of(game()));
		MultiplayerMatch first = service.start(new Match("m1", 7, List.of(1, 2), now));
		first.answer(1, 1, "a", now);

		MultiplayerMatch again = service.start(new Match("m1", 7, List.of(1, 2), now));

		assertThat(again.getQuestions().get(0).getAnswers()).hasSize(1);
	}

	@Test
	void unknownGameFailsSoKafkaCanRetry() {
		when(games.findById(7)).thenReturn(Optional.empty());
		when(source.fetch(7)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.start(new Match("m1", 7, List.of(1, 2), now)))
				.isInstanceOf(NotFoundException.class);
	}

	@Test
	void resultIsPublishedOnceWithStandings() {
		when(games.findById(7)).thenReturn(Optional.of(game()));
		service.start(new Match("m1", 7, List.of(1, 2), now));

		service.answer("m1", 2, 1, "a");
		service.answer("m1", 1, 1, "a");
		service.get("m1", 1);

		ArgumentCaptor<MultiPlayerResult> result = ArgumentCaptor.forClass(MultiPlayerResult.class);
		verify(events, times(1)).matchCompleted(result.capture());
		assertThat(result.getValue().standings().get(0).userId()).isEqualTo(2);
		assertThat(result.getValue().questions().get(0).winnerUserId()).isEqualTo(2);
	}

	@Test
	void onlyPlayersCanSeeTheMatch() {
		when(games.findById(7)).thenReturn(Optional.of(game()));
		service.start(new Match("m1", 7, List.of(1, 2), now));

		assertThatThrownBy(() -> service.get("m1", 3)).isInstanceOf(MatchRuleException.class);
		verify(events, never()).matchCompleted(any());
	}
}
