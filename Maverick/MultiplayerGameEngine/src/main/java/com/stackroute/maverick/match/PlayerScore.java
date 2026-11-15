package com.stackroute.maverick.match;

public class PlayerScore {

	private int userId;
	private int score;
	private int questionsWon;
	private int wrongAnswers;

	public PlayerScore() {
	}

	PlayerScore(int userId) {
		this.userId = userId;
	}

	void won(int points) {
		score += points;
		questionsWon++;
	}

	void wrong(int penalty) {
		score = Math.max(0, score - penalty);
		wrongAnswers++;
	}

	public int getUserId() {
		return userId;
	}

	public int getScore() {
		return score;
	}

	public int getQuestionsWon() {
		return questionsWon;
	}

	public int getWrongAnswers() {
		return wrongAnswers;
	}
}
