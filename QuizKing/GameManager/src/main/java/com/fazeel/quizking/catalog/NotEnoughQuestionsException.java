package com.fazeel.quizking.catalog;

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
