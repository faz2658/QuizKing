package com.fazeel.quizcadia.catalog;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.fazeel.quizcadia.domain.MultiPlayerGame;

/** Multiplayer games, with the lookups the catalog's API needs. */
public interface MultiPlayerGameRepository extends MongoRepository<MultiPlayerGame, Integer> {

	List<MultiPlayerGame> findByCategoryIdAndTopicTopicNameIgnoreCase(int categoryId, String topicName);
}
