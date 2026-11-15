package com.fazeel.maverick.catalog;

import static org.springframework.data.mongodb.core.FindAndModifyOptions.options;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * Atomic id sequence. Replaces {@code rnd.nextInt(900000) + rnd.nextInt(900000)}, which could hand the
 * same id to two games and silently overwrite one of them.
 */
@Component
public class GameIdGenerator {

	/** Ids start above the range the old random generator used, so they never clash with old games. */
	static final long FIRST_ID = 2_000_000;

	private final MongoOperations mongo;

	public GameIdGenerator(MongoOperations mongo) {
		this.mongo = mongo;
	}

	public int nextGameId() {
		Sequence seq = mongo.findAndModify(query(where("_id").is("games")), new Update().inc("value", 1),
				options().returnNew(true).upsert(true), Sequence.class);
		return Math.toIntExact(FIRST_ID + seq.getValue());
	}
}
