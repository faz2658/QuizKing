package com.fazeel.quizcadia.topicquiz;

import java.time.Instant;
import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

/** Topic quizzes in MongoDB. */
public interface TopicQuizRepository extends MongoRepository<TopicQuiz, String> {

	long countByCreatorIdAndCreatedAtAfter(int creatorId, Instant since);

	long countByStatus(TopicQuiz.Status status);

	List<TopicQuiz> findByStatus(TopicQuiz.Status status);
}
