package com.fazeel.quizcadia;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

import com.fazeel.quizcadia.catalog.QuestionSelector;

/**
 * The game manager: owns the catalog of categories, topics and games (solo and multiplayer), and tells the game
 * engines about changes over Kafka.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class GameManagerApplication {

	public static void main(String[] args) {
		SpringApplication.run(GameManagerApplication.class, args);
	}

	@Bean
	QuestionSelector questionSelector() {
		return new QuestionSelector();
	}
}
