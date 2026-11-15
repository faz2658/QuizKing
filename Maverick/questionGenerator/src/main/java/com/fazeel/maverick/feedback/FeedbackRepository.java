package com.fazeel.maverick.feedback;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface FeedbackRepository extends MongoRepository<QuestionFeedback, String> {

	List<QuestionFeedback> findByQuestionId(int questionId);

	void deleteByQuestionId(int questionId);
}
