package com.fazeel.quizcadia.topicquiz;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface TopicQuizAttemptRepository extends MongoRepository<TopicQuizAttempt, String> {

	List<TopicQuizAttempt> findByQuizIdOrderByScoreDescCompletedAtAsc(String quizId);
}
