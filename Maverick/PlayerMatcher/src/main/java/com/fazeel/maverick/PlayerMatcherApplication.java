package com.fazeel.maverick;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.fazeel.maverick.matching.Matchmaker;
import com.fazeel.maverick.matching.PrivateRooms;
import com.fazeel.maverick.matching.MatchmakingRules;

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class PlayerMatcherApplication {

	private static final Logger log = LoggerFactory.getLogger(PlayerMatcherApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(PlayerMatcherApplication.class, args);
	}

	@Bean
	Matchmaker matchmaker(MatchmakingRules rules, KafkaTemplate<String, Object> kafka,
			@Value("${maverick.kafka.enabled}") boolean kafkaEnabled,
			@Value("${maverick.topics.players-matched}") String topic) {
		return new Matchmaker(rules, Clock.systemUTC(), match -> {
			log.info("Match {} for game {} with players {}", match.matchId(), match.gameId(), match.players());
			if (kafkaEnabled) {
				kafka.send(topic, match.matchId(), match);
			}
		});
	}

	@Bean
	PrivateRooms privateRooms(KafkaTemplate<String, Object> kafka, @Value("${maverick.kafka.enabled}") boolean kafkaEnabled,
			@Value("${maverick.topics.players-matched}") String topic) {
		return new PrivateRooms(Clock.systemUTC(), new java.security.SecureRandom(), match -> {
			log.info("Private room match {} for game {} with players {}", match.matchId(), match.gameId(), match.players());
			if (kafkaEnabled) {
				kafka.send(topic, match.matchId(), match);
			}
		});
	}

	@Bean
	LobbyTimer lobbyTimer(Matchmaker matchmaker, PrivateRooms rooms) {
		return new LobbyTimer(matchmaker, rooms);
	}

	static class LobbyTimer {
		private final Matchmaker matchmaker;
		private final PrivateRooms rooms;

		LobbyTimer(Matchmaker matchmaker, PrivateRooms rooms) {
			this.matchmaker = matchmaker;
			this.rooms = rooms;
		}

		@Scheduled(fixedDelay = 500)
		void tick() {
			matchmaker.tick();
			rooms.tick();
		}
	}
}
