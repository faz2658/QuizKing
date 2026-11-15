package com.fazeel.quizking.progress;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.fazeel.quizking.board.LeaderboardService;
import com.fazeel.quizking.progress.Achievements.Achievement;
import com.fazeel.quizking.progress.Streaks.Streak;
import com.fazeel.quizking.report.GameReport;
import com.fazeel.quizking.report.GameReportRepository;

/** A player's XP, level and streak, worked out from their game reports. */
@Service
public class ProgressService {

	private final GameReportRepository reports;
	private final LeaderboardService boards;
	private final Clock clock;

	public ProgressService(GameReportRepository reports, LeaderboardService boards, Clock clock) {
		this.reports = reports;
		this.boards = boards;
		this.clock = clock;
	}

	/** Every badge, earned or not, with how far along the player is. */
	public List<Achievement> achievements(int userId) {
		List<GameReport> all = reports.findByUserIdOrderByCompletedAtDesc(userId);
		return Achievements.of(all, r -> boards.dailyRank(r.variantKey(), userId));
	}

	public Progress progress(int userId) {
		return of(userId, reports.findByUserIdOrderByCompletedAtDesc(userId), today());
	}

	LocalDate today() {
		return LocalDate.now(clock.withZone(ZoneOffset.UTC));
	}

	static Progress of(int userId, List<GameReport> all, LocalDate today) {
		int xp = all.stream().mapToInt(Xp::of).sum();
		int level = Xp.level(xp);
		List<LocalDate> days = all.stream().map(GameReport::completedAt).filter(Objects::nonNull)
				.map(t -> t.atZone(ZoneOffset.UTC).toLocalDate()).toList();
		return new Progress(userId, xp, level, Xp.title(level), Xp.forLevel(level), Xp.forLevel(level + 1),
				Streaks.of(days, today), all.size());
	}

	/** {@code levelStartXp} and {@code nextLevelXp} bound the current level, for a progress bar. */
	public record Progress(int userId, int xp, int level, String title, int levelStartXp, int nextLevelXp,
			Streak streak, int gamesPlayed) {
	}
}
