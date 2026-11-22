package com.fazeel.quizcadia.bank;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.fazeel.quizcadia.domain.Category;

/** Categories in MongoDB, each document holding its topics and their questions. */
public interface CategoryRepository extends MongoRepository<Category, Integer> {

	Optional<Category> findByCategoryNameIgnoreCase(String name);

	/** The category holding the topic with this id (topic ids are unique across the bank). */
	Optional<Category> findFirstByTopic_TopicId(int topicId);
}
