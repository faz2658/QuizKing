package com.fazeel.quizcadia.progress;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fazeel.quizcadia.progress.ProgressService.Progress;
import com.fazeel.quizcadia.progress.Streaks.Streak;
import com.fazeel.quizcadia.report.GameReport;
import com.fazeel.quizcadia.report.GameReport.GameMode;

class ProgressTest {

	static final LocalDate TODAY = LocalDate.parse("2026-11-15");

	static GameReport solo(int correct, int total, String day, String variant, Integer opponent, int score) {
		return new GameReport("s" + day + correct + score, GameMode.SINGLE, 1, 5, "Art", 9, score, 100, correct, total,
				null, null, null, Instant.parse(day + "T12:00:00Z"), List.of(), variant, null, opponent);
	}

	@Test
	void xpRewardsCorrectAnswersAndBonuses() {
		assertThat(Xp.of(solo(3, 10, "2026-11-15", null, null, 30))).isEqualTo(5 + 30);
		assertThat(Xp.of(solo(10, 10, "2026-11-15", null, null, 100))).isEqualTo(5 + 100 + 25); // perfect
		assertThat(Xp.of(solo(4, 10, "2026-11-15", "DAILY", null, 40))).isEqualTo(5 + 40 + 20);
		assertThat(Xp.of(solo(4, 10, "2026-11-15", "CHALLENGE", 30, 40))).isEqualTo(5 + 40 + 25); // beat the friend
		assertThat(Xp.of(solo(4, 10, "2026-11-15", "CHALLENGE", 50, 40))).isEqualTo(5 + 40);

		GameReport win = new GameReport("m", GameMode.MULTI, 1, 5, "Art", 9, 30, null, 3, 5, 1, 3, null,
				Instant.parse("2026-11-15T12:00:00Z"), List.of());
		GameReport aloneInMatch = new GameReport("m2", GameMode.MULTI, 1, 5, "Art", 9, 30, null, 3, 5, 1, 1, null,
				Instant.parse("2026-11-15T12:00:00Z"), List.of());
		assertThat(Xp.of(win)).isEqualTo(5 + 30 + 30);
		assertThat(Xp.of(aloneInMatch)).isEqualTo(5 + 30);

		GameReport climbed = new GameReport("a", GameMode.ADAPTIVE, 1, 5, "Art", 9, 80, null, 6, 12, null, null, 3,
				Instant.parse("2026-11-15T12:00:00Z"), List.of());
		assertThat(Xp.of(climbed)).isEqualTo(5 + 60 + 20);
	}

	@Test
	void levelsGetSteeperAndHaveRoyalTitles() {
		assertThat(Xp.level(0)).isEqualTo(1);
		assertThat(Xp.level(99)).isEqualTo(1);
		assertThat(Xp.level(100)).isEqualTo(2);
		assertThat(Xp.level(300)).isEqualTo(3);
		assertThat(Xp.level(999)).isEqualTo(4);
		assertThat(Xp.level(1000)).isEqualTo(5);
		assertThat(Xp.title(1)).isEqualTo("Peasant");
		assertThat(Xp.title(5)).isEqualTo("Knight");
		assertThat(Xp.title(30)).isEqualTo("King");
	}

	@Test
	void streakCountsDaysInARowAndSurvivesUntilMidnight() {
		List<LocalDate> days = List.of(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-02"),
				LocalDate.parse("2026-11-03"), LocalDate.parse("2026-11-03"), LocalDate.parse("2026-11-13"),
				LocalDate.parse("2026-11-14"));

		Streak beforePlayingToday = Streaks.of(days, TODAY);
		assertThat(beforePlayingToday).isEqualTo(new Streak(2, 3, false)); // 13th and 14th, still alive

		Streak afterPlaying = Streaks.of(new ArrayList<>(days) {{ add(TODAY); }}, TODAY);
		assertThat(afterPlaying).isEqualTo(new Streak(3, 3, true));

		assertThat(Streaks.of(days, TODAY.plusDays(2)).current()).isZero(); // missed a whole day
		assertThat(Streaks.of(List.of(), TODAY)).isEqualTo(new Streak(0, 0, false));
	}

	@Test
	void progressAddsItAllUp() {
		Progress p = ProgressService.of(1, List.of(solo(10, 10, "2026-11-15", null, null, 100),
				solo(5, 10, "2026-11-14", "DAILY", null, 50)), TODAY);

		assertThat(p.xp()).isEqualTo(130 + 75);
		assertThat(p.level()).isEqualTo(2);
		assertThat(p.title()).isEqualTo("Page");
		assertThat(p.levelStartXp()).isEqualTo(100);
		assertThat(p.nextLevelXp()).isEqualTo(300);
		assertThat(p.streak()).isEqualTo(new Streak(2, 2, true));
		assertThat(p.gamesPlayed()).isEqualTo(2);
	}

	@Test
	void winningTheArenaPaysABonus() {
		GameReport winner = new GameReport("a", GameMode.SINGLE, 1, 0, "World Arena", 0, 120, 240, 6, 7, 1, 25, null,
				Instant.parse("2026-11-15T12:00:00Z"), List.of(), "ARENA", "2026-11-15T12:00:00Z", null);
		GameReport second = new GameReport("b", GameMode.SINGLE, 1, 0, "World Arena", 0, 100, 240, 5, 6, 2, 25, null,
				Instant.parse("2026-11-15T12:00:00Z"), List.of(), "ARENA", "2026-11-15T12:00:00Z", null);
		assertThat(Xp.of(winner)).isEqualTo(5 + 60 + 50);
		assertThat(Xp.of(second)).isEqualTo(5 + 50);
	}
}
