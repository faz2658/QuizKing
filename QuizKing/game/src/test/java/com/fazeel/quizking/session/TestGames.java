package com.fazeel.quizking.session;

import java.util.ArrayList;
import java.util.List;

import com.fazeel.quizking.domain.Game;
import com.fazeel.quizking.domain.GameQuestionLevel;
import com.fazeel.quizking.domain.GameQuestionScore;
import com.fazeel.quizking.domain.GameQuestionTime;
import com.fazeel.quizking.domain.Questions;

public final class TestGames {

	private TestGames() {
	}

	public static Questions question(int id, int level, String correct, String... options) {
		String[] o = { "", "", "", "" };
		System.arraycopy(options, 0, o, 0, options.length);
		return new Questions(id, level, "Question " + id + "?", o[0], o[1], o[2], o[3], correct);
	}

	public static Game game(int id, GameQuestionLevel mix, Questions... questions) {
		Game g = new Game();
		g.setGameId(id);
		g.setGameName("Game " + id);
		g.setCategoryId(7);
		g.setQuestionLevels(mix);
		g.setQuestions(new ArrayList<>(java.util.Arrays.asList(questions)));
		return g;
	}

	public static Game withTimesAndScores(Game g, GameQuestionTime time, GameQuestionScore score) {
		g.setQuestionTime(time);
		g.setQuestionScore(score);
		return g;
	}

	public static SessionQuestion sq(int id, Difficulty d, String correct, int seconds, int points) {
		return new SessionQuestion(id, d, "Q" + id, List.of(correct, "wrong-" + id), correct, seconds, points);
	}
}
