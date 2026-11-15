package com.fazeel.quizking.knowme;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface KnowMeAttemptRepository extends MongoRepository<KnowMeAttempt, String> {

	List<KnowMeAttempt> findByQuizIdOrderByScoreDescCompletedAtAsc(String quizId);

	long countByQuizId(String quizId);
}
