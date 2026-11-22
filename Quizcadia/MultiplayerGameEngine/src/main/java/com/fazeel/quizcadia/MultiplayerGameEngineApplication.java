package com.fazeel.quizcadia;

import java.security.SecureRandom;
import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.fazeel.quizcadia.match.MatchFactory;

/**
 * The multiplayer game engine: runs fastest-finger matches and live room quizzes, with all timing and scoring on
 * the server.
 */
@SpringBootApplication
public class MultiplayerGameEngineApplication {

	public static void main(String[] args) {
		SpringApplication.run(MultiplayerGameEngineApplication.class, args);
	}

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

	@Bean
	MatchFactory matchFactory() {
		return new MatchFactory(new SecureRandom());
	}
}
