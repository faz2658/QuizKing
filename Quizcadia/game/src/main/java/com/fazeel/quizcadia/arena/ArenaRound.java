package com.fazeel.quizcadia.arena;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fazeel.quizcadia.session.SessionQuestion;

/** One World Arena round's questions, fixed when first needed (seeded by the start time). */
@Document(collection = "arena_rounds")
public record ArenaRound(@Id String id, Instant startsAt, List<SessionQuestion> questions, List<String> topics) {
}
