package com.fazeel.quizcadia.match;

import com.fazeel.quizcadia.domain.MultiPlayerResult;

/** Where a finished match's result goes (Kafka in production, a log line when Kafka is off). */
public interface MatchEvents {

	void matchCompleted(MultiPlayerResult result);
}
