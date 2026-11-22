package com.fazeel.quizcadia.match;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A "fastest finger first" match. Every player sees the same question at the same time; the first
 * player to answer correctly wins the question's points, a wrong answer costs a penalty (scores never
 * drop below zero), and every player can answer each question once. A question closes when every
 * player has answered or its time is up. All timing uses server time; the client only sends the option
 * it picked (the old engine trusted a correct answer and timestamp sent by the browser).
 * <p>
 * A {@link Format#ROOM_QUIZ room quiz} (party mode on a big screen) plays differently: everyone who answers right
 * scores, faster answers score more (up to {@link #pointsPerQuestion}, at least half of it), wrong answers cost
 * nothing, and the host paces it. Each question goes {@link Phase#QUESTION} → {@link Phase#REVEAL} (the answer and
 * the leaderboard) when everyone answered, time is up or the host says so; the next question opens when the host
 * moves on, or after {@link #revealSeconds} when that is above zero. The first question opens after a short
 * {@link #LEAD_IN} so phones can catch up.
 */
@Document(collection = "matches")
public class MultiplayerMatch {

	public static final Duration GRACE = Duration.ofSeconds(1);
	public static final int WRONG_ANSWER_PENALTY = 5;
	/** How often a bot picks the right answer. */
	public static final double BOT_ACCURACY = 0.55;

	/** IN_PROGRESS until the last question closes (or the host ends a room quiz), then COMPLETED. */
	public enum Status {
		IN_PROGRESS, COMPLETED
	}

	/** How the match is played: fastest finger (the first right answer wins the points) or a host-paced room quiz. */
	public enum Format {
		FASTEST_FINGER, ROOM_QUIZ
	}

	/** Where a match is right now; a fastest-finger match is always in {@code QUESTION} until it is over. */
	public enum Phase {
		GET_READY, QUESTION, REVEAL, FINISHED
	}

	/** What a room quiz's host can do from the big screen. */
	public enum HostAction {
		/** Close the open question now and show its answer. */
		REVEAL,
		/** From the answer, open the next question (or the final results after the last one). */
		NEXT,
		/** Stop the quiz here and show the final results. */
		END
	}

	/** Room quiz: time before the first question opens. */
	public static final Duration LEAD_IN = Duration.ofSeconds(5);
	/** Room quiz: the most a right answer scores; an answer at the buzzer scores half of it. */
	public static final int ROOM_QUIZ_POINTS = 1000;

	@Id
	private String matchId;
	@Version
	private Long version;
	private int gameId;
	private String gameName;
	private int categoryId;
	private int secondsPerQuestion;
	private int pointsPerQuestion;
	private List<PlayerScore> players = new ArrayList<>();
	private List<MatchQuestion> questions = new ArrayList<>();
	private int currentIndex;
	private Status status = Status.IN_PROGRESS;
	private Instant startedAt;
	private Instant completedAt;
	private boolean resultPublished;
	/** A party host who watches on a big screen without playing, or null. */
	private Integer screenUserId;
	/** Null in matches saved before room quizzes existed: those are fastest finger. */
	private Format format;
	/** Room quiz: how long the answer shows before the next question opens by itself; 0 waits for the host. */
	private int revealSeconds;
	/** Room quiz: when the last answer was revealed. */
	private Instant revealedAt;

	public MultiplayerMatch() {
	}

	/**
	 * A live room quiz for a party screen: {@code players} (one or more) answer {@code questions} in this order.
	 *
	 * @param screenUserId the host's big screen, which may watch and pace the quiz but not answer
	 */
	public static MultiplayerMatch roomQuiz(String matchId, List<Integer> players, Map<Integer, String> names,
			List<MatchQuestion> questions, int secondsPerQuestion, int revealSeconds, Integer screenUserId, Instant now) {
		if (players.isEmpty()) {
			throw new IllegalArgumentException("A quiz needs at least one player");
		}
		if (questions.isEmpty()) {
			throw new IllegalArgumentException("A quiz needs at least one question");
		}
		MultiplayerMatch m = new MultiplayerMatch();
		m.matchId = matchId;
		m.gameName = "Room quiz";
		m.format = Format.ROOM_QUIZ;
		m.secondsPerQuestion = secondsPerQuestion;
		m.pointsPerQuestion = ROOM_QUIZ_POINTS;
		m.revealSeconds = Math.max(0, revealSeconds);
		players.stream().distinct().forEach(p -> m.players.add(new PlayerScore(p, names.get(p), null)));
		m.questions = new ArrayList<>(questions);
		m.startedAt = now;
		m.questions.get(0).setOpenedAt(now.plus(LEAD_IN));
		m.screenUserId = screenUserId;
		return m;
	}

	public boolean isRoomQuiz() {
		return format == Format.ROOM_QUIZ;
	}

	public Format getFormat() {
		return format == null ? Format.FASTEST_FINGER : format;
	}

	public int getRevealSeconds() {
		return revealSeconds;
	}

	public Instant getRevealedAt() {
		return revealedAt;
	}

	/** Where the match is at this moment (see Phase). */
	public Phase phase(Instant now) {
		if (status == Status.COMPLETED) {
			return Phase.FINISHED;
		}
		if (isOpen()) {
			return now.isBefore(current().getOpenedAt()) ? Phase.GET_READY : Phase.QUESTION;
		}
		return Phase.REVEAL;
	}

	/** A question is open (or about to open, during the lead-in). */
	private boolean isOpen() {
		return status == Status.IN_PROGRESS && currentIndex < questions.size() && current().getOpenedAt() != null;
	}

	/** Room quiz: seconds until the first question opens (0 once it has). */
	public long secondsToStart(Instant now) {
		return isOpen() && now.isBefore(current().getOpenedAt())
				? (long) Math.ceil(Duration.between(now, current().getOpenedAt()).toMillis() / 1000.0)
				: 0;
	}

	/** Room quiz: seconds until the next question opens by itself, or 0 when the host moves on. */
	public long secondsToNext(Instant now) {
		if (!isRoomQuiz() || revealSeconds <= 0 || revealedAt == null || phase(now) != Phase.REVEAL) {
			return 0;
		}
		return Math.max(0, (long) Math.ceil(Duration.between(now, revealedAt.plusSeconds(revealSeconds)).toMillis() / 1000.0));
	}

	/**
	 * The host's big screen paces a room quiz. @return true if anything changed
	 *
	 * @throws MatchRuleException if this isn't a room quiz or the caller isn't its screen
	 */
	public boolean host(int userId, HostAction action, Instant now) {
		if (!isRoomQuiz()) {
			throw new MatchRuleException("Only a room quiz is paced by its host");
		}
		if (!isScreen(userId)) {
			throw new MatchRuleException("Only the host's screen can do that");
		}
		boolean changed = advanceClock(now);
		if (status == Status.COMPLETED) {
			return changed;
		}
		switch (action) {
			case REVEAL -> {
				if (!isOpen()) {
					return changed;
				}
				reveal(now);
			}
			case NEXT -> {
				if (isOpen() && now.isBefore(current().getOpenedAt())) {
					current().setOpenedAt(now); // no need to wait out the lead-in
				} else if (isOpen()) {
					reveal(now); // skipping ahead from a question shows its answer first
				} else {
					openNext(now);
				}
			}
			case END -> {
				if (isOpen()) {
					reveal(now);
				}
				finish(now);
			}
		}
		return true;
	}

	/** Room quiz: what an answer given at {@code at} scores, if right. */
	public int pointsFor(MatchQuestion q, PlayerAnswer a) {
		if (!a.correct()) {
			return 0;
		}
		if (!isRoomQuiz()) {
			return q.getWinnerUserId() != null && q.getWinnerUserId() == a.userId() ? pointsPerQuestion : 0;
		}
		long limit = Math.max(1, secondsPerQuestion * 1000L);
		long taken = Math.clamp(Duration.between(q.getOpenedAt(), a.answeredAt()).toMillis(), 0, limit);
		return (int) Math.round(pointsPerQuestion * (1 - 0.5 * taken / limit));
	}

	public MultiplayerMatch(String matchId, int gameId, String gameName, int categoryId, List<Integer> playerIds,
			List<MatchQuestion> questions, int secondsPerQuestion, int pointsPerQuestion, Instant now) {
		if (playerIds.size() < 2) {
			throw new IllegalArgumentException("A match needs at least two players");
		}
		if (questions.isEmpty()) {
			throw new IllegalArgumentException("A match needs at least one question");
		}
		this.matchId = matchId;
		this.gameId = gameId;
		this.gameName = gameName;
		this.categoryId = categoryId;
		this.secondsPerQuestion = secondsPerQuestion;
		this.pointsPerQuestion = pointsPerQuestion;
		playerIds.stream().distinct().forEach(p -> players.add(new PlayerScore(p)));
		this.questions = new ArrayList<>(questions);
		this.startedAt = now;
		this.questions.get(0).setOpenedAt(now);
	}

	/** Same, with the names and countries to show on the scoreboard. */
	public MultiplayerMatch(String matchId, int gameId, String gameName, int categoryId, List<Integer> playerIds,
			List<MatchQuestion> questions, int secondsPerQuestion, int pointsPerQuestion, Instant now,
			Map<Integer, String> names, Map<Integer, String> countries) {
		this(matchId, gameId, gameName, categoryId, playerIds, questions, secondsPerQuestion, pointsPerQuestion, now);
		this.players = new ArrayList<>(players.stream()
				.map(p -> new PlayerScore(p.getUserId(), names.get(p.getUserId()), countries.get(p.getUserId()))).toList());
	}

	/** Closes questions whose time ran out. @return true if anything changed */
	public boolean advanceClock(Instant now) {
		if (isRoomQuiz()) {
			return advanceRoomQuiz(now);
		}
		boolean changed = false;
		while (status == Status.IN_PROGRESS) {
			MatchQuestion q = current();
			Instant deadline = deadline(q);
			int before = currentIndex;
			changed |= playBots(now.isBefore(deadline) ? now : deadline);
			if (currentIndex != before) {
				continue; // a bot's answer closed the question: look at the next one
			}
			if (!now.isAfter(deadline.plus(GRACE))) {
				break;
			}
			closeCurrent(deadline.plus(GRACE)); // clients see the next question after the grace period
			changed = true;
		}
		return changed;
	}

	private boolean advanceRoomQuiz(Instant now) {
		boolean changed = false;
		while (status == Status.IN_PROGRESS) {
			if (isOpen()) {
				Instant closesAt = deadline(current()).plus(GRACE);
				if (!now.isAfter(closesAt)) {
					break;
				}
				reveal(closesAt);
			} else {
				if (revealSeconds <= 0 || revealedAt == null || now.isBefore(revealedAt.plusSeconds(revealSeconds))) {
					break;
				}
				openNext(revealedAt.plusSeconds(revealSeconds));
			}
			changed = true;
		}
		return changed;
	}

	/** Room quiz: closes the open question and shows its answer; the next one waits. */
	private void reveal(Instant at) {
		current().setClosedAt(at);
		currentIndex++;
		revealedAt = at;
	}

	/** Room quiz: opens the next question, or ends the quiz after the last one. */
	private void openNext(Instant at) {
		if (currentIndex >= questions.size()) {
			finish(at);
		} else {
			current().setOpenedAt(at);
		}
	}

	private void finish(Instant at) {
		status = Status.COMPLETED;
		completedAt = at;
	}

	/**
	 * Records a player's answer to the open question at time now, scored by the match's format. Throws when the
	 * player isn't in the match, the match is over, or the question isn't open.
	 */
	public AnswerOutcome answer(int userId, int questionId, String answer, Instant now) {
		PlayerScore player = player(userId).orElseThrow(() -> new MatchRuleException("You are not in this match"));
		advanceClock(now);
		if (status == Status.COMPLETED) {
			throw new MatchRuleException("The match is over");
		}
		if (!isOpen()) {
			throw new MatchRuleException("Question " + questionId + " is no longer open");
		}
		MatchQuestion q = current();
		if (q.getQuestionId() != questionId) {
			throw new MatchRuleException("Question " + questionId + " is no longer open");
		}
		if (now.isBefore(q.getOpenedAt())) {
			throw new MatchRuleException("Question " + questionId + " isn't open yet");
		}
		if (q.answerOf(userId).isPresent()) {
			throw new MatchRuleException("You already answered this question");
		}
		return record(player, q, answer, now);
	}

	private AnswerOutcome record(PlayerScore player, MatchQuestion q, String answer, Instant at) {
		boolean correct = answer != null && answer.trim().equalsIgnoreCase(q.getCorrectAnswer().trim());
		PlayerAnswer given = new PlayerAnswer(player.getUserId(), answer, correct, at);
		q.getAnswers().add(given);
		if (isRoomQuiz()) {
			if (correct) {
				player.won(pointsFor(q, given));
			} else {
				player.missed();
			}
			if (q.getAnswers().size() == players.size()) {
				reveal(at);
			}
			return new AnswerOutcome(correct, false, player.getScore());
		}
		boolean won = false;
		if (correct && q.getWinnerUserId() == null) {
			q.setWinnerUserId(player.getUserId());
			player.won(pointsPerQuestion);
			won = true;
		} else if (!correct) {
			player.wrong(WRONG_ANSWER_PENALTY);
		}
		if (q.getAnswers().size() == players.size()) {
			closeCurrent(at);
		}
		return new AnswerOutcome(correct, won, player.getScore());
	}

	/**
	 * Bots answer the open question after a delay of 2 seconds up to 2 seconds before the deadline, right
	 * {@value #BOT_ACCURACY} of the time. Both are fixed per match, question and bot, so a replayed request
	 * gives the same result. @return true if a bot answered
	 */
	private boolean playBots(Instant upTo) {
		boolean played = false;
		MatchQuestion q = current();
		List<PlayerScore> due = new ArrayList<>();
		Map<Integer, Instant> when = new HashMap<>();
		for (PlayerScore p : players) {
			if (!p.isBot() || q.answerOf(p.getUserId()).isPresent()) {
				continue;
			}
			Instant at = botAnswersAt(q, p);
			if (!upTo.isBefore(at)) {
				due.add(p);
				when.put(p.getUserId(), at);
			}
		}
		due.sort(Comparator.comparing(p -> when.get(p.getUserId())));
		for (PlayerScore bot : due) {
			if (currentIndex >= questions.size() || current() != q) {
				break;
			}
			Random r = new Random(Objects.hash(matchId, q.getQuestionId(), bot.getUserId(), "pick"));
			String pick;
			if (r.nextDouble() < BOT_ACCURACY) {
				pick = q.getCorrectAnswer();
			} else {
				List<String> wrong = q.getOptions().stream().filter(o -> !o.equalsIgnoreCase(q.getCorrectAnswer())).toList();
				pick = wrong.isEmpty() ? q.getCorrectAnswer() : wrong.get(r.nextInt(wrong.size()));
			}
			record(bot, q, pick, when.get(bot.getUserId()));
			played = true;
		}
		return played;
	}

	/** When a bot answers question {@code q}: fixed per match, question and bot (see {@link #playBots}). */
	private Instant botAnswersAt(MatchQuestion q, PlayerScore bot) {
		Random r = new Random(Objects.hash(matchId, q.getQuestionId(), bot.getUserId()));
		long window = Math.max(1000, (secondsPerQuestion - 4) * 1000L);
		return q.getOpenedAt().plusMillis(2000 + (long) (r.nextDouble() * window));
	}

	/**
	 * The next moment the match changes by itself, without anyone answering: a bot answering, or the open question
	 * closing after its grace period. Null once the match is over. Lets a waiting client be answered exactly then.
	 */
	public Instant nextChangeAt(Instant now) {
		if (status != Status.IN_PROGRESS) {
			return null;
		}
		if (isRoomQuiz()) {
			if (isOpen()) {
				Instant opens = current().getOpenedAt();
				return now.isBefore(opens) ? opens : deadline(current()).plus(GRACE);
			}
			// showing an answer: the next question opens by itself, or whenever the host moves on
			return revealSeconds > 0 && revealedAt != null ? revealedAt.plusSeconds(revealSeconds) : now.plus(Duration.ofMinutes(1));
		}
		MatchQuestion q = current();
		Instant next = deadline(q).plus(GRACE);
		for (PlayerScore p : players) {
			if (p.isBot() && q.answerOf(p.getUserId()).isEmpty()) {
				Instant at = botAnswersAt(q, p);
				if (at.isAfter(now) && at.isBefore(next)) {
					next = at;
				}
			}
		}
		return next;
	}

	/** The open question; none while a room quiz shows an answer, or once the match is over. */
	public Optional<MatchQuestion> currentQuestion() {
		return isOpen() ? Optional.of(current()) : Optional.empty();
	}

	/** The most recently closed question, so clients can show its answer and winner. */
	public Optional<MatchQuestion> lastClosedQuestion() {
		return currentIndex == 0 ? Optional.empty() : Optional.of(questions.get(currentIndex - 1));
	}

	/** Whole seconds left on the open question (never more than its time limit; 0 when none is open). */
	public long secondsRemaining(Instant now) {
		return currentQuestion().map(q -> Math.min(secondsPerQuestion,
				Math.max(0, Duration.between(now, deadline(q)).toSeconds()))).orElse(0L);
	}

	/** Players ordered by score, then by questions won, then by fewest wrong answers. */
	public List<PlayerScore> standings() {
		return players.stream().sorted(Comparator.comparingInt(PlayerScore::getScore).reversed()
				.thenComparing(Comparator.comparingInt(PlayerScore::getQuestionsWon).reversed())
				.thenComparingInt(PlayerScore::getWrongAnswers)).toList();
	}

	/** The party host's big screen: may watch the match but not answer. */
	public boolean isScreen(int userId) {
		return screenUserId != null && screenUserId == userId;
	}

	/** Marks the party host's screen, which may watch (and pace a room quiz) but not answer. */
	public MultiplayerMatch shownOn(Integer screenUserId) {
		this.screenUserId = screenUserId;
		return this;
	}

	public Integer getScreenUserId() {
		return screenUserId;
	}

	public boolean hasPlayer(int userId) {
		return player(userId).isPresent();
	}

	private Optional<PlayerScore> player(int userId) {
		return players.stream().filter(p -> p.getUserId() == userId).findFirst();
	}

	private Instant deadline(MatchQuestion q) {
		return q.getOpenedAt().plusSeconds(secondsPerQuestion);
	}

	private MatchQuestion current() {
		return questions.get(currentIndex);
	}

	private void closeCurrent(Instant at) {
		current().setClosedAt(at);
		currentIndex++;
		if (currentIndex >= questions.size()) {
			status = Status.COMPLETED;
			completedAt = at;
		} else {
			current().setOpenedAt(at);
		}
	}

	/** Notes that the result went to Kafka, so it is published exactly once. */
	public void markResultPublished() {
		resultPublished = true;
	}

	public boolean isResultPublished() {
		return resultPublished;
	}

	public String getMatchId() {
		return matchId;
	}

	public int getGameId() {
		return gameId;
	}

	public String getGameName() {
		return gameName;
	}

	public int getCategoryId() {
		return categoryId;
	}

	public int getSecondsPerQuestion() {
		return secondsPerQuestion;
	}

	public int getPointsPerQuestion() {
		return pointsPerQuestion;
	}

	public List<PlayerScore> getPlayers() {
		return players;
	}

	public List<MatchQuestion> getQuestions() {
		return questions;
	}

	public int getCurrentIndex() {
		return currentIndex;
	}

	public Status getStatus() {
		return status;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}

	/**
	 * How an answer went: right or not, whether it won the question (fastest finger), and the player's score after
	 * it.
	 */
	public record AnswerOutcome(boolean correct, boolean wonQuestion, int score) {
	}
}
