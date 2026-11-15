package com.fazeel.maverick.arena;

import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.repository.MongoRepository;

/** Storage for the arena. */
public final class ArenaRepositories {

	private ArenaRepositories() {
	}

	public interface Rounds extends MongoRepository<ArenaRound, String> {
	}

	public interface Entries extends MongoRepository<ArenaEntry, String> {
		List<ArenaEntry> findByRoundId(String roundId);

		long countByRoundId(String roundId);
	}

	/** Inserted once per round when its results are published (a duplicate insert means "already done"). */
	@Document(collection = "arena_results")
	public record Published(@Id String roundId) {
	}

	public interface PublishedRounds extends MongoRepository<Published, String> {
	}
}
