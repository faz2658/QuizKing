package com.fazeel.quizcadia;

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

import com.fazeel.quizcadia.matching.Matchmaker;
import com.fazeel.quizcadia.matching.Presence;
import com.fazeel.quizcadia.matching.PrivateRooms;
import com.fazeel.quizcadia.matching.QuickMatch;
import com.fazeel.quizcadia.matching.MatchmakingRules;

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
	java.util.function.Consumer<com.fazeel.quizcadia.domain.Match> matchPublisher(Presence presence,
			KafkaTemplate<String, Object> kafka, @Value("${quizcadia.kafka.enabled}") boolean kafkaEnabled,
			@Value("${quizcadia.topics.players-matched}") String topic) {
		return raw -> {
			com.fazeel.quizcadia.domain.Match match = presence.enrich(raw);
			log.info("Match {} for game {} with players {}", match.matchId(), match.gameId(), match.players());
			if (kafkaEnabled) {
				kafka.send(topic, match.matchId(), match);
			}
		};
	}

	@Bean
	Matchmaker matchmaker(MatchmakingRules rules, java.util.function.Consumer<com.fazeel.quizcadia.domain.Match> matchPublisher) {
		return new Matchmaker(rules, Clock.systemUTC(), matchPublisher);
	}

	@Bean
	PrivateRooms privateRooms(java.util.function.Consumer<com.fazeel.quizcadia.domain.Match> matchPublisher) {
		return new PrivateRooms(Clock.systemUTC(), new java.security.SecureRandom(), matchPublisher);
	}

	@Bean
	QuickMatch quickMatch(org.springframework.web.client.RestClient.Builder http,
			@Value("${quizcadia.game-manager-url}") String gameManagerUrl,
			java.util.function.Consumer<com.fazeel.quizcadia.domain.Match> matchPublisher) {
		return new QuickMatch(Clock.systemUTC(), new java.security.SecureRandom(),
				new com.fazeel.quizcadia.config.MultiplayerGames(http, gameManagerUrl, Clock.systemUTC()), matchPublisher);
	}

	/** Background ticks (lobby timeouts, bots, forgetting old presence). Tests switch them off with quizcadia.timers.enabled=false. */
	@Bean
	@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "quizcadia.timers.enabled", havingValue = "true", matchIfMissing = true)
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
