package com.fazeel.quizcadia.topicquiz;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

/** Topic quiz attempts in MongoDB. */
public interface TopicQuizAttemptRepository extends MongoRepository<TopicQuizAttempt, String> {

	List<TopicQuizAttempt> findByQuizIdOrderByScoreDescCompletedAtAsc(String quizId);
}
