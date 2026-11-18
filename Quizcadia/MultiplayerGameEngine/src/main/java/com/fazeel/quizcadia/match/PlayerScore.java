package com.fazeel.quizcadia.match;

public class PlayerScore {

	private int userId;
	private int score;
	private int questionsWon;
	private int wrongAnswers;
	private String name;
	private String country;

	public PlayerScore() {
	}

	PlayerScore(int userId) {
		this.userId = userId;
	}

	PlayerScore(int userId, String name, String country) {
		this.userId = userId;
		this.name = name;
		this.country = country;
	}

	/** Negative ids are bots that keep a lone Quick Match player company. */
	public boolean isBot() {
		return userId < 0;
	}

	public String getName() {
		return name;
	}

	public String getCountry() {
		return country;
	}

	void won(int points) {
		score += points;
		questionsWon++;
	}

	/** A wrong answer that costs nothing (room quiz). */
	void missed() {
		wrongAnswers++;
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
