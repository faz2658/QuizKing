package com.fazeel.quizcadia.arena;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Timing and ranking of World Arena rounds. A round starts every {@link #INTERVAL} (at :00, :15, :30 and :45
 * UTC); joining opens {@link #JOIN_BEFORE} earlier. Question i opens at {@code start + i * SLOT}; answers are
 * taken for {@link #ANSWER_TIME} (plus a second of network grace), then the answer is revealed. A wrong or
 * missing answer is out. If, after a question, at most one player is left (with two or more in the round),
 * the round ends: last one standing wins.
 */
public final class ArenaRules {

	public static final Duration INTERVAL = Duration.ofMinutes(15);
	public static final Duration JOIN_BEFORE = Duration.ofMinutes(10);
	public static final Duration ANSWER_TIME = Duration.ofSeconds(15);
	public static final Duration GRACE = Duration.ofSeconds(1);
	public static final Duration REVEAL = Duration.ofSeconds(4);
	public static final Duration SLOT = ANSWER_TIME.plus(REVEAL);
	public static final int QUESTIONS = 12;
	public static final int BASE_POINTS = 10;
	public static final int SPEED_POINTS = 10;

	private ArenaRules() {
	}

	public static Instant roundStart(Instant now) {
		long s = INTERVAL.toSeconds();
		return Instant.ofEpochSecond(Math.floorDiv(now.getEpochSecond(), s) * s);
	}

	public static Instant opens(Instant start, int index) {
		return start.plus(SLOT.multipliedBy(index));
	}

	/** Answers to question {@code index} are taken until this moment. */
	public static Instant closes(Instant start, int index) {
		return opens(start, index).plus(ANSWER_TIME).plus(GRACE);
	}

	/** Questions whose answers are no longer taken at {@code now}. */
	public static int closedCount(Instant start, Instant now) {
		int n = 0;
		while (n < QUESTIONS && !now.isBefore(closes(start, n))) {
			n++;
		}
		return n;
	}

	/**
	 * The last question the round plays: the first one after which at most one player is left (with 2+ players;
	 * a solo player plays until a miss), else the twelfth. Only questions already closed at {@code now} decide.
	 */
	public static int lastQuestion(Instant start, List<ArenaEntry> entries, Instant now) {
		int closed = closedCount(start, now);
		for (int k = 0; k < closed; k++) {
			final int survivedK = k + 1;
			long alive = entries.stream().filter(e -> e.survived(closed) >= survivedK).count();
			if ((entries.size() >= 2 && alive <= 1) || alive == 0) {
				return k;
			}
		}
		return QUESTIONS - 1;
	}

	public static Instant endsAt(Instant start, int lastQuestion) {
		return opens(start, lastQuestion + 1).minus(Duration.ZERO);
	}

	/** Points for a right answer: the base plus up to {@value #SPEED_POINTS} for answering early. */
	public static int points(Instant opened, Instant answered) {
		long left = ANSWER_TIME.toMillis() - Duration.between(opened, answered).toMillis();
		return BASE_POINTS + (int) Math.max(0, Math.round(SPEED_POINTS * (double) left / ANSWER_TIME.toMillis()));
	}

	/** Longest survival first, then points, then who joined first. */
	public static Comparator<ArenaEntry> ranking(int closed) {
		return Comparator.comparingInt((ArenaEntry e) -> e.survived(closed)).reversed()
				.thenComparing(Comparator.comparingInt(ArenaEntry::score).reversed())
				.thenComparing(ArenaEntry::getJoinedAt);
	}
}
