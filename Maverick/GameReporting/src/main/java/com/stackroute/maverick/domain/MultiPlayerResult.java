package com.stackroute.maverick.domain;

import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection="multiplayerresult")
public class MultiPlayerResult {
	
	@Id
	private int userId;
	private String userName;
	private List<MultiGameDetails> multigamedetails;
	
	public int getUserId() {
		return userId;
	}
	public void setUserId(int userId) {
		this.userId = userId;
	}
	public String getUserName() {
		return userName;
	}
	public void setUserName(String userName) {
		this.userName = userName;
	}
	public List<MultiGameDetails> getMultigamedetails() {
		return multigamedetails;
	}
	public void setMultigamedetails(List<MultiGameDetails> multigamedetails) {
		this.multigamedetails = multigamedetails;
	}
	public MultiPlayerResult(int userId, String userName, List<MultiGameDetails> multigamedetails) {
		super();
		this.userId = userId;
		this.userName = userName;
		this.multigamedetails = multigamedetails;
	}
	
	public MultiPlayerResult()
	{}
}
