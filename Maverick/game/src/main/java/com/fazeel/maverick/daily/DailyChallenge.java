package com.fazeel.maverick.daily;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fazeel.maverick.session.SessionQuestion;

/** One day's challenge, fixed when it is first requested so every player gets the same questions. */
@Document(collection = "daily_challenges")
public record DailyChallenge(@Id String date, List<SessionQuestion> questions, List<String> topics,
		Instant createdAt) {
}
