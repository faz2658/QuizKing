package com.fazeel.quizking.catalog;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.fazeel.quizking.domain.MultiPlayerGame;

public interface MultiPlayerGameRepository extends MongoRepository<MultiPlayerGame, Integer> {

	List<MultiPlayerGame> findByCategoryIdAndTopicTopicNameIgnoreCase(int categoryId, String topicName);
}
