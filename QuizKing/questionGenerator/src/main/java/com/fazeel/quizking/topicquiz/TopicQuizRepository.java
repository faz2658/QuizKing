package com.fazeel.quizking.topicquiz;

import java.time.Instant;
import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface TopicQuizRepository extends MongoRepository<TopicQuiz, String> {

	long countByCreatorIdAndCreatedAtAfter(int creatorId, Instant since);

	long countByStatus(TopicQuiz.Status status);

	List<TopicQuiz> findByStatus(TopicQuiz.Status status);
}
