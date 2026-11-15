package com.fazeel.quizking.report;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.fazeel.quizking.domain.AdaptiveResult;
import com.fazeel.quizking.domain.MultiPlayerResult;
import com.fazeel.quizking.domain.SinglePlayerResult;
import com.fazeel.quizking.report.GameReport.GameMode;

@Service
public class ReportService {

	private final GameReportRepository reports;
	private final PlayerNames names;

	public ReportService(GameReportRepository reports, PlayerNames names) {
		this.reports = reports;
		this.names = names;
	}

	public void record(SinglePlayerResult r) {
		names.remember(r.userId(), r.playerName());
		reports.save(ReportMapper.single(r));
	}

	public void record(AdaptiveResult r) {
		reports.save(ReportMapper.adaptive(r));
	}

	public void record(MultiPlayerResult r) {
		reports.saveAll(ReportMapper.multi(r));
	}

	public List<GameReport> reports(int userId, GameMode mode) {
		return mode == null ? reports.findByUserIdOrderByCompletedAtDesc(userId)
				: reports.findByUserIdAndModeOrderByCompletedAtDesc(userId, mode);
	}

	public UserSummary summary(int userId) {
		List<GameReport> all = reports.findByUserIdOrderByCompletedAtDesc(userId);
		Map<GameMode, ModeSummary> byMode = Arrays.stream(GameMode.values())
				.collect(Collectors.toMap(Function.identity(), m -> ModeSummary.of(all.stream()
						.filter(r -> r.mode() == m).toList())));
		long wins = all.stream().filter(r -> r.mode() == GameMode.MULTI && Integer.valueOf(1).equals(r.rank()))
				.count();
		return new UserSummary(userId, all.size(), ModeSummary.of(all).averageAccuracy(), wins, byMode);
	}

	public record UserSummary(int userId, int gamesPlayed, int averageAccuracy, long multiplayerWins,
			Map<GameMode, ModeSummary> modes) {
	}

	public record ModeSummary(int gamesPlayed, int bestScore, int averageAccuracy) {

		static ModeSummary of(List<GameReport> reports) {
			int best = reports.stream().mapToInt(GameReport::score).max().orElse(0);
			int avg = (int) Math.round(reports.stream().mapToInt(GameReport::accuracy).average().orElse(0));
			return new ModeSummary(reports.size(), best, avg);
		}
	}
}
