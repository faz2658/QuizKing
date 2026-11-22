package com.fazeel.quizcadia.adaptive;

import java.time.Instant;
import java.util.List;

/**
 * A question as it was asked in a session: its level, options and right answer, when it was asked, and how the
 * player answered (or that time ran out).
 */
public class AskedQuestion {

	private int questionId;
	private int level;
	private String stem;
	private List<String> options;
	private String correctAnswer;
	private Instant askedAt;
	private String answer;
	private boolean correct;
	private boolean timedOut;
	private int points;

	public AskedQuestion() {
	}

	AskedQuestion(PooledQuestion q, Instant askedAt) {
		this.questionId = q.questionId();
		this.level = q.level();
		this.stem = q.stem();
		this.options = q.options();
		this.correctAnswer = q.correctAnswer();
		this.askedAt = askedAt;
	}

	boolean isOpen() {
		return answer == null && !timedOut;
	}

	void answer(String answer, boolean correct, int points) {
		this.answer = answer;
		this.correct = correct;
		this.points = points;
	}

	void timeOut() {
		this.timedOut = true;
	}

	public int getQuestionId() {
		return questionId;
	}

	public int getLevel() {
		return level;
	}

	public String getStem() {
		return stem;
	}

	public List<String> getOptions() {
		return options;
	}

	public String getCorrectAnswer() {
		return correctAnswer;
	}

	public Instant getAskedAt() {
		return askedAt;
	}

	public String getAnswer() {
		return answer;
	}

	public boolean isCorrect() {
		return correct;
	}

	public boolean isTimedOut() {
		return timedOut;
	}

	public int getPoints() {
		return points;
	}
}
