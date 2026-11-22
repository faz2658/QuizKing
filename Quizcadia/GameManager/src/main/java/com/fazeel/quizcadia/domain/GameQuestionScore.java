package com.fazeel.quizcadia.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonCreator;

/** Points for a right answer at each level (easy, medium, advanced). */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GameQuestionScore {

	private int easyQuestionsScore;
	private int mediumQuestionsScore;
	private int advanceQuestionsScore;

	@JsonCreator
	public GameQuestionScore() {
	}

	public GameQuestionScore(int easyQuestionsScore, int mediumQuestionsScore, int advanceQuestionsScore) {
		this.easyQuestionsScore = easyQuestionsScore;
		this.mediumQuestionsScore = mediumQuestionsScore;
		this.advanceQuestionsScore = advanceQuestionsScore;
	}

	public int getEasyQuestionsScore() {
		return easyQuestionsScore;
	}

	public void setEasyQuestionsScore(int easyQuestionsScore) {
		this.easyQuestionsScore = easyQuestionsScore;
	}

	public int getMediumQuestionsScore() {
		return mediumQuestionsScore;
	}

	public void setMediumQuestionsScore(int mediumQuestionsScore) {
		this.mediumQuestionsScore = mediumQuestionsScore;
	}

	public int getAdvanceQuestionsScore() {
		return advanceQuestionsScore;
	}

	public void setAdvanceQuestionsScore(int advanceQuestionsScore) {
		this.advanceQuestionsScore = advanceQuestionsScore;
	}

	@Override
	public String toString() {
		return "GameQuestionScore [easyQuestionsScore=" + easyQuestionsScore + ", mediumQuestionsScore="
				+ mediumQuestionsScore + ", advanceQuestionsScore=" + advanceQuestionsScore + "]";
	}
}
