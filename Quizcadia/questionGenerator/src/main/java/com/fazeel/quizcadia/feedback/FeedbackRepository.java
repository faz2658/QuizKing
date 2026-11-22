package com.fazeel.quizcadia.feedback;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

/** Player feedback in MongoDB (question_feedback). */
public interface FeedbackRepository extends MongoRepository<QuestionFeedback, String> {

	List<QuestionFeedback> findByQuestionId(int questionId);

	void deleteByQuestionId(int questionId);
}
