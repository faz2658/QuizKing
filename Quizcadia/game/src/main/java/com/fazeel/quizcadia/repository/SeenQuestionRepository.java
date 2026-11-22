package com.fazeel.quizcadia.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.fazeel.quizcadia.session.SeenQuestion;

/** When each player last saw each question, so new ones come first. */
public interface SeenQuestionRepository extends MongoRepository<SeenQuestion, String> {

	List<SeenQuestion> findByUserIdAndQuestionIdIn(int userId, Collection<Integer> questionIds);
}
