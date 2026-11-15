package com.fazeel.maverick.challenge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.fazeel.maverick.repository.GameSessionRepository;
import com.fazeel.maverick.session.Difficulty;
import com.fazeel.maverick.session.GameSession;
import com.fazeel.maverick.session.QuizRuleException;
import com.fazeel.maverick.session.SessionQuestion;
import com.fazeel.maverick.session.SessionVariant;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FriendChallengeServiceTest {

	static final Instant T0 = Instant.parse("2026-11-15T12:00:00Z");

	@Mock
	FriendChallengeRepository challenges;
	@Mock
	GameSessionRepository sessions;

	FriendChallengeService service;

	@BeforeEach
	void setUp() {
		service = new FriendChallengeService(challenges, sessions, Clock.fixed(T0, ZoneOffset.UTC));
		when(challenges.save(any(FriendChallenge.class))).thenAnswer(i -> i.getArgument(0));
		when(challenges.findBySessionId(anyString())).thenReturn(Optional.empty());
		when(sessions.save(any(GameSession.class))).thenAnswer(i -> i.getArgument(0));
		when(sessions.findFirstByUserIdAndVariantAndVariantKey(anyInt(), any(), anyString())).thenReturn(Optional.empty());
	}

	static GameSession finished(SessionVariant variant) {
		List<SessionQuestion> qs = List.of(
				new SessionQuestion(1, Difficulty.EASY, "Q1", List.of("b", "a", "c", "d"), "a", 10, 10),
				new SessionQuestion(2, Difficulty.EASY, "Q2", List.of("x", "y"), "y", 10, 10));
		GameSession s = new GameSession("sess", 5, 1, 9, "Capitals", qs, T0).asVariant(variant, null, null);
		s.answer(1, "a", T0.plusSeconds(1));
		s.answer(2, "x", T0.plusSeconds(2));
		return s;
	}

	@Test
	void aFinishedGameBecomesAChallengeWithTheSameQuestionsUnanswered() {
		FriendChallenge c = service.create(finished(SessionVariant.STANDARD), "Ada");

		assertThat(c.id()).hasSize(8);
		assertThat(c.challengerName()).isEqualTo("Ada");
		assertThat(c.score()).isEqualTo(10);
		assertThat(c.questions()).extracting(SessionQuestion::getOptions)
				.containsExactly(List.of("b", "a", "c", "d"), List.of("x", "y")); // same order for the friend
		assertThat(c.questions()).allMatch(q -> q.getAnswer() == null && !q.isCorrect());
	}

	@Test
	void dailyRunsAndUnfinishedGamesCannotBeShared() {
		assertThatThrownBy(() -> service.create(finished(SessionVariant.DAILY), "Ada"))
				.isInstanceOf(QuizRuleException.class).hasMessageContaining("secret");
		GameSession unfinished = new GameSession("u", 5, 1, 9, "Capitals",
				List.of(new SessionQuestion(1, Difficulty.EASY, "Q1", List.of("a", "b"), "a", 10, 10)), T0);
		assertThatThrownBy(() -> service.create(unfinished, "Ada")).hasMessageContaining("Finish");
	}

	@Test
	void aFriendGetsOneAttemptWithTheScoreToBeat() {
		FriendChallenge c = service.create(finished(SessionVariant.STANDARD), "Ada");
		when(challenges.findById(c.id())).thenReturn(Optional.of(c));

		GameSession run = service.accept(c.id(), 2, "Bo");
		assertThat(run.getVariant()).isEqualTo(SessionVariant.CHALLENGE);
		assertThat(run.getVariantKey()).isEqualTo(c.id());
		assertThat(run.getOpponentScore()).isEqualTo(10);

		assertThatThrownBy(() -> service.accept(c.id(), 1, "Ada")).hasMessageContaining("your own");

		when(sessions.findFirstByUserIdAndVariantAndVariantKey(eq(2), eq(SessionVariant.CHALLENGE), eq(c.id())))
				.thenReturn(Optional.of(run));
		assertThat(service.accept(c.id(), 2, "Bo")).isSameAs(run); // resume
		run.answer(1, "a", T0.plusSeconds(1));
		run.answer(2, "y", T0.plusSeconds(2));
		assertThatThrownBy(() -> service.accept(c.id(), 2, "Bo")).hasMessageContaining("already taken");
	}
}
