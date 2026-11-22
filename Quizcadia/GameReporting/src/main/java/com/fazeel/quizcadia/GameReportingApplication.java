package com.fazeel.quizcadia;

import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * The reporting service: turns finished games (from Kafka) into reports, and from them works out XP, streaks,
 * badges, leaderboards, the Country Cup, the admin numbers and streak reminders.
 */
@SpringBootApplication
public class GameReportingApplication {

	public static void main(String[] args) {
		SpringApplication.run(GameReportingApplication.class, args);
	}

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}
}
