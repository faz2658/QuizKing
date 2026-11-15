package com.fazeel.maverick.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonCreator;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GameQuestionTime {

	/*@Id
	private int gqTimeId;*/
	private int easyQuestionsTime;
	private int mediumQuestionsTime;
	private int advanceQuestionsTime;

	@JsonCreator

	public GameQuestionTime() {
		// TODO Auto-generated constructor stub
	}

	public GameQuestionTime(int easyQuestionsTime, int mediumQuestionsTime, int advanceQuestionsTime) {
		super();
		this.easyQuestionsTime = easyQuestionsTime;
		this.mediumQuestionsTime = mediumQuestionsTime;
		this.advanceQuestionsTime = advanceQuestionsTime;
	}

	public int getEasyQuestionsTime() {
		return easyQuestionsTime;
	}

	public void setEasyQuestionsTime(int easyQuestionsTime) {
		this.easyQuestionsTime = easyQuestionsTime;
	}

	public int getMediumQuestionsTime() {
		return mediumQuestionsTime;
	}

	public void setMediumQuestionsTime(int mediumQuestionsTime) {
		this.mediumQuestionsTime = mediumQuestionsTime;
	}

	public int getAdvanceQuestionsTime() {
		return advanceQuestionsTime;
	}

	public void setAdvanceQuestionsTime(int advanceQuestionsTime) {
		this.advanceQuestionsTime = advanceQuestionsTime;
	}

	@Override
	public String toString() {
		return "GameQuestionTime [easyQuestionsTime=" + easyQuestionsTime + ", mediumQuestionsTime="
				+ mediumQuestionsTime + ", advanceQuestionsTime=" + advanceQuestionsTime + "]";
	}

	
	

}
