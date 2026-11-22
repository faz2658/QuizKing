package com.fazeel.quizcadia.report;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.fazeel.quizcadia.board.BoardCache;
import com.fazeel.quizcadia.board.LeaderboardService;
import com.fazeel.quizcadia.domain.AdaptiveResult;
import com.fazeel.quizcadia.domain.MultiPlayerResult;
import com.fazeel.quizcadia.domain.SinglePlayerResult;
import com.fazeel.quizcadia.report.GameReport.GameMode;

@Service
public class ReportService {

	private final GameReportRepository reports;
	private final PlayerNames names;
	private final BoardCache boards;

	public ReportService(GameReportRepository reports, PlayerNames names, BoardCache boards) {
		this.reports = reports;
		this.names = names;
		this.boards = boards;
	}

	public void record(SinglePlayerResult r) {
		names.remember(r.userId(), r.playerName());
		GameReport report = reports.save(ReportMapper.single(r));
		if (report != null && report.isDaily()) {
			// the player's result page asks for the daily board right away, so it must include this run
			boards.evict(LeaderboardService.DAILY_KEY);
		}
	}

	public void record(AdaptiveResult r) {
		reports.save(ReportMapper.adaptive(r));
	}

	public void record(MultiPlayerResult r) {
		reports.saveAll(ReportMapper.multi(r));
	}

	public List<GameReport> reports(int userId, GameMode mode) {
		return mode == null ? reports.findSummariesByUserId(userId) : reports.findSummariesByUserIdAndMode(userId, mode);
	}

	public UserSummary summary(int userId) {
		List<GameReport> all = reports.findSummariesByUserId(userId);
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
