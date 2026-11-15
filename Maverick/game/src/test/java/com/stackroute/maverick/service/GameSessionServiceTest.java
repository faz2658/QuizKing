package com.stackroute.maverick.service;

import static com.stackroute.maverick.session.TestGames.game;
import static com.stackroute.maverick.session.TestGames.question;
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

import com.stackroute.maverick.domain.Game;
import com.stackroute.maverick.domain.GameRequest;
import com.stackroute.maverick.domain.SinglePlayerResult;
import com.stackroute.maverick.kafka.GameEvents;
import com.stackroute.maverick.repository.GameRepository;
import com.stackroute.maverick.repository.GameSessionRepository;
import com.stackroute.maverick.session.GameSession;
import com.stackroute.maverick.session.QuizBuilder;
import com.stackroute.maverick.session.QuizRuleException;
import com.stackroute.maverick.session.SessionQuestion;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GameSessionServiceTest {

	@Mock
	GameRepository games;
	@Mock
	GameSessionRepository sessions;
	@Mock
	GameEvents events;

	MutableClock clock = new MutableClock(Instant.parse("2026-01-01T10:00:00Z"));
	Map<String, GameSession> store = new HashMap<>();
	GameSessionService service;

	@BeforeEach
	void setUp() {
		service = new GameSessionService(games, sessions, events, new QuizBuilder(new Random(1)), clock);
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
}
