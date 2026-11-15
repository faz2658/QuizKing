package com.fazeel.quizking.matching;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param minPlayers players needed for a match once the wait is over
 * @param maxPlayers a lobby with this many players starts immediately
 * @param waitTime how long a lobby waits for more players
 */
@ConfigurationProperties("quizking.matching")
public record MatchmakingRules(int minPlayers, int maxPlayers, Duration waitTime) {

	public MatchmakingRules {
		if (minPlayers < 2 || maxPlayers < minPlayers || waitTime == null || waitTime.isNegative()) {
			throw new IllegalArgumentException("Need 2 <= minPlayers <= maxPlayers and a positive wait");
		}
	}
}
