package com.fazeel.quizcadia.report;

import java.time.Instant;

/** Who finished a game when: the two fields of a {@link GameReport} the retention figures read. */
public record PlayTime(int userId, Instant completedAt) {
}
