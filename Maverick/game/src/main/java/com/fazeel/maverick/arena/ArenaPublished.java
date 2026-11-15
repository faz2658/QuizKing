package com.fazeel.maverick.arena;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Inserted once per round when its results are published (a duplicate insert means "already done"). */
@Document(collection = "arena_results")
public record ArenaPublished(@Id String roundId) {
}
