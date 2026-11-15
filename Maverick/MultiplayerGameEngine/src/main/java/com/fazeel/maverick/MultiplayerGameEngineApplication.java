package com.fazeel.maverick;

import java.security.SecureRandom;
import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.fazeel.maverick.match.MatchFactory;

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
