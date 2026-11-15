package com.stackroute.maverick;

import java.security.SecureRandom;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.stackroute.maverick.catalog.QuestionSelector;

@SpringBootApplication
public class GameManagerApplication {

	public static void main(String[] args) {
		SpringApplication.run(GameManagerApplication.class, args);
	}

	@Bean
	QuestionSelector questionSelector() {
		return new QuestionSelector(new SecureRandom());
	}
}
