package com.fazeel.quizcadia;

import java.security.SecureRandom;
import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.fazeel.quizcadia.session.QuizBuilder;

/**
 * The solo game engine: plays quizzes one question at a time, plus the Daily Challenge, World Arena, friend
 * challenges and duels, and "How well do you know me?" quizzes.
 */
@SpringBootApplication
public class App {

	public static void main(String[] args) {
		SpringApplication.run(App.class, args);
	}

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

	@Bean
	QuizBuilder quizBuilder() {
		return new QuizBuilder(new SecureRandom());
	}
}
