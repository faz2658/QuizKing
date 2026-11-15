package com.fazeel.maverick.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.fazeel.maverick.session.SeenQuestion;

public interface SeenQuestionRepository extends MongoRepository<SeenQuestion, String> {

	List<SeenQuestion> findByUserIdAndQuestionIdIn(int userId, Collection<Integer> questionIds);
}
