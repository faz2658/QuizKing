package com.fazeel.maverick;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

import com.fazeel.maverick.catalog.QuestionSelector;

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
