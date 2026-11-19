package com.fazeel.quizcadia.push;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * When to remind a player whose streak is at risk. Streaks count UTC days, so every streak ends at UTC midnight:
 * 7 pm in Toronto, 4 pm in Los Angeles, but 5:30 am in India. The reminder aims for {@link #BEFORE} the end, moved
 * into waking hours ({@link #WAKE} to {@link #SLEEP}, local): if that moment falls at night, it goes at the evening
 * before instead, as long as that is still inside the same UTC day (and not in its first {@link #NOT_BEFORE_DAY_START}).
 */
public final class ReminderTimes {

	static final Duration BEFORE = Duration.ofHours(3);
	static final LocalTime WAKE = LocalTime.of(9, 0);
	static final LocalTime SLEEP = LocalTime.of(21, 0);
	static final Duration NOT_BEFORE_DAY_START = Duration.ofHours(1);

	private ReminderTimes() {
	}

	/** The moment UTC day {@code day} ends: the streak deadline. */
	public static Instant deadline(LocalDate day) {
		return day.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
	}

	/** When to remind for UTC day {@code day} in {@code zone}; empty when no waking moment in that day fits. */
	public static Optional<Instant> reminderAt(LocalDate day, ZoneId zone) {
		Instant start = day.atStartOfDay().toInstant(ZoneOffset.UTC);
		Instant target = deadline(day).minus(BEFORE);
		ZonedDateTime local = target.atZone(zone);
		if (!local.toLocalTime().isBefore(WAKE) && local.toLocalTime().isBefore(SLEEP)) {
			return Optional.of(target);
		}
		// at night there: the last evening (SLEEP) before the target instead
		ZonedDateTime evening = local.with(SLEEP);
		if (evening.toInstant().isAfter(target)) {
			evening = evening.minusDays(1);
		}
		Instant at = evening.toInstant();
		return at.isBefore(start.plus(NOT_BEFORE_DAY_START)) ? Optional.empty() : Optional.of(at);
	}
}
