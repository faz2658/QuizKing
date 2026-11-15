package com.fazeel.quizking.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.fazeel.quizking.session.SeenQuestion;

public interface SeenQuestionRepository extends MongoRepository<SeenQuestion, String> {

	List<SeenQuestion> findByUserIdAndQuestionIdIn(int userId, Collection<Integer> questionIds);
}
