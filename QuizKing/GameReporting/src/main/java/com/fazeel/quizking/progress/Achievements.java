package com.fazeel.quizking.progress;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.ToIntFunction;

import com.fazeel.quizking.report.GameReport;
import com.fazeel.quizking.report.GameReport.GameMode;
import com.fazeel.quizking.report.GameReport.ReviewItem;

/**
 * Badges, worked out by replaying a player's reports in the order they were played, so each badge knows when
 * it was earned. Nothing is stored: a badge's rule can change and every player is re-evaluated.
 */
public final class Achievements {

	public enum Badge {
		FIRST_GAME("First steps", "Finish your first game", "🎯", 1),
		REGULAR("Regular", "Finish 10 games", "🎮", 10),
		DEVOTED("Devoted", "Finish 50 games", "🏰", 50),
		FLAWLESS("Flawless", "Answer every question right in a solo game (5+ questions)", "💎", 1),
		COMBO("Combo master", "Get 5 answers right in a row in one game", "⚡", 5),
		ON_FIRE("On fire", "Play 3 days in a row", "🔥", 3),
		UNSTOPPABLE("Unstoppable", "Play 7 days in a row", "🌋", 7),
		EXPLORER("Explorer", "Play games in 5 different categories", "🧭", 5),
		SCHOLAR("Scholar", "Score 80%+ in 10 different topics", "📜", 10),
		DAILY("Daily player", "Finish a Daily Challenge", "📅", 1),
		PODIUM("Podium", "Finish in the top 3 of a Daily Challenge", "🏅", 1),
		CHAMPION("Champion", "Win a multiplayer match", "🏆", 1),
		CLIMBER("Climber", "Reach level 3 in an adaptive game", "🧗", 3),
		KING_SLAYER("Beat a King", "Beat a friend's challenge score", "⚔️", 1),
		ARENA_CHAMPION("Last one standing", "Win a World Arena round", "🌍", 1),
		KNIGHTED("Knighted", "Reach player level 5 (Knight)", "🛡️", 5),
		RECRUITER("Recruiter", "A friend joins through your invite link", "🤝", 1),
		AMBASSADOR("Ambassador", "5 friends join through your invite link", "📣", 5);

		public final String title;
		public final String description;
		public final String icon;
		public final int goal;

		Badge(String title, String description, String icon, int goal) {
			this.title = title;
			this.description = description;
			this.icon = icon;
			this.goal = goal;
		}
	}

	/** {@code progress} is how far along a locked badge is (capped at {@code goal}). */
	public record Achievement(Badge id, String title, String description, String icon, int progress, int goal,
			Instant unlockedAt) {
	}

	private Achievements() {
	}

	/**
	 * @param dailyRank the player's final rank in the Daily Challenge of a given day (report's variant key)
	 */
	public static List<Achievement> of(List<GameReport> reports, ToIntFunction<GameReport> dailyRank) {
		return of(reports, dailyRank, List.of());
	}

	/** @param invited when each friend the player invited joined, oldest first */
	public static List<Achievement> of(List<GameReport> reports, ToIntFunction<GameReport> dailyRank,
			List<Instant> invited) {
		List<GameReport> played = new ArrayList<>(reports.stream().filter(r -> r.completedAt() != null).toList());
		played.sort(Comparator.comparing(GameReport::completedAt));

		Map<Badge, Integer> progress = new EnumMap<>(Badge.class);
		Map<Badge, Instant> unlocked = new EnumMap<>(Badge.class);
		Set<Integer> categories = new HashSet<>();
		Set<String> masteredTopics = new HashSet<>();
		Set<LocalDate> days = new HashSet<>();
		int games = 0;
		int xp = 0;
		for (GameReport r : played) {
			Instant at = r.completedAt();
			games++;
			xp += Xp.of(r);
			categories.add(r.categoryId());
			if (r.accuracy() >= 80 && r.totalQuestions() >= 5 && r.gameName() != null && !r.isDaily()) {
				masteredTopics.add(r.gameName());
			}
			days.add(at.atZone(ZoneOffset.UTC).toLocalDate());

			reach(Badge.FIRST_GAME, games, at, progress, unlocked);
			reach(Badge.REGULAR, games, at, progress, unlocked);
			reach(Badge.DEVOTED, games, at, progress, unlocked);
			if (r.mode() == GameMode.SINGLE && r.totalQuestions() >= 5 && r.correctAnswers() == r.totalQuestions()) {
				reach(Badge.FLAWLESS, 1, at, progress, unlocked);
			}
			reach(Badge.COMBO, longestRun(r.questions()), at, progress, unlocked);
			int streak = streakEndingOn(days, at.atZone(ZoneOffset.UTC).toLocalDate());
			reach(Badge.ON_FIRE, streak, at, progress, unlocked);
			reach(Badge.UNSTOPPABLE, streak, at, progress, unlocked);
			reach(Badge.EXPLORER, categories.size(), at, progress, unlocked);
			reach(Badge.SCHOLAR, masteredTopics.size(), at, progress, unlocked);
			if (r.isDaily()) {
				reach(Badge.DAILY, 1, at, progress, unlocked);
				int rank = dailyRank.applyAsInt(r);
				if (rank >= 1 && rank <= 3) {
					reach(Badge.PODIUM, 1, at, progress, unlocked);
				}
			}
			if (r.mode() == GameMode.MULTI && Xp.wonMatch(r)) {
				reach(Badge.CHAMPION, 1, at, progress, unlocked);
			}
			if (r.mode() == GameMode.ADAPTIVE && r.highestLevel() != null) {
				reach(Badge.CLIMBER, r.highestLevel(), at, progress, unlocked);
			}
			if (Xp.wonChallenge(r)) {
				reach(Badge.KING_SLAYER, 1, at, progress, unlocked);
			}
			if (Xp.wonArena(r)) {
				reach(Badge.ARENA_CHAMPION, 1, at, progress, unlocked);
			}
			reach(Badge.KNIGHTED, Xp.level(xp), at, progress, unlocked);
		}
		for (int i = 0; i < invited.size(); i++) {
			reach(Badge.RECRUITER, i + 1, invited.get(i), progress, unlocked);
			reach(Badge.AMBASSADOR, i + 1, invited.get(i), progress, unlocked);
		}
		List<Achievement> all = new ArrayList<>();
		for (Badge b : Badge.values()) {
			all.add(new Achievement(b, b.title, b.description, b.icon, Math.min(b.goal, progress.getOrDefault(b, 0)),
					b.goal, unlocked.get(b)));
		}
		return all;
	}

	private static void reach(Badge badge, int value, Instant at, Map<Badge, Integer> progress,
			Map<Badge, Instant> unlocked) {
		progress.merge(badge, value, Math::max);
		if (value >= badge.goal) {
			unlocked.putIfAbsent(badge, at);
		}
	}

	static int longestRun(List<ReviewItem> questions) {
		int best = 0;
		int run = 0;
		for (ReviewItem q : Objects.requireNonNullElse(questions, List.<ReviewItem>of())) {
			run = q.correct() ? run + 1 : 0;
			best = Math.max(best, run);
		}
		return best;
	}

	private static int streakEndingOn(Set<LocalDate> days, LocalDate day) {
		int n = 0;
		while (days.contains(day.minusDays(n))) {
			n++;
		}
		return n;
	}
}
