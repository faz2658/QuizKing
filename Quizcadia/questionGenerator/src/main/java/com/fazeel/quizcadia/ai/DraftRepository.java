package com.fazeel.quizcadia.ai;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

/** AI drafts in MongoDB (draft_questions). */
public interface DraftRepository extends MongoRepository<DraftQuestion, String> {

	List<DraftQuestion> findByStatusOrderByCreatedAtDesc(DraftQuestion.Status status);

	List<DraftQuestion> findByCategoryIdAndTopicNameIgnoreCase(int categoryId, String topicName);
}
