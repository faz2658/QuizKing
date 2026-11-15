package com.fazeel.quizking.session;

/**
 * Question difficulty. Question levels are stored as 1 (easy), 2 (medium) and 3 (advanced)
 * by the question generator and game manager.
 */
public enum Difficulty {
	EASY(1, 10, 10), MEDIUM(2, 15, 20), ADVANCED(3, 20, 30);

	private final int level;
	private final int defaultSeconds;
	private final int defaultPoints;

	Difficulty(int level, int defaultSeconds, int defaultPoints) {
		this.level = level;
		this.defaultSeconds = defaultSeconds;
		this.defaultPoints = defaultPoints;
	}

	public int level() {
		return level;
	}

	public int defaultSeconds() {
		return defaultSeconds;
	}

	public int defaultPoints() {
		return defaultPoints;
	}

	/** Unknown or missing levels are treated as easy rather than failing the whole game. */
	public static Difficulty fromLevel(int level) {
		for (Difficulty d : values()) {
			if (d.level == level) {
				return d;
			}
		}
		return EASY;
	}
}
