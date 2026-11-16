package com.fazeel.quizcadia.service;

import static com.fazeel.quizcadia.session.TestGames.game;
import static com.fazeel.quizcadia.session.TestGames.question;
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

import com.fazeel.quizcadia.domain.Game;
import com.fazeel.quizcadia.domain.GameRequest;
import com.fazeel.quizcadia.domain.SinglePlayerResult;
import com.fazeel.quizcadia.kafka.GameEvents;
import com.fazeel.quizcadia.repository.GameRepository;
import com.fazeel.quizcadia.repository.GameSessionRepository;
import com.fazeel.quizcadia.repository.SeenQuestionRepository;
import com.fazeel.quizcadia.session.SeenQuestion;
import com.fazeel.quizcadia.session.GameSession;
import com.fazeel.quizcadia.session.QuizBuilder;
import com.fazeel.quizcadia.session.QuizRuleException;
import com.fazeel.quizcadia.session.SessionQuestion;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GameSessionServiceTest {

	@Mock
	GameRepository games;
	@Mock
	GameSessionRepository sessions;
	@Mock
	GameEvents events;
	@Mock
	SeenQuestionRepository seen;

	MutableClock clock = new MutableClock(Instant.parse("2026-01-01T10:00:00Z"));
	Map<String, GameSession> store = new HashMap<>();
	GameSessionService service;

	@BeforeEach
	void setUp() {
		service = new GameSessionService(games, sessions, events, seen, new QuizBuilder(new Random(1)), clock);
		when(sessions.save(any())).thenAnswer(inv -> {
			GameSession s = inv.getArgument(0);
			store.put(s.getId(), s);
			return s;
		});
		when(sessions.findById(any())).thenAnswer(inv -> Optional.ofNullable(store.get(inv.getArgument(0))));
	}

	private Game twoQuestionGame() {
		return game(5, null, question(1, 1, "a", "a", "b"), question(2, 1, "c", "c", "d"));
	}

	@Test
	void unknownGameIsRequestedFromTheGameManager() {
		when(games.findById(5)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.start(5, 42)).isInstanceOf(GameNotReadyException.class);
		verify(events).requestGame(new GameRequest(5, 42));
	}

	@Test
	void gameWithoutPlayableQuestionsCannotBeStarted() {
		when(games.findById(5)).thenReturn(Optional.of(game(5, null, question(1, 1, "x", "a", "b"))));

		assertThatThrownBy(() -> service.start(5, 42)).isInstanceOf(QuizRuleException.class);
	}

	@Test
	void eachStartCreatesAnIndependentSession() {
		when(games.findById(5)).thenReturn(Optional.of(twoQuestionGame()));

		GameSession a = service.start(5, 1);
		GameSession b = service.start(5, 2);

		assertThat(a.getId()).isNotEqualTo(b.getId());
		service.answer(a.getId(), a.currentQuestion().orElseThrow().getQuestionId(), "anything");
		assertThat(service.get(b.getId()).getCurrentIndex()).isZero();
	}

	@Test
	void resultIsPublishedExactlyOnceWhenTheLastQuestionIsAnswered() {
		when(games.findById(5)).thenReturn(Optional.of(twoQuestionGame()));
		GameSession s = service.start(5, 42);

		answerCorrectly(s.getId());
		verify(events, never()).publishResult(any());
		answerCorrectly(s.getId());
		service.result(s.getId());
		service.get(s.getId());

		ArgumentCaptor<SinglePlayerResult> result = ArgumentCaptor.forClass(SinglePlayerResult.class);
		verify(events, times(1)).publishResult(result.capture());
		assertThat(result.getValue().score()).isEqualTo(20);
		assertThat(result.getValue().percentage()).isEqualTo(100);
		assertThat(result.getValue().userId()).isEqualTo(42);
		assertThat(result.getValue().playedData()).hasSize(2);
	}

	@Test
	void resultIsPublishedWhenThePlayerTimesOutOnTheLastQuestion() {
		when(games.findById(5)).thenReturn(Optional.of(twoQuestionGame()));
		GameSession s = service.start(5, 42);

		clock.advanceSeconds(3600);
		GameSession finished = service.result(s.getId());

		assertThat(finished.getScore()).isZero();
		verify(events).publishResult(any());
	}

	@Test
	void resultIsNotAvailableWhileTheGameIsRunning() {
		when(games.findById(5)).thenReturn(Optional.of(twoQuestionGame()));
		GameSession s = service.start(5, 42);

		assertThatThrownBy(() -> service.result(s.getId())).isInstanceOf(QuizRuleException.class);
	}

	@Test
	void playersGetQuestionsTheyHaveNotSeenAndTheSessionIsRemembered() {
		Game g = game(5, new com.fazeel.quizcadia.domain.GameQuestionLevel(0, 2, 0, 0), question(1, 1, "a", "a", "b"),
				question(2, 1, "a", "a", "b"), question(3, 1, "a", "a", "b"), question(4, 1, "a", "a", "b"));
		when(games.findById(5)).thenReturn(Optional.of(g));
		when(seen.findByUserIdAndQuestionIdIn(org.mockito.ArgumentMatchers.eq(42), any())).thenReturn(java.util.List.of(
				SeenQuestion.of(42, 1, clock.instant()), SeenQuestion.of(42, 2, clock.instant())));

		GameSession s = service.start(5, 42);

		assertThat(s.getQuestions()).extracting(SessionQuestion::getQuestionId).containsExactlyInAnyOrder(3, 4);
		verify(seen).saveAll(org.mockito.ArgumentMatchers.argThat((Iterable<SeenQuestion> it) -> {
			java.util.List<Integer> ids = new java.util.ArrayList<>();
			it.forEach(x -> ids.add(x.questionId()));
			return ids.containsAll(java.util.List.of(3, 4)) && ids.size() == 2;
		}));
	}

	@Test
	void unknownSessionIsNotFound() {
		assertThatThrownBy(() -> service.get("nope")).isInstanceOf(NotFoundException.class);
	}

	private void answerCorrectly(String id) {
		SessionQuestion q = service.get(id).currentQuestion().orElseThrow();
		clock.advanceSeconds(1);
		service.answer(id, q.getQuestionId(), q.getCorrectAnswer());
	}

	static final class MutableClock extends Clock {
		private Instant now;

		MutableClock(Instant now) {
			this.now = now;
		}

		void advanceSeconds(long s) {
			now = now.plusSeconds(s);
		}

		@Override
		public java.time.ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}
	}

	static Game topic(int id, String name, int firstQuestion) {
		java.util.List<com.fazeel.quizcadia.domain.Questions> qs = new java.util.ArrayList<>();
		for (int level = 1; level <= 3; level++) {
			for (int i = 0; i < 4; i++) {
				int qid = firstQuestion + level * 10 + i;
				qs.add(question(qid, level, "A" + qid, "A" + qid, "B" + qid, "C" + qid, "D" + qid));
			}
		}
		Game g = game(id, null, qs.toArray(com.fazeel.quizcadia.domain.Questions[]::new));
		g.setTopic(new com.fazeel.quizcadia.domain.Topic(id, name, null, java.util.List.of()));
		return g;
	}

	@Test
	void customQuizMixesTopicsAtTheChosenDifficulty() {
		when(games.findById(1)).thenReturn(Optional.of(topic(1, "Art", 100)));
		when(games.findById(2)).thenReturn(Optional.of(topic(2, "History", 200)));

		GameSession hard = service.startCustom(java.util.List.of(1, 2), GameSessionService.CustomDifficulty.ADVANCED, 6, 42, "Ada");
		assertThat(hard.getQuestions()).hasSize(6).allMatch(q -> q.getDifficulty() == com.fazeel.quizcadia.session.Difficulty.ADVANCED);
		assertThat(hard.getQuestions()).extracting(q -> q.getQuestionId() / 100).contains(1, 2); // both topics
		assertThat(hard.getVariant()).isEqualTo(com.fazeel.quizcadia.session.SessionVariant.CUSTOM);
		assertThat(hard.getGameName()).isEqualTo("Custom quiz: Art, History");

		GameSession mixed = service.startCustom(java.util.List.of(1), GameSessionService.CustomDifficulty.MIXED, 10, 42, "Ada");
		assertThat(mixed.getQuestions()).extracting(q -> q.getDifficulty().level()).containsExactly(1, 1, 1, 1, 2, 2, 2, 3, 3, 3);
	}

	@Test
	void customQuizLimits() {
		assertThatThrownBy(() -> service.startCustom(java.util.List.of(), GameSessionService.CustomDifficulty.MIXED, 10, 42, "Ada"))
				.isInstanceOf(QuizRuleException.class);
		assertThatThrownBy(() -> service.startCustom(java.util.List.of(1), GameSessionService.CustomDifficulty.MIXED, 30, 42, "Ada"))
				.hasMessageContaining("5 to 20");
		when(games.findById(9)).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.startCustom(java.util.List.of(9), GameSessionService.CustomDifficulty.EASY, 5, 42, "Ada"))
				.isInstanceOf(GameNotReadyException.class);
	}
}
