package com.fazeel.quizcadia.daily;

import static com.fazeel.quizcadia.session.TestGames.game;
import static com.fazeel.quizcadia.session.TestGames.question;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.fazeel.quizcadia.domain.Game;
import com.fazeel.quizcadia.domain.Questions;
import com.fazeel.quizcadia.domain.Topic;
import com.fazeel.quizcadia.repository.GameRepository;
import com.fazeel.quizcadia.repository.GameSessionRepository;
import com.fazeel.quizcadia.session.GameSession;
import com.fazeel.quizcadia.session.QuizRuleException;
import com.fazeel.quizcadia.session.SessionQuestion;
import com.fazeel.quizcadia.session.SessionVariant;
import com.fazeel.quizcadia.session.TestGames;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class DailyChallengeServiceTest {

	static final LocalDate DAY = LocalDate.parse("2026-11-15");

	@Mock
	GameRepository games;
	@Mock
	DailyChallengeRepository challenges;
	@Mock
	GameSessionRepository sessions;

	DailyChallengeService service;

	/** Five topics with three questions per level each. */
	public static List<Game> catalog() {
		List<Game> all = new ArrayList<>();
		int id = 1;
		for (int t = 1; t <= 5; t++) {
			List<Questions> qs = new ArrayList<>();
			for (int level = 1; level <= 3; level++) {
				for (int i = 0; i < 3; i++, id++) {
					qs.add(question(id, level, "A" + id, "A" + id, "B" + id, "C" + id, "D" + id));
				}
			}
			Game g = game(t, null, qs.toArray(Questions[]::new));
			g.setTopic(new Topic(t, "Topic " + t, null, List.of()));
			all.add(g);
		}
		return all;
	}

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(Instant.parse("2026-11-15T15:00:00Z"), ZoneOffset.UTC);
		service = new DailyChallengeService(games, challenges, sessions, clock);
		when(games.findAll()).thenReturn(catalog());
		when(challenges.findById(anyString())).thenReturn(Optional.empty());
		when(challenges.insert(any(DailyChallenge.class))).thenAnswer(i -> i.getArgument(0));
		when(sessions.save(any(GameSession.class))).thenAnswer(i -> i.getArgument(0));
		when(sessions.findFirstByUserIdAndVariantAndVariantKey(anyInt(), any(), anyString()))
				.thenReturn(Optional.empty());
	}

	@Test
	void sameDaySameQuestionsForEveryone() {
		DailyChallenge a = DailyChallengeService.build(DAY, catalog());
		DailyChallenge b = DailyChallengeService.build(DAY, catalog());
		DailyChallenge tomorrow = DailyChallengeService.build(DAY.plusDays(1), catalog());

		assertThat(ids(a)).isEqualTo(ids(b));
		assertThat(a.questions()).extracting(SessionQuestion::getOptions)
				.isEqualTo(b.questions().stream().map(SessionQuestion::getOptions).toList());
		assertThat(ids(tomorrow)).isNotEqualTo(ids(a));
	}

	@Test
	void tenQuestionsEasyFirstAcrossManyTopics() {
		DailyChallenge c = DailyChallengeService.build(DAY, catalog());

		assertThat(c.questions()).hasSize(10);
		assertThat(c.questions()).extracting(q -> q.getDifficulty().level())
				.containsExactly(1, 1, 1, 1, 2, 2, 2, 3, 3, 3);
		assertThat(ids(c)).doesNotHaveDuplicates();
		assertThat(c.topics().stream().distinct()).hasSize(5); // every topic gets used
	}

	@Test
	void oneAttemptPerDay() {
		GameSession started = service.start(42, "Ada");

		assertThat(started.getVariant()).isEqualTo(SessionVariant.DAILY);
		assertThat(started.getVariantKey()).isEqualTo("2026-11-15");
		assertThat(started.getPlayerName()).isEqualTo("Ada");
		assertThat(started.getQuestions()).allMatch(q -> q.getAnswer() == null);

		// an unfinished run is resumed, not restarted
		when(sessions.findFirstByUserIdAndVariantAndVariantKey(eq(42), eq(SessionVariant.DAILY), eq("2026-11-15")))
				.thenReturn(Optional.of(started));
		assertThat(service.start(42, "Ada")).isSameAs(started);

		// a finished run cannot be replayed
		for (SessionQuestion q : List.copyOf(started.getQuestions())) {
			started.answer(q.getQuestionId(), q.getCorrectAnswer(), Instant.parse("2026-11-15T15:00:01Z"));
		}
		assertThatThrownBy(() -> service.start(42, "Ada")).isInstanceOf(QuizRuleException.class)
				.hasMessageContaining("already played");
	}

	@Test
	void theChallengeIsStoredOnFirstRequest() {
		DailyChallenge stored = DailyChallengeService.build(DAY, catalog());
		when(challenges.findById("2026-11-15")).thenReturn(Optional.of(stored));

		assertThat(service.challenge(DAY)).isSameAs(stored);
	}

	static List<Integer> ids(DailyChallenge c) {
		return c.questions().stream().map(SessionQuestion::getQuestionId).toList();
	}

	@Test
	void theSharedChallengeLeavesCountrySpecificQuestionsOut() {
		List<Questions> qs = new ArrayList<>();
		for (int i = 0; i < 12; i++) {
			Questions home = TestGames.question(100 + i, i % 3 + 1, "a", "a", "b");
			home.setCountry("IN");
			qs.add(home);
			qs.add(TestGames.question(200 + i, i % 3 + 1, "a", "a", "b"));
		}
		Game g = TestGames.game(1, null,
				qs.toArray(Questions[]::new));

		DailyChallengeService.Picked p = DailyChallengeService.pick(42, List.of(g), 3, 3, 3);

		Assertions.assertThat(p.questions()).hasSize(9)
				.allMatch(q -> q.getQuestionId() >= 200);
	}
}
