package com.fazeel.quizcadia.challenge;

import static com.fazeel.quizcadia.session.TestGames.game;
import static com.fazeel.quizcadia.session.TestGames.question;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.fazeel.quizcadia.challenge.FriendDuelService.Standing;
import com.fazeel.quizcadia.challenge.FriendDuelService.State;
import com.fazeel.quizcadia.domain.Game;
import com.fazeel.quizcadia.domain.GameQuestionLevel;
import com.fazeel.quizcadia.domain.Questions;
import com.fazeel.quizcadia.kafka.GameEvents;
import com.fazeel.quizcadia.repository.GameRepository;
import com.fazeel.quizcadia.repository.GameSessionRepository;
import com.fazeel.quizcadia.repository.SeenQuestionRepository;
import com.fazeel.quizcadia.service.GameSessionService;
import com.fazeel.quizcadia.session.GameSession;
import com.fazeel.quizcadia.session.QuizBuilder;
import com.fazeel.quizcadia.session.QuizRuleException;
import com.fazeel.quizcadia.session.SeenQuestion;
import com.fazeel.quizcadia.session.SessionQuestion;
import com.fazeel.quizcadia.session.SessionStatus;
import com.fazeel.quizcadia.session.SessionVariant;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FriendDuelServiceTest {

	static final int ADA = 1;
	static final int BO = 2;
	static final int CY = 3;
	static final int GAME = 5;

	@Mock
	GameRepository games;
	@Mock
	GameSessionRepository sessionRepo;
	@Mock
	GameEvents events;
	@Mock
	SeenQuestionRepository seen;
	@Mock
	FriendDuelRepository duelRepo;

	final Map<String, GameSession> sessions = new HashMap<>();
	final Map<String, FriendDuel> duels = new HashMap<>();
	final List<SeenQuestion> sightings = new ArrayList<>();
	final MutableClock clock = new MutableClock(Instant.parse("2026-11-17T12:00:00Z"));
	GameSessionService gameSessions;
	FriendDuelService service;

	/** 20 Indian, 20 German and 20 general questions (ids 100+, 200+, 300+); a game asks 10. */
	static Game mixedGame() {
		List<Questions> qs = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			qs.add(tagged(question(100 + i, 1, "a", "a", "b", "c", "d"), "IN"));
			qs.add(tagged(question(200 + i, 1, "a", "a", "b", "c", "d"), "DE"));
			qs.add(tagged(question(300 + i, 1, "a", "a", "b", "c", "d"), null));
		}
		return game(GAME, null, qs.toArray(Questions[]::new));
	}

	static Questions tagged(Questions q, String country) {
		q.setCountry(country);
		return q;
	}

	@BeforeEach
	void setUp() {
		gameSessions = new GameSessionService(games, sessionRepo, events, seen, new QuizBuilder(new Random(1)), clock);
		service = new FriendDuelService(duelRepo, gameSessions, sessionRepo, seen, new QuizBuilder(new Random(2)), clock);
		when(games.findById(GAME)).thenAnswer(i -> Optional.of(mixedGame()));
		when(sessionRepo.save(any(GameSession.class))).thenAnswer(i -> {
			GameSession s = i.getArgument(0);
			sessions.put(s.getId(), s);
			return s;
		});
		when(sessionRepo.findById(any())).thenAnswer(i -> Optional.ofNullable(sessions.get(i.<String>getArgument(0))));
		when(duelRepo.save(any(FriendDuel.class))).thenAnswer(i -> {
			FriendDuel d = i.getArgument(0);
			duels.put(d.id(), d);
			return d;
		});
		when(duelRepo.findById(any())).thenAnswer(i -> Optional.ofNullable(duels.get(i.<String>getArgument(0))));
		when(seen.findByUserIdAndQuestionIdIn(anyInt(), anyCollection())).thenAnswer(i -> sightings.stream()
				.filter(s -> s.userId() == i.<Integer>getArgument(0)).toList());
	}

	private FriendDuel accepted(String challengerCountry, String friendCountry, boolean international) {
		FriendDuel d = service.create(ADA, "Ada", GAME, international, challengerCountry);
		return service.accept(d.id(), BO, "Bo", friendCountry);
	}

	private static List<Integer> ids(List<SessionQuestion> qs) {
		return qs.stream().map(SessionQuestion::getQuestionId).toList();
	}

	private static long between(List<SessionQuestion> qs, int from, int to) {
		return qs.stream().filter(q -> q.getQuestionId() >= from && q.getQuestionId() < to).count();
	}

	/** Answers every question of the player's attempt, {@code right} of them correctly. */
	private GameSession playThrough(String duelId, int userId, int right) {
		GameSession s = service.play(duelId, userId, "x");
		for (int i = 0; i < s.getQuestions().size(); i++) {
			SessionQuestion q = gameSessions.get(s.getId()).currentQuestion().orElseThrow();
			String pick = i < right ? q.getCorrectAnswer()
					: q.getOptions().stream().filter(o -> !o.equals(q.getCorrectAnswer())).findFirst().orElseThrow();
			gameSessions.answer(s.getId(), q.getQuestionId(), pick);
		}
		return gameSessions.get(s.getId());
	}

	@Test
	void friendsFromTheSameCountryGetMostlyItsQuestionsAndTheRestGeneral() {
		FriendDuel d = accepted("IN", "in", false);

		assertThat(d.questions()).hasSize(10);
		assertThat(between(d.questions(), 100, 200)).isEqualTo(8); // 75% of 10, rounded
		assertThat(between(d.questions(), 300, 400)).isEqualTo(2);
		assertThat(between(d.questions(), 200, 300)).isZero();
	}

	@Test
	void friendsFromDifferentCountriesGetGeneralQuestionsOnly() {
		FriendDuel d = accepted("IN", "DE", false);

		assertThat(d.questions()).hasSize(10).allMatch(q -> q.getQuestionId() >= 300);
	}

	@Test
	void anUnknownCountryMeansGeneralQuestionsOnly() {
		assertThat(accepted("IN", null, false).questions()).allMatch(q -> q.getQuestionId() >= 300);
		duels.clear();
		assertThat(accepted(null, null, false).questions()).allMatch(q -> q.getQuestionId() >= 300);
	}

	@Test
	void internationalUsesQuestionsFromEverywhere() {
		Game big = mixedGame();
		big.setQuestionLevels(new GameQuestionLevel(0, 60, 0, 0));
		when(games.findById(GAME)).thenReturn(Optional.of(big));

		FriendDuel d = accepted("IN", "IN", true);

		assertThat(d.questions()).hasSize(60); // every country's questions, not 75/25 for India
		assertThat(between(d.questions(), 200, 300)).isEqualTo(20);
	}

	@Test
	void bothPlayersGetExactlyTheSameQuestionsInTheSameOrderWithTheSameOptions() {
		FriendDuel d = accepted("DE", "DE", false);

		GameSession ada = service.play(d.id(), ADA, "Ada");
		GameSession bo = service.play(d.id(), BO, "Bo");

		assertThat(ids(ada.getQuestions())).isEqualTo(ids(bo.getQuestions())).isEqualTo(ids(d.questions()));
		assertThat(ada.getQuestions()).extracting(SessionQuestion::getOptions)
				.isEqualTo(bo.getQuestions().stream().map(SessionQuestion::getOptions).toList());
		assertThat(ada.getVariant()).isEqualTo(SessionVariant.DUEL);
		assertThat(ada.getVariantKey()).isEqualTo(d.id());
	}

	@Test
	void questionsNeitherFriendHasSeenComeFirst() {
		for (int i = 0; i < 18; i++) {
			sightings.add(SeenQuestion.of(i % 2 == 0 ? ADA : BO, 300 + i, clock.instant()));
		}

		FriendDuel d = accepted("IN", "DE", false); // general only: 20, of which 18 seen by one of them

		assertThat(ids(d.questions())).contains(318, 319);
	}

	@Test
	void playingAgainResumesTheSameGameAndAFinishedGameCannotBeReplayed() {
		FriendDuel d = accepted("IN", "IN", false);
		GameSession first = service.play(d.id(), BO, "Bo");

		assertThat(service.play(d.id(), BO, "Bo").getId()).isEqualTo(first.getId());

		playThrough(d.id(), BO, 3);
		assertThatThrownBy(() -> service.play(d.id(), BO, "Bo")).isInstanceOf(QuizRuleException.class)
				.hasMessageContaining("already played");
	}

	@Test
	void theHigherScoreWinsOnceBothHavePlayed() {
		FriendDuel d = accepted("IN", "IN", false);
		playThrough(d.id(), ADA, 4);
		assertThat(service.standing(duels.get(d.id())).state()).isEqualTo(State.PLAYING);

		playThrough(d.id(), BO, 7);
		Standing st = service.standing(duels.get(d.id()));

		assertThat(st.state()).isEqualTo(State.FINISHED);
		assertThat(st.winnerUserId()).isEqualTo(BO);
		assertThat(st.challenger().session().getScore()).isLessThan(st.opponent().session().getScore());
		assertThat(st.challenger().session().getStatus()).isEqualTo(SessionStatus.COMPLETED);
	}

	@Test
	void equalScoresAreATie() {
		FriendDuel d = accepted("IN", "IN", false);
		playThrough(d.id(), ADA, 0);
		playThrough(d.id(), BO, 0);

		Standing st = service.standing(duels.get(d.id()));
		assertThat(st.tie()).isTrue();
		assertThat(st.winnerUserId()).isNull();
	}

	@Test
	void youCannotAcceptYourOwnChallengeAndOnlyOneFriendCanTakeIt() {
		FriendDuel d = service.create(ADA, "Ada", GAME, false, "IN");

		assertThatThrownBy(() -> service.accept(d.id(), ADA, "Ada", "IN")).hasMessageContaining("your own");
		service.accept(d.id(), BO, "Bo", "IN");
		assertThat(service.accept(d.id(), BO, "Bo", "IN").opponent().userId()).isEqualTo(BO); // again: harmless
		assertThatThrownBy(() -> service.accept(d.id(), CY, "Cy", "IN")).hasMessageContaining("already accepted");
		assertThatThrownBy(() -> service.play(d.id(), CY, "Cy")).hasMessageContaining("two other players");
	}

	@Test
	void nobodyPlaysBeforeTheFriendAccepts() {
		FriendDuel d = service.create(ADA, "Ada", GAME, false, "IN");

		assertThat(d.questions()).isEmpty();
		assertThat(service.standing(d).state()).isEqualTo(State.WAITING);
		assertThatThrownBy(() -> service.play(d.id(), ADA, "Ada")).hasMessageContaining("hasn't accepted");
	}

	@Test
	void aDeclinedChallengeIsClosed() {
		FriendDuel d = service.create(ADA, "Ada", GAME, false, "IN");

		FriendDuel declined = service.decline(d.id(), BO, "Bo");

		assertThat(service.standing(declined).state()).isEqualTo(State.DECLINED);
		assertThat(declined.opponent().name()).isEqualTo("Bo");
		assertThatThrownBy(() -> service.accept(d.id(), CY, "Cy", "IN")).hasMessageContaining("declined");
		assertThatThrownBy(() -> service.decline(d.id(), ADA, "Ada")).hasMessageContaining("your own");
	}

	@Test
	void anUnansweredChallengeExpires() {
		FriendDuel d = service.create(ADA, "Ada", GAME, false, "IN");

		clock.advance(FriendDuelService.ANSWER_WITHIN);

		assertThat(service.standing(d).state()).isEqualTo(State.EXPIRED);
		assertThatThrownBy(() -> service.accept(d.id(), BO, "Bo", "IN")).hasMessageContaining("expired");
	}

	@Test
	void afterThePlayTimeOnlyThoseWhoPlayedCountAndNobodyNewCanStart() {
		FriendDuel d = accepted("IN", "IN", false);
		playThrough(d.id(), ADA, 1);

		clock.advance(FriendDuelService.PLAY_WITHIN);
		Standing st = service.standing(duels.get(d.id()));

		assertThat(st.state()).isEqualTo(State.FINISHED);
		assertThat(st.winnerUserId()).isEqualTo(ADA); // Bo never played
		assertThatThrownBy(() -> service.play(d.id(), BO, "Bo")).hasMessageContaining("expired");
	}

	@Test
	void anAcceptedChallengeNobodyPlayedExpires() {
		FriendDuel d = accepted("IN", "IN", false);

		clock.advance(FriendDuelService.PLAY_WITHIN);

		assertThat(service.standing(duels.get(d.id())).state()).isEqualTo(State.EXPIRED);
	}

	@Test
	void aGameWithoutQuestionsCannotBeChallenged() {
		when(games.findById(GAME)).thenReturn(Optional.of(game(GAME, null, question(1, 1, "x", "a", "b"))));

		assertThatThrownBy(() -> service.create(ADA, "Ada", GAME, false, "IN")).isInstanceOf(QuizRuleException.class);
	}

	@Test
	void sharedCountryNeedsEveryoneFromTheSameKnownCountry() {
		assertThat(QuizBuilder.sharedCountry(List.of("in", "IN "))).isEqualTo("IN");
		assertThat(QuizBuilder.sharedCountry(List.of("IN", "DE"))).isNull();
		assertThat(QuizBuilder.sharedCountry(Arrays.asList("IN", null))).isNull();
		assertThat(QuizBuilder.sharedCountry(List.of())).isNull();
	}

	static final class MutableClock extends Clock {
		private Instant now;

		MutableClock(Instant now) {
			this.now = now;
		}

		void advance(Duration d) {
			now = now.plus(d);
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
