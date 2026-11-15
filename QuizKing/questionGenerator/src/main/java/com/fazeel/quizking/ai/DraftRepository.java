package com.fazeel.quizking.ai;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface DraftRepository extends MongoRepository<DraftQuestion, String> {

	List<DraftQuestion> findByStatusOrderByCreatedAtDesc(DraftQuestion.Status status);

	List<DraftQuestion> findByCategoryIdAndTopicNameIgnoreCase(int categoryId, String topicName);
}
