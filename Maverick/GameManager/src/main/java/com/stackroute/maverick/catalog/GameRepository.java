package com.stackroute.maverick.catalog;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.stackroute.maverick.domain.Game;

public interface GameRepository extends MongoRepository<Game, Integer> {

	List<Game> findByGameNameStartingWithIgnoreCase(String prefix);

	List<Game> findByGameNameIgnoreCase(String name);

	List<Game> findByCategoryIdAndTopicTopicNameIgnoreCase(int categoryId, String topicName);
}
