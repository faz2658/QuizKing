package com.fazeel.quizcadia.progress;

import com.fazeel.quizcadia.report.GameReport;

/**
 * Experience points. Every finished game earns {@value #PER_GAME}, every correct answer {@value #PER_CORRECT};
 * bonuses: a perfect solo game {@value #PERFECT}, the Daily Challenge {@value #DAILY}, a multiplayer win
 * {@value #WIN}, beating a friend's challenge {@value #CHALLENGE_WON}, and {@value #PER_ADAPTIVE_LEVEL} per
 * adaptive level climbed above the first, and {@value #ARENA_WIN} for winning a World Arena round.
 *
 * <p>Levels need 50·L·(L−1) XP in total: level 2 at 100, 3 at 300, 4 at 600, 5 at 1,000 and so on.
 */
public final class Xp {

	static final int PER_GAME = 5;
	static final int PER_CORRECT = 10;
	static final int PERFECT = 25;
	static final int DAILY = 20;
	static final int WIN = 30;
	static final int CHALLENGE_WON = 25;
	static final int PER_ADAPTIVE_LEVEL = 10;
	static final int ARENA_WIN = 50;

	private Xp() {
	}

	/**
	 * XP for one finished game: a base for playing and more per right answer, plus by mode. Solo: a perfect game
	 * (every answer right, at least 5 questions), playing the Daily Challenge, winning a challenge or an Arena round.
	 * Multiplayer: winning a match of two or more. Adaptive: each level reached above easy.
	 */
	public static int of(GameReport r) {
		int xp = PER_GAME + PER_CORRECT * r.correctAnswers();
		switch (r.mode()) {
			case SINGLE -> {
				if (r.totalQuestions() >= 5 && r.correctAnswers() == r.totalQuestions()) {
					xp += PERFECT;
				}
				if (r.isDaily()) {
					xp += DAILY;
				}
				if (wonChallenge(r)) {
					xp += CHALLENGE_WON;
				}
				if (wonArena(r)) {
					xp += ARENA_WIN;
				}
			}
			case MULTI -> {
				if (wonMatch(r)) {
					xp += WIN;
				}
			}
			case ADAPTIVE -> xp += PER_ADAPTIVE_LEVEL * Math.max(0, (r.highestLevel() == null ? 1 : r.highestLevel()) - 1);
		}
		return xp;
	}

	static boolean wonMatch(GameReport r) {
		return r.rank() != null && r.rank() == 1 && r.players() != null && r.players() >= 2;
	}

	static boolean wonChallenge(GameReport r) {
		return GameReport.CHALLENGE.equals(r.variant()) && r.opponentScore() != null && r.score() > r.opponentScore();
	}

	/** Last one standing in a World Arena round with at least two players. */
	static boolean wonArena(GameReport r) {
		return GameReport.ARENA.equals(r.variant()) && r.rank() != null && r.rank() == 1 && r.players() != null
				&& r.players() >= 2;
	}

	/** Total XP needed to reach {@code level}. */
	public static int forLevel(int level) {
		return 50 * level * (level - 1);
	}

	/** The level a total of XP reaches. */
	public static int level(int xp) {
		int level = 1;
		while (forLevel(level + 1) <= xp) {
			level++;
		}
		return level;
	}

	/** Royal ranks, from peasant to king. */
	public static String title(int level) {
		if (level >= 25) return "King";
		if (level >= 20) return "Prince";
		if (level >= 15) return "Duke";
		if (level >= 11) return "Earl";
		if (level >= 8) return "Baron";
		if (level >= 5) return "Knight";
		if (level >= 3) return "Squire";
		if (level == 2) return "Page";
		return "Peasant";
	}
}
