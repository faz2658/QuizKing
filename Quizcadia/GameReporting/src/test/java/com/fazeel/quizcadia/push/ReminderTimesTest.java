package com.fazeel.quizcadia.push;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

class ReminderTimesTest {

	static final LocalDate DAY = LocalDate.parse("2026-11-18");

	static String local(String zone) {
		return ReminderTimes.reminderAt(DAY, ZoneId.of(zone)).map(i -> i.atZone(ZoneId.of(zone)).toLocalDateTime().toString())
				.orElse("none");
	}

	@Test
	void streaksEndAtUtcMidnight() {
		assertThat(ReminderTimes.deadline(DAY)).isEqualTo(Instant.parse("2026-11-19T00:00:00Z"));
	}

	@Test
	void threeHoursBeforeTheEndWhenThatIsDaytimeThere() {
		assertThat(local("UTC")).isEqualTo("2026-11-18T21:00");              // 9 pm, the end at midnight
		assertThat(local("Europe/London")).isEqualTo("2026-11-18T21:00");
		assertThat(local("America/Toronto")).isEqualTo("2026-11-18T16:00");  // ends 7 pm local
		assertThat(local("America/Los_Angeles")).isEqualTo("2026-11-18T13:00"); // ends 4 pm local
		assertThat(local("Pacific/Honolulu")).isEqualTo("2026-11-18T11:00");
		assertThat(local("Pacific/Auckland")).isEqualTo("2026-11-19T10:00"); // ends 1 pm local the next day
	}

	@Test
	void theEveningBeforeWhenTheEndFallsAtNight() {
		assertThat(local("Asia/Kolkata")).isEqualTo("2026-11-18T21:00"); // ends 5:30 am: remind the evening before
		assertThat(local("Asia/Tokyo")).isEqualTo("2026-11-18T21:00");   // ends 9 am
		assertThat(local("Europe/Moscow")).isEqualTo("2026-11-18T21:00"); // ends 3 am
	}

	@Test
	void alwaysInsideTheUtcDayItRemindsAbout() {
		for (String zone : ZoneId.getAvailableZoneIds()) {
			ReminderTimes.reminderAt(DAY, ZoneId.of(zone)).ifPresent(at -> {
				assertThat(at).as(zone).isAfterOrEqualTo(Instant.parse("2026-11-18T01:00:00Z"));
				assertThat(at).as(zone).isBefore(ReminderTimes.deadline(DAY));
				int hour = at.atZone(ZoneId.of(zone)).getHour();
				assertThat(hour).as(zone).isBetween(9, 21);
			});
		}
	}
}
