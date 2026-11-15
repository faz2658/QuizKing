package com.stackroute.maverick.domain;

public class MultiGameDetails {
	public int gameSessionId;
	public MultiSessionActivity multiSessionActivity;
	
	public int getGameSessionId() {
		return gameSessionId;
	}
	public void setGameSessionId(int gameSessionId) {
		this.gameSessionId = gameSessionId;
	}
	public MultiSessionActivity getMultiSessionActivity() {
		return multiSessionActivity;
	}
	public void setMultiSessionActivity(MultiSessionActivity multiSessionActivity) {
		this.multiSessionActivity = multiSessionActivity;
	}
	public MultiGameDetails(int gameSessionId, MultiSessionActivity multiSessionActivity) {
		super();
		this.gameSessionId = gameSessionId;
		this.multiSessionActivity = multiSessionActivity;
	}
	
	public MultiGameDetails()
	{}
}
