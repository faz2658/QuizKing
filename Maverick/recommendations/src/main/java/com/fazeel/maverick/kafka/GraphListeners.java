package com.fazeel.maverick.kafka;

import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fazeel.maverick.domain.Events.AdaptiveResult;
import com.fazeel.maverick.domain.Events.GameCard;
import com.fazeel.maverick.domain.Events.MultiPlayerResult;
import com.fazeel.maverick.domain.Events.SinglePlayerResult;
import com.fazeel.maverick.domain.Events.UserRegistered;
import com.fazeel.maverick.graph.RecommendationGraph;
import com.fazeel.maverick.recommend.GameMode;

/** Keeps the recommendation graph in sync with games, users and results from the other services. */
@Component
@ConditionalOnProperty(name = "maverick.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class GraphListeners {

	private static final String TYPE = "spring.json.value.default.type=com.fazeel.maverick.domain.Events$";

	private final RecommendationGraph graph;

	public GraphListeners(RecommendationGraph graph) {
		this.graph = graph;
	}

	@KafkaListener(topics = "${maverick.topics.game-created}", properties = TYPE + "GameCard")
	public void game(GameCard g) {
		graph.upsertGame(g.gameId(), g.gameName(), g.gameImage(), g.categoryId(), GameMode.SINGLE);
	}

	@KafkaListener(topics = "${maverick.topics.multiplayer-game-created}", properties = TYPE + "GameCard")
	public void multiGame(GameCard g) {
		graph.upsertGame(g.gameId(), g.gameName(), g.gameImage(), g.categoryId(), GameMode.MULTI);
	}

	@KafkaListener(topics = "${maverick.topics.game-deleted}", properties = "spring.json.value.default.type=java.lang.Integer")
	public void deleted(Integer gameId) {
		graph.deleteGame(gameId);
	}

	@KafkaListener(topics = "${maverick.topics.user-registered}", properties = TYPE + "UserRegistered")
	public void user(UserRegistered u) {
		graph.upsertUser(u.userId(), u.userName());
	}

	@KafkaListener(topics = "${maverick.topics.single-player-result}", properties = TYPE + "SinglePlayerResult")
	public void single(SinglePlayerResult r) {
		graph.recordPlay(r.userId(), r.gameId(), Instant.now());
	}

	@KafkaListener(topics = "${maverick.topics.adaptive-result}", properties = TYPE + "AdaptiveResult")
	public void adaptive(AdaptiveResult r) {
		graph.recordPlay(r.userId(), r.gameId(), Instant.now());
	}

	@KafkaListener(topics = "${maverick.topics.multiplayer-result}", properties = TYPE + "MultiPlayerResult")
	public void multi(MultiPlayerResult r) {
		if (r.standings() != null) {
			r.standings().forEach(s -> graph.recordPlay(s.userId(), r.gameId(), Instant.now()));
		}
	}
}
