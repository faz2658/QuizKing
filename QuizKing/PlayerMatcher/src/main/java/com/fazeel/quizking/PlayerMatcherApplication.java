package com.fazeel.quizking;

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

import com.fazeel.quizking.matching.Matchmaker;
import com.fazeel.quizking.matching.Presence;
import com.fazeel.quizking.matching.PrivateRooms;
import com.fazeel.quizking.matching.QuickMatch;
import com.fazeel.quizking.matching.MatchmakingRules;

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class PlayerMatcherApplication {

	private static final Logger log = LoggerFactory.getLogger(PlayerMatcherApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(PlayerMatcherApplication.class, args);
	}

	@Bean
	Presence presence() {
		return new Presence(Clock.systemUTC());
	}

	/** Publishes a match to the multiplayer engine, with the players' names and countries. */
	@Bean
	java.util.function.Consumer<com.fazeel.quizking.domain.Match> matchPublisher(Presence presence,
			KafkaTemplate<String, Object> kafka, @Value("${quizking.kafka.enabled}") boolean kafkaEnabled,
			@Value("${quizking.topics.players-matched}") String topic) {
		return raw -> {
			com.fazeel.quizking.domain.Match match = presence.enrich(raw);
			log.info("Match {} for game {} with players {}", match.matchId(), match.gameId(), match.players());
			if (kafkaEnabled) {
				kafka.send(topic, match.matchId(), match);
			}
		};
	}

	@Bean
	Matchmaker matchmaker(MatchmakingRules rules, java.util.function.Consumer<com.fazeel.quizking.domain.Match> matchPublisher) {
		return new Matchmaker(rules, Clock.systemUTC(), matchPublisher);
	}

	@Bean
	PrivateRooms privateRooms(java.util.function.Consumer<com.fazeel.quizking.domain.Match> matchPublisher) {
		return new PrivateRooms(Clock.systemUTC(), new java.security.SecureRandom(), matchPublisher);
	}

	@Bean
	QuickMatch quickMatch(org.springframework.web.client.RestClient.Builder http,
			@Value("${quizking.game-manager-url}") String gameManagerUrl,
			java.util.function.Consumer<com.fazeel.quizking.domain.Match> matchPublisher) {
		return new QuickMatch(Clock.systemUTC(), new java.security.SecureRandom(),
				new com.fazeel.quizking.config.MultiplayerGames(http, gameManagerUrl, Clock.systemUTC()), matchPublisher);
	}

	@Bean
	LobbyTimer lobbyTimer(Matchmaker matchmaker, PrivateRooms rooms, QuickMatch quick, Presence presence) {
		return new LobbyTimer(matchmaker, rooms, quick, presence);
	}

	static class LobbyTimer {
		private final Matchmaker matchmaker;
		private final PrivateRooms rooms;
		private final QuickMatch quick;
		private final Presence presence;

		LobbyTimer(Matchmaker matchmaker, PrivateRooms rooms, QuickMatch quick, Presence presence) {
			this.matchmaker = matchmaker;
			this.rooms = rooms;
			this.quick = quick;
			this.presence = presence;
		}

		@Scheduled(fixedDelay = 500)
		void tick() {
			matchmaker.tick();
			rooms.tick();
			quick.tick();
		}

		@Scheduled(fixedDelay = 3_600_000)
		void forget() {
			presence.tick();
		}
	}
}
