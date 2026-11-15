package com.fazeel.maverick.adaptive;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param blockSize questions asked at a level before deciding whether to move up or down
 * @param promoteAtPercent block score at or above which the player moves up a level
 * @param demoteBelowPercent block score below which the player moves down a level
 * @param totalQuestions length of a session
 * @param secondsPerQuestion time limit of each question
 * @param pointsPerLevel points for a correct answer, multiplied by the question's level
 */
@ConfigurationProperties("maverick.adaptive")
public record AdaptiveRules(int blockSize, int promoteAtPercent, int demoteBelowPercent, int totalQuestions,
		int secondsPerQuestion, int pointsPerLevel) {

	public static final AdaptiveRules DEFAULT = new AdaptiveRules(4, 60, 30, 12, 20, 10);

	public AdaptiveRules {
		if (blockSize < 1 || totalQuestions < 1 || secondsPerQuestion < 1 || pointsPerLevel < 1
				|| demoteBelowPercent > promoteAtPercent) {
			throw new IllegalArgumentException("Invalid adaptive rules");
		}
	}
}
