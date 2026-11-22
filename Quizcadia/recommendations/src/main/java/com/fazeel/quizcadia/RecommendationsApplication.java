package com.fazeel.quizcadia;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The recommendation service: a Neo4j graph of players, games and categories, fed by Kafka events, that ranks
 * games for each player.
 */
@SpringBootApplication
public class RecommendationsApplication {

	public static void main(String[] args) {
		SpringApplication.run(RecommendationsApplication.class, args);
	}
}
