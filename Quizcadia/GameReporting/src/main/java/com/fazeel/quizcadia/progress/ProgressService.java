package com.fazeel.quizcadia.progress;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.fazeel.quizcadia.board.LeaderboardService;
import com.fazeel.quizcadia.progress.Achievements.Achievement;
import com.fazeel.quizcadia.progress.Streaks.Streak;
import com.fazeel.quizcadia.report.GameReport;
import com.fazeel.quizcadia.report.GameReportRepository;

/** A player's XP, level and streak, worked out from their game reports. */
@Service
public class ProgressService {

	private final GameReportRepository reports;
	private final LeaderboardService boards;
	private final Clock clock;

	private final com.fazeel.quizcadia.invite.Invites invites;

	public ProgressService(GameReportRepository reports, LeaderboardService boards, Clock clock,
			com.fazeel.quizcadia.invite.Invites invites) {
		this.reports = reports;
		this.boards = boards;
		this.clock = clock;
		this.invites = invites;
	}

	/** Every badge, earned or not, with how far along the player is. */
	public List<Achievement> achievements(int userId) {
		List<GameReport> all = reports.findByUserIdOrderByCompletedAtDesc(userId);
		return Achievements.of(all, r -> boards.dailyRank(r.variantKey(), userId), invites.invited(userId));
	}

	public Progress progress(int userId) {
		return of(userId, reports.findByUserIdOrderByCompletedAtDesc(userId), today(), invites.shields(userId));
	}

	LocalDate today() {
		return LocalDate.now(clock.withZone(ZoneOffset.UTC));
	}

	static Progress of(int userId, List<GameReport> all, LocalDate today) {
		return of(userId, all, today, 0);
	}

	static Progress of(int userId, List<GameReport> all, LocalDate today, int shields) {
		int xp = all.stream().mapToInt(Xp::of).sum();
		int level = Xp.level(xp);
		List<LocalDate> days = all.stream().map(GameReport::completedAt).filter(Objects::nonNull)
				.map(t -> t.atZone(ZoneOffset.UTC).toLocalDate()).toList();
		return new Progress(userId, xp, level, Xp.title(level), Xp.forLevel(level), Xp.forLevel(level + 1),
				Streaks.of(days, today, shields), all.size());
	}

	/** {@code levelStartXp} and {@code nextLevelXp} bound the current level, for a progress bar. */
	public record Progress(int userId, int xp, int level, String title, int levelStartXp, int nextLevelXp,
			Streak streak, int gamesPlayed) {
	}
}
