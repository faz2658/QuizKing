package com.fazeel.maverick.progress;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.fazeel.maverick.progress.Achievements.Achievement;
import com.fazeel.maverick.progress.Achievements.Badge;
import com.fazeel.maverick.report.GameReport;
import com.fazeel.maverick.report.GameReport.GameMode;
import com.fazeel.maverick.report.GameReport.ReviewItem;

class AchievementsTest {

	static List<ReviewItem> answers(boolean... correct) {
		List<ReviewItem> items = new ArrayList<>();
		for (boolean c : correct) {
			items.add(new ReviewItem("q", "a", c ? "a" : "b", c, false, c ? 10 : 0));
		}
		return items;
	}

	static GameReport solo(String day, int category, String topic, String variant, boolean... correct) {
		int right = 0;
		for (boolean c : correct) {
			right += c ? 1 : 0;
		}
		return new GameReport(day + topic, GameMode.SINGLE, 1, 5, topic, category, right * 10, correct.length * 10,
				right, correct.length, null, null, null, Instant.parse(day + "T12:00:00Z"), answers(correct), variant,
				day, null);
	}

	static Map<Badge, Achievement> byId(List<Achievement> all) {
		return all.stream().collect(Collectors.toMap(Achievement::id, Function.identity()));
	}

	@Test
	void badgesUnlockWhenFirstEarnedAndLockedOnesShowProgress() {
		List<GameReport> reports = List.of(
				solo("2026-11-13", 1, "Art", null, true, true, false, true, true),
				solo("2026-11-14", 2, "Maths", null, true, true, true, true, true), // perfect, combo of 5
				solo("2026-11-15", 3, "History", "DAILY", false, true, false, true, false));

		Map<Badge, Achievement> got = byId(Achievements.of(reports, r -> 2));

		assertThat(got.get(Badge.FIRST_GAME).unlockedAt()).isEqualTo(Instant.parse("2026-11-13T12:00:00Z"));
		assertThat(got.get(Badge.FLAWLESS).unlockedAt()).isEqualTo(Instant.parse("2026-11-14T12:00:00Z"));
		assertThat(got.get(Badge.COMBO).unlockedAt()).isEqualTo(Instant.parse("2026-11-14T12:00:00Z"));
		assertThat(got.get(Badge.ON_FIRE).unlockedAt()).isEqualTo(Instant.parse("2026-11-15T12:00:00Z"));
		assertThat(got.get(Badge.DAILY).unlockedAt()).isNotNull();
		assertThat(got.get(Badge.PODIUM).unlockedAt()).isNotNull(); // rank 2 that day

		Achievement regular = got.get(Badge.REGULAR);
		assertThat(regular.unlockedAt()).isNull();
		assertThat(regular.progress()).isEqualTo(3);
		assertThat(regular.goal()).isEqualTo(10);
		assertThat(got.get(Badge.EXPLORER).progress()).isEqualTo(3);
		assertThat(got.get(Badge.SCHOLAR).progress()).isEqualTo(2); // Art 80%, Maths 100%
		assertThat(got.get(Badge.CHAMPION).unlockedAt()).isNull();
	}

	@Test
	void everyBadgeIsListedEvenForANewPlayer() {
		List<Achievement> none = Achievements.of(List.of(), r -> 0);
		assertThat(none).hasSize(Badge.values().length).allMatch(a -> a.unlockedAt() == null && a.progress() == 0);
	}

	@Test
	void longestRunOfCorrectAnswers() {
		assertThat(Achievements.longestRun(answers(true, true, false, true, true, true))).isEqualTo(3);
		assertThat(Achievements.longestRun(null)).isZero();
	}
}
