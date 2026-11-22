package com.fazeel.quizcadia.knowme;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

/** Friends' answers to know-me quizzes. */
public interface KnowMeAttemptRepository extends MongoRepository<KnowMeAttempt, String> {

	List<KnowMeAttempt> findByQuizIdOrderByScoreDescCompletedAtAsc(String quizId);

	long countByQuizId(String quizId);
}
