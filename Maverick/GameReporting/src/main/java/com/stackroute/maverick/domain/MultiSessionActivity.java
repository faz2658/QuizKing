package com.stackroute.maverick.domain;

import java.util.List;

public class MultiSessionActivity {
	
	public int gameId;
	public String gameName;
	public String gameTypeName;
	public List<MultiOpponents> opponent;
	public List<MultiQuestion> question;
	
	public int getGameId() {
		return gameId;
	}
	public void setGameId(int gameId) {
		this.gameId = gameId;
	}
	public String getGameName() {
		return gameName;
	}
	public void setGameName(String gameName) {
		this.gameName = gameName;
	}
	public String getGameTypeName() {
		return gameTypeName;
	}
	public void setGameTypeName(String gameTypeName) {
		this.gameTypeName = gameTypeName;
	}
	public List<MultiOpponents> getOpponent() {
		return opponent;
	}
	public void setOpponent(List<MultiOpponents> opponent) {
		this.opponent = opponent;
	}
	public List<MultiQuestion> getQuestion() {
		return question;
	}
	public void setQuestion(List<MultiQuestion> question) {
		this.question = question;
	}
	public MultiSessionActivity(int gameId, String gameName, String gameTypeName, List<MultiOpponents> opponent,
			List<MultiQuestion> question) {
		super();
		this.gameId = gameId;
		this.gameName = gameName;
		this.gameTypeName = gameTypeName;
		this.opponent = opponent;
		this.question = question;
	}
	
	public MultiSessionActivity()
	{}
}
