package com.fazeel.maverick;

import java.security.SecureRandom;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.fazeel.maverick.generator.FactQuestionGenerator;

@SpringBootApplication
public class QuestionGeneratorApplication {

	public static void main(String[] args) {
		SpringApplication.run(QuestionGeneratorApplication.class, args);
	}

	@Bean
	FactQuestionGenerator factQuestionGenerator() {
		return new FactQuestionGenerator(new SecureRandom());
	}
}
