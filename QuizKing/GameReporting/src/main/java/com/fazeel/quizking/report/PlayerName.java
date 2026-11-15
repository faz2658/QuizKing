package com.fazeel.quizking.report;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** The name to show for a player on leaderboards, taken from their results and their own sign-in token. */
@Document(collection = "player_names")
public record PlayerName(@Id int userId, String name, Instant updatedAt, String country) {

	public PlayerName(int userId, String name, Instant updatedAt) {
		this(userId, name, updatedAt, null);
	}
}
