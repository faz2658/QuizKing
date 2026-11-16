package com.fazeel.quizcadia.opentdb;

import java.time.Instant;

/** Progress of the Open Trivia DB import. */
public record ImportStatus(State state, int fetched, int added, int topics, Instant startedAt, Instant finishedAt,
		String message) {

	public enum State {
		IDLE, RUNNING, DONE, FAILED
	}

	static ImportStatus idle() {
		return new ImportStatus(State.IDLE, 0, 0, 0, null, null, null);
	}
}
