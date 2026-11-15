package com.stackroute.maverick.adaptive;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface SeenQuestionRepository extends MongoRepository<SeenQuestion, String> {

	List<SeenQuestion> findByUserIdAndGameId(int userId, int gameId);
}
