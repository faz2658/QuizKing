package com.fazeel.quizcadia.catalog;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.fazeel.quizcadia.domain.Game;

/** Solo games, with the lookups the catalog's API needs. */
public interface GameRepository extends MongoRepository<Game, Integer> {

	List<Game> findByGameNameStartingWithIgnoreCase(String prefix);

	List<Game> findByGameNameIgnoreCase(String name);

	List<Game> findByCategoryIdAndTopicTopicNameIgnoreCase(int categoryId, String topicName);
}
