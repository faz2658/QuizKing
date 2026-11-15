package com.fazeel.maverick.adaptive;

import java.util.Collection;
import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface SeenQuestionRepository extends MongoRepository<SeenQuestion, String> {

	List<SeenQuestion> findByUserIdAndQuestionIdIn(int userId, Collection<Integer> questionIds);
}
