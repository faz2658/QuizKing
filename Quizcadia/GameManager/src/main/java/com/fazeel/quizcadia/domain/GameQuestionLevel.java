package com.fazeel.quizcadia.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonCreator;

/** How many easy, medium and advanced questions a game asks. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GameQuestionLevel {

	private int easyLevel;
	private int mediumLevel;
	private int advancedLevel;

	@JsonCreator
	public GameQuestionLevel() {
	}

	public GameQuestionLevel(int easyLevel, int mediumLevel, int advancedLevel) {
		this.easyLevel = easyLevel;
		this.mediumLevel = mediumLevel;
		this.advancedLevel = advancedLevel;
	}

	public int getEasyLevel() {
		return easyLevel;
	}

	public void setEasyLevel(int easyLevel) {
		this.easyLevel = easyLevel;
	}

	public int getMediumLevel() {
		return mediumLevel;
	}

	public void setMediumLevel(int mediumLevel) {
		this.mediumLevel = mediumLevel;
	}

	public int getAdvancedLevel() {
		return advancedLevel;
	}

	public void setAdvancedLevel(int advancedLevel) {
		this.advancedLevel = advancedLevel;
	}

	@Override
	public String toString() {
		return "GameQuestionLevel [easyLevel=" + easyLevel + ", mediumLevel=" + mediumLevel + ", advancedLevel="
				+ advancedLevel + "]";
	}
}
