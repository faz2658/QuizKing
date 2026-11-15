package com.fazeel.quizking.catalog;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Automatic games: when enabled, every topic with enough questions gets a solo game and a
 * multiplayer game without an admin having to create them.
 *
 * @param minQuestions topics with fewer playable questions are skipped
 * @param soloQuestions questions per solo game
 * @param multiplayerQuestions questions per multiplayer match
 */
@ConfigurationProperties("quizking.auto-games")
public record AutoGames(boolean enabled, int minQuestions, int soloQuestions, int multiplayerQuestions) {

	public AutoGames {
		if (minQuestions <= 0) {
			minQuestions = 12;
		}
		if (soloQuestions <= 0) {
			soloQuestions = 10;
		}
		if (multiplayerQuestions <= 0) {
			multiplayerQuestions = 8;
		}
	}

	/**
	 * Splits {@code total} questions over easy/medium/advanced as close to {@code preferred} as the
	 * available counts allow, moving the shortfall of one level to the others.
	 */
	static int[] fit(int total, int[] preferred, int[] available) {
		int[] mix = new int[3];
		int remaining = total;
		for (int i = 0; i < 3; i++) {
			mix[i] = Math.min(preferred[i], available[i]);
			remaining -= mix[i];
		}
		for (int i = 0; i < 3 && remaining > 0; i++) {
			int extra = Math.min(remaining, available[i] - mix[i]);
			mix[i] += extra;
			remaining -= extra;
		}
		return mix;
	}
}
