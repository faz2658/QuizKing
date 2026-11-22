package com.fazeel.quizcadia.match;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * One question of a match: its options and right answer, when it opened and closed, who won it, and every player's
 * answer.
 */
public class MatchQuestion {

	private int questionId;
	private String stem;
	private List<String> options;
	private String correctAnswer;
	private Instant openedAt;
	private Instant closedAt;
	private Integer winnerUserId;
	private List<PlayerAnswer> answers = new ArrayList<>();

	public MatchQuestion() {
	}

	public MatchQuestion(int questionId, String stem, List<String> options, String correctAnswer) {
		this.questionId = questionId;
		this.stem = stem;
		this.options = List.copyOf(options);
		this.correctAnswer = correctAnswer;
	}

	Optional<PlayerAnswer> answerOf(int userId) {
		return answers.stream().filter(a -> a.userId() == userId).findFirst();
	}

	public int getQuestionId() {
		return questionId;
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

	public Instant getOpenedAt() {
		return openedAt;
	}

	void setOpenedAt(Instant openedAt) {
		this.openedAt = openedAt;
	}

	public Instant getClosedAt() {
		return closedAt;
	}

	void setClosedAt(Instant closedAt) {
		this.closedAt = closedAt;
	}

	public Integer getWinnerUserId() {
		return winnerUserId;
	}

	void setWinnerUserId(Integer winnerUserId) {
		this.winnerUserId = winnerUserId;
	}

	public List<PlayerAnswer> getAnswers() {
		return answers;
	}
}
