package com.fazeel.quizcadia.catalog;

/**
 * The topic hasn't enough questions of a level for the game's mix; the message says how many were wanted and how
 * many exist.
 */
public class NotEnoughQuestionsException extends InvalidGameException {

	public NotEnoughQuestionsException(int level, int wanted, int available) {
		super("Asked for " + wanted + " " + levelName(level) + " questions but the topic only has " + available);
	}

	private static String levelName(int level) {
		return switch (level) {
			case 1 -> "easy";
			case 2 -> "medium";
			default -> "advanced";
		};
	}
}
