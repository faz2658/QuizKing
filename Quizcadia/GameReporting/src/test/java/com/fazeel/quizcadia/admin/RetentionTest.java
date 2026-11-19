package com.fazeel.quizcadia.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fazeel.quizcadia.admin.Retention.Cohort;
import com.fazeel.quizcadia.admin.Retention.DayN;
import com.fazeel.quizcadia.admin.Retention.Play;

class RetentionTest {

	/** Wednesday, so "this week" started Monday 2026-11-16. */
	static final LocalDate TODAY = LocalDate.parse("2026-11-18");
	static final int GUEST = 900_000_001;

	final List<Play> plays = new ArrayList<>();

	/** {@code user} finished a game at 15:00 UTC on each of these days. */
	void played(int user, String... days) {
		for (String d : days) {
			plays.add(new Play(user, LocalDate.parse(d).atTime(15, 0).toInstant(ZoneOffset.UTC)));
		}
	}

	static DayN day(List<DayN> figures, int n) {
		return figures.stream().filter(d -> d.day() == n).findFirst().orElseThrow();
	}

	@Test
	void dayOneCountsPlayersWhoCameBackTheNextDay() {
		played(1, "2026-11-01", "2026-11-02"); // back on day 1
		played(2, "2026-11-01", "2026-11-03"); // day 2 only: not day-1 retained
		played(3, "2026-11-05");                // never back
		played(4, "2026-11-09", "2026-11-09");  // two games, one day

		DayN d1 = day(Retention.compute(plays, TODAY, 8).everyone(), 1);

		assertThat(d1.eligible()).isEqualTo(4);
		assertThat(d1.returned()).isEqualTo(1);
		assertThat(d1.rate()).isCloseTo(0.25, within(1e-9));
	}

	@Test
	void playersWhoseDayNIsntOverYetAreLeftOut() {
		played(1, "2026-11-17");          // day 1 is today: too early to judge
		played(2, "2026-11-16", "2026-11-17"); // day 1 was yesterday: counted, and back
		played(3, "2026-11-12");          // day 7 would be 2026-11-19: not eligible for D7

		var view = Retention.compute(plays, TODAY, 8);

		assertThat(day(view.everyone(), 1).eligible()).isEqualTo(2); // users 2 and 3
		assertThat(day(view.everyone(), 1).returned()).isEqualTo(1);
		assertThat(day(view.everyone(), 7).eligible()).isZero();
		assertThat(day(view.everyone(), 7).rate()).isNull(); // nothing to measure yet, not 0%
	}

	@Test
	void dayNIsExactlyNDaysLater() {
		played(1, "2026-10-01", "2026-10-08"); // day 7
		played(2, "2026-10-01", "2026-10-09"); // day 8: not day-7 retained
		played(3, "2026-10-01", "2026-10-31"); // day 30

		var view = Retention.compute(plays, TODAY, 8);

		assertThat(day(view.everyone(), 7).returned()).isEqualTo(1);
		assertThat(day(view.everyone(), 30).returned()).isEqualTo(1);
		assertThat(day(view.everyone(), 30).eligible()).isEqualTo(3);
	}

	@Test
	void accountsFiguresLeaveGuestsOut() {
		played(1, "2026-11-01", "2026-11-02");
		played(GUEST, "2026-11-01");

		var view = Retention.compute(plays, TODAY, 8);

		assertThat(day(view.everyone(), 1).eligible()).isEqualTo(2);
		assertThat(day(view.accounts(), 1).eligible()).isEqualTo(1);
		assertThat(day(view.accounts(), 1).rate()).isEqualTo(1.0);
	}

	@Test
	void weeklyCohortsShowTheShareActiveInEachLaterWeek() {
		// started the week of Mon 2026-11-02
		played(1, "2026-11-03", "2026-11-10", "2026-11-17"); // weeks 0, 1, 2
		played(2, "2026-11-04", "2026-11-17");               // weeks 0, 2
		played(GUEST, "2026-11-08");                         // week 0 only (Sunday)
		// started the week of Mon 2026-11-16
		played(4, "2026-11-16");

		List<Cohort> cohorts = Retention.compute(plays, TODAY, 4).cohorts();

		assertThat(cohorts).extracting(Cohort::weekStart).containsExactly(LocalDate.parse("2026-10-26"),
				LocalDate.parse("2026-11-02"), LocalDate.parse("2026-11-09"), LocalDate.parse("2026-11-16"));
		Cohort nov2 = cohorts.get(1);
		assertThat(nov2.players()).isEqualTo(3);
		assertThat(nov2.guests()).isEqualTo(1);
		assertThat(nov2.weeks()).hasSize(3); // its own week and the two after it so far
		assertThat(nov2.weeks().get(0)).isEqualTo(1.0);
		assertThat(nov2.weeks().get(1)).isCloseTo(1 / 3.0, within(1e-9));
		assertThat(nov2.weeks().get(2)).isCloseTo(2 / 3.0, within(1e-9));
		assertThat(cohorts.get(0).players()).isZero();
		assertThat(cohorts.get(0).weeks()).containsOnlyNulls();
		assertThat(cohorts.get(3).weeks()).containsExactly(1.0);
	}

	@Test
	void earlyPlayersBeforeTheWindowDontCount() {
		played(1, "2025-01-01", "2026-11-17"); // started long ago: not a new player of these weeks
		played(2, "2026-11-10", "2026-11-11");

		var view = Retention.compute(plays, TODAY, 4);

		assertThat(view.players()).isEqualTo(1);
		assertThat(day(view.everyone(), 1).eligible()).isEqualTo(1);
		assertThat(view.cohorts()).allSatisfy(c -> assertThat(c.players()).isLessThanOrEqualTo(1));
	}
}
