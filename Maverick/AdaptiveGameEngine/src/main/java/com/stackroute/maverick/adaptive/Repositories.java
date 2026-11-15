package com.stackroute.maverick.adaptive;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.stackroute.maverick.domain.Game;

public final class Repositories {

	private Repositories() {
	}

	public interface SessionRepository extends MongoRepository<AdaptiveSession, String> {
	}

	public interface GameRepository extends MongoRepository<Game, Integer> {
	}

	public interface SeenQuestionRepository extends MongoRepository<SeenQuestion, String> {

		List<SeenQuestion> findByUserIdAndGameId(int userId, int gameId);
	}
}
