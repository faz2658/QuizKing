package com.fazeel.quizking.match;

import java.util.Collection;
import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface SeenQuestionRepository extends MongoRepository<SeenQuestion, String> {

	List<SeenQuestion> findByUserIdInAndQuestionIdIn(Collection<Integer> userIds, Collection<Integer> questionIds);
}
