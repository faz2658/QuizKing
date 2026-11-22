package com.fazeel.quizcadia;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestClient;

import com.fazeel.quizcadia.config.MultiplayerGames;
import com.fazeel.quizcadia.domain.Match;
import com.fazeel.quizcadia.matching.Matchmaker;
import com.fazeel.quizcadia.matching.MatchmakingRules;
import com.fazeel.quizcadia.matching.Presence;
import com.fazeel.quizcadia.matching.PrivateRooms;
import com.fazeel.quizcadia.matching.QuickMatch;

/**
 * The player matcher: per-game lobbies, the global Quick Match queue, private and party rooms, and who is online.
 * Formed matches go to the multiplayer engine over Kafka.
 */
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
	Consumer<Match> matchPublisher(Presence presence,
			KafkaTemplate<String, Object> kafka, @Value("${quizcadia.kafka.enabled}") boolean kafkaEnabled,
			@Value("${quizcadia.topics.players-matched}") String topic) {
		return raw -> {
			Match match = presence.enrich(raw);
			log.info("Match {} for game {} with players {}", match.matchId(), match.gameId(), match.players());
			if (kafkaEnabled) {
				kafka.send(topic, match.matchId(), match);
			}
		};
	}

	@Bean
	Matchmaker matchmaker(MatchmakingRules rules, Consumer<Match> matchPublisher) {
		return new Matchmaker(rules, Clock.systemUTC(), matchPublisher);
	}

	@Bean
	PrivateRooms privateRooms(Consumer<Match> matchPublisher) {
		return new PrivateRooms(Clock.systemUTC(), new SecureRandom(), matchPublisher);
	}

	@Bean
	QuickMatch quickMatch(RestClient.Builder http,
			@Value("${quizcadia.game-manager-url}") String gameManagerUrl,
			Consumer<Match> matchPublisher) {
		return new QuickMatch(Clock.systemUTC(), new SecureRandom(),
				new MultiplayerGames(http, gameManagerUrl, Clock.systemUTC()), matchPublisher);
	}

	/** Background ticks (lobby timeouts, bots, forgetting old presence). Tests switch them off with quizcadia.timers.enabled=false. */
	@Bean
	@ConditionalOnProperty(name = "quizcadia.timers.enabled", havingValue = "true", matchIfMissing = true)
	LobbyTimer lobbyTimer(Matchmaker matchmaker, PrivateRooms rooms, QuickMatch quick, Presence presence) {
		return new LobbyTimer(matchmaker, rooms, quick, presence);
	}

	/**
	 * Twice a second: starts or expires lobbies, rooms and the Quick Match queue whose time is up; hourly: forgets
	 * long-gone players.
	 */
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
