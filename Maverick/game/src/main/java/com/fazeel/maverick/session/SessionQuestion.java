package com.fazeel.maverick.session;

import java.time.Instant;
import java.util.List;

/** One question inside a player's session, together with how the player answered it. */
public class SessionQuestion {

	private int questionId;
	private Difficulty difficulty;
	private String stem;
	private List<String> options;
	private String correctAnswer;
	private int timeLimitSeconds;
	private int points;

	private Instant askedAt;
	private Instant answeredAt;
	private String answer;
	private boolean correct;
	private boolean timedOut;
	private int pointsAwarded;

	public SessionQuestion() {
	}

	public SessionQuestion(int questionId, Difficulty difficulty, String stem, List<String> options,
			String correctAnswer, int timeLimitSeconds, int points) {
		this.questionId = questionId;
		this.difficulty = difficulty;
		this.stem = stem;
		this.options = List.copyOf(options);
		this.correctAnswer = correctAnswer;
		this.timeLimitSeconds = timeLimitSeconds;
		this.points = points;
	}

	/** The same question, not yet asked: for sessions that replay a fixed list (daily, challenge). */
	public SessionQuestion fresh() {
		return new SessionQuestion(questionId, difficulty, stem, options, correctAnswer, timeLimitSeconds, points);
	}

	public boolean isAnswered() {
		return answeredAt != null;
	}

	public Instant deadline() {
		return askedAt == null ? null : askedAt.plusSeconds(timeLimitSeconds);
	}

	public int getQuestionId() {
		return questionId;
	}

	public void setQuestionId(int questionId) {
		this.questionId = questionId;
	}

	public Difficulty getDifficulty() {
		return difficulty;
	}

	public void setDifficulty(Difficulty difficulty) {
		this.difficulty = difficulty;
	}

	public String getStem() {
		return stem;
	}

	public void setStem(String stem) {
		this.stem = stem;
	}

	public List<String> getOptions() {
		return options;
	}

	public void setOptions(List<String> options) {
		this.options = options;
	}

	public String getCorrectAnswer() {
		return correctAnswer;
	}

	public void setCorrectAnswer(String correctAnswer) {
		this.correctAnswer = correctAnswer;
	}

	public int getTimeLimitSeconds() {
		return timeLimitSeconds;
	}

	public void setTimeLimitSeconds(int timeLimitSeconds) {
		this.timeLimitSeconds = timeLimitSeconds;
	}

	public int getPoints() {
		return points;
	}

	public void setPoints(int points) {
		this.points = points;
	}

	public Instant getAskedAt() {
		return askedAt;
	}

	public void setAskedAt(Instant askedAt) {
		this.askedAt = askedAt;
	}

	public Instant getAnsweredAt() {
		return answeredAt;
	}

	public void setAnsweredAt(Instant answeredAt) {
		this.answeredAt = answeredAt;
	}

	public String getAnswer() {
		return answer;
	}

	public void setAnswer(String answer) {
		this.answer = answer;
	}

	public boolean isCorrect() {
		return correct;
	}

	public void setCorrect(boolean correct) {
		this.correct = correct;
	}

	public boolean isTimedOut() {
		return timedOut;
	}

	public void setTimedOut(boolean timedOut) {
		this.timedOut = timedOut;
	}

	public int getPointsAwarded() {
		return pointsAwarded;
	}

	public void setPointsAwarded(int pointsAwarded) {
		this.pointsAwarded = pointsAwarded;
	}
}
