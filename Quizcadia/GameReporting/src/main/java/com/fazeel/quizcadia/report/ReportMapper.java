package com.fazeel.quizcadia.report;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fazeel.quizcadia.domain.AdaptiveResult;
import com.fazeel.quizcadia.domain.MultiPlayerResult;
import com.fazeel.quizcadia.domain.PlayedQuestion;
import com.fazeel.quizcadia.domain.SinglePlayerResult;
import com.fazeel.quizcadia.report.GameReport.GameMode;
import com.fazeel.quizcadia.report.GameReport.ReviewItem;

/** Turns engine results into per-player reports. */
public final class ReportMapper {

	private ReportMapper() {
	}

	/** The report for a finished solo game, with its question review. */
	public static GameReport single(SinglePlayerResult r) {
		List<ReviewItem> items = Objects.requireNonNullElse(r.playedData(), List.<PlayedQuestion>of())
				.stream().map(q -> new ReviewItem(q.question(), q.correctAnswer(), q.answer(), q.correct(),
						q.timedOut(), q.pointsAwarded()))
				.toList();
		return new GameReport("single:" + r.sessionId(), GameMode.SINGLE, r.userId(), r.gameId(), r.gameName(),
				r.categoryId(), r.score(), r.maxScore(), r.correctAnswers(), r.totalQuestions(), r.rank(), r.players(), null,
				r.completedAt(), items, r.variant(), r.variantKey(), r.opponentScore());
	}

	/** The report for a finished adaptive session, with its question review and the highest level reached. */
	public static GameReport adaptive(AdaptiveResult r) {
		List<ReviewItem> items = Objects.requireNonNullElse(r.answers(), List.<AdaptiveResult.Answer>of()).stream()
				.map(a -> new ReviewItem(a.question(), a.correctAnswer(), a.answer(), a.correct(), a.timedOut(),
						a.points()))
				.toList();
		return new GameReport("adaptive:" + r.sessionId(), GameMode.ADAPTIVE, r.userId(), r.gameId(), r.gameName(),
				r.categoryId(), r.score(), null, r.correctAnswers(), r.totalQuestions(), null, null, r.highestLevel(),
				r.completedAt(), items);
	}

	/** A multiplayer match produces one report per player, from that player's point of view. */
	public static List<GameReport> multi(MultiPlayerResult r) {
		List<MultiPlayerResult.Standing> standings = Objects.requireNonNullElse(r.standings(), List.of());
		List<MultiPlayerResult.QuestionResult> questions = Objects.requireNonNullElse(r.questions(), List.of());
		return standings.stream().map(s -> {
			List<ReviewItem> items = questions.stream().map(q -> {
				Map<Integer, MultiPlayerResult.PlayerAnswer> byUser = Objects
						.requireNonNullElse(q.answers(), List.<MultiPlayerResult.PlayerAnswer>of()).stream()
						.collect(Collectors.toMap(MultiPlayerResult.PlayerAnswer::userId, Function.identity(),
								(a, b) -> a));
				MultiPlayerResult.PlayerAnswer mine = byUser.get(s.userId());
				boolean won = Objects.equals(q.winnerUserId(), s.userId());
				return new ReviewItem(q.question(), q.correctAnswer(), mine == null ? null : mine.answer(),
						mine != null && mine.correct(), mine == null, won ? 1 : 0);
			}).toList();
			int correct = (int) items.stream().filter(ReviewItem::correct).count();
			return new GameReport("multi:" + r.matchId() + ":" + s.userId(), GameMode.MULTI, s.userId(), r.gameId(),
					r.gameName(), r.categoryId(), s.score(), null, correct, questions.size(), s.rank(),
					standings.size(), null, r.completedAt(), items);
		}).toList();
	}
}
