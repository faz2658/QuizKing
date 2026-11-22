package com.fazeel.quizcadia.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.OptimisticLockingFailureException;

import tools.jackson.databind.json.JsonMapper;

import com.fazeel.quizcadia.domain.Match;
import com.fazeel.quizcadia.domain.MultiPlayerGame;
import com.fazeel.quizcadia.domain.MultiPlayerResult;

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
	@Mock
	SeenQuestionRepository seen;

	Map<String, MultiplayerMatch> store = new HashMap<>();
	Instant now = Instant.parse("2026-01-01T10:00:00Z");
	MatchService service;

	static MultiPlayerGame game() {
		return new MultiPlayerGame(7, "Race", 3, 10, 10,
				List.of(new MultiPlayerGame.Question(1, 1, "Q1", "a", "b", "", null, "a"),
						new MultiPlayerGame.Question(2, 1, "broken", "a", "b", null, null, "zzz")), 0, null);
	}

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(now, ZoneOffset.UTC);
		service = new MatchService(matches, games, source, new MatchFactory(new Random(2)), events, seen, clock);
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
	void aGameThatReferencesItsTopicIsPlayedWithTheTopicsQuestions() {
		Clock clock = Clock.fixed(now, ZoneOffset.UTC);
		service = new MatchService(matches, games, source, new MatchFactory(new Random(2)), events, seen, clock,
				new MatchUpdates(), new TopicPools(id -> id == 40
						? List.of(new MultiPlayerGame.Question(1, 1, "Q1", "a", "b", null, null, "a"),
								new MultiPlayerGame.Question(2, 1, "Q2", "a", "b", null, null, "b"))
						: List.of(), clock, Duration.ofMinutes(10)));
		MultiPlayerGame slim = new MultiPlayerGame(7, "History Showdown", 3, 10, 10, null, 2, null,
				new MultiPlayerGame.TopicRef(40, "History"));
		when(games.findById(7)).thenReturn(Optional.of(slim));

		MultiplayerMatch m = service.start(new Match("m9", 7, List.of(1, 2), now));

		assertThat(m.getQuestions()).hasSize(2);
	}

	@Test
	void readsTheTopicOfAGameDefinition() {
		MultiPlayerGame g = JsonMapper.builder().build().readValue(
				"{\"gameId\":7,\"gameName\":\"History Showdown\",\"categoryId\":3,\"timePerQuestion\":15,\"scorePerQuestion\":10,\"numberOfQuestions\":8,\"topic\":{\"topicId\":40,\"topicName\":\"History\",\"topicImage\":null},\"questionCount\":2400}",
				MultiPlayerGame.class);

		assertThat(g.topic().topicId()).isEqualTo(40);
		assertThat(g.autoquestions()).isNull();
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

	@Test
	void thePartyScreenMayWatchButNotAnswer() {
		when(games.findById(7)).thenReturn(Optional.of(game()));
		service.start(new Match("m1", 7, List.of(1, 2), now, Map.of(), Map.of(), 50));

		assertThat(service.get("m1", 50).getMatchId()).isEqualTo("m1");
		assertThatThrownBy(() -> service.answer("m1", 50, 1, "x")).hasMessageContaining("not in this match");
		assertThatThrownBy(() -> service.get("m1", 51)).hasMessageContaining("not in this match");
	}

	static List<MultiPlayerGame.Question> mediumMix(int n) {
		return IntStream.rangeClosed(1, n)
				.mapToObj(i -> new MultiPlayerGame.Question(100 + i, 2, "Q" + i + "?", "R" + i, "W1", "W2", "W3", "R" + i))
				.toList();
	}

	@Test
	void aRoomQuizAsksTheQuestionServicesMediumMixInOrder() {
		List<Integer> asked = new ArrayList<>();
		RoomQuizQuestions quizQuestions = new RoomQuizQuestions((level, count) -> {
			asked.add(level);
			asked.add(count);
			return mediumMix(count);
		});
		service = new MatchService(matches, games, source, new MatchFactory(new Random(2)), events, seen,
				Clock.fixed(now, ZoneOffset.UTC), new MatchUpdates(), TopicPools.none(), quizQuestions);

		MultiplayerMatch m = service.start(new Match("q1", 0, List.of(900_000_001, 900_000_002), now,
				Map.of(900_000_001, "Ada", 900_000_002, "Bo"), Map.of(), 50, new Match.RoomQuiz(20, 20, 0)));

		assertThat(asked).containsExactly(RoomQuizQuestions.MEDIUM, 20);
		assertThat(m.isRoomQuiz()).isTrue();
		assertThat(m.getQuestions()).extracting(MatchQuestion::getQuestionId)
				.containsExactlyElementsOf(IntStream.rangeClosed(101, 120).boxed().toList());
		assertThat(m.getPlayers()).extracting(PlayerScore::getName).containsExactly("Ada", "Bo");
		assertThat(m.getScreenUserId()).isEqualTo(50);
		verify(games, never()).findById(any());
		// everyone plays the one stored match, so the same questions in the same order
		assertThat(service.start(new Match("q1", 0, List.of(900_000_001, 900_000_002), now, Map.of(), Map.of(), 50,
				new Match.RoomQuiz(20, 20, 0)))).isSameAs(m);
		assertThat(asked).hasSize(2);
	}

	@Test
	void aRoomQuizWithoutQuestionsDoesNotStart() {
		service = new MatchService(matches, games, source, new MatchFactory(new Random(2)), events, seen,
				Clock.fixed(now, ZoneOffset.UTC), new MatchUpdates(), TopicPools.none(), RoomQuizQuestions.none());

		assertThatThrownBy(() -> service.start(new Match("q1", 0, List.of(1), now, Map.of(), Map.of(), 50,
				new Match.RoomQuiz(20, 20, 0)))).isInstanceOf(NotFoundException.class);
	}

	@Test
	void aFinishedRoomQuizPublishesNoGameResult() {
		service = new MatchService(matches, games, source, new MatchFactory(new Random(2)), events, seen,
				Clock.fixed(now, ZoneOffset.UTC), new MatchUpdates(), TopicPools.none(),
				new RoomQuizQuestions((level, count) -> mediumMix(1)));
		service.start(new Match("q1", 0, List.of(1), now, Map.of(), Map.of(), 50, new Match.RoomQuiz(20, 20, 0)));

		MultiplayerMatch done = service.host("q1", 50, MultiplayerMatch.HostAction.END);

		assertThat(done.getStatus()).isEqualTo(MultiplayerMatch.Status.COMPLETED);
		verify(events, never()).matchCompleted(any());
	}

	@Test
	void answersRetryWhenAnotherAnswerWasSavedAtTheSameMoment() {
		Supplier<MultiplayerMatch> quiz = () -> MultiplayerMatch.roomQuiz("q1", List.of(1, 2), Map.of(),
				List.of(new MatchQuestion(5, "Q?", List.of("A", "B"), "A")), 20, 0, 50, now.minus(MultiplayerMatch.LEAD_IN));
		store.put("q1", quiz.get());
		AtomicInteger loads = new AtomicInteger();
		// the first load is a copy someone else saves over before this answer is saved
		when(matches.findById("q1")).thenAnswer(i -> Optional.of(loads.getAndIncrement() == 0 ? quiz.get() : store.get("q1")));
		AtomicInteger saves = new AtomicInteger();
		Mockito.doAnswer(i -> {
			if (saves.getAndIncrement() == 0) {
				throw new OptimisticLockingFailureException("someone else saved first");
			}
			MultiplayerMatch m = i.getArgument(0);
			store.put(m.getMatchId(), m);
			return m;
		}).when(matches).save(any());

		var result = service.answer("q1", 1, 5, "A");

		assertThat(result.outcome().correct()).isTrue();
		assertThat(saves.get()).isEqualTo(2);
		assertThat(loads.get()).isEqualTo(2);
	}
}
