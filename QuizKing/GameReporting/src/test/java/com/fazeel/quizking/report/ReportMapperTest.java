package com.fazeel.quizking.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fazeel.quizking.domain.AdaptiveResult;
import com.fazeel.quizking.domain.MultiPlayerResult;
import com.fazeel.quizking.domain.PlayedQuestion;
import com.fazeel.quizking.domain.SinglePlayerResult;
import com.fazeel.quizking.report.GameReport.GameMode;

import tools.jackson.databind.json.JsonMapper;

class ReportMapperTest {

	static final Instant T = Instant.parse("2026-01-01T10:00:00Z");

	@Test
	void singlePlayerResultBecomesAReportForThatPlayer() {
		SinglePlayerResult r = new SinglePlayerResult("s1", 5, "Capitals", 1, 42, 30, 40, 75, 2, 3, T,
				List.of(new PlayedQuestion(1, 1, "Q1", "A", "A", true, false, 10),
						new PlayedQuestion(2, 2, "Q2", "B", null, false, true, 0),
						new PlayedQuestion(3, 2, "Q3", "C", "C", true, false, 20)),
				"DAILY", "2026-01-01", null, "Ada", null, null);

		GameReport report = ReportMapper.single(r);

		assertThat(report.id()).isEqualTo("single:s1");
		assertThat(report.userId()).isEqualTo(42);
		assertThat(report.isDaily()).isTrue();
		assertThat(report.variantKey()).isEqualTo("2026-01-01");
		assertThat(report.mode()).isEqualTo(GameMode.SINGLE);
		assertThat(report.accuracy()).isEqualTo(67);
		assertThat(report.questions()).hasSize(3);
		assertThat(report.questions().get(1).timedOut()).isTrue();
	}

	@Test
	void multiplayerResultBecomesOneReportPerPlayer() {
		MultiPlayerResult r = new MultiPlayerResult("m1", 7, "Race", 2, T,
				List.of(new MultiPlayerResult.Standing(1, 10, 20, 2, 0), new MultiPlayerResult.Standing(2, 20, 0, 0, 1)),
				List.of(new MultiPlayerResult.QuestionResult(1, "Q1", "A", 10,
						List.of(new MultiPlayerResult.PlayerAnswer(10, "A", true, 900),
								new MultiPlayerResult.PlayerAnswer(20, "X", false, 500))),
						new MultiPlayerResult.QuestionResult(2, "Q2", "B", 10,
								List.of(new MultiPlayerResult.PlayerAnswer(10, "B", true, 700)))));

		List<GameReport> reports = ReportMapper.multi(r);

		assertThat(reports).hasSize(2);
		GameReport loser = reports.get(1);
		assertThat(loser.userId()).isEqualTo(20);
		assertThat(loser.rank()).isEqualTo(2);
		assertThat(loser.players()).isEqualTo(2);
		assertThat(loser.correctAnswers()).isZero();
		assertThat(loser.questions().get(1).timedOut()).as("never answered Q2").isTrue();
		assertThat(reports.get(0).id()).isEqualTo("multi:m1:10");
	}

	@Test
	void adaptiveResultKeepsTheHighestLevel() {
		AdaptiveResult r = new AdaptiveResult("a1", 9, 5, "Adaptive", 1, 7, 60, 2, 3, 3, 4, T,
				List.of(new AdaptiveResult.Answer(1, 1, "Q", "A", "A", true, false, 10)));

		GameReport report = ReportMapper.adaptive(r);

		assertThat(report.highestLevel()).isEqualTo(3);
		assertThat(report.mode()).isEqualTo(GameMode.ADAPTIVE);
	}

	@Test
	void resultJsonFromTheGameEngineIsUnderstood() throws Exception {
		String json = """
				{"sessionId":"s1","gameId":5,"gameName":"Capitals","categoryId":1,"userId":42,"score":10,
				 "maxScore":10,"percentage":100,"correctAnswers":1,"totalQuestions":1,
				 "completedAt":"2026-01-01T10:00:00Z",
				 "playedData":[{"questionId":1,"questionLevel":1,"question":"Q","correctAnswer":"A",
				 "answer":"A","correct":true,"timedOut":false,"pointsAwarded":10}]}""";

		SinglePlayerResult r = JsonMapper.builder().build().readValue(json, SinglePlayerResult.class);

		assertThat(ReportMapper.single(r).questions()).hasSize(1);
	}
}
