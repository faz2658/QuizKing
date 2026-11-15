package com.fazeel.maverick.match;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A "fastest finger first" match. Every player sees the same question at the same time; the first
 * player to answer correctly wins the question's points, a wrong answer costs a penalty (scores never
 * drop below zero), and every player can answer each question once. A question closes when every
 * player has answered or its time is up. All timing uses server time; the client only sends the option
 * it picked (the old engine trusted a correct answer and timestamp sent by the browser).
 */
@Document(collection = "matches")
public class MultiplayerMatch {

	public static final Duration GRACE = Duration.ofSeconds(1);
	public static final int WRONG_ANSWER_PENALTY = 5;
	/** How often a bot picks the right answer. */
	public static final double BOT_ACCURACY = 0.55;

	public enum Status {
		IN_PROGRESS, COMPLETED
	}

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

	public MultiplayerMatch() {
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
			java.util.Map<Integer, String> names, java.util.Map<Integer, String> countries) {
		this(matchId, gameId, gameName, categoryId, playerIds, questions, secondsPerQuestion, pointsPerQuestion, now);
		this.players = new ArrayList<>(players.stream()
				.map(p -> new PlayerScore(p.getUserId(), names.get(p.getUserId()), countries.get(p.getUserId()))).toList());
	}

	/** Closes questions whose time ran out. @return true if anything changed */
	public boolean advanceClock(Instant now) {
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

	public AnswerOutcome answer(int userId, int questionId, String answer, Instant now) {
		PlayerScore player = player(userId).orElseThrow(() -> new MatchRuleException("You are not in this match"));
		advanceClock(now);
		if (status == Status.COMPLETED) {
			throw new MatchRuleException("The match is over");
		}
		MatchQuestion q = current();
		if (q.getQuestionId() != questionId) {
			throw new MatchRuleException("Question " + questionId + " is no longer open");
		}
		if (q.answerOf(userId).isPresent()) {
			throw new MatchRuleException("You already answered this question");
		}
		return record(player, q, answer, now);
	}

	private AnswerOutcome record(PlayerScore player, MatchQuestion q, String answer, Instant at) {
		boolean correct = answer != null && answer.trim().equalsIgnoreCase(q.getCorrectAnswer().trim());
		q.getAnswers().add(new PlayerAnswer(player.getUserId(), answer, correct, at));
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
		java.util.Map<Integer, Instant> when = new java.util.HashMap<>();
		for (PlayerScore p : players) {
			if (!p.isBot() || q.answerOf(p.getUserId()).isPresent()) {
				continue;
			}
			java.util.Random r = new java.util.Random(java.util.Objects.hash(matchId, q.getQuestionId(), p.getUserId()));
			long window = Math.max(1000, (secondsPerQuestion - 4) * 1000L);
			Instant at = q.getOpenedAt().plusMillis(2000 + (long) (r.nextDouble() * window));
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
			java.util.Random r = new java.util.Random(java.util.Objects.hash(matchId, q.getQuestionId(), bot.getUserId(), "pick"));
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

	public Optional<MatchQuestion> currentQuestion() {
		return status == Status.COMPLETED ? Optional.empty() : Optional.of(current());
	}

	/** The most recently closed question, so clients can show its answer and winner. */
	public Optional<MatchQuestion> lastClosedQuestion() {
		return currentIndex == 0 ? Optional.empty() : Optional.of(questions.get(currentIndex - 1));
	}

	public long secondsRemaining(Instant now) {
		return currentQuestion().map(q -> Math.max(0, Duration.between(now, deadline(q)).toSeconds())).orElse(0L);
	}

	/** Players ordered by score, then by questions won, then by fewest wrong answers. */
	public List<PlayerScore> standings() {
		return players.stream().sorted(Comparator.comparingInt(PlayerScore::getScore).reversed()
				.thenComparing(Comparator.comparingInt(PlayerScore::getQuestionsWon).reversed())
				.thenComparingInt(PlayerScore::getWrongAnswers)).toList();
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

	public record AnswerOutcome(boolean correct, boolean wonQuestion, int score) {
	}
}
