package com.fazeel.maverick.board;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fazeel.maverick.report.GameReport;
import com.fazeel.maverick.report.GameReportRepository;
import com.fazeel.maverick.report.PlayerNames;

/** Rankings across players. */
@Service
public class LeaderboardService {

	static final int TOP = 20;

	private final GameReportRepository reports;
	private final PlayerNames names;

	public LeaderboardService(GameReportRepository reports, PlayerNames names) {
		this.reports = reports;
		this.names = names;
	}

	/** One day's challenge: highest score first, the earlier finish wins a tie. */
	public DailyBoard daily(LocalDate date, int callerId) {
		List<GameReport> runs = new ArrayList<>(reports.findByVariantAndVariantKey(GameReport.DAILY, date.toString()));
		runs.sort(Comparator.comparingInt(GameReport::score).reversed()
				.thenComparing(GameReport::completedAt, Comparator.nullsLast(Comparator.naturalOrder())));
		Map<Integer, String> who = names.lookup(runs.stream().map(GameReport::userId).toList());
		List<DailyEntry> entries = new ArrayList<>();
		for (int i = 0; i < runs.size(); i++) {
			GameReport r = runs.get(i);
			entries.add(new DailyEntry(i + 1, r.userId(), who.get(r.userId()), r.score(), r.maxScore(),
					r.correctAnswers(), r.totalQuestions()));
		}
		DailyEntry you = entries.stream().filter(e -> e.userId() == callerId).findFirst().orElse(null);
		return new DailyBoard(date.toString(), entries.size(), entries.stream().limit(TOP).toList(), you);
	}

	public record DailyBoard(String date, int players, List<DailyEntry> top, DailyEntry you) {
	}

	public record DailyEntry(int rank, int userId, String name, int score, Integer maxScore, int correctAnswers,
			int totalQuestions) {
	}
}
