package com.fazeel.quizcadia.session;

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
	/** When the player's screen reported showing it (its clock was restarted then); null until then. */
	private Instant shownAt;
	private Instant answeredAt;
	private String answer;
	private boolean correct;
	private boolean timedOut;
	private int pointsAwarded;
	private boolean skipped;
	/** Options taken away by a 50/50. */
	private List<String> removedOptions = List.of();
	private int bonusSeconds;
	/** The combo multiplier the points were awarded with. */
	private double multiplier = 1.0;
	/** Shown once the question is answered (may be null). */
	private String explanation;

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
		SessionQuestion copy = new SessionQuestion(questionId, difficulty, stem, options, correctAnswer, timeLimitSeconds, points);
		copy.setExplanation(explanation);
		return copy;
	}

	public Instant getShownAt() {
		return shownAt;
	}

	public void setShownAt(Instant shownAt) {
		this.shownAt = shownAt;
	}

	public boolean isAnswered() {
		return answeredAt != null;
	}

	/** When time runs out: when it was asked plus the limit and any extra time; null until asked. */
	public Instant deadline() {
		return askedAt == null ? null : askedAt.plusSeconds(timeLimitSeconds + bonusSeconds);
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

	public boolean isSkipped() {
		return skipped;
	}

	public void setSkipped(boolean skipped) {
		this.skipped = skipped;
	}

	public List<String> getRemovedOptions() {
		return removedOptions == null ? List.of() : removedOptions;
	}

	public void setRemovedOptions(List<String> removedOptions) {
		this.removedOptions = removedOptions;
	}

	public int getBonusSeconds() {
		return bonusSeconds;
	}

	public void setBonusSeconds(int bonusSeconds) {
		this.bonusSeconds = bonusSeconds;
	}

	public double getMultiplier() {
		return multiplier;
	}

	public void setMultiplier(double multiplier) {
		this.multiplier = multiplier;
	}

	public String getExplanation() {
		return explanation;
	}

	public void setExplanation(String explanation) {
		this.explanation = explanation;
	}
}
