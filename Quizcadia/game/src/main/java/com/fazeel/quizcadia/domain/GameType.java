package com.fazeel.quizcadia.domain;

import com.fasterxml.jackson.annotation.JsonCreator;

import org.springframework.data.annotation.Id;

/** The kind of game: an id, a name and a description. */
public class GameType {

	@Id
	private int gameTypeId;
	private String gameTypeName;
	private String gameTypeDescription;

	public int getGameTypeId() {
		return gameTypeId;
	}

	public void setGameTypeId(int gameTypeId) {
		this.gameTypeId = gameTypeId;
	}

	public String getGameTypeName() {
		return gameTypeName;
	}

	public void setGameTypeName(String gameTypeName) {
		this.gameTypeName = gameTypeName;
	}

	public String getGameTypeDescription() {
		return gameTypeDescription;
	}

	public void setGameTypeDescription(String gameTypeDescription) {
		this.gameTypeDescription = gameTypeDescription;
	}

	public GameType(int gameTypeId, String gameTypeName, String gameTypeDescription) {
		this.gameTypeId = gameTypeId;
		this.gameTypeName = gameTypeName;
		this.gameTypeDescription = gameTypeDescription;
	}

	@JsonCreator
	public GameType() {
	}
}
