package com.fazeel.quizcadia.arena;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** One player in one round. Each player has their own document, so answers never conflict. */
@Document(collection = "arena_entries")
public class ArenaEntry {

	public record Answer(String answer, boolean correct, Instant at, int points) {
	}

	@Id
	private String id;
	@Indexed
	private String roundId;
	private int userId;
	private String name;
	private String country;
	private Instant joinedAt;
	/** By question index ("0", "1", ...; Mongo keys are strings). */
	private Map<String, Answer> answers = new HashMap<>();

	public ArenaEntry() {
	}

	public ArenaEntry(String roundId, int userId, String name, String country, Instant joinedAt) {
		this.id = roundId + ":" + userId;
		this.roundId = roundId;
		this.userId = userId;
		this.name = name;
		this.country = country;
		this.joinedAt = joinedAt;
	}

	public Answer answer(int index) {
		return answers.get(String.valueOf(index));
	}

	public void record(int index, Answer a) {
		answers.put(String.valueOf(index), a);
	}

	/** Questions answered right in a row from the first, among the first {@code closed} questions. */
	public int survived(int closed) {
		int n = 0;
		while (n < closed) {
			Answer a = answer(n);
			if (a == null || !a.correct()) {
				break;
			}
			n++;
		}
		return n;
	}

	public int score() {
		return answers.values().stream().mapToInt(Answer::points).sum();
	}

	public String getId() {
		return id;
	}

	public String getRoundId() {
		return roundId;
	}

	public int getUserId() {
		return userId;
	}

	public String getName() {
		return name;
	}

	public String getCountry() {
		return country;
	}

	public void setCountry(String country) {
		this.country = country;
	}

	public Instant getJoinedAt() {
		return joinedAt;
	}

	public Map<String, Answer> getAnswers() {
		return answers;
	}
}
